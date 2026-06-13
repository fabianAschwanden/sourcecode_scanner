package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Framework-frei (TR-24). */
class PiiPatternsDetectorTest {

    private final PiiPatternsDetector detector = new PiiPatternsDetector();

    private ScanUnit unit(String content) {
        return new ScanUnit("repo", "src/Data.java", "c1", "a@b.ch", Instant.now(), content, null);
    }

    private DetectorConfig allPatterns() {
        return new DetectorConfig(true, Map.of());
    }

    private List<Finding> scan(String content, DetectorConfig cfg) {
        return detector.scan(unit(content), cfg);
    }

    @Test
    void gueltige_kreditkarte_wird_gemeldet_und_redigiert() {
        List<Finding> f = scan("card = 4111 1111 1111 1111", allPatterns());
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("creditcard")));
        Finding cc = f.stream().filter(x -> x.ruleId().equals("creditcard")).findFirst().orElseThrow();
        assertFalse(cc.redactedMatch().contains("4111 1111 1111 1111"));
    }

    @Test
    void luhn_ungueltige_kreditkarte_wird_nicht_gemeldet() {
        // 4111...1112 verletzt die Luhn-Prüfsumme
        List<Finding> f = scan("card = 4111 1111 1111 1112", allPatterns());
        assertFalse(f.stream().anyMatch(x -> x.ruleId().equals("creditcard")));
    }

    @Test
    void email_und_iban_werden_erkannt() {
        List<Finding> f = scan("mail: john.doe@example.com\niban: DE89 3704 0044 0532 0130 00", allPatterns());
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("email")));
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("iban")));
    }

    @Test
    void ungueltige_iban_pruefsumme_wird_nicht_gemeldet() {
        List<Finding> f = scan("iban: DE00 3704 0044 0532 0130 00", allPatterns());
        assertFalse(f.stream().anyMatch(x -> x.ruleId().equals("iban")));
    }

    @Test
    void patterns_filter_begrenzt_auf_email() {
        DetectorConfig onlyEmail = new DetectorConfig(true, Map.of("patterns", List.of("email")));
        List<Finding> f = scan("mail a@b.com card 4111 1111 1111 1111", onlyEmail);
        assertTrue(f.stream().allMatch(x -> x.ruleId().equals("email")));
    }

    @Test
    void deaktiviert_scannt_nicht() {
        assertTrue(scan("a@b.com", new DetectorConfig(false, Map.of())).isEmpty());
    }
}
