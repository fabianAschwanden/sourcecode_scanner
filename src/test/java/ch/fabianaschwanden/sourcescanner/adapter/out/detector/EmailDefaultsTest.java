package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.adapter.out.detector.EmailMatcher.Defaults;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Laden der Test-E-Mail-Defaults aus YAML (DR-57). Framework-frei (TR-24). */
class EmailDefaultsTest {

    @Test
    void laedt_alle_listen_aus_yaml_kleingeschrieben(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("test-emails.yaml");
        Files.writeString(file, """
                version: 1
                tlds: [Test]
                slds: [Example]
                domains: [Googletest.com]
                localParts: [Tester]
                addresses: [Mail@Mail.com]
                """);
        Defaults d = Defaults.fromFile(file);
        assertTrue(d.tlds().contains("test"));
        assertTrue(d.slds().contains("example"));
        assertTrue(d.domains().contains("googletest.com"));
        assertTrue(d.localParts().contains("tester"));
        assertTrue(d.addresses().contains("mail@mail.com"));
    }

    @Test
    void fehlende_datei_faellt_auf_eingebaute_liste_zurueck() {
        Defaults d = Defaults.fromFile(Path.of("/does/not/exist-emails.yaml"));
        // Built-in enthält die bekannten Defaults — der Filter läuft nie still leer.
        assertEquals(Defaults.builtIn().localParts(), d.localParts());
        assertTrue(d.localParts().contains("musterfrau"));
    }

    @Test
    void mitgeliefertes_config_file_deckt_die_gemeldeten_testnamen_ab() {
        // Schützt das ausgelieferte config/pii-test-emails.yaml vor versehentlichem Verlust der Einträge.
        Defaults d = Defaults.fromFile(Path.of("config/pii-test-emails.yaml"));
        assertTrue(d.localParts().contains("tester"));
        assertTrue(d.localParts().contains("musterfrau"));
        assertTrue(d.addresses().contains("mail@mail.com"));
        assertTrue(d.tlds().contains("example"));
    }
}
