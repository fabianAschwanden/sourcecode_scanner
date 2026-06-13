package ch.fabianaschwanden.sourcescanner.adapter.out.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SarifReportWriterTest {

    private final SarifReportWriter writer = new SarifReportWriter();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void schreibt_valides_sarif_skelett_nur_mit_redigiertem_treffer(@TempDir Path dir) throws IOException {
        Finding finding = new Finding("secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH,
                "aws-access-key-id", "src/Config.java", 2, "AKIA************MPLE", "deadbeef", false);
        ScanResult result = new ScanResult("repo", Instant.now(), Instant.now(),
                List.of(finding), List.of());
        List<DetectorRule> rules = List.of(
                new DetectorRule("aws-access-key-id", "aws-access-key-id", "AWS Access Key ID", Severity.HIGH));

        Path file = writer.write(List.of(result), rules, dir);

        assertTrue(Files.exists(file));
        String content = Files.readString(file);
        assertFalse(content.contains("AKIAIOSFODNN7EXAMPLE"), "kein Klartext-Secret im Report");

        JsonNode root = json.readTree(content);
        assertEquals("2.1.0", root.get("version").asText());
        JsonNode run = root.get("runs").get(0);
        assertEquals("sourcecode-scanner", run.get("tool").get("driver").get("name").asText());
        JsonNode res = run.get("results").get(0);
        assertEquals("aws-access-key-id", res.get("ruleId").asText());
        assertEquals("error", res.get("level").asText());
        assertEquals(2, res.get("locations").get(0).get("physicalLocation").get("region")
                .get("startLine").asInt());
        assertTrue(res.get("partialFingerprints").has("scannerFingerprint/v1"));
    }

    @Test
    void leeres_ergebnis_erzeugt_leere_results(@TempDir Path dir) throws IOException {
        ScanResult result = new ScanResult("repo", Instant.now(), Instant.now(), List.of(), List.of());
        Path file = writer.write(List.of(result), List.of(), dir);
        JsonNode root = json.readTree(Files.readString(file));
        assertEquals(0, root.get("runs").get(0).get("results").size());
    }
}
