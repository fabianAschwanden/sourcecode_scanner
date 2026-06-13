package ch.fabianaschwanden.sourcescanner.adapter.out.datasource;

import ch.fabianaschwanden.sourcescanner.adapter.out.datasource.api.RestDataSourceApi;
import ch.fabianaschwanden.sourcescanner.domain.model.AttributeRule;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceAuthType;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceSchema;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourcePort;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

/**
 * REST-Adapter hinter {@link DataSourcePort} (IR-60..66). Ruft die externe API auf, extrahiert über
 * einen JSONPath-Auszug ({@link JsonRecords}) die Datensätze und liest je Attribut die Werte. Auth
 * nur über eine Secret-Referenz ({@link DataSourceTokenResolver}); der Token wird nie geloggt.
 *
 * <p><b>Datenschutz:</b> {@code probe} gibt nur ein redigiertes Schema zurück (maskierte Beispiele).
 * Geladene Klartextwerte werden ausschliesslich im flüchtigen TTL-Cache gehalten (nie auf Platte,
 * nie geloggt, NFR-23/IR-64) und nur an den Detektor weitergegeben.
 */
@ApplicationScoped
public class RestDataSourceAdapter implements DataSourcePort {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public DataSourceSchema probe(DataSourceDefinition definition) {
        try {
            List<JsonNode> records = fetchRecords(definition);
            if (records.isEmpty()) {
                return new DataSourceSchema(true, 0, List.of(), "Antwort enthält keine Datensätze.");
            }
            // Attribute aus dem ersten Datensatz; Beispiel maskiert (nie Klartext, WR-51/DR-26).
            List<DataSourceSchema.AttributeSample> attrs = new ArrayList<>();
            JsonNode first = records.getFirst();
            first.fieldNames().forEachRemaining(field -> {
                String value = textOf(first.get(field));
                attrs.add(new DataSourceSchema.AttributeSample(field, DataSourceRedaction.mask(value)));
            });
            return new DataSourceSchema(true, records.size(), attrs, "OK");
        } catch (RuntimeException e) {
            return DataSourceSchema.unreachable("nicht erreichbar: " + e.getMessage());
        }
    }

    @Override
    public Map<AttributeRule, List<String>> loadValues(DataSourceDefinition definition) {
        List<AttributeRule> checked = definition.checkedAttributes();
        if (checked.isEmpty()) {
            return Map.of();
        }
        List<JsonNode> records = cachedRecords(definition);
        Map<AttributeRule, List<String>> result = new LinkedHashMap<>();
        for (AttributeRule rule : checked) {
            Set<String> values = new LinkedHashSet<>();
            for (JsonNode record : records) {
                String value = textOf(record.get(rule.field()));
                if (value != null && value.length() >= definition.minValueLength()) {
                    values.add(value);
                }
            }
            result.put(rule, List.copyOf(values));
        }
        return result;
    }

    /** Datensätze aus dem TTL-Cache (DR-27); bei Ablauf/Fehlen frisch laden. Werte bleiben im Speicher. */
    private List<JsonNode> cachedRecords(DataSourceDefinition definition) {
        String key = definition.id() == null ? definition.name() : definition.id().toString();
        CacheEntry entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && now < entry.expiresAtMillis()) {
            return entry.records();
        }
        List<JsonNode> fresh = fetchRecords(definition);
        cache.put(key, new CacheEntry(fresh, now + definition.cacheTtlSeconds() * 1000L));
        return fresh;
    }

    private List<JsonNode> fetchRecords(DataSourceDefinition definition) {
        RestDataSourceApi api = RestClientBuilder.newBuilder()
                .baseUri(URI.create(fullUri(definition)))
                .build(RestDataSourceApi.class);
        JsonNode root = api.fetch(authHeader(definition));
        return JsonRecords.select(root, definition.recordsPath());
    }

    /** Setzt Base-URL und Pfad zur vollständigen Abruf-URI zusammen. */
    private String fullUri(DataSourceDefinition definition) {
        String base = definition.baseUrl().endsWith("/")
                ? definition.baseUrl().substring(0, definition.baseUrl().length() - 1)
                : definition.baseUrl();
        String path = definition.path() == null || definition.path().isBlank() ? "" : definition.path();
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    /** Baut den Authorization-Header aus der Secret-Referenz; leer ⇒ kein Header (anonym). */
    private String authHeader(DataSourceDefinition definition) {
        if (definition.authType() == DataSourceAuthType.NONE) {
            return "";
        }
        Optional<String> token = DataSourceTokenResolver.resolve(definition.tokenRef());
        if (token.isEmpty()) {
            return "";
        }
        return switch (definition.authType()) {
            case BEARER -> "Bearer " + token.get();
            case BASIC -> "Basic " + Base64.getEncoder()
                    .encodeToString(token.get().getBytes(StandardCharsets.UTF_8));
            case HEADER -> token.get();
            case NONE -> "";
        };
    }

    private String textOf(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.isValueNode() ? node.asText() : null;
    }

    private record CacheEntry(List<JsonNode> records, long expiresAtMillis) {
    }
}
