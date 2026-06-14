package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.UUID;

/**
 * Ein über die UI verwaltetes Secret (WR-17/19). Trägt <b>nie</b> einen Klartext-Wert nach aussen:
 * je nach {@link SecretStorageMode} hält es nur eine Referenz ({@code env:}/{@code vault:}) oder einen
 * Status, dass ein Wert verschlüsselt in der DB liegt. {@code resolvable} signalisiert der UI, ob das
 * Secret derzeit auflösbar ist (Anzeige, nie der Wert selbst, WR-19a).
 */
public record ManagedSecret(
        UUID id,
        String name,
        SecretStorageMode mode,
        String reference,
        boolean hasStoredValue,
        boolean resolvable) {

    public ManagedSecret {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("secret name must not be blank");
        }
        mode = mode == null ? SecretStorageMode.REFERENCE : mode;
        if (mode == SecretStorageMode.REFERENCE && (reference == null || reference.isBlank())) {
            throw new IllegalArgumentException("REFERENCE secret needs a reference (env:/vault:)");
        }
        reference = reference == null ? "" : reference;
    }
}
