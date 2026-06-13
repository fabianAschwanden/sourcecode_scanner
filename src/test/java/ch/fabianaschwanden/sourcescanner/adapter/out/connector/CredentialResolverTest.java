package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CredentialResolverTest {

    private final CredentialResolver resolver = new CredentialResolver();

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
}
