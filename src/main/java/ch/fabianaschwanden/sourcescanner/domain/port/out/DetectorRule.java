package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;

/**
 * Regel-Metadaten eines Detektors für das Reporting (SARIF {@code tool.driver.rules}).
 * {@code id} entspricht der {@code ruleId} eines Fundes.
 */
public record DetectorRule(String id, String name, String description, Severity defaultSeverity) {

    public DetectorRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("rule id must not be blank");
        }
        name = name == null || name.isBlank() ? id : name;
        description = description == null ? "" : description;
    }
}
