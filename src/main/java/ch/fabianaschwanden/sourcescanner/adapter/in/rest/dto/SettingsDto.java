package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.Settings;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * REST-Transport der systemweiten Einstellungen (WR-15..18). {@code secretRefs} enthalten nur die
 * Referenz und ihren Auflösbar-Status — niemals den Klartext-Wert (WR-17/WR-32).
 */
public record SettingsDto(
        String generalNotificationEmail,
        String defaultFailOn,
        String defaultScanMode,
        int retentionDays,
        List<SecretRefStatus> secretRefs) {

    /** Eine Secret-Referenz und ob sie aktuell auflösbar ist. */
    public record SecretRefStatus(String ref, boolean resolvable) {
    }

    public static SettingsDto from(Settings s, Map<String, Boolean> refStatus) {
        List<SecretRefStatus> refs = s.secretRefs().stream()
                .map(r -> new SecretRefStatus(r, refStatus.getOrDefault(r, false)))
                .toList();
        return new SettingsDto(s.generalNotificationEmail(), s.defaultFailOn().name(),
                s.defaultScanMode(), s.retentionDays(), refs);
    }

    public Settings toDomain() {
        Severity failOn = defaultFailOn == null || defaultFailOn.isBlank()
                ? Severity.HIGH : Severity.valueOf(defaultFailOn.trim().toUpperCase(Locale.ROOT));
        List<String> refs = secretRefs == null ? List.of()
                : secretRefs.stream().map(SecretRefStatus::ref).toList();
        return new Settings(generalNotificationEmail, failOn, defaultScanMode, retentionDays, refs);
    }
}
