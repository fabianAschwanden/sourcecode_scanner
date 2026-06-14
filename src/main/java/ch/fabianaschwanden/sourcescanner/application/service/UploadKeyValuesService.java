package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AttributeRule;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.KeyValuePair;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.port.in.UploadKeyValuesUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourceDefinitionPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourceValuePort;
import ch.fabianaschwanden.sourcescanner.domain.service.ValueHashing;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Verarbeitet eine hochgeladene Key-Value-Liste (IR-67): hasht die Werte (NFR-23, {@link ValueHashing})
 * und persistiert nur die Hashes je Attribut. Legt für jeden vorkommenden Key automatisch eine
 * Attribut-Regel an (geprüft, Default-Severity), sofern noch nicht vorhanden. Klartext wird nie
 * gespeichert oder geloggt; Audit (NFR-11).
 */
@ApplicationScoped
public class UploadKeyValuesService implements UploadKeyValuesUseCase {

    private final DataSourceDefinitionPort definitions;
    private final DataSourceValuePort values;
    private final AuditPort audit;
    private final String pepper;

    @Inject
    public UploadKeyValuesService(
            DataSourceDefinitionPort definitions,
            DataSourceValuePort values,
            AuditPort audit,
            @ConfigProperty(name = "scanner.datasource.hash-pepper") java.util.Optional<String> pepper) {
        this.definitions = definitions;
        this.values = values;
        this.audit = audit;
        this.pepper = pepper.orElse("");
    }

    @Override
    @Transactional
    public Map<String, Integer> upload(UUID dataSourceId, List<KeyValuePair> pairs, String actor) {
        DataSourceDefinition definition = definitions.byId(dataSourceId)
                .orElseThrow(() -> new IllegalArgumentException("data source not found: " + dataSourceId));

        // Werte je Attribut hashen (Klartext bleibt nur hier, flüchtig).
        Map<String, Set<String>> hashesByAttribute = new LinkedHashMap<>();
        int minLen = definition.minValueLength();
        for (KeyValuePair pair : pairs) {
            if (pair.value().length() < minLen) {
                continue;
            }
            hashesByAttribute.computeIfAbsent(pair.key(), k -> new LinkedHashSet<>())
                    .add(ValueHashing.hash(pair.value(), pepper));
        }

        values.replace(dataSourceId, hashesByAttribute);
        definitions.save(withAttributesFor(definition, hashesByAttribute.keySet()));

        Map<String, Integer> counts = new LinkedHashMap<>();
        hashesByAttribute.forEach((field, set) -> counts.put(field, set.size()));
        audit.record(AuditEvent.of(actor, "datasource.upload", definition.name(),
                counts.size() + " Attribut(e), " + pairs.size() + " Wert(e) gehasht"));
        return counts;
    }

    /** Ergänzt fehlende Attribut-Regeln für die hochgeladenen Keys (geprüft, Default MEDIUM/PII). */
    private DataSourceDefinition withAttributesFor(DataSourceDefinition definition, Set<String> keys) {
        List<AttributeRule> merged = new ArrayList<>(definition.attributes());
        for (String key : keys) {
            Optional<AttributeRule> existing = merged.stream().filter(a -> a.field().equals(key)).findFirst();
            if (existing.isEmpty()) {
                merged.add(new AttributeRule(key, true, Severity.MEDIUM, DetectorCategory.PII));
            }
        }
        return new DataSourceDefinition(definition.id(), definition.name(), definition.kind(),
                definition.baseUrl(), definition.method(), definition.path(), definition.authType(),
                definition.tokenRef(), definition.authHeaderName(), definition.recordsPath(),
                definition.cacheTtlSeconds(), definition.minValueLength(), definition.enabled(), merged);
    }
}
