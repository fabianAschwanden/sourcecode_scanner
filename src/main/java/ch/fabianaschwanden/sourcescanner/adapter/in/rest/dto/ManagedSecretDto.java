package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.ManagedSecret;
import ch.fabianaschwanden.sourcescanner.domain.model.SecretStorageMode;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageSecretsUseCase.SaveSecretCommand;
import java.util.Locale;
import java.util.UUID;

/**
 * REST-Transport eines verwalteten Secrets (WR-17/19). Ausgabe trägt nie einen Wert (WR-19a) — nur
 * Name, Modus, Referenz und Status. Eingabe ({@code plaintext}) ist transient und wird nie
 * zurückgegeben.
 */
public record ManagedSecretDto(
        UUID id,
        String name,
        String mode,
        String reference,
        boolean hasStoredValue,
        boolean resolvable,
        String plaintext,
        String vaultPath) {

    /** Ausgabe-Sicht: maskiert/ohne Wert. */
    public static ManagedSecretDto from(ManagedSecret s) {
        return new ManagedSecretDto(s.id(), s.name(), s.mode().name(), s.reference(),
                s.hasStoredValue(), s.resolvable(), null, null);
    }

    public SaveSecretCommand toCommand() {
        return new SaveSecretCommand(id, name, parseMode(mode), reference, plaintext, vaultPath);
    }

    private SecretStorageMode parseMode(String s) {
        try {
            return s == null ? SecretStorageMode.REFERENCE
                    : SecretStorageMode.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SecretStorageMode.REFERENCE;
        }
    }
}
