package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import java.util.List;
import java.util.UUID;

/** Driving Port — Verwaltung der Repository-Quellen (WR-02). */
public interface ManageSourcesUseCase {

    RepositorySource create(RepositorySource source, String actor);

    RepositorySource update(UUID id, RepositorySource source, String actor);

    void delete(UUID id, String actor);

    List<RepositorySource> all();

    /** Testet die Erreichbarkeit/Berechtigung einer Quelle, ohne Credentials zurückzugeben (WR-02). */
    boolean testConnection(UUID id);
}
