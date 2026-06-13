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

class TeamCityServiceMessageWriterTest {

    private final TeamCityServiceMessageWriter writer = new TeamCityServiceMessageWriter();

    @Test
    void erzeugt_service_messages_und_buildproblem_ohne_klartext(@TempDir Path dir) throws Exception {
        Finding f = new Finding("secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH,
                "aws-access-key-id", "src/Config.java", 2, "AKIA************MPLE", "c1", false);
        ScanResult result = new ScanResult("repo", Instant.now(), Instant.now(), List.of(f), List.of());

        Path file = writer.write(List.of(result), List.of(), dir);
        String content = Files.readString(file);

        assertTrue(content.contains("##teamcity[inspection "));
        assertTrue(content.contains("##teamcity[buildProblem"));
        assertFalse(content.contains("AKIAIOSFODNN7EXAMPLE"), "kein Klartext");
    }

    @Test
    void sauberes_ergebnis_ohne_buildproblem(@TempDir Path dir) {
        ScanResult clean = new ScanResult("repo", Instant.now(), Instant.now(), List.of(), List.of());
        Path file = writer.write(List.of(clean), List.of(), dir);
        assertTrue(Files.exists(file));
    }
}
