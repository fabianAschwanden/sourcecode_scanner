package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.FixRequest;
import ch.fabianaschwanden.sourcescanner.domain.model.PrRef;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationProposal;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationStatus;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.port.in.RemediateUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.FindingPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.MetricsPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.PrCreationPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RemediationProposalPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositorySourcePort;
import ch.fabianaschwanden.sourcescanner.domain.service.RemediationPlanner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Auto-Fix per PR/MR (RMR-10): lädt einen Fund + seine Quelle, holt einen redigierten Fix-Vorschlag,
 * plant ihn und reicht ihn als Pull/Merge Request ein — nie Direct-Push (RMR-11). Standardmässig
 * deaktiviert; läuft nur, wenn Remediation global <b>und</b> für das Repo opt-in ist (RMR-02). RBAC
 * (Operator/Admin) wird in der REST-Schicht erzwungen; jede Aktion wird auditiert (RMR-03).
 */
@ApplicationScoped
public class RemediationService implements RemediateUseCase {

    private final FindingPort findings;
    private final RepositorySourcePort sources;
    private final RemediationProposalPort proposals;
    private final Instance<PrCreationPort> prCreators;
    private final AuditPort audit;
    private final MetricsPort metrics;
    private final boolean globalEnabled;
    private final boolean autoMode;
    private final String branchPrefix;
    private final List<String> reviewers;
    private final List<String> labels;
    private final Optional<String> fixTokenRef;

    @Inject
    public RemediationService(
            FindingPort findings,
            RepositorySourcePort sources,
            RemediationProposalPort proposals,
            @jakarta.enterprise.inject.Any Instance<PrCreationPort> prCreators,
            AuditPort audit,
            MetricsPort metrics,
            @ConfigProperty(name = "scanner.remediation.auto-fix.enabled", defaultValue = "false") boolean globalEnabled,
            @ConfigProperty(name = "scanner.remediation.auto-fix.mode", defaultValue = "proposal") String mode,
            @ConfigProperty(name = "scanner.remediation.auto-fix.branch-prefix", defaultValue = "fix/scanner-") String branchPrefix,
            @ConfigProperty(name = "scanner.remediation.auto-fix.reviewers") Optional<List<String>> reviewers,
            @ConfigProperty(name = "scanner.remediation.auto-fix.labels") Optional<List<String>> labels,
            @ConfigProperty(name = "scanner.remediation.auto-fix.token-ref") Optional<String> fixTokenRef) {
        this.findings = findings;
        this.sources = sources;
        this.proposals = proposals;
        this.prCreators = prCreators;
        this.audit = audit;
        this.metrics = metrics;
        this.globalEnabled = globalEnabled;
        this.autoMode = "auto".equalsIgnoreCase(mode);
        this.branchPrefix = branchPrefix;
        this.reviewers = reviewers.orElseGet(List::of);
        this.labels = labels.orElseGet(List::of);
        this.fixTokenRef = fixTokenRef;
    }

    @Override
    @Transactional
    public PrRef remediate(UUID findingId, String actor) {
        if (!globalEnabled) {
            metrics.recordRemediation("auto-fix", "blocked");
            throw new IllegalStateException("remediation is globally disabled (scanner.remediation.auto-fix.enabled)");
        }
        StoredFinding finding = findings.byId(findingId)
                .orElseThrow(() -> new IllegalArgumentException("finding not found: " + findingId));
        RepositorySource source = sourceFor(finding)
                .orElseThrow(() -> new IllegalStateException("no managed repository for finding: " + findingId));
        if (!source.remediationEnabled()) {
            metrics.recordRemediation("auto-fix", "blocked");
            throw new IllegalStateException("remediation not enabled for repository: " + source.name());
        }

        RemediationProposal proposal = proposals.proposeForStored(finding)
                .orElseThrow(() -> new IllegalStateException("no fix proposal available for finding: " + findingId));
        RemediationPlanner.FixPlan plan = RemediationPlanner.plan(List.of(proposal), autoMode);
        if (plan.isEmpty()) {
            metrics.recordRemediation("auto-fix", "blocked");
            throw new IllegalStateException("fix plan produced no edits for finding: " + findingId);
        }

        PrCreationPort creator = prCreators.stream().filter(c -> c.supports(source.type())).findFirst()
                .orElseThrow(() -> new IllegalStateException("no PR adapter for platform type: " + source.type()));

        // Token: getrennte, höher privilegierte Fix-Referenz, sonst die Quell-Referenz (RMR-44).
        String tokenRef = fixTokenRef.filter(s -> !s.isBlank()).orElse(source.tokenRef());
        String fixBranch = branchPrefix + finding.id();
        FixRequest request = new FixRequest(source.location(), defaultBranch(source), fixBranch,
                "chore(security): auto-fix " + finding.ruleId(), plan.edits(),
                description(finding, plan), reviewers, labels, tokenRef);

        try {
            PrRef pr = creator.createFixPr(request);
            findings.save(finding.withRemediationStatus(RemediationStatus.PR_OPEN));
            audit.record(AuditEvent.of(actor, "remediation.auto-fix", finding.id().toString(),
                    "PR " + pr.url() + " für " + finding.ruleId() + " in " + finding.file() + ":" + finding.line()));
            metrics.recordRemediation("auto-fix", "success");
            return pr;
        } catch (RuntimeException e) {
            metrics.recordRemediation("auto-fix", "error");
            audit.record(AuditEvent.of(actor, "remediation.auto-fix.failed", finding.id().toString(),
                    "Fehler bei Auto-Fix: " + e.getMessage()));
            throw e;
        }
    }

    /** Findet die verwaltete Quelle, deren {@code name} dem {@code repoId} des Fundes entspricht. */
    private Optional<RepositorySource> sourceFor(StoredFinding finding) {
        return sources.all().stream().filter(s -> s.name().equals(finding.repoId())).findFirst();
    }

    private String defaultBranch(RepositorySource source) {
        return source.branches().isEmpty() ? "main" : source.branches().get(0);
    }

    /** PR-Beschreibung — garantiert redigiert: nur der bereits redigierte Plan-Text, kein Klartext (RMR-12). */
    private String description(StoredFinding finding, RemediationPlanner.FixPlan plan) {
        StringBuilder b = new StringBuilder();
        b.append("Automatischer Sicherheits-Fix für ").append(finding.ruleId())
                .append(" in ").append(finding.file()).append(":").append(finding.line()).append(".\n\n");
        b.append(plan.summary());
        b.append("\nCheckliste:\n- [ ] Secret rotieren\n- [ ] Fix prüfen und mergen\n");
        b.append("- [ ] Optional: History-Scrub veranlassen, falls das Secret committet war\n");
        if (plan.reviewRequired()) {
            b.append("\n> Vorschlags-PR — bitte vor dem Merge fachlich prüfen.");
        }
        return b.toString();
    }
}
