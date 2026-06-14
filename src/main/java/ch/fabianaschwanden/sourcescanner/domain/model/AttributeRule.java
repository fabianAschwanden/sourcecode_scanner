package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Mapping-Regel für ein Attribut (Antwort-Feld) einer externen Datenquelle (FR-22, WR-52): ob es im
 * Code geprüft wird, mit welcher Severity und unter welcher Kategorie. Trägt nur den <b>Feldnamen</b>
 * (z. B. {@code partnernummer}), nie einen Klartextwert (FR-23, DR-26).
 */
public record AttributeRule(String field, boolean check, Severity severity, DetectorCategory category) {

    public AttributeRule {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("attribute field must not be blank");
        }
        severity = severity == null ? Severity.MEDIUM : severity;
        category = category == null ? DetectorCategory.PII : category;
        if (category != DetectorCategory.PII && category != DetectorCategory.CUSTOM) {
            throw new IllegalArgumentException("attribute category must be PII or CUSTOM");
        }
    }
}
