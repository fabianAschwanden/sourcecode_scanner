package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.ManagedSecret;
import ch.fabianaschwanden.sourcescanner.domain.model.SecretStorageMode;
import java.util.List;
import java.util.UUID;

/**
 * Verwaltung der über die UI gepflegten Secrets (WR-17/19). Nur Admin (WR-19b); jede Änderung wird
 * auditiert. Der Klartext-Wert ({@code plaintext}) lebt ausschliesslich transient im Save-Aufruf —
 * er wird je nach Modus an den Vault geschrieben oder verschlüsselt in der DB abgelegt, nie
 * zurückgegeben (WR-19a).
 */
public interface ManageSecretsUseCase {

    List<ManagedSecret> list();

    ManagedSecret save(SaveSecretCommand command, String actor);

    void delete(UUID id, String actor);

    /**
     * @param id        bei Bearbeitung gesetzt, sonst {@code null}
     * @param reference nur für Modus REFERENCE relevant
     * @param plaintext nur für VAULT_WRITE/DB_ENCRYPTED; transient, wird nie persistiert/zurückgegeben
     * @param vaultPath optionaler Zielpfad für VAULT_WRITE (sonst aus dem Namen abgeleitet)
     */
    record SaveSecretCommand(UUID id, String name, SecretStorageMode mode, String reference,
                             String plaintext, String vaultPath) {
        public SaveSecretCommand {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("secret name must not be blank");
            }
            mode = mode == null ? SecretStorageMode.REFERENCE : mode;
        }
    }
}
