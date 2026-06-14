package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;

/**
 * Redigiertes Schema eines Probe-Abrufs gegen eine Datenquelle (IR-63, WR-51): die verfügbaren
 * Attributnamen plus eine <b>maskierte</b> Beispielausprägung je Attribut — nie ein Klartextwert
 * (WR-33, DR-26). Dient dem UI-Attribut-Mapping als Auswahlgrundlage.
 */
public record DataSourceSchema(boolean reachable, int sampleRecords, List<AttributeSample> attributes, String message) {

    public DataSourceSchema {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        message = message == null ? "" : message;
    }

    /** Ein Attribut mit redigiertem Beispiel (z. B. {@code partnernummer → 12******}). */
    public record AttributeSample(String field, String maskedExample) {
        public AttributeSample {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("attribute field must not be blank");
            }
            maskedExample = maskedExample == null ? "" : maskedExample;
        }
    }

    public static DataSourceSchema unreachable(String message) {
        return new DataSourceSchema(false, 0, List.of(), message);
    }
}
