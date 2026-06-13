package ch.fabianaschwanden.sourcescanner.adapter.out.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlReportWriterTest {

    private final HtmlReportWriter writer = new HtmlReportWriter();

    @Test
    void schreibt_html_nur_mit_redigiertem_treffer(@TempDir Path dir) throws Exception {
        Finding f = new Finding("secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH,
                "aws-access-key-id", "src/Config.java", 2, "AKIA************MPLE", "c1", false);
        ScanResult result = new ScanResult("repo", Instant.now(), Instant.now(), List.of(f), List.of());

        Path file = writer.write(List.of(result), List.of(), dir);

        assertTrue(Files.exists(file));
        String html = Files.readString(file);
        assertTrue(html.contains("<html"));
        assertTrue(html.contains("aws-access-key-id"));
        assertTrue(html.contains("AKIA************MPLE"));
        assertFalse(html.contains("AKIAIOSFODNN7EXAMPLE"), "kein Klartext im HTML");
    }
}
