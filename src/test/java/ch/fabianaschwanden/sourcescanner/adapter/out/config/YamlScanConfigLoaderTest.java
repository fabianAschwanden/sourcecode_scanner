package ch.fabianaschwanden.sourcescanner.adapter.out.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.HistoryMode;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanConfigPort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlScanConfigLoaderTest {

    private final YamlScanConfigLoader loader = new YamlScanConfigLoader();

    private Path write(Path dir, String yaml) throws IOException {
        Path file = dir.resolve("scanner.yaml");
        Files.writeString(file, yaml);
        return file;
    }

    @Test
    void laedt_vollstaendige_konfig(@TempDir Path dir) throws IOException {
        Path cfg = write(dir, """
                scan:
                  repositories:
                    - type: localGit
                      id: self
                      path: .
                  history:
                    mode: full
                  concurrency:
                    workers: 4
                    detectorTimeoutSeconds: 15
                  detectors:
                    secrets:
                      enabled: true
                      ruleset: gitleaks-default
                  gate:
                    failOn: HIGH
                    softFail: false
                  output:
                    formats: [sarif]
                    directory: ./out
                    redact: true
                """);
        ScanConfig config = loader.load(cfg);

        assertEquals(1, config.repositories().size());
        assertEquals(HistoryMode.FULL, config.mode());
        assertEquals(4, config.workers());
        assertEquals(15, config.detectorTimeoutSeconds());
        assertTrue(config.detector("secrets").enabled());
        assertEquals(Severity.HIGH, config.gate().failOn());
        assertEquals("./out", config.output().directory());
    }

    @Test
    void fehlende_repositories_brechen_mit_feldbezug(@TempDir Path dir) throws IOException {
        Path cfg = write(dir, "scan:\n  history:\n    mode: full\n");
        ScanConfigPort.ConfigException ex = assertThrows(ScanConfigPort.ConfigException.class,
                () -> loader.load(cfg));
        assertTrue(ex.getMessage().contains("repositories"));
    }

    @Test
    void unbekannte_severity_bricht(@TempDir Path dir) throws IOException {
        Path cfg = write(dir, """
                scan:
                  repositories:
                    - { type: localGit, path: . }
                  gate:
                    failOn: SUPERBAD
                """);
        ScanConfigPort.ConfigException ex = assertThrows(ScanConfigPort.ConfigException.class,
                () -> loader.load(cfg));
        assertTrue(ex.getMessage().contains("failOn"));
    }

    @Test
    void fehlende_datei_bricht() {
        assertThrows(ScanConfigPort.ConfigException.class, () -> loader.load(Path.of("/nope/scanner.yaml")));
    }
}
