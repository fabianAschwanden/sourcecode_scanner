package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.in.IngestScanResultUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Nimmt das Ergebnis eines CI/CD-Laufs entgegen und legt es in der zentralen DB ab (IR-22): erzeugt
 * einen {@link ScanRecord} mit {@code trigger=CI} und persistiert die bereits redigierten Funde.
 * Idempotent über {@code ci.runRef} (IR-25): dieselbe Lauf-Referenz ersetzt den bestehenden Lauf.
 * RBAC (Rolle {@code ci}) erzwingt die REST-Schicht; jede Einlieferung wird auditiert (NFR-11).
 */
@ApplicationScoped
public class IngestScanService implements IngestScanResultUseCase {

    private final ScanRecordPort scanRecords;
    private final ServerScanPersistence persistence;
    private final AuditPort audit;

    @Inject
    public IngestScanService(ScanRecordPort scanRecords, ServerScanPersistence persistence, AuditPort audit) {
        this.scanRecords = scanRecords;
        this.persistence = persistence;
        this.audit = audit;
    }

    @Override
    public ScanRecord ingest(IngestRequest request, String actor) {
        // Idempotenz: bekannte runRef ⇒ bestehende Scan-ID wiederverwenden (ersetzen statt duplizieren).
        UUID scanId = scanRecords.byCiRunRef(request.ci().runRef())
                .map(ScanRecord::id)
                .orElseGet(UUID::randomUUID);

        ScanRecord record = ScanRecord.ingested(scanId, request.repoId(), request.mode(),
                request.status(), request.findings().size(), request.ci());

        List<StoredFinding> stored = request.findings().stream()
                .map(f -> toStored(scanId, request.repoId(), f))
                .toList();

        persistence.persistResult(record, stored);
        audit.record(AuditEvent.of(actor, "scan.ingest", request.repoId(),
                "CI-Lauf eingeliefert: " + stored.size() + " Fund(e), runRef=" + safe(request.ci().runRef())));
        return record;
    }

    private StoredFinding toStored(UUID scanId, String repoId, IngestedFinding f) {
        // Stabiler Fingerprint für Dedup/Baseline; eingelieferte Funde tragen nur den redigierten Treffer.
        String fingerprint = f.fingerprint() == null || f.fingerprint().isBlank()
                ? repoId + ":" + f.ruleId() + ":" + f.file() + ":" + f.line()
                : f.fingerprint();
        Instant now = Instant.now();
        return new StoredFinding(UUID.randomUUID(), scanId, repoId, f.detectorId(), f.category(),
                f.severity(), f.ruleId(), f.file(), f.line(), f.redactedMatch(), fingerprint, f.verified(),
                TriageStatus.OPEN, null, null, now, now);
    }

    private String safe(String runRef) {
        return runRef == null ? "—" : runRef;
    }
}
