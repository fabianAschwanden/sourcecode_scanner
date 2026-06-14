package ch.fabianaschwanden.sourcescanner.adapter.out.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** AES-GCM-Verschlüsselung für DB-verschlüsselte Secrets (NFR-29/30) — direkt instanziierbar. */
class AesSecretEncryptionAdapterTest {

    private final AesSecretEncryptionAdapter adapter =
            new AesSecretEncryptionAdapter(Optional.of("test-key-material"));

    @Test
    void verschluesselt_und_entschluesselt_round_trip() {
        String ct = adapter.encrypt("super-secret-value");
        assertNotEquals("super-secret-value", ct, "Chiffrat darf nicht Klartext sein");
        assertEquals("super-secret-value", adapter.decrypt(ct).orElseThrow());
    }

    @Test
    void zwei_chiffrate_desselben_werts_unterscheiden_sich() {
        // Zufälliger IV je Verschlüsselung ⇒ unterschiedliche Chiffrate.
        assertNotEquals(adapter.encrypt("x"), adapter.encrypt("x"));
    }

    @Test
    void ohne_schluessel_ist_modus_deaktiviert() {
        AesSecretEncryptionAdapter noKey = new AesSecretEncryptionAdapter(Optional.empty());
        assertFalse(noKey.available());
        assertTrue(noKey.decrypt("anything").isEmpty());
    }

    @Test
    void mit_schluessel_verfuegbar() {
        assertTrue(adapter.available());
    }
}
