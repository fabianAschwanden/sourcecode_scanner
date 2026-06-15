package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Feingranulare Regel-Einstellung innerhalb eines {@link Ruleset} (DR-50..52). Adressiert eine Regel
 * über ihre ID (z. B. {@code email}, {@code iban}, {@code secret.high-entropy}): an/aus, optionale
 * Severity-Überschreibung und — für wertbezogene Regeln — ein {@link RuleMatchMode} samt optionaler
 * Datenquellen-Referenz ({@code dataSourceName}, für {@code LIST}/{@code API}).
 */
public record RuleOverride(
        String ruleId,
        boolean enabled,
        Severity severity,
        RuleMatchMode matchMode,
        String dataSourceName) {

    public RuleOverride {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId must not be blank");
        }
        matchMode = matchMode == null ? RuleMatchMode.ALWAYS : matchMode;
    }

    /** {@code true}, wenn diese Regel eine Severity-Überschreibung trägt. */
    public boolean hasSeverityOverride() {
        return severity != null;
    }
}
