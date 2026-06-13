package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Eine im Server verwaltete Repository-Quelle (WR-02). Credentials werden ausschliesslich als
 * Secret-Store-Referenz gehalten ({@code tokenRef}, z. B. {@code env:NAME}), nie im Klartext (WR-32).
 */
public record RepositorySource(
        UUID id,
        String name,
        String type,
        String location,
        List<String> branches,
        String tokenRef,
        boolean enabled) {

    public RepositorySource {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("source name must not be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("source type must not be blank");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("source location must not be blank");
        }
        branches = branches == null ? List.of() : List.copyOf(branches);
    }

    /** Wandelt die Quelle in eine scanbare {@link RepositoryRef} (für den Orchestrator). */
    public RepositoryRef toRef() {
        return new RepositoryRef(name, type, location, branches, tokenRef);
    }
}
