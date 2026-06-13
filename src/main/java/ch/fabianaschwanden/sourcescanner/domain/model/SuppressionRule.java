package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Pfad-basierte Suppression-Regel (docs/03 §2): unterdrückt Funde, deren Datei dem {@code pathGlob}
 * entspricht. {@code detector} referenziert optional eine Detektor-Gruppe ({@code secrets}, {@code pii})
 * oder eine konkrete Detektor-ID; {@code null}/leer trifft jeden Detektor.
 */
public record SuppressionRule(String pathGlob, String detector, String reason) {

    public SuppressionRule {
        if (pathGlob == null || pathGlob.isBlank()) {
            throw new IllegalArgumentException("suppression pathGlob must not be blank");
        }
    }

    /** {@code true}, wenn diese Regel auf den genannten Detektor (ID oder Gruppe) zutrifft. */
    public boolean matchesDetector(String detectorId, String detectorGroup) {
        if (detector == null || detector.isBlank()) {
            return true;
        }
        return detector.equals(detectorId) || detector.equals(detectorGroup);
    }
}
