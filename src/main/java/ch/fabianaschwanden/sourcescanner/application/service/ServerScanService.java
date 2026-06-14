package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.application.service.ScanProgressBroadcaster.ScanEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.AggregatedFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.HistoryMode;
import ch.fabianaschwanden.sourcescanner.domain.model.OutputConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Policy;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanStatus;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageScansUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.FindingPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositorySourcePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
import ch.fabianaschwanden.sourcescanner.domain.port.in.StartScanUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

/**
 * Server-getriebene Scan-Steuerung (WR-03): startet Scans asynchron über den bestehenden
 * {@link StartScanUseCase}, persistiert Lauf + redigierte Funde und sendet Live-Events (WR-04).
 * Keine Geschäftsregeln — Orchestrierung/Persistenz/Audit werden delegiert.
 */
@ApplicationScoped
public class ServerScanService implements ManageScansUseCase {

    private static final Logger LOG = Logger.getLogger(ServerScanService.class);

    private final RepositorySourcePort sources;
    private final StartScanUseCase orchestrator;
    private final ScanRecordPort scanRecords;
    private final FindingPort findings;
    private final AuditPort audit;
    private final ScanProgressBroadcaster broadcaster;
    private final ManagedExecutor executor;
    private final ServerScanPersistence persistence;
    private final ch.fabianaschwanden.sourcescanner.domain.port.out.MetricsPort metrics;
    private final ch.fabianaschwanden.sourcescanner.domain.port.in.ManagePoliciesUseCase policies;
    private final ch.fabianaschwanden.sourcescanner.domain.port.out.EmailNotificationPort email;

    /** Laufende Scans für die Abbruch-Anforderung (best effort). */
    private final ConcurrentHashMap<UUID, Boolean> cancelRequested = new ConcurrentHashMap<>();

    @Inject
    public ServerScanService(RepositorySourcePort sources, StartScanUseCase orchestrator,
                             ScanRecordPort scanRecords, FindingPort findings, AuditPort audit,
                             ScanProgressBroadcaster broadcaster, ManagedExecutor executor,
                             ServerScanPersistence persistence,
                             ch.fabianaschwanden.sourcescanner.domain.port.out.MetricsPort metrics,
                             ch.fabianaschwanden.sourcescanner.domain.port.in.ManagePoliciesUseCase policies,
                             ch.fabianaschwanden.sourcescanner.domain.port.out.EmailNotificationPort email) {
        this.sources = sources;
        this.orchestrator = orchestrator;
        this.scanRecords = scanRecords;
        this.findings = findings;
        this.audit = audit;
        this.broadcaster = broadcaster;
        this.executor = executor;
        this.persistence = persistence;
        this.metrics = metrics;
        this.policies = policies;
        this.email = email;
    }

    @Override
    public ScanRecord startScan(UUID sourceId, String mode, String actor) {
        RepositorySource source = sources.byId(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("unknown repository source: " + sourceId));
        UUID scanId = UUID.randomUUID();
        HistoryMode historyMode = parseMode(mode);
        ScanRecord starting = ScanRecord.starting(scanId, source.name(), historyMode.name().toLowerCase(Locale.ROOT));
        persistence.saveRecord(starting);
        audit.record(AuditEvent.of(actor, "scan.start", source.name(), "mode=" + historyMode));

        // Policy im Request-Kontext auflösen (DB-Zugriff); der async Lauf macht keine Config-DB-Reads.
        Policy policy = policies.resolveFor(source.name());
        executor.runAsync(() -> runScan(starting, source, historyMode, policy));
        return starting;
    }

    private void runScan(ScanRecord starting, RepositorySource source, HistoryMode mode, Policy policy) {
        UUID scanId = starting.id();
        broadcaster.publish(new ScanEvent(scanId, ScanStatus.RUNNING.name(), 10, 0));
        try {
            ScanConfig config = configFor(source, mode, policy);
            // Granularer Fortschritt während des Laufs (WR-04b): jede Orchestrator-Meldung wird als
            // RUNNING-Event an die SSE-Abonnenten verteilt.
            List<ScanResult> results = orchestrator.scan(config,
                    percent -> broadcaster.publish(new ScanEvent(scanId, ScanStatus.RUNNING.name(), percent, 0)));
            if (cancelRequested.remove(scanId) != null) {
                finish(starting.cancelled(), List.of());
                return;
            }
            List<AggregatedFinding> aggregated = results.stream()
                    .flatMap(r -> r.aggregated().stream())
                    .toList();
            List<StoredFinding> stored = aggregated.stream()
                    .map(a -> StoredFinding.from(scanId, source.name(), a))
                    .toList();
            ScanRecord completed = starting.completed(stored.size());
            finish(completed, stored);
            sendReport(source, completed, stored);
        } catch (RuntimeException e) {
            LOG.errorf(e, "scan %s failed", scanId);
            persistence.saveRecord(starting.failed(shortReason(e)));
            broadcaster.publish(new ScanEvent(scanId, ScanStatus.FAILED.name(), 100, 0));
            broadcaster.complete(scanId);
        }
    }

