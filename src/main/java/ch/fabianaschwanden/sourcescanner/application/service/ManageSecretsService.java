package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.ManagedSecret;
import ch.fabianaschwanden.sourcescanner.domain.model.SecretStorageMode;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageSecretsUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ManagedSecretPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.SecretEncryptionPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.SecretReferencePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.SecretStoreWritePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

/**
 * Verwaltung der UI-Secrets (WR-17/19). Setzt die drei Modi um — REFERENCE (nur Verweis), VAULT_WRITE
 * (Klartext an den Store, nur Referenz behalten) und DB_ENCRYPTED (Klartext verschlüsselt at-rest,
 * NFR-29). Der Klartext lebt nur transient im {@code save}-Aufruf und wird nie persistiert/zurück-
 * gegeben/geloggt (WR-19a). RBAC (Admin) erzwingt die REST-Schicht; jede Änderung wird auditiert.
 */
@ApplicationScoped
public class ManageSecretsService implements ManageSecretsUseCase {

    private final ManagedSecretPort secrets;
    private final SecretEncryptionPort encryption;
    private final SecretStoreWritePort vault;
    private final SecretReferencePort references;
    private final AuditPort audit;

    @Inject
    public ManageSecretsService(ManagedSecretPort secrets, SecretEncryptionPort encryption,
                                SecretStoreWritePort vault, SecretReferencePort references, AuditPort audit) {
        this.secrets = secrets;
        this.encryption = encryption;
        this.vault = vault;
        this.references = references;
        this.audit = audit;
    }

    @Override
    public List<ManagedSecret> list() {
        return secrets.all().stream().map(this::refineResolvable).toList();
    }

    @Override
    public ManagedSecret save(SaveSecretCommand cmd, String actor) {
        ManagedSecret saved = switch (cmd.mode()) {
            case REFERENCE -> saveReference(cmd);
            case VAULT_WRITE -> saveVaultWrite(cmd);
            case DB_ENCRYPTED -> saveDbEncrypted(cmd);
        };
        audit.record(AuditEvent.of(actor, "secret.save", saved.name(), "Modus " + saved.mode()));
        return refineResolvable(saved);
    }

    @Override
    public void delete(UUID id, String actor) {
        String name = secrets.byId(id).map(ManagedSecret::name).orElse(id.toString());
        secrets.delete(id);
        audit.record(AuditEvent.of(actor, "secret.delete", name, "Secret gelöscht"));
    }

    private ManagedSecret saveReference(SaveSecretCommand cmd) {
        if (cmd.reference() == null || cmd.reference().isBlank()) {
            throw new IllegalArgumentException("REFERENCE benötigt eine Referenz (env:/vault:)");
        }
        return secrets.save(cmd.id(), cmd.name(), SecretStorageMode.REFERENCE, cmd.reference(), null);
    }

    private ManagedSecret saveVaultWrite(SaveSecretCommand cmd) {
        if (!vault.available()) {
            throw new IllegalStateException("Vault-Write ist nicht verfügbar (kein Secret-Store verdrahtet)");
        }
        requirePlaintext(cmd);
        String path = cmd.vaultPath() == null || cmd.vaultPath().isBlank()
                ? "scanner/" + cmd.name() : cmd.vaultPath();
        // Klartext geht nur an den Store; behalten wird ausschliesslich die Referenz.
        String reference = vault.write(path, cmd.plaintext());
        return secrets.save(cmd.id(), cmd.name(), SecretStorageMode.VAULT_WRITE, reference, null);
    }

    private ManagedSecret saveDbEncrypted(SaveSecretCommand cmd) {
        if (!encryption.available()) {
            throw new IllegalStateException(
                    "DB-verschlüsselte Secrets benötigen scanner.secrets.encryption-key (NFR-30)");
        }
        requirePlaintext(cmd);
        String ciphertext = encryption.encrypt(cmd.plaintext());
        return secrets.save(cmd.id(), cmd.name(), SecretStorageMode.DB_ENCRYPTED, "", ciphertext);
    }

    private void requirePlaintext(SaveSecretCommand cmd) {
        if (cmd.plaintext() == null || cmd.plaintext().isEmpty()) {
            throw new IllegalArgumentException("Dieser Modus benötigt einen Wert");
        }
    }

    /** Verfeinert die Auflösbarkeit für REFERENCE über den SecretReferencePort (Status, nie Wert). */
    private ManagedSecret refineResolvable(ManagedSecret s) {
        if (s.mode() != SecretStorageMode.REFERENCE) {
            return s;
        }
        boolean resolvable = references.resolvable(s.reference());
        return new ManagedSecret(s.id(), s.name(), s.mode(), s.reference(), s.hasStoredValue(), resolvable);
    }
}
