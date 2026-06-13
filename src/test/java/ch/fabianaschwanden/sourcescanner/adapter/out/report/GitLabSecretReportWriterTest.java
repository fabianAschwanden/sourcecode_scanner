package ch.fabianaschwanden.sourcescanner.adapter.out.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitLabSecretReportWriterTest {

    private final GitLabSecretReportWriter writer = new GitLabSecretReportWriter();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void erzeugt_gitlab_secret_detection_report_ohne_klartext(@TempDir Path dir) throws Exception {
        Finding f = new Finding("secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH,
                "aws-access-key-id", "src/Config.java", 2, "AKIA************MPLE", "c1", false);
        ScanResult result = new ScanResult("repo", Instant.now(), Instant.now(), List.of(f), List.of());

        Path file = writer.write(List.of(result), List.of(), dir);

        assertTrue(Files.exists(file));
        assertEquals("gl-secret-detection-report.json", file.getFileName().toString());
        String content = Files.readString(file);
        assertFalse(content.contains("AKIAIOSFODNN7EXAMPLE"), "kein Klartext im Report");

        JsonNode root = json.readTree(content);
        assertEquals("secret_detection", root.get("scan").get("type").asText());
        JsonNode vuln = root.get("vulnerabilities").get(0);
        assertEquals("High", vuln.get("severity").asText());
        assertEquals("src/Config.java", vuln.get("location").get("file").asText());
    }
}
