package ch.fabianaschwanden.sourcescanner.application.service;

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
import java.util.Optional;
import java.util.UUID;
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
    private final ServerScanPersistence persistence;
    private final ch.fabianaschwanden.sourcescanner.domain.port.out.MetricsPort metrics;
    private final ch.fabianaschwanden.sourcescanner.domain.port.in.ManagePoliciesUseCase policies;
    private final ch.fabianaschwanden.sourcescanner.domain.port.out.EmailNotificationPort email;
    private final ch.fabianaschwanden.sourcescanner.domain.port.out.RulesetPort rulesets;

    /** Max. gleichzeitige Scans <b>pro Pod</b> (OOM-Schutz auf kleinen Instanzen); konfigurierbar. */
    @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "scanner.scan.max-concurrent", defaultValue = "2")
    int maxConcurrentScans;

    @Inject
    public ServerScanService(RepositorySourcePort sources, StartScanUseCase orchestrator,
                             ScanRecordPort scanRecords, FindingPort findings, AuditPort audit,
                             ServerScanPersistence persistence,
                             ch.fabianaschwanden.sourcescanner.domain.port.out.MetricsPort metrics,
                             ch.fabianaschwanden.sourcescanner.domain.port.in.ManagePoliciesUseCase policies,
                             ch.fabianaschwanden.sourcescanner.domain.port.out.EmailNotificationPort email,
                             ch.fabianaschwanden.sourcescanner.domain.port.out.RulesetPort rulesets) {
        this.sources = sources;
        this.orchestrator = orchestrator;
        this.scanRecords = scanRecords;
        this.findings = findings;
        this.audit = audit;
        this.persistence = persistence;
        this.metrics = metrics;
        this.policies = policies;
        this.email = email;
        this.rulesets = rulesets;
    }

    @Override
    public ScanRecord startScan(UUID sourceId, String mode, String actor) {
        RepositorySource source = sources.byId(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("unknown repository source: " + sourceId));
        UUID scanId = UUID.randomUUID();
        HistoryMode historyMode = parseMode(mode);

        // Immer QUEUED in die DB schreiben — ausgeführt wird der Lauf von einem Pod-Worker, der ihn
        // atomar claimt (horizontale Skalierung). Kein In-Memory-Zustand, keine Pod-Affinität.
        ScanRecord record = ScanRecord.queued(scanId, source.name(),
                historyMode.name().toLowerCase(Locale.ROOT));
        persistence.saveRecord(record);
        audit.record(AuditEvent.of(actor, "scan.start", source.name(),
                "mode=" + historyMode + "; QUEUED"));
        return record;
    }

    /**
     * Führt einen vom {@link ScanWorker} bereits geclaimten (auf RUNNING gesetzten) Lauf aus. Die
     * Config (Source/Policy/Rulesets) wird hier aus der DB rekonstruiert — der claimende Pod braucht
     * keinen geteilten Speicherzustand. Läuft im Managed-Executor-Thread des Workers.
     */
    /** Max. gleichzeitige Scans pro Pod — vom {@link ScanWorker} für das Claim-Limit gelesen. */
    int maxConcurrentScans() {
        return maxConcurrentScans;
    }

    void runClaimed(ScanRecord claimed) {
        HistoryMode mode = parseMode(claimed.mode());
        RepositorySource source;
        Policy policy;
        ch.fabianaschwanden.sourcescanner.domain.service.RulesetResolution.Resolved resolvedRules;
        // Config-Rekonstruktion aus der DB: schlägt sie fehl, MUSS der Lauf als FAILED enden — sonst
        // bliebe der Datensatz RUNNING und der Reaper würde ihn endlos neu einreihen (Fehler-Schleife).
        try {
            Optional<RepositorySource> found = sources.byName(claimed.repoId());
            if (found.isEmpty()) {
                persistence.saveRecord(claimed.failed("repository source no longer exists: " + claimed.repoId()));
                return;
            }
            source = found.get();
            policy = policies.resolveFor(claimed.repoId());
            resolvedRules = ch.fabianaschwanden.sourcescanner.domain.service.RulesetResolution.resolve(
                    rulesets.all(), claimed.repoId());
        } catch (RuntimeException e) {
            LOG.errorf(e, "scan %s: could not resolve config", claimed.id());
            persistence.saveRecord(claimed.failed(shortReason(e)));
            return;
        }
        runScanInternal(claimed, source, mode, policy, resolvedRules);
    }

    private void runScanInternal(ScanRecord starting, RepositorySource source, HistoryMode mode, Policy policy,
                         ch.fabianaschwanden.sourcescanner.domain.service.RulesetResolution.Resolved resolvedRules) {
        UUID scanId = starting.id();
        try {
            ScanConfig config = configFor(source, mode, policy, resolvedRules);
            // Granularer Fortschritt während des Laufs (WR-04b): jede Orchestrator-Meldung schreibt
            // progress + Heartbeat in die DB — die SSE-Resource pollt das pod-übergreifend, und der
            // Reaper sieht am Heartbeat, dass der Lauf lebt. Zugleich Abbruch-Flag prüfen.
            List<ScanResult> results = orchestrator.scan(config, percent -> {
                scanRecords.save(starting.withProgress(percent));
                scanRecords.heartbeat(scanId);
            });
            if (scanRecords.isCancelRequested(scanId)) {
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
        // Terminaler Zustand wird persistiert; die SSE-Resource erkennt ihn beim DB-Polling und
        // schliesst den Stream (kein In-Process-Broadcast mehr — pod-übergreifend).
        persistence.persistResult(record, stored);
        metrics.recordScan(record.repoId(), record.status(),
                java.time.Duration.between(record.startedAt(),
                        record.finishedAt() == null ? java.time.Instant.now() : record.finishedAt()));
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
        scanRecords.byId(scanId).ifPresent(record -> {
            if (record.status() == ScanStatus.QUEUED) {
                // Noch nicht geclaimt: direkt als CANCELLED markieren — kein Pod wird ihn übernehmen.
                persistence.saveRecord(record.cancelled());
            } else if (record.status() == ScanStatus.RUNNING) {
                // Läuft (evtl. auf einem anderen Pod): Flag in der DB setzen; der ausführende Pod
                // prüft es an den Fortschritts-Checkpoints und bricht best-effort ab.
                scanRecords.requestCancel(scanId);
            }
        });
        audit.record(AuditEvent.of(actor, "scan.cancel", scanId.toString(), null));
    }

    /**
     * Baut die Ein-Repo-Scan-Konfiguration aus der für die Org-Unit (Quellenname) aufgelösten Policy
     * (FR-20): Gate-Schwelle und aktivierte Detektor-Gruppen kommen aus der Governance, nicht mehr
     * hardkodiert. Ohne passende Policy gilt die Default-/Fallback-Policy.
     */
    private ScanConfig configFor(RepositorySource source, HistoryMode mode, Policy policy,
                                 ch.fabianaschwanden.sourcescanner.domain.service.RulesetResolution.Resolved rules) {
        Map<String, DetectorConfig> detectors = new java.util.LinkedHashMap<>();
        for (String group : policy.enabledDetectorGroups()) {
            // Effektive Regel-Overrides (an/aus, Severity, Modus) als params an die Detektoren der
            // Gruppe durchreichen (DR-50..52); leer ⇒ Default-Verhalten der Detektoren.
            Map<String, Object> params = rules.isEmpty() ? Map.of()
                    : Map.of("ruleOverrides", ruleOverrideParams(rules));
            detectors.put(group, new DetectorConfig(true, params));
        }
        return new ScanConfig(
                List.of(source.toRef()), List.of(), mode,
                Runtime.getRuntime().availableProcessors(), 30, detectors,
                policy.gate(), OutputConfig.defaults(), null, List.of(), false, null);
    }

    /** Wandelt die effektiven Regel-Overrides in eine framework-neutrale params-Map (ruleId → Felder). */
    private Map<String, Object> ruleOverrideParams(
            ch.fabianaschwanden.sourcescanner.domain.service.RulesetResolution.Resolved rules) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        rules.byRule().forEach((ruleId, o) -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("enabled", o.enabled());
            m.put("severity", o.severity() == null ? null : o.severity().name());
            m.put("matchMode", o.matchMode().name());
            m.put("dataSourceName", o.dataSourceName());
            out.put(ruleId, m);
        });
        return out;
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
