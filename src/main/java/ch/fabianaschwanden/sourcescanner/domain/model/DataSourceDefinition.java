package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Eine verwaltete externe REST-Datenquelle für vertrauliche Kundendaten (IR-60). Liefert konkrete
 * Werte (z. B. Partnernummern), die im Code gesucht werden. {@code tokenRef} ist ausschliesslich eine
 * Secret-Referenz ({@code env:}/{@code vault:}), nie Klartext (IR-61, NFR-08). {@code recordsPath}
 * ist ein JSONPath auf die Datensätze der Antwort (IR-62). {@code attributes} ist das UI-gepflegte
 * Attribut-Mapping (WR-52). Reines Domänen-Modell — framework-frei.
 */
public record DataSourceDefinition(
        UUID id,
        String name,
        String baseUrl,
        String method,
        String path,
        DataSourceAuthType authType,
        String tokenRef,
        String authHeaderName,
        String recordsPath,
        int cacheTtlSeconds,
        int minValueLength,
        boolean enabled,
        List<AttributeRule> attributes) {

    public DataSourceDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("data source name must not be blank");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("data source baseUrl must not be blank");
        }
        method = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase(java.util.Locale.ROOT);
        path = path == null ? "" : path;
        authType = authType == null ? DataSourceAuthType.NONE : authType;
        recordsPath = recordsPath == null || recordsPath.isBlank() ? "$[*]" : recordsPath;
        cacheTtlSeconds = cacheTtlSeconds <= 0 ? 600 : cacheTtlSeconds;
        minValueLength = minValueLength < 1 ? 4 : minValueLength;
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /** Die Felder, die laut Mapping tatsächlich geprüft werden sollen (DR-24). */
    public List<AttributeRule> checkedAttributes() {
        return attributes.stream().filter(AttributeRule::check).toList();
    }
}
