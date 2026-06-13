package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistenz der verwalteten Repository-Quellen (WR-02). */
public interface RepositorySourcePort {

    RepositorySource save(RepositorySource source);

    Optional<RepositorySource> byId(UUID id);

    List<RepositorySource> all();

    void delete(UUID id);
}
