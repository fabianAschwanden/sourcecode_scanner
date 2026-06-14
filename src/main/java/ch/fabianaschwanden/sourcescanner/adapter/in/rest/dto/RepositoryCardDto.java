package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryCard;
import java.time.Instant;
import java.util.UUID;

/** REST-Transport einer Repo-Karte für die Übersicht (WR-82); ohne Lizenz/Sterne. */
public record RepositoryCardDto(
        UUID id,
        String name,
        String type,
        String visibility,
        String description,
        boolean enabled,
        String language,
        Instant lastScanAt) {

    public static RepositoryCardDto from(RepositoryCard c) {
        return new RepositoryCardDto(c.id(), c.name(), c.type(), c.visibility(), c.description(),
                c.enabled(), c.language(), c.lastScanAt());
    }
}
