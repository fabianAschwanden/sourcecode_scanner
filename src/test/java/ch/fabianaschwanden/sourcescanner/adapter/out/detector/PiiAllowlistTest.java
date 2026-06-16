package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Laden + Normalisierung der PII-Allowlist (DR-23). Framework-frei (TR-24). */
class PiiAllowlistTest {

    @Test
    void laedt_und_normalisiert_werte_aus_yaml(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("allow.yaml");
        Files.writeString(file, """
                version: 1
                iban:
                  - value: "ch93 0076 2011 6238 5295 7"
                creditCard:
                  - value: "4111-1111-1111-1111"
                """);
        PiiAllowlist allow = PiiAllowlist.fromFile(file);

        // IBAN: gross + ohne Whitespace; Treffer im Text mit Leerzeichen/Kleinschreibung matcht.
        assertTrue(allow.contains("iban", "CH93 0076 2011 6238 5295 7"));
        // Kreditkarte: ohne Bindestriche/Whitespace.
        assertTrue(allow.contains("creditcard", "4111 1111 1111 1111"));
        assertFalse(allow.contains("iban", "DE89 3704 0044 0532 0130 00"));
    }

    @Test
    void fehlende_datei_ergibt_leere_allowlist() {
        PiiAllowlist allow = PiiAllowlist.fromFile(Path.of("/does/not/exist-allow.yaml"));
        assertFalse(allow.contains("iban", "CH93 0076 2011 6238 5295 7"));
        assertFalse(allow.contains("creditcard", "4111 1111 1111 1111"));
    }

    @Test
    void mitgeliefertes_config_file_enthaelt_die_kanonischen_beispiele() {
        // Schützt davor, dass das ausgelieferte config/pii-allowlist.yaml versehentlich kaputtgeht.
        PiiAllowlist allow = PiiAllowlist.fromFile(Path.of("config/pii-allowlist.yaml"));
        assertTrue(allow.contains("iban", "CH93 0076 2011 6238 5295 7"));
        assertTrue(allow.contains("creditcard", "4111 1111 1111 1111"));
    }
}
