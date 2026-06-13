package ch.fabianaschwanden.sourcescanner.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    void defaults_sind_gesetzt() {
        Settings s = Settings.defaults();
        assertEquals(Severity.HIGH, s.defaultFailOn());
        assertEquals("full", s.defaultScanMode());
        assertEquals(365, s.retentionDays());
        assertTrue(s.secretRefs().isEmpty());
    }

    @Test
    void leere_werte_werden_auf_defaults_normalisiert() {
        Settings s = new Settings(null, null, " ", 0, null);
        assertEquals(Severity.HIGH, s.defaultFailOn());
        assertEquals("full", s.defaultScanMode());
        assertEquals(365, s.retentionDays());
        assertTrue(s.secretRefs().isEmpty());
    }

    @Test
    void secretRefs_werden_uebernommen() {
        Settings s = new Settings("a@b.ch", Severity.CRITICAL, "incremental", 30, List.of("env:GH"));
        assertEquals(List.of("env:GH"), s.secretRefs());
        assertEquals(Severity.CRITICAL, s.defaultFailOn());
    }
}
