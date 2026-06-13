package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Schweregrad eines Fundes. Reihenfolge ist aufsteigend kritisch — die Ordinalwerte
 * tragen den Vergleich (siehe {@link #atLeast(Severity)}).
 */
public enum Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** {@code true}, wenn dieser Schweregrad mindestens so kritisch wie {@code threshold} ist (Gate-Vergleich). */
    public boolean atLeast(Severity threshold) {
        return this.ordinal() >= threshold.ordinal();
    }
}
