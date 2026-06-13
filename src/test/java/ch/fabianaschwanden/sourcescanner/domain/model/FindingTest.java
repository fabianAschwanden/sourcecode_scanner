package ch.fabianaschwanden.sourcescanner.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FindingTest {

    private Finding finding(String ruleId, String file, int line, String redacted) {
        return new Finding("secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH,
                ruleId, file, line, redacted, "abc123", false);
    }

    @Test
    void invarianten_brechen_bei_leeren_pflichtfeldern() {
        assertThrows(IllegalArgumentException.class, () -> finding("", "a.txt", 1, "AKIA****"));
        assertThrows(IllegalArgumentException.class, () -> finding("rule", " ", 1, "AKIA****"));
        assertThrows(IllegalArgumentException.class, () -> finding("rule", "a.txt", 0, "AKIA****"));
    }

    @Test
    void fingerprint_ist_deterministisch_und_unterscheidet() {
        Finding a = finding("aws", "src/A.java", 4, "AKIA****KEY1");
        Finding b = finding("aws", "src/A.java", 4, "AKIA****KEY1");
        Finding other = finding("aws", "src/B.java", 4, "AKIA****KEY1");
        assertEquals(a.fingerprint(), b.fingerprint());
        assertNotEquals(a.fingerprint(), other.fingerprint());
    }
}
