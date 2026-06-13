package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.GateConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Policy;
import ch.fabianaschwanden.sourcescanner.domain.port.out.PolicyPort;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Panache-Repository für Policies; mappt zwischen Entity und Domänen-{@link Policy} (TR-23). */
@ApplicationScoped
public class PolicyRepository implements PanacheRepositoryBase<PolicyEntity, UUID>, PolicyPort {

    @Override
    @Transactional
    public Policy save(Policy policy) {
        UUID id = policy.id() == null ? UUID.randomUUID() : policy.id();
        PolicyEntity entity = findById(id);
        if (entity == null) {
            entity = new PolicyEntity();
            entity.id = id;
        }
        entity.orgUnit = policy.isDefault() ? null : policy.orgUnit();
        entity.failOn = policy.gate().failOn();
        entity.failOnNewOnly = policy.gate().failOnNewOnly();
        entity.softFail = policy.gate().softFail();
        entity.warnThreshold = policy.warnThreshold() == null
                ? ch.fabianaschwanden.sourcescanner.domain.model.Severity.MEDIUM : policy.warnThreshold();
        entity.enabledDetectorGroups = String.join(",", policy.enabledDetectorGroups());
        persist(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<Policy> byId(UUID id) {
        return Optional.ofNullable(findById(id)).map(PolicyRepository::toDomain);
    }

    @Override
    public List<Policy> all() {
        return listAll().stream().map(PolicyRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        deleteById(id);
    }

    static Policy toDomain(PolicyEntity e) {
        Set<String> groups = e.enabledDetectorGroups == null || e.enabledDetectorGroups.isBlank()
                ? Set.of() : Set.of(e.enabledDetectorGroups.split(","));
        return new Policy(e.id, e.orgUnit,
                new GateConfig(e.failOn, e.failOnNewOnly, e.softFail), groups, e.warnThreshold);
    }
}
