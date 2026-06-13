package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;

/**
 * Referenz auf ein konkret scanbares Repository. {@code type} bestimmt den Connector
 * ({@code localGit}, {@code bitbucket}, {@code github}, {@code gitlab}); {@code location} ist der
 * lokale Pfad (localGit) bzw. die Clone-URL (Plattform). {@code tokenRef} verweist auf das Secret
 * für authentifiziertes Klonen (NFR-08), nie der Klartext-Token.
 */
public record RepositoryRef(String id, String type, String location, List<String> branches, String tokenRef) {

    public RepositoryRef {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("repository id must not be blank");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("repository location must not be blank");
        }
        branches = branches == null ? List.of() : List.copyOf(branches);
    }

    /** Bequemer Konstruktor ohne Auth (localGit / Tests). */
    public RepositoryRef(String id, String type, String location, List<String> branches) {
        this(id, type, location, branches, null);
    }
}
