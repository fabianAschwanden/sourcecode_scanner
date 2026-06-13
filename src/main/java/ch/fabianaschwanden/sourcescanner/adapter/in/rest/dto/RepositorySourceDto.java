package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import java.util.List;
import java.util.UUID;

/**
 * REST-Transport einer Repository-Quelle. {@code tokenRef} ist nur die Secret-Referenz; ein
 * Klartext-Token wird nie zurückgegeben (WR-32).
 */
public record RepositorySourceDto(
        UUID id,
        String name,
        String type,
        String location,
        List<String> branches,
        String tokenRef,
        boolean enabled) {

    public static RepositorySourceDto from(RepositorySource s) {
        return new RepositorySourceDto(s.id(), s.name(), s.type(), s.location(), s.branches(),
                s.tokenRef(), s.enabled());
    }

    public RepositorySource toDomain() {
        return new RepositorySource(id, name, type, location, branches, tokenRef, enabled);
    }
}
