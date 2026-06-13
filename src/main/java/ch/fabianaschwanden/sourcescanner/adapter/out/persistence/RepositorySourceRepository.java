package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositorySourcePort;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Panache-Repository für verwaltete Repository-Quellen (WR-02). */
@ApplicationScoped
public class RepositorySourceRepository
        implements PanacheRepositoryBase<RepositorySourceEntity, UUID>, RepositorySourcePort {

    @Override
    @Transactional
    public RepositorySource save(RepositorySource source) {
        UUID id = source.id() == null ? UUID.randomUUID() : source.id();
        RepositorySourceEntity entity = findById(id);
        if (entity == null) {
            entity = new RepositorySourceEntity();
            entity.id = id;
        }
        entity.name = source.name();
        entity.type = source.type();
        entity.location = source.location();
        entity.branches = String.join(",", source.branches());
        entity.tokenRef = source.tokenRef();
        entity.enabled = source.enabled();
        persist(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<RepositorySource> byId(UUID id) {
        return Optional.ofNullable(findById(id)).map(RepositorySourceRepository::toDomain);
    }

    @Override
    public List<RepositorySource> all() {
        return listAll().stream().map(RepositorySourceRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        deleteById(id);
    }

    static RepositorySource toDomain(RepositorySourceEntity e) {
        List<String> branches = e.branches == null || e.branches.isBlank()
                ? List.of() : List.of(e.branches.split(","));
        return new RepositorySource(e.id, e.name, e.type, e.location, branches, e.tokenRef, e.enabled);
    }
}
