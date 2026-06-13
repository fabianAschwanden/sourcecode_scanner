package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourceValuePort;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Panache-Repository für die Hashes hochgeladener Werte (IR-67, NFR-23). Speichert/liest nur Hashes —
 * nie Klartext. {@link #replace} ersetzt die Hashes einer Datenquelle atomar (idempotenter Upload).
 */
@ApplicationScoped
public class DataSourceValueRepository
        implements PanacheRepositoryBase<DataSourceValueEntity, UUID>, DataSourceValuePort {

    @Override
    @Transactional
    public void replace(UUID dataSourceId, Map<String, Set<String>> hashesByAttribute) {
        delete("dataSourceId", dataSourceId);
        for (Map.Entry<String, Set<String>> entry : hashesByAttribute.entrySet()) {
            for (String hash : entry.getValue()) {
                DataSourceValueEntity e = new DataSourceValueEntity();
                e.id = UUID.randomUUID();
                e.dataSourceId = dataSourceId;
                e.attribute = entry.getKey();
                e.valueHash = hash;
                persist(e);
            }
        }
    }

    @Override
    public Map<String, Set<String>> hashesByAttribute(UUID dataSourceId) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (DataSourceValueEntity e : list("dataSourceId", dataSourceId)) {
            result.computeIfAbsent(e.attribute, k -> new LinkedHashSet<>()).add(e.valueHash);
        }
        return result;
    }

    @Override
    public Map<String, Integer> countByAttribute(UUID dataSourceId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DataSourceValueEntity e : list("dataSourceId", dataSourceId)) {
            counts.merge(e.attribute, 1, Integer::sum);
        }
        return counts;
    }

    @Override
    @Transactional
    public void deleteFor(UUID dataSourceId) {
        delete("dataSourceId", dataSourceId);
    }
}
