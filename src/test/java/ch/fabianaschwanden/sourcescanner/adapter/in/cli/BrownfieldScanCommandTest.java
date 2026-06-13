package ch.fabianaschwanden.sourcescanner.adapter.in.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-End über den verdrahteten CDI-Graphen: Brownfield-Workflow (Baseline + failOnNewOnly),
 * Suppression und HTML-Report (Phase 2). Prüft den Exit-Code-Vertrag und die Rauschunterdrückung.
 */
@QuarkusTest
class BrownfieldScanCommandTest {

    private static final String SECRET_LINE = "String k = \"AKIAIOSFODNN7EXAMPLE\";\n";

    @Inject
    @TopCommand
    ScanCommand command;

    private Path initRepo(Path dir, String content) throws Exception {
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            Files.writeString(dir.resolve("Config.java"), content);
            git.add().addFilepattern("Config.java").call();
            git.commit().setMessage("init").setAuthor("Test", "t@example.ch").call();
        }
        return dir;
    }

    private void addCommit(Path dir, String fileName, String content) throws Exception {
        try (Git git = Git.open(dir.toFile())) {
            Files.writeString(dir.resolve(fileName), content);
            git.add().addFilepattern(fileName).call();
            git.commit().setMessage("add " + fileName).setAuthor("Test", "t@example.ch").call();
        }
    }

    private Path writeConfig(Path dir, Path repo, Path baseline, boolean failOnNewOnly, String suppress) throws Exception {
        Path cfg = dir.resolve("scanner.yaml");
        Files.writeString(cfg, """
                scan:
                  repositories:
                    - { type: localGit, id: self, path: %s }
                  history: { mode: full }
                  concurrency: { workers: 2, detectorTimeoutSeconds: 30 }
                  detectors:
                    secrets: { enabled: true }
                  baseline: %s
                  %s
                  gate: { failOn: HIGH, failOnNewOnly: %s, softFail: false }
                  output: { formats: [sarif, html], directory: %s, redact: true }
                """.formatted(repo, baseline, suppress, failOnNewOnly, dir.resolve("reports")));
        return cfg;
    }

    @Test
    void brownfield_baseline_workflow(@TempDir Path dir) throws Exception {
        Path repo = initRepo(dir.resolve("repo"), SECRET_LINE);
        Path baseline = dir.resolve(".scanner-baseline.json");

        // 1) Ohne Baseline bricht das Gate (Exit 1).
        command.config = writeConfig(dir, repo, baseline, true, "");
        command.outputFormats = List.of("sarif");
        command.baseline = null;
        command.writeBaseline = false;
        assertEquals(1, command.call());

        // 2) Baseline aus dem Erstscan schreiben (Exit 0).
        command.writeBaseline = true;
        assertEquals(0, command.call());
        assertTrue(Files.exists(baseline), "Baseline-Datei muss erzeugt sein");

        // 3) Folgelauf mit Baseline + failOnNewOnly: Altfund zählt nicht (Exit 0).
        command.writeBaseline = false;
        assertEquals(0, command.call());

        // 4) Neuer Fund nach Baseline bricht das Gate (Exit 1).
        addCommit(repo, "Other.java", "String t = \"ghp_0123456789012345678901234567890123ab\";\n");
        assertEquals(1, command.call());
    }

    @Test
    void pfad_suppression_unterdrueckt_fund(@TempDir Path dir) throws Exception {
        Path repo = initRepo(dir.resolve("repo"), SECRET_LINE);
        Path baseline = dir.resolve(".scanner-baseline.json");
        command.config = writeConfig(dir, repo, baseline, false,
                "suppress:\n    - { path: '**/Config.java', detector: secrets, reason: test }");
        command.outputFormats = List.of("sarif", "html");
        command.baseline = null;
        command.writeBaseline = false;

        assertEquals(0, command.call(), "unterdrückter Fund darf das Gate nicht brechen");

        Path html = dir.resolve("reports").resolve("report.html");
        assertTrue(Files.exists(html));
        String content = Files.readString(html);
        assertFalse(content.contains("AKIAIOSFODNN7EXAMPLE"), "kein Klartext im HTML");
    }

    @Test
    void inline_suppression_unterdrueckt_fund(@TempDir Path dir) throws Exception {
        Path repo = initRepo(dir.resolve("repo"),
                "String k = \"AKIAIOSFODNN7EXAMPLE\"; // scanner:ignore-secret reason=\"docs example\"\n");
        Path baseline = dir.resolve(".scanner-baseline.json");
        command.config = writeConfig(dir, repo, baseline, false, "");
        command.outputFormats = List.of("sarif");
        command.baseline = null;
        command.writeBaseline = false;

        assertEquals(0, command.call(), "Inline-Direktive muss den Fund unterdrücken");
    }
}
