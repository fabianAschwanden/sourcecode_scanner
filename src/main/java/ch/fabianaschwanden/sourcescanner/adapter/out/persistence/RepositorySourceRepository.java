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
        entity.reportEmails = String.join(",", source.reportEmails());
        entity.remediationEnabled = source.remediationEnabled();
        entity.description = source.description();
        entity.visibility = source.visibility();
        persist(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<RepositorySource> byId(UUID id) {
        return Optional.ofNullable(findById(id)).map(RepositorySourceRepository::toDomain);
    }

    @Override
    public Optional<RepositorySource> byName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return find("name", name).firstResultOptional().map(RepositorySourceRepository::toDomain);
    }

    @Override
    public List<RepositorySource> all() {
        return listAll().stream().map(RepositorySourceRepository::toDomain).toList();
    }

    @Override
    public List<RepositorySource> query(SourceQuery query) {
        // q (Name/Beschreibung) + type serverseitig filtern; Sortierung name (Default) hier,
        // updated/language löst die Application-Schicht über die abgeleiteten Felder auf.
        return all().stream()
                .filter(s -> query.type() == null || query.type().equalsIgnoreCase(s.type()))
                .filter(s -> matchesText(s, query.q()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    private boolean matchesText(RepositorySource s, String q) {
        if (q == null) {
            return true;
        }
        String needle = q.toLowerCase();
        return s.name().toLowerCase().contains(needle) || s.description().toLowerCase().contains(needle);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        deleteById(id);
    }

    static RepositorySource toDomain(RepositorySourceEntity e) {
        return new RepositorySource(e.id, e.name, e.type, e.location, csv(e.branches), e.tokenRef,
                e.enabled, csv(e.reportEmails), e.remediationEnabled, e.description, e.visibility);
    }

    private static List<String> csv(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }
}
