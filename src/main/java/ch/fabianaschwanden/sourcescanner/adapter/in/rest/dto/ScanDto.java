package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import java.time.Instant;
import java.util.UUID;

/** REST-Transport eines Scan-Laufs. */
public record ScanDto(
        UUID id,
        String repoId,
        String mode,
        String status,
        int progress,
        int findingCount,
        Instant startedAt,
        Instant finishedAt) {

    public static ScanDto from(ScanRecord r) {
        return new ScanDto(r.id(), r.repoId(), r.mode(), r.status().name(), r.progress(),
                r.findingCount(), r.startedAt(), r.finishedAt());
    }
}
