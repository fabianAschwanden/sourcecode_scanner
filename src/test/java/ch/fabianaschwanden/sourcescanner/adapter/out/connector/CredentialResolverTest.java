package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.ManagedSecret;
import ch.fabianaschwanden.sourcescanner.domain.model.SecretStorageMode;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ManagedSecretPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.SecretEncryptionPort;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialResolverTest {

    /** Resolver ohne Secret-Store (Instances „unsatisfied") — für env:/vault:-Tests. */
    private final CredentialResolver resolver = new CredentialResolver(empty(), empty());

    @Test
    void leere_ref_ist_anonym() {
        assertTrue(resolver.resolve(null).isEmpty());
        assertTrue(resolver.resolve("  ").isEmpty());
    }

    @Test
    void env_ref_wird_aufgeloest() {
        // PATH ist auf jedem System gesetzt — als deterministische env-Quelle nutzbar.
        assertTrue(resolver.resolve("env:PATH").isPresent());
        assertEquals(System.getenv("PATH"), resolver.resolve("env:PATH").orElseThrow());
    }

    @Test
    void fehlende_env_var_bricht() {
        assertThrows(IllegalStateException.class,
                () -> resolver.resolve("env:SCANNER_DOES_NOT_EXIST_42"));
    }

    @Test
    void vault_ref_ist_phase2_stub() {
        assertThrows(UnsupportedOperationException.class,
                () -> resolver.resolve("vault:secret/x#token"));
    }

    @Test
    void unbekanntes_schema_bricht() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("file:/etc/token"));
    }

    @Test
    void secret_ref_ohne_store_bricht_klar() {
        assertThrows(IllegalStateException.class, () -> resolver.resolve("secret:gh-token"));
    }

    @Test
    void secret_ref_wird_aus_db_secret_entschluesselt() {
        UUID id = UUID.randomUUID();
        var port = new FakeSecretPort(
                new ManagedSecret(id, "gh-token", SecretStorageMode.DB_ENCRYPTED, "", true, true),
                "ENC(ghp_real_token)");
        var enc = new FakeEncryption(); // entschlüsselt ENC(x) -> x
        CredentialResolver r = new CredentialResolver(single(port), single(enc));
        assertEquals("ghp_real_token", r.resolve("secret:gh-token").orElseThrow());
    }

    @Test
    void secret_ref_unbekannter_name_bricht() {
        CredentialResolver r = new CredentialResolver(single(new FakeSecretPort(null, null)),
                single(new FakeEncryption()));
        assertThrows(IllegalStateException.class, () -> r.resolve("secret:missing"));
    }

    // --- Test-Doubles -----------------------------------------------------------------------------

    private static final class FakeSecretPort implements ManagedSecretPort {
        private final ManagedSecret secret;
        private final String encryptedValue;

        FakeSecretPort(ManagedSecret secret, String encryptedValue) {
            this.secret = secret;
            this.encryptedValue = encryptedValue;
        }

        @Override
        public ManagedSecret save(UUID id, String name, SecretStorageMode mode, String reference, String encrypted) {
            return secret;
        }

        @Override
        public List<ManagedSecret> all() {
            return secret == null ? List.of() : List.of(secret);
        }

        @Override
        public Optional<ManagedSecret> byId(UUID id) {
            return Optional.ofNullable(secret);
        }

        @Override
        public Optional<ManagedSecret> byName(String name) {
            return secret != null && secret.name().equals(name) ? Optional.of(secret) : Optional.empty();
        }

        @Override
        public Optional<String> encryptedValue(UUID id) {
            return Optional.ofNullable(encryptedValue);
        }

        @Override
        public void delete(UUID id) {
            // no-op
        }
    }

    private static final class FakeEncryption implements SecretEncryptionPort {
        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String encrypt(String plaintext) {
            return "ENC(" + plaintext + ")";
        }

        @Override
        public Optional<String> decrypt(String ciphertext) {
            if (ciphertext.startsWith("ENC(") && ciphertext.endsWith(")")) {
                return Optional.of(ciphertext.substring(4, ciphertext.length() - 1));
            }
            return Optional.empty();
        }
    }

    // --- Minimal-Instance-Hilfen (CDI-frei testbar) -----------------------------------------------

    private static <T> Instance<T> empty() {
        return new StubInstance<>(null);
    }

    private static <T> Instance<T> single(T value) {
        return new StubInstance<>(value);
    }

    /** Minimaler {@link Instance}-Stub: leer (unsatisfied) oder genau ein Wert. */
    private static final class StubInstance<T> implements Instance<T> {
        private final T value;

        StubInstance(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public boolean isUnsatisfied() {
            return value == null;
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public Iterator<T> iterator() {
            return (value == null ? List.<T>of() : List.of(value)).iterator();
        }

        @Override
        public Instance<T> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroy(T instance) {
            // no-op
        }

        @Override
        public Handle<T> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Handle<T>> handles() {
            throw new UnsupportedOperationException();
        }
    }
}
