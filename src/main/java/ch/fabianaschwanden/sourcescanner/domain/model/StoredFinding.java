package ch.fabianaschwanden.sourcescanner.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistierter Fund im Server-Betrieb: ein aggregierter Fund mit stabiler ID und Triage-Status.
 * Trägt ausschliesslich den <b>redigierten</b> Treffer (FR-18, WR-33). Reines Domänen-Modell; das
 * Entity-Mapping liegt im Persistence-Adapter (TR-23).
 */
public record StoredFinding(
        UUID id,
        UUID scanId,
        String repoId,
        String detectorId,
        DetectorCategory category,
        Severity severity,
        String ruleId,
        String file,
        int line,
        String redactedMatch,
        String fingerprint,
        boolean verified,
        TriageStatus triageStatus,
        String triageReason,
        RemediationStatus remediationStatus,
        Instant firstSeen,
        Instant lastSeen) {

    public StoredFinding {
        if (id == null) {
            throw new IllegalArgumentException("finding id must not be null");
        }
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        if (redactedMatch == null) {
            throw new IllegalArgumentException("redactedMatch must not be null");
        }
        triageStatus = triageStatus == null ? TriageStatus.OPEN : triageStatus;
        remediationStatus = remediationStatus == null ? RemediationStatus.OPEN : remediationStatus;
    }

    /** Erzeugt einen persistierbaren Fund aus einem aggregierten Fund eines Laufs. */
    public static StoredFinding from(UUID scanId, String repoId, AggregatedFinding agg) {
        Finding f = agg.finding();
        return new StoredFinding(
                UUID.randomUUID(), scanId, repoId, f.detectorId(), f.category(), f.severity(),
                f.ruleId(), f.file(), f.line(), f.redactedMatch(), agg.fingerprint(), f.verified(),
                agg.suppressed() ? TriageStatus.SUPPRESSED : (agg.baseline() ? TriageStatus.BASELINE : TriageStatus.OPEN),
                null, RemediationStatus.OPEN, agg.firstSeen(), agg.lastSeen());
    }

    public StoredFinding withTriage(TriageStatus status, String reason) {
        return new StoredFinding(id, scanId, repoId, detectorId, category, severity, ruleId, file, line,
                redactedMatch, fingerprint, verified, status, reason, remediationStatus, firstSeen, lastSeen);
    }

    public StoredFinding withRemediationStatus(RemediationStatus status) {
        return new StoredFinding(id, scanId, repoId, detectorId, category, severity, ruleId, file, line,
                redactedMatch, fingerprint, verified, triageStatus, triageReason, status, firstSeen, lastSeen);
    }
}
