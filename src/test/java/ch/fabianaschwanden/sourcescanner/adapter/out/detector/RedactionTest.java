package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class RedactionTest {

    @Test
    void zeigt_erste_und_letzte_vier_zeichen() {
        // 20 Zeichen → 4 sichtbar + 12 maskiert + 4 sichtbar
        assertEquals("AKIA************MPLE", Redaction.redact("AKIAIOSFODNN7EXAMPLE"));
    }

    @Test
    void kurze_werte_werden_vollstaendig_maskiert() {
        assertEquals("********", Redaction.redact("12345678"));
        assertFalse(Redaction.redact("secret12").contains("secret"));
    }

    @Test
    void null_wird_zu_leerstring() {
        assertEquals("", Redaction.redact(null));
    }
}
