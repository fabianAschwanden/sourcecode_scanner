package ch.fabianaschwanden.sourcescanner.adapter.in.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-End über den verdrahteten CDI-Graphen (Connector + Detektor + Report + Gate).
 * Prüft den Exit-Code-Vertrag (docs/08 §7): 0 pass · 1 Gate verletzt · 2 Konfig-Fehler.
 */
@QuarkusTest
class ScanCommandTest {

    @Inject
    @TopCommand
    ScanCommand command;

    private Path initRepo(Path dir, String fileContent) throws Exception {
        try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
            Files.writeString(dir.resolve("Config.java"), fileContent);
            git.add().addFilepattern("Config.java").call();
            git.commit().setMessage("init").setAuthor("Test", "t@example.ch").call();
        }
        return dir;
    }

    private Path writeConfig(Path dir, Path repo, String reportDir) throws Exception {
        Path cfg = dir.resolve("scanner.yaml");
        Files.writeString(cfg, """
                scan:
                  repositories:
                    - { type: localGit, id: self, path: %s }
                  history: { mode: full }
                  concurrency: { workers: 2, detectorTimeoutSeconds: 30 }
                  detectors:
                    secrets: { enabled: true }
                  gate: { failOn: HIGH, softFail: false }
                  output: { formats: [sarif], directory: %s, redact: true }
                """.formatted(repo.toString(), reportDir));
        return cfg;
    }

    @Test
    void secret_im_repo_verletzt_gate_exit_1(@TempDir Path dir) throws Exception {
        Path repo = initRepo(dir.resolve("repo"), "String k = \"AKIAIOSFODNN7EXAMPLE\";\n");
        Path reports = dir.resolve("reports");
        command.config = writeConfig(dir, repo, reports.toString());
        command.outputFormats = java.util.List.of("sarif");

        assertEquals(1, command.call());
        Path sarif = reports.resolve("results.sarif");
        assertTrue(Files.exists(sarif));
        assertTrue(Files.readString(sarif).contains("aws-access-key-id"));
        assertTrue(!Files.readString(sarif).contains("AKIAIOSFODNN7EXAMPLE"), "kein Klartext im Report");
    }

    @Test
    void sauberes_repo_passt_gate_exit_0(@TempDir Path dir) throws Exception {
        Path repo = initRepo(dir.resolve("repo"), "// nichts geheimes hier\n");
        command.config = writeConfig(dir, repo, dir.resolve("reports").toString());
        command.outputFormats = java.util.List.of("sarif");

        assertEquals(0, command.call());
    }

    @Test
    void fehlende_config_exit_2(@TempDir Path dir) {
        command.config = dir.resolve("does-not-exist.yaml");
        command.outputFormats = java.util.List.of("sarif");

        assertEquals(2, command.call());
    }
}
