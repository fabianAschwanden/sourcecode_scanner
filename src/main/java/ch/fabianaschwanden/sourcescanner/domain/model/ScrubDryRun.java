package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Ergebnis eines Scrub-Dry-Runs (RMR-22): ein redigierter Bericht, der ohne Historien-Umschreibung
 * zeigt, was entfernt würde. {@code toolAvailable} signalisiert, ob das reale Rewrite-Tool vorhanden
 * ist; ist es das nicht, ist nur der Dry-Run-Bericht verfügbar (kein {@code execute}).
 */
public record ScrubDryRun(
        boolean toolAvailable,
        int affectedSecrets,
        String diffSummary) {

    public ScrubDryRun {
        diffSummary = diffSummary == null ? "" : diffSummary;
    }
}
