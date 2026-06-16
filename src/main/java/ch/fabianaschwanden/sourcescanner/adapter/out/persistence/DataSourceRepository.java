package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.AttributeRule;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourceDefinitionPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Panache-Repository für externe Datenquellen + Attribut-Mapping (WR-50/53). */
@ApplicationScoped
public class DataSourceRepository
        implements PanacheRepositoryBase<DataSourceEntity, UUID>, DataSourceDefinitionPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> ATTR_TYPE = new TypeReference<>() {
    };

    @Override
    @Transactional
    public DataSourceDefinition save(DataSourceDefinition definition) {
        UUID id = definition.id() == null ? UUID.randomUUID() : definition.id();
        DataSourceEntity entity = findById(id);
        if (entity == null) {
            entity = new DataSourceEntity();
            entity.id = id;
        }
        entity.name = definition.name();
        entity.kind = definition.kind();
        entity.baseUrl = definition.baseUrl();
        entity.method = definition.method();
        entity.path = definition.path();
        entity.authType = definition.authType();
        entity.tokenRef = definition.tokenRef();
        entity.authHeaderName = definition.authHeaderName();
        entity.recordsPath = definition.recordsPath();
        entity.cacheTtlSeconds = definition.cacheTtlSeconds();
        entity.minValueLength = definition.minValueLength();
        entity.enabled = definition.enabled();
        entity.attributesJson = writeAttributes(definition.attributes());
        persist(entity);
        return toDomain(entity);
    }

    // Lesen ggf. aus dem async Scan-Thread (Detektor pii.customer-data-api): eigener Transaktions-/
    // Request-Kontext, sonst ContextNotActiveException ausserhalb des Request-Threads.
    @Override
    @ActivateRequestContext
    @Transactional
    public Optional<DataSourceDefinition> byId(UUID id) {
        return Optional.ofNullable(findById(id)).map(DataSourceRepository::toDomain);
    }

    @Override
    @ActivateRequestContext
    @Transactional
    public Optional<DataSourceDefinition> byName(String name) {
        return find("name", name).firstResultOptional().map(DataSourceRepository::toDomain);
    }

    @Override
    @ActivateRequestContext
    @Transactional
    public List<DataSourceDefinition> all() {
        return listAll().stream().map(DataSourceRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        deleteById(id);
    }

    private static String writeAttributes(List<AttributeRule> attributes) {
        try {
            List<Map<String, Object>> raw = attributes.stream()
                    .map(a -> Map.<String, Object>of(
                            "field", a.field(),
                            "check", a.check(),
                            "severity", a.severity().name(),
                            "category", a.category().name()))
                    .toList();
            return MAPPER.writeValueAsString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize attribute mapping", e);
        }
    }

    static DataSourceDefinition toDomain(DataSourceEntity e) {
        return new DataSourceDefinition(e.id, e.name, e.kind, e.baseUrl, e.method, e.path, e.authType,
                e.tokenRef, e.authHeaderName, e.recordsPath, e.cacheTtlSeconds, e.minValueLength, e.enabled,
                readAttributes(e.attributesJson));
    }

    private static List<AttributeRule> readAttributes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(json, ATTR_TYPE);
            return raw.stream().map(DataSourceRepository::toRule).toList();
        } catch (Exception e) {
            throw new IllegalStateException("failed to read attribute mapping", e);
        }
    }

    private static AttributeRule toRule(Map<String, Object> map) {
        String field = String.valueOf(map.get("field"));
        boolean check = Boolean.TRUE.equals(map.get("check"));
        Severity severity = parseSeverity(map.get("severity"));
        DetectorCategory category = parseCategory(map.get("category"));
        return new AttributeRule(field, check, severity, category);
    }

    private static Severity parseSeverity(Object raw) {
        try {
            return raw == null ? Severity.MEDIUM : Severity.valueOf(raw.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Severity.MEDIUM;
        }
    }

    private static DetectorCategory parseCategory(Object raw) {
        try {
            DetectorCategory c = raw == null ? DetectorCategory.PII
                    : DetectorCategory.valueOf(raw.toString().trim().toUpperCase(Locale.ROOT));
            return c == DetectorCategory.CUSTOM ? DetectorCategory.CUSTOM : DetectorCategory.PII;
        } catch (IllegalArgumentException e) {
            return DetectorCategory.PII;
        }
    }
}
