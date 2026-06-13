package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationStatus;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import ch.fabianaschwanden.sourcescanner.domain.model.ScrubDryRun;
import ch.fabianaschwanden.sourcescanner.domain.model.ScrubReplacement;
import ch.fabianaschwanden.sourcescanner.domain.model.ScrubRequest;
import ch.fabianaschwanden.sourcescanner.domain.model.ScrubResult;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ScrubHistoryUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.FindingPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.HistoryRewritePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.MetricsPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositorySourcePort;
import ch.fabianaschwanden.sourcescanner.domain.service.ScrubWorkflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Treibt die History-Scrub-Orchestrierung (RMR-20..30): löst Quelle + offene Secret-Funde im
 * Request-Kontext auf, erzwingt über {@link ScrubWorkflow} die Gate-Reihenfolge (Rotation-Gate,
 * Dry-Run-Pflicht, Force-Push-Freigabe) und delegiert das eigentliche Rewrite an {@link HistoryRewritePort}.
 * Standardmässig deaktiviert (global + pro Repo, RMR-02). Jeder Schritt wird auditiert (RMR-03).
 *
 * <p>Der Dry-Run-Nachweis wird in-memory je Repo gehalten — {@code execute} ohne vorausgegangenen
 * Dry-Run blockiert (RMR-22). Das genügt für den Single-Node-Betrieb dieser Phase.
 */
@ApplicationScoped
public class HistoryScrubService implements ScrubHistoryUseCase {

    private final RepositorySourcePort sources;
    private final FindingPort findings;
    private final HistoryRewritePort rewrite;
    private final AuditPort audit;
    private final MetricsPort metrics;
    private final boolean globalEnabled;
    private final Set<UUID> dryRunDone = ConcurrentHashMap.newKeySet();

    @Inject
    public HistoryScrubService(
            RepositorySourcePort sources,
            FindingPort findings,
            HistoryRewritePort rewrite,
            AuditPort audit,
            MetricsPort metrics,
            @ConfigProperty(name = "scanner.remediation.history-scrub.enabled", defaultValue = "false") boolean globalEnabled) {
        this.sources = sources;
        this.findings = findings;
        this.rewrite = rewrite;
        this.audit = audit;
        this.metrics = metrics;
        this.globalEnabled = globalEnabled;
    }

    @Override
    @Transactional
    public ScrubDryRun dryRun(UUID repoId, String actor) {
        RepositorySource source = requireSource(repoId);
        ScrubWorkflow.Decision decision = ScrubWorkflow.evaluateDryRun(state(source, false, false));
        if (!decision.allowed()) {
            metrics.recordRemediation("scrub-dry-run", "blocked");
            audit.record(AuditEvent.of(actor, "remediation.scrub.dry-run.blocked", source.name(), decision.reason()));
            throw new IllegalStateException(decision.reason());
        }
        ScrubRequest request = request(source, false);
        ScrubDryRun result = rewrite.dryRun(request);
        dryRunDone.add(repoId);
        metrics.recordRemediation("scrub-dry-run", "success");
        audit.record(AuditEvent.of(actor, "remediation.scrub.dry-run", source.name(),
                result.affectedSecrets() + " Secret(s); Werkzeug verfügbar=" + result.toolAvailable()));
        return result;
    }

    @Override
    @Transactional
    public ScrubResult execute(UUID repoId, boolean forcePushApproved, boolean rotationConfirmed, String actor) {
        RepositorySource source = requireSource(repoId);
        boolean dryRunCompleted = dryRunDone.contains(repoId);
        ScrubWorkflow.State state = state(source, rotationConfirmed, forcePushApproved)
                .withDryRun(dryRunCompleted);
        ScrubWorkflow.Decision decision = ScrubWorkflow.evaluateExecute(state);
        if (!decision.allowed()) {
            metrics.recordRemediation("scrub-execute", "blocked");
            audit.record(AuditEvent.of(actor, "remediation.scrub.execute.blocked", source.name(),
                    decision.blockingGate() + ": " + decision.reason()));
            throw new IllegalStateException(decision.reason());
        }
        ScrubResult result = rewrite.execute(request(source, forcePushApproved));
        if (result.success()) {
            markScrubbed(source.name());
            dryRunDone.remove(repoId);
            metrics.recordRemediation("scrub-execute", "success");
        } else {
            metrics.recordRemediation("scrub-execute", "error");
        }
        audit.record(AuditEvent.of(actor, "remediation.scrub.execute", source.name(),
                "success=" + result.success() + "; verbleibend=" + result.remainingFindings()
                        + "; " + result.message()));
        return result;
    }

    private RepositorySource requireSource(UUID repoId) {
        return sources.byId(repoId)
                .orElseThrow(() -> new IllegalArgumentException("repository not found: " + repoId));
    }

    private ScrubWorkflow.State state(RepositorySource source, boolean rotationConfirmed, boolean forcePushApproved) {
        List<StoredFinding> openSecrets = openSecretFindings(source.name());
        boolean activeSecret = openSecrets.stream().anyMatch(StoredFinding::verified);
        return new ScrubWorkflow.State(
                globalEnabled && source.remediationEnabled(),
                false,
                activeSecret,
                rotationConfirmed,
                false,
                forcePushApproved,
                rewrite.available());
    }

    private ScrubRequest request(RepositorySource source, boolean forcePushApproved) {
        List<ScrubReplacement> replacements = openSecretFindings(source.name()).stream()
                .map(f -> new ScrubReplacement(f.fingerprint(), f.redactedMatch(), f.file(), f.line()))
                .toList();
        return new ScrubRequest(source.location(), source.tokenRef(), replacements, forcePushApproved);
    }

    private List<StoredFinding> openSecretFindings(String repoName) {
        return findings.query(new FindingPort.FindingQuery(repoName, null, null, TriageStatus.OPEN, 0, 500))
                .stream().filter(f -> f.category() == DetectorCategory.SECRET).toList();
    }

    private void markScrubbed(String repoName) {
        for (StoredFinding f : openSecretFindings(repoName)) {
            findings.save(f.withRemediationStatus(RemediationStatus.SCRUBBED));
        }
    }
}
