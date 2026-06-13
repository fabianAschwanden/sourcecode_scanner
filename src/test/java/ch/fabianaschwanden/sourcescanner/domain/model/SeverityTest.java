package ch.fabianaschwanden.sourcescanner.domain.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeverityTest {

    @Test
    void atLeast_vergleicht_aufsteigend() {
        assertTrue(Severity.CRITICAL.atLeast(Severity.HIGH));
        assertTrue(Severity.HIGH.atLeast(Severity.HIGH));
        assertFalse(Severity.MEDIUM.atLeast(Severity.HIGH));
        assertFalse(Severity.INFO.atLeast(Severity.LOW));
    }
}
