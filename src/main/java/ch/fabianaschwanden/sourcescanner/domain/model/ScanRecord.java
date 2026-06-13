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
        Instant finishedAt) {

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
    }

    /** Neuer Lauf im Status RUNNING. */
    public static ScanRecord starting(UUID id, String repoId, String mode) {
        return new ScanRecord(id, repoId, mode, ScanStatus.RUNNING, 0, 0, Instant.now(), null);
    }

    public ScanRecord withProgress(int newProgress) {
        return new ScanRecord(id, repoId, mode, status, newProgress, findingCount, startedAt, finishedAt);
    }

    public ScanRecord completed(int findings) {
        return new ScanRecord(id, repoId, mode, ScanStatus.COMPLETED, 100, findings, startedAt, Instant.now());
    }

    public ScanRecord failed() {
        return new ScanRecord(id, repoId, mode, ScanStatus.FAILED, progress, findingCount, startedAt, Instant.now());
    }

    public ScanRecord cancelled() {
        return new ScanRecord(id, repoId, mode, ScanStatus.CANCELLED, progress, findingCount, startedAt, Instant.now());
    }
}
