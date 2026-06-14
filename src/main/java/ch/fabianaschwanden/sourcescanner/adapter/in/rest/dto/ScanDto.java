package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import java.time.Instant;
import java.util.UUID;

/** REST-Transport eines Scan-Laufs inkl. Herkunft (Server/CI) und CI-Metadaten (WR-69). */
public record ScanDto(
        UUID id,
        String repoId,
        String mode,
        String status,
        int progress,
        int findingCount,
        Instant startedAt,
        Instant finishedAt,
        String trigger,
        String ciPipelineUrl,
        String ciCommit,
        String ciBranch,
        String ciActor) {

    public static ScanDto from(ScanRecord r) {
        return new ScanDto(r.id(), r.repoId(), r.mode(), r.status().name(), r.progress(),
                r.findingCount(), r.startedAt(), r.finishedAt(), r.trigger().name(),
                r.ci().pipelineUrl(), r.ci().commit(), r.ci().branch(), r.ci().actor());
    }
}
