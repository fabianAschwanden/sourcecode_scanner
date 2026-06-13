package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.AttributeRule;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.Locale;

/** REST-Transport einer Attribut-Mapping-Regel (WR-52). Trägt nur den Feldnamen, nie einen Wert. */
public record AttributeRuleDto(String field, boolean check, String severity, String category) {

    public static AttributeRuleDto from(AttributeRule rule) {
        return new AttributeRuleDto(rule.field(), rule.check(), rule.severity().name(), rule.category().name());
    }

    public AttributeRule toDomain() {
        return new AttributeRule(field, check, parseSeverity(severity), parseCategory(category));
    }

    private Severity parseSeverity(String s) {
        try {
            return s == null ? Severity.MEDIUM : Severity.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Severity.MEDIUM;
        }
    }

    private DetectorCategory parseCategory(String s) {
        try {
            DetectorCategory c = s == null ? DetectorCategory.PII
                    : DetectorCategory.valueOf(s.trim().toUpperCase(Locale.ROOT));
            return c == DetectorCategory.CUSTOM ? DetectorCategory.CUSTOM : DetectorCategory.PII;
        } catch (IllegalArgumentException e) {
            return DetectorCategory.PII;
        }
    }
}
