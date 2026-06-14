package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;

/**
 * Regel-Metadaten eines Detektors für das Reporting (SARIF {@code tool.driver.rules}) und den
 * Ruleset-Editor. {@code id} entspricht der {@code ruleId} eines Fundes. {@code defaultEnabled} gibt
 * an, ob die Regel ohne explizite Konfiguration aktiv ist (DR-50) — z. B. {@code phone} ist aus.
 */
public record DetectorRule(String id, String name, String description, Severity defaultSeverity,
        boolean defaultEnabled) {

    public DetectorRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("rule id must not be blank");
        }
        name = name == null || name.isBlank() ? id : name;
        description = description == null ? "" : description;
    }

    /** Bequemer Konstruktor: Regel ist standardmässig aktiv. */
    public DetectorRule(String id, String name, String description, Severity defaultSeverity) {
        this(id, name, description, defaultSeverity, true);
    }
}
