package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import ch.fabianaschwanden.sourcescanner.domain.model.ManagedSecret;
import ch.fabianaschwanden.sourcescanner.domain.model.SecretStorageMode;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ManagedSecretPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.SecretEncryptionPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Löst {@code secret:NAME}-Referenzen auf ein UI-verwaltetes, DB-verschlüsseltes Secret auf (WR-19c,
 * NFR-29/30). Eigene Bohne, damit der DB-Zugriff mit aktivem Request-Kontext und (Lese-)Transaktion
 * läuft — auch wenn die Auflösung aus dem asynchronen Scan-Thread heraus erfolgt (sonst
 * {@code ContextNotActiveException}). Der entschlüsselte Wert ist transient und wird nie geloggt.
 */
@ApplicationScoped
public class ManagedSecretResolver {

    private final ManagedSecretPort secrets;
    private final SecretEncryptionPort encryption;

    @Inject
    public ManagedSecretResolver(ManagedSecretPort secrets, SecretEncryptionPort encryption) {
        this.secrets = secrets;
        this.encryption = encryption;
    }

    /**
     * Liefert den entschlüsselten Klartext eines DB-verschlüsselten Secrets (nie loggen/zurückgeben).
     * {@code @Transactional(REQUIRED)} öffnet eine (Lese-)Transaktion samt JPA-Session, auch wenn die
     * Auflösung aus dem async Scan-Thread ohne aktiven Kontext kommt; {@code @ActivateRequestContext}
     * stellt zusätzlich den Request-Scope bereit.
     */
    @ActivateRequestContext
    @Transactional(Transactional.TxType.REQUIRED)
    public String resolve(String name) {
        ManagedSecret secret = secrets.byName(name)
                .orElseThrow(() -> new IllegalStateException("no managed secret named: secret:" + name));
        if (secret.mode() != SecretStorageMode.DB_ENCRYPTED) {
            throw new IllegalStateException(
                    "secret:" + name + " is not DB-encrypted (mode " + secret.mode() + "); reference it directly");
        }
        String ciphertext = secrets.encryptedValue(secret.id())
                .orElseThrow(() -> new IllegalStateException("managed secret has no stored value: secret:" + name));
        return encryption.decrypt(ciphertext)
                .orElseThrow(() -> new IllegalStateException("could not decrypt managed secret: secret:" + name));
    }
}
