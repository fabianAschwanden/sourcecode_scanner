package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PiiCustomRegexDetectorTest {

    private final PiiCustomRegexDetector detector = new PiiCustomRegexDetector();

    private ScanUnit unit(String content) {
        return new ScanUnit("repo", "src/Data.java", "c1", "a@b.ch", Instant.now(), content, null);
    }

    private DetectorConfig withRule(String name, String pattern, String severity) {
        return new DetectorConfig(true, Map.of("customRegex",
                List.of(Map.of("name", name, "pattern", pattern, "severity", severity))));
    }

    @Test
    void custom_muster_matcht_mit_ruleId_und_severity() {
        List<Finding> f = detector.scan(unit("id CUST-12345678 hier"), withRule("customer-id", "CUST-\\d{8}", "HIGH"));
        assertEquals(1, f.size());
        assertEquals("customer-id", f.getFirst().ruleId());
        assertEquals(Severity.HIGH, f.getFirst().severity());
        assertTrue(f.getFirst().redactedMatch().contains("*"));
    }

    @Test
    void ohne_customRegex_keine_funde() {
        assertTrue(detector.scan(unit("CUST-12345678"), new DetectorConfig(true, Map.of())).isEmpty());
    }

    @Test
    void ungueltiges_regex_degradiert_mit_klarer_meldung() {
        DetectorConfig bad = withRule("broken", "CUST-[", "HIGH");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> detector.scan(unit("x"), bad));
        assertTrue(ex.getMessage().contains("broken"));
    }
}
