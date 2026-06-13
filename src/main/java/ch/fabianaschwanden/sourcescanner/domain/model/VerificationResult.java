package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Ergebnis einer optionalen aktiven Verifikation eines Fundes (DR-05, docs/02 §2). Der Core liefert
 * stets {@link Status#UNVERIFIED}; echte Gültigkeitsprüfungen (z. B. Key noch aktiv?) sind als
 * Verifikations-Plugins vorgesehen. Ein als {@link Status#ACTIVE} bestätigtes Secret wird auf
 * CRITICAL hochgestuft (DR-14, siehe {@code SeverityScoring}).
 */
public record VerificationResult(Status status, String detail) {

    public enum Status {
        /** Nicht verifiziert (Default, keine aktive Prüfung durchgeführt). */
        UNVERIFIED,
        /** Bestätigt aktiv/gültig (z. B. Key authentifiziert erfolgreich). */
        ACTIVE,
        /** Bestätigt inaktiv/widerrufen. */
        INACTIVE,
        /** Verifikation fehlgeschlagen (Netz/Provider-Fehler) — kein Urteil. */
        ERROR
    }

    public VerificationResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    public static VerificationResult unverified() {
        return new VerificationResult(Status.UNVERIFIED, null);
    }

    public static VerificationResult active(String detail) {
        return new VerificationResult(Status.ACTIVE, detail);
    }

    public static VerificationResult inactive(String detail) {
        return new VerificationResult(Status.INACTIVE, detail);
    }

    public static VerificationResult error(String detail) {
        return new VerificationResult(Status.ERROR, detail);
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
