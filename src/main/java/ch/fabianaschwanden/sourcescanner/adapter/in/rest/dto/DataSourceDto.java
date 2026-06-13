package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceAuthType;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceKind;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * REST-Transport einer externen Datenquelle inkl. Attribut-Mapping (WR-50). {@code tokenRef} ist nur
 * eine Secret-Referenz; ein Klartext-Token wird nie zurückgegeben (WR-32). Werte erscheinen hier nie.
 */
public record DataSourceDto(
        UUID id,
        String name,
        String kind,
        String baseUrl,
        String method,
        String path,
        String authType,
        String tokenRef,
        String authHeaderName,
        String recordsPath,
        int cacheTtlSeconds,
        int minValueLength,
        boolean enabled,
        List<AttributeRuleDto> attributes) {

    public DataSourceDto {
        attributes = attributes == null ? List.of() : attributes;
    }

    public static DataSourceDto from(DataSourceDefinition d) {
        return new DataSourceDto(d.id(), d.name(), d.kind().name(), d.baseUrl(), d.method(), d.path(),
                d.authType().name(), d.tokenRef(), d.authHeaderName(), d.recordsPath(), d.cacheTtlSeconds(),
                d.minValueLength(), d.enabled(), d.attributes().stream().map(AttributeRuleDto::from).toList());
    }

    public DataSourceDefinition toDomain() {
        return new DataSourceDefinition(id, name, parseKind(kind), baseUrl, method, path, parseAuth(authType),
                tokenRef, authHeaderName, recordsPath, cacheTtlSeconds, minValueLength, enabled,
                attributes.stream().map(AttributeRuleDto::toDomain).toList());
    }

    private DataSourceKind parseKind(String s) {
        try {
            return s == null ? DataSourceKind.REST : DataSourceKind.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DataSourceKind.REST;
        }
    }

    private DataSourceAuthType parseAuth(String s) {
        try {
            return s == null ? DataSourceAuthType.NONE : DataSourceAuthType.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DataSourceAuthType.NONE;
        }
    }
}
