package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import ch.fabianaschwanden.sourcescanner.domain.model.ManagedSecret;
import ch.fabianaschwanden.sourcescanner.domain.model.SecretStorageMode;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ManagedSecretPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.SecretEncryptionPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Löst Credential-Referenzen auf (NFR-08, IR-30): {@code env:NAME} aus der Umgebung,
 * {@code secret:NAME} aus einem UI-verwalteten DB-verschlüsselten Secret (WR-19a, NFR-29/30) und
 * {@code vault:path#key} ist in Phase 2 ein klar gemeldeter Stub (echte Vault-Anbindung ist
 * Server-Thema, Phase 4). Der Klartext-Token verlässt diese Klasse nur zum authentifizierten Klonen
 * und wird nie geloggt.
 */
@ApplicationScoped
public class CredentialResolver {

    private final Instance<ManagedSecretPort> secrets;
    private final Instance<SecretEncryptionPort> encryption;

    @Inject
    public CredentialResolver(Instance<ManagedSecretPort> secrets, Instance<SecretEncryptionPort> encryption) {
        this.secrets = secrets;
        this.encryption = encryption;
    }

    /**
     * Bequemer Konstruktor ohne Secret-Store (nur {@code env:}/{@code vault:}); {@code secret:}-Referenzen
     * sind damit nicht auflösbar. Für direkte Instanziierung in Tests/CLI-Kontexten.
     */
    public CredentialResolver() {
        this(null, null);
    }

    /** Löst eine {@code tokenRef} auf; {@code empty} bei {@code null}/leer (anonymer Zugriff). */
    public Optional<String> resolve(String tokenRef) {
        if (tokenRef == null || tokenRef.isBlank()) {
            return Optional.empty();
        }
        if (tokenRef.startsWith("env:")) {
            String name = tokenRef.substring("env:".length());
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("environment variable not set for tokenRef: env:" + name);
            }
            return Optional.of(value);
        }
        if (tokenRef.startsWith("secret:")) {
            return Optional.of(resolveManaged(tokenRef.substring("secret:".length())));
        }
        if (tokenRef.startsWith("vault:")) {
            throw new UnsupportedOperationException(
                    "vault: credential references require the server profile (Phase 4); use env: for CLI scans");
        }
        throw new IllegalArgumentException(
                "unsupported tokenRef scheme (expected env:, secret: or vault:): " + tokenRef);
    }

    /** Entschlüsselt transient ein DB-verschlüsseltes, UI-verwaltetes Secret (nie loggen/zurückgeben). */
    private String resolveManaged(String name) {
        if (secrets == null || encryption == null || secrets.isUnsatisfied() || encryption.isUnsatisfied()) {
            throw new IllegalStateException(
                    "secret: references require the managed secret store (server/dev profile): secret:" + name);
        }
        ManagedSecret secret = secrets.get().byName(name)
                .orElseThrow(() -> new IllegalStateException("no managed secret named: secret:" + name));
        if (secret.mode() != SecretStorageMode.DB_ENCRYPTED) {
            throw new IllegalStateException(
                    "secret:" + name + " is not DB-encrypted (mode " + secret.mode() + "); reference it directly");
        }
        String ciphertext = secrets.get().encryptedValue(secret.id())
                .orElseThrow(() -> new IllegalStateException("managed secret has no stored value: secret:" + name));
        return encryption.get().decrypt(ciphertext)
                .orElseThrow(() -> new IllegalStateException("could not decrypt managed secret: secret:" + name));
    }
}
