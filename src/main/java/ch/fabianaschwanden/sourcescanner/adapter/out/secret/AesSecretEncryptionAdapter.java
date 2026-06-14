package ch.fabianaschwanden.sourcescanner.adapter.out.secret;

import ch.fabianaschwanden.sourcescanner.domain.port.out.SecretEncryptionPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * AES-GCM-Verschlüsselung für DB-verschlüsselte Secrets (NFR-29/30). Der Schlüssel stammt aus der
 * Konfiguration/Env ({@code scanner.secrets.encryption-key}); fehlt er, ist der Modus deaktiviert
 * ({@link #available()} == false) — es wird nie unverschlüsselt gespeichert. Der 256-bit-Schlüssel
 * wird aus dem konfigurierten Wert via SHA-256 abgeleitet; pro Wert ein zufälliger 96-bit-IV, der dem
 * Chiffrat vorangestellt wird. Der Schlüssel liegt nie in der DB.
 */
@ApplicationScoped
public class AesSecretEncryptionAdapter implements SecretEncryptionPort {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public AesSecretEncryptionAdapter(
            @ConfigProperty(name = "scanner.secrets.encryption-key") Optional<String> configuredKey) {
        this.key = configuredKey
                .filter(k -> !k.isBlank())
                .map(AesSecretEncryptionAdapter::deriveKey)
                .orElse(null);
    }

    @Override
    public boolean available() {
        return key != null;
    }

    @Override
    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ct, 0, combined, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("secret encryption failed", e);
        }
    }

    @Override
    public Optional<String> decrypt(String ciphertext) {
        if (key == null || ciphertext == null || ciphertext.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] ct = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return Optional.of(new String(cipher.doFinal(ct), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException(
                    "DB-encrypted secrets require scanner.secrets.encryption-key (NFR-30)");
        }
    }

    private static byte[] deriveKey(String configured) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(configured.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("cannot derive secret key", e);
        }
    }
}
