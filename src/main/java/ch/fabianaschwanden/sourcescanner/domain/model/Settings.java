package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;

/**
 * Systemweite Einstellungen (WR-15..18), als einzelner Datensatz (Singleton) verwaltet.
 * {@code generalNotificationEmail} ist die allgemeine Benachrichtigungs-Adresse (WR-16, IR-54);
 * {@code secretRefs} sind verwaltete Credential-Referenzen wie {@code env:GITHUB_TOKEN} — nur
 * Referenzen, nie Klartext (WR-17, WR-32). Reines Domänen-Modell.
 */
public record Settings(
        String generalNotificationEmail,
        Severity defaultFailOn,
        String defaultScanMode,
        int retentionDays,
        List<String> secretRefs) {

    public Settings {
        defaultFailOn = defaultFailOn == null ? Severity.HIGH : defaultFailOn;
        defaultScanMode = defaultScanMode == null || defaultScanMode.isBlank() ? "full" : defaultScanMode;
        retentionDays = retentionDays <= 0 ? 365 : retentionDays;
        secretRefs = secretRefs == null ? List.of() : List.copyOf(secretRefs);
    }

    /** Default-Einstellungen, falls noch keine gespeichert wurden. */
    public static Settings defaults() {
        return new Settings(null, Severity.HIGH, "full", 365, List.of());
    }
}
