package ch.fabianaschwanden.sourcescanner.domain.port.out;

import java.util.Optional;

/**
 * Symmetrische Verschlüsselung für DB-verschlüsselte Secrets (NFR-29/30). Der Schlüssel stammt aus
 * Env/Secret-Store, nie aus der DB. Ist kein Schlüssel konfiguriert, ist der Modus deaktiviert
 * ({@link #available()} == false) — kein unverschlüsseltes Fallback.
 */
public interface SecretEncryptionPort {

    boolean available();

    /** Verschlüsselt Klartext zu einem speicherbaren Chiffrat (Base64). */
    String encrypt(String plaintext);

    /** Entschlüsselt ein gespeichertes Chiffrat transient zum Auflösen; nie loggen/zurückgeben. */
    Optional<String> decrypt(String ciphertext);
}
