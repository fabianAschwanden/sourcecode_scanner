package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import java.time.Instant;
import java.util.UUID;

/**
 * REST-Transport eines Fundes. Trägt ausschliesslich den <b>redigierten</b> Treffer (WR-33, FR-18).
 * REST-DTOs leben nur im REST-Adapter (TR-23).
 */
public record FindingDto(
        UUID id,
        UUID scanId,
        String repoId,
        String detectorId,
        String category,
        String severity,
        String ruleId,
        String file,
        int line,
        String redactedMatch,
        boolean verified,
        String triageStatus,
        String triageReason,
        Instant firstSeen,
        Instant lastSeen) {

    public static FindingDto from(StoredFinding f) {
        return new FindingDto(f.id(), f.scanId(), f.repoId(), f.detectorId(), f.category().name(),
                f.severity().name(), f.ruleId(), f.file(), f.line(), f.redactedMatch(), f.verified(),
                f.triageStatus().name(), f.triageReason(), f.firstSeen(), f.lastSeen());
    }
}
