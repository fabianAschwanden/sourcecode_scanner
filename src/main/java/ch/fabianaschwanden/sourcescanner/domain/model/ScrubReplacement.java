package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Eine Ersetzungsregel für die History-Bereinigung (RMR-20): das (redigierte) Erkennungs-Fingerprint
 * eines Secrets, das aus der gesamten Historie entfernt/ersetzt werden soll. Trägt nie Klartext —
 * der reale Token-Wert wird erst im (hier nicht ausgeführten) Tool-Aufruf aus der Referenz aufgelöst.
 */
public record ScrubReplacement(String fingerprint, String redactedMatch, String file, int line) {

    public ScrubReplacement {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        redactedMatch = redactedMatch == null ? "" : redactedMatch;
    }
}
