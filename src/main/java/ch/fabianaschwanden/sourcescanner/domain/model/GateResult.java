package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Ergebnis der Quality-Gate-Bewertung. {@code blockingCount} ist die Zahl der Funde, die das
 * Gate verletzen; {@code softFail} spiegelt die Konfiguration und entscheidet über den Exit-Code.
 */
public record GateResult(boolean passed, int blockingCount, Severity failOn, boolean softFail) {

    /**
     * Exit-Code-Vertrag (docs/08 §7, IR-14): {@code 0} pass · {@code 1} Gate verletzt ·
     * {@code 3} softFail mit Funden. {@code 2} (Konfig-/Laufzeitfehler) wird ausserhalb gesetzt.
     */
    public int exitCode() {
        if (passed) {
            return 0;
        }
        return softFail ? 3 : 1;
    }
}
