package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Framework-frei: kein Quarkus, reiner Unit-Test gegen scan() (TR-24). */
class RegexRulesetDetectorTest {

    private static final String FAKE_AWS_KEY = "AKIAIOSFODNN7EXAMPLE";

    private final RegexRulesetDetector detector = new RegexRulesetDetector();
    private final DetectorConfig enabled = new DetectorConfig(true, Map.of());

    private ScanUnit unit(String content) {
        return new ScanUnit("repo", "src/Config.java", "deadbeef", "a@b.ch",
                Instant.now(), content, null);
    }

    @Test
    void findet_aws_key_mit_korrekter_zeile_und_redigiert() {
        ScanUnit unit = unit("line one\nString k = \"" + FAKE_AWS_KEY + "\";\nline three");
        List<Finding> findings = detector.scan(unit, enabled);

        assertEquals(1, findings.size());
        Finding f = findings.getFirst();
        assertEquals("aws-access-key-id", f.ruleId());
        assertEquals(2, f.line());
        assertFalse(f.redactedMatch().contains(FAKE_AWS_KEY), "Klartext darf nicht im Finding stehen");
        assertTrue(f.redactedMatch().contains("*"), "Treffer muss maskiert sein");
        assertTrue(f.redactedMatch().startsWith("AKIA"));
    }

    @Test
    void sauberer_inhalt_liefert_keine_funde() {
        assertTrue(detector.scan(unit("nur harmloser Text\nkeine Geheimnisse hier"), enabled).isEmpty());
    }

    @Test
    void deaktivierter_detektor_scannt_nicht() {
        ScanUnit unit = unit("String k = \"" + FAKE_AWS_KEY + "\";");
        assertTrue(detector.scan(unit, new DetectorConfig(false, Map.of())).isEmpty());
    }

    @Test
    void privater_schluessel_wird_als_critical_erkannt() {
        ScanUnit unit = unit("-----BEGIN RSA PRIVATE KEY-----");
        List<Finding> findings = detector.scan(unit, enabled);
        assertTrue(findings.stream().anyMatch(f -> f.ruleId().equals("private-key")));
    }
}
