package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceSchema;
import java.util.List;

/** REST-Transport des redigierten Probe-Schemas (IR-63, WR-51). Nur Feldnamen + maskierte Beispiele. */
public record DataSourceSchemaDto(boolean reachable, int sampleRecords, List<AttributeSampleDto> attributes,
                                  String message) {

    public record AttributeSampleDto(String field, String maskedExample) {
    }

    public static DataSourceSchemaDto from(DataSourceSchema s) {
        return new DataSourceSchemaDto(s.reachable(), s.sampleRecords(),
                s.attributes().stream().map(a -> new AttributeSampleDto(a.field(), a.maskedExample())).toList(),
                s.message());
    }
}
