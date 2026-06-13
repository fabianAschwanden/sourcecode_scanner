package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Ergebnis eines (versuchten) realen Scrub-Laufs (RMR-24): {@code success} samt verbleibender Funde
 * nach Re-Scan (0 = sauber, RMR-24) und einer redigierten Meldung. Wenn das Tool nicht verfügbar ist,
 * verweigert der Adapter mit {@code success=false} und einer klaren Begründung.
 */
public record ScrubResult(boolean success, int remainingFindings, String message) {

    public ScrubResult {
        message = message == null ? "" : message;
    }

    public static ScrubResult notExecutable(String reason) {
        return new ScrubResult(false, -1, reason);
    }
}