    /** Kurze, anzeigetaugliche Fehlerursache (keine Stacktraces); nutzt die Message der Ursache. */
    private String shortReason(RuntimeException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String msg = cause.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = cause.getClass().getSimpleName();
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }

    private void finish(ScanRecord record, List<StoredFinding> stored) {
        persistence.persistResult(record, stored);
        metrics.recordScan(record.repoId(), record.status(),
                java.time.Duration.between(record.startedAt(),
                        record.finishedAt() == null ? java.time.Instant.now() : record.finishedAt()));
        broadcaster.publish(new ScanEvent(record.id(), record.status().name(), 100, stored.size()));
        broadcaster.complete(record.id());
    }

    /**
     * Versendet nach einem Scan einen Report an die für das Repo hinterlegten Empfänger (IR-53).
     * Opt-in über vorhandene Empfänger + aktivierten Mailer; nur redigierte Inhalte (FR-18).
     */
    private void sendReport(RepositorySource source, ScanRecord record, List<StoredFinding> stored) {
        if (source.reportEmails().isEmpty() || !email.enabled()) {
            return;
        }
        String body = buildReportBody(record, stored);
        String subject = "[scanner] " + source.name() + ": " + stored.size() + " Fund(e)";
        email.send(new ch.fabianaschwanden.sourcescanner.domain.model.EmailReport(
                source.reportEmails(), subject, body));
        audit.record(AuditEvent.of("system", "report.email", source.name(),
                "recipients=" + source.reportEmails().size()));
    }

    /** Redigierte Report-Zusammenfassung: Severity-Verteilung + Top-Funde (kein Klartext). */
    private String buildReportBody(ScanRecord record, List<StoredFinding> stored) {
        var bySeverity = stored.stream().collect(java.util.stream.Collectors.groupingBy(
                StoredFinding::severity, java.util.stream.Collectors.counting()));
        StringBuilder b = new StringBuilder();
        b.append("Scan-Report für ").append(record.repoId()).append('\n');
        b.append("Status: ").append(record.status()).append(", Funde gesamt: ").append(stored.size()).append("\n\n");
        b.append("Nach Severity:\n");
        bySeverity.forEach((sev, count) -> b.append("  ").append(sev).append(": ").append(count).append('\n'));
        b.append("\nFunde (redigiert):\n");
        stored.stream().limit(50).forEach(f -> b.append("  ").append(f.severity()).append("  ")
                .append(f.ruleId()).append("  ").append(f.file()).append(':').append(f.line())
                .append("  ").append(f.redactedMatch()).append('\n'));
        if (stored.size() > 50) {
            b.append("  … und ").append(stored.size() - 50).append(" weitere.\n");
        }
        return b.toString();
    }

    @Override
    public List<ScanRecord> recentScans(int limit) {
        return scanRecords.recent(limit);
    }

    @Override
    public void cancelScan(UUID scanId, String actor) {
        cancelRequested.put(scanId, Boolean.TRUE);
        audit.record(AuditEvent.of(actor, "scan.cancel", scanId.toString(), null));
    }

    /**
     * Baut die Ein-Repo-Scan-Konfiguration aus der für die Org-Unit (Quellenname) aufgelösten Policy
     * (FR-20): Gate-Schwelle und aktivierte Detektor-Gruppen kommen aus der Governance, nicht mehr
     * hardkodiert. Ohne passende Policy gilt die Default-/Fallback-Policy.
     */
    private ScanConfig configFor(RepositorySource source, HistoryMode mode, Policy policy) {
        Map<String, DetectorConfig> detectors = new java.util.LinkedHashMap<>();
        for (String group : policy.enabledDetectorGroups()) {
            detectors.put(group, new DetectorConfig(true, Map.of()));
        }
        return new ScanConfig(
                List.of(source.toRef()), List.of(), mode,
                Runtime.getRuntime().availableProcessors(), 30, detectors,
                policy.gate(), OutputConfig.defaults(), null, List.of(), false, null);
    }

    private HistoryMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return HistoryMode.FULL;
        }
        try {
            return HistoryMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return HistoryMode.FULL;
        }
    }

    /** Findings-Zugriff für eine schnelle Dashboard-/Listen-Abfrage (Delegation an den Port). */
    public List<StoredFinding> latestFindings(FindingPort.FindingQuery query) {
        return findings.query(query);
    }
}
