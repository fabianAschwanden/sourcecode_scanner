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

/** Framework-frei (TR-24): kein Quarkus. */
class HighEntropyDetectorTest {

    private final HighEntropyDetector detector = new HighEntropyDetector();

    private ScanUnit unit(String content) {
        return new ScanUnit("repo", "src/Config.java", "c1", "a@b.ch", Instant.now(), content, null);
    }

    private DetectorConfig config(double threshold, int minLength) {
        Map<String, Object> entropy = Map.of("enabled", true, "threshold", threshold, "minLength", minLength);
        return new DetectorConfig(true, Map.of("entropy", entropy));
    }

    @Test
    void hochentropischer_string_ueber_schwelle_wird_gemeldet_und_redigiert() {
        String highEntropy = "aZ9kQ2xV7bN4mL8pR3wT6yU1cD5fG0hJ"; // gemischt, hohe Entropie
        List<Finding> findings = detector.scan(unit("token = \"" + highEntropy + "\""), config(4.0, 20));
        assertFalse(findings.isEmpty());
        Finding f = findings.getFirst();
        assertFalse(f.redactedMatch().contains(highEntropy), "Klartext darf nicht erscheinen");
        assertTrue(f.redactedMatch().contains("*"));
    }

    @Test
    void niedrigentropischer_string_wird_nicht_gemeldet() {
        assertTrue(detector.scan(unit("value = \"aaaaaaaaaaaaaaaaaaaaaaaa\""), config(4.0, 20)).isEmpty());
    }

    @Test
    void unter_mindestlaenge_kein_fund() {
        String shortHigh = "aZ9kQ2xV"; // hohe Entropie, aber zu kurz
        assertTrue(detector.scan(unit("k = \"" + shortHigh + "\""), config(3.0, 20)).isEmpty());
    }

    @Test
    void ohne_entropy_block_inaktiv() {
        // secrets aktiviert, aber kein entropy-Block -> Entropie-Detektor meldet nichts
        DetectorConfig noEntropy = new DetectorConfig(true, Map.of());
        String highEntropy = "aZ9kQ2xV7bN4mL8pR3wT6yU1cD5fG0hJ";
        assertTrue(detector.scan(unit("token = \"" + highEntropy + "\""), noEntropy).isEmpty());
    }
}
