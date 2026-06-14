package ch.fabianaschwanden.sourcescanner.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** Deterministisches, redigierendes Hashing hochgeladener Werte (NFR-23/26). */
class ValueHashingTest {

    @Test
    void gleicher_wert_gleicher_hash() {
        assertEquals(ValueHashing.hash("12345678", ""), ValueHashing.hash("12345678", ""));
    }

    @Test
    void hash_enthaelt_nie_den_klartext() {
        String hash = ValueHashing.hash("12345678", "");
        assertEquals(64, hash.length(), "SHA-256 hex");
        org.junit.jupiter.api.Assertions.assertFalse(hash.contains("12345678"));
    }

    @Test
    void pepper_aendert_den_hash() {
        assertNotEquals(ValueHashing.hash("max", "pepper-A"), ValueHashing.hash("max", "pepper-B"));
    }

    @Test
    void null_wert_ist_leerer_hash() {
        assertEquals("", ValueHashing.hash(null, "x"));
    }
}
