package ch.fabianaschwanden.sourcescanner.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistenter Datensatz eines server-getriebenen Scan-Laufs (docs/06). Reines Domänen-Modell; das
 * Mapping auf die JPA-Entity liegt im Persistence-Adapter (TR-23). {@code progress} ist 0..100.
 */
public record ScanRecord(
        UUID id,
        String repoId,
        String mode,
        ScanStatus status,
        int progress,
        int findingCount,
        Instant startedAt,
        Instant finishedAt,
        ScanTrigger trigger,
        CiMetadata ci) {

    public ScanRecord {
        if (id == null) {
            throw new IllegalArgumentException("scan record id must not be null");
        }
        if (repoId == null || repoId.isBlank()) {
            throw new IllegalArgumentException("repoId must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("progress must be within 0..100");
        }
        trigger = trigger == null ? ScanTrigger.SERVER : trigger;
        ci = ci == null ? CiMetadata.NONE : ci;
    }

    /** Bequemer Konstruktor für server-getriebene Läufe (Abwärtskompatibilität). */
    public ScanRecord(UUID id, String repoId, String mode, ScanStatus status, int progress,
                      int findingCount, Instant startedAt, Instant finishedAt) {
        this(id, repoId, mode, status, progress, findingCount, startedAt, finishedAt,
                ScanTrigger.SERVER, CiMetadata.NONE);
    }

    /** Neuer server-getriebener Lauf im Status RUNNING. */
    public static ScanRecord starting(UUID id, String repoId, String mode) {
        return new ScanRecord(id, repoId, mode, ScanStatus.RUNNING, 0, 0, Instant.now(), null);
    }

    /** Abgeschlossener, aus CI/CD eingelieferter Lauf (IR-22/25). */
    public static ScanRecord ingested(UUID id, String repoId, String mode, ScanStatus status,
                                      int findingCount, CiMetadata ci) {
        return new ScanRecord(id, repoId, mode, status, 100, findingCount, Instant.now(), Instant.now(),
                ScanTrigger.CI, ci);
    }

    public ScanRecord withProgress(int newProgress) {
        return new ScanRecord(id, repoId, mode, status, newProgress, findingCount, startedAt, finishedAt,
                trigger, ci);
    }

    public ScanRecord completed(int findings) {
        return new ScanRecord(id, repoId, mode, ScanStatus.COMPLETED, 100, findings, startedAt, Instant.now(),
                trigger, ci);
    }

    public ScanRecord failed() {
        return new ScanRecord(id, repoId, mode, ScanStatus.FAILED, progress, findingCount, startedAt,
                Instant.now(), trigger, ci);
    }

    public ScanRecord cancelled() {
        return new ScanRecord(id, repoId, mode, ScanStatus.CANCELLED, progress, findingCount, startedAt,
                Instant.now(), trigger, ci);
    }
}
