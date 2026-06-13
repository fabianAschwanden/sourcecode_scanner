package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Quality-Gate-Vorgaben (docs/03 §2 / §9). {@code failOn} ist die Mindest-Severity, die den
 * Build rot macht; {@code softFail} meldet Funde ohne Abbruch (Einführungsphase).
 */
public record GateConfig(Severity failOn, boolean failOnNewOnly, boolean softFail) {

    public GateConfig {
        if (failOn == null) {
            throw new IllegalArgumentException("gate.failOn must not be null");
        }
    }

    public static GateConfig defaults() {
        return new GateConfig(Severity.HIGH, false, false);
    }
}
