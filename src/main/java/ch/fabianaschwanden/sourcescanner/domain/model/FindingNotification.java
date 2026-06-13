package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Redigierte Zusammenfassung eines/mehrerer Funde für externe Benachrichtigungen (IR-50/51). Trägt
 * nie Klartext (FR-18); {@code highestSeverity} steuert die Schwellenwert-Filterung.
 */
public record FindingNotification(String repoId, Severity highestSeverity, int findingCount, String summary) {

    public FindingNotification {
        if (repoId == null || repoId.isBlank()) {
            throw new IllegalArgumentException("repoId must not be blank");
        }
        if (highestSeverity == null) {
            throw new IllegalArgumentException("highestSeverity must not be null");
        }
    }
}
