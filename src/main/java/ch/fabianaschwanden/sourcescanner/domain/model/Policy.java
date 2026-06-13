package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.Set;
import java.util.UUID;

/**
 * Zentrale Governance-Vorgabe pro Organisationseinheit (FR-20): Gate-Schwelle, aktivierte
 * Detektor-Gruppen und Warn-Schwelle. Reines Domänen-Modell; die spezifischste passende Policy
 * gewinnt (siehe {@code PolicyResolution}). {@code orgUnit == null} markiert die Default-Policy.
 */
public record Policy(
        UUID id,
        String orgUnit,
        GateConfig gate,
        Set<String> enabledDetectorGroups,
        Severity warnThreshold) {

    public Policy {
        if (gate == null) {
            throw new IllegalArgumentException("policy gate must not be null");
        }
        enabledDetectorGroups = enabledDetectorGroups == null ? Set.of() : Set.copyOf(enabledDetectorGroups);
    }

    public boolean isDefault() {
        return orgUnit == null || orgUnit.isBlank();
    }

    /** Default-Policy, die das bisherige Verhalten abbildet (Gate HIGH, secrets+pii aktiv). */
    public static Policy fallback() {
        return new Policy(null, null, GateConfig.defaults(), Set.of("secrets", "pii"), Severity.MEDIUM);
    }
}
