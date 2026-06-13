package ch.fabianaschwanden.sourcescanner.adapter.out.report;

import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorRule;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ReportFormat;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ReportPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Schreibt SARIF 2.1.0 (FR-03, IR-40, NFR-17): GitHub/GitLab/IDE-kompatibel. Es werden ausschliesslich
 * redigierte Treffer ausgegeben (FR-18); Klartext verlässt den Prozess nie.
 */
@ApplicationScoped
public class SarifReportWriter implements ReportPort {

    private static final String SARIF_VERSION = "2.1.0";
    private static final String SCHEMA =
            "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json";
    private static final String TOOL_NAME = "sourcecode-scanner";

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public ReportFormat format() {
        return ReportFormat.SARIF;
    }

    @Override
    public Path write(List<ScanResult> results, List<DetectorRule> rules, Path outputDir) {
        ObjectNode root = json.createObjectNode();
        root.put("version", SARIF_VERSION);
        root.put("$schema", SCHEMA);
        ArrayNode runs = root.putArray("runs");
        runs.add(buildRun(results, rules));

        try {
            Files.createDirectories(outputDir);
            Path target = outputDir.resolve("results.sarif");
            json.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), root);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write SARIF report to " + outputDir, e);
        }
    }

    private ObjectNode buildRun(List<ScanResult> results, List<DetectorRule> rules) {
        ObjectNode run = json.createObjectNode();
        ObjectNode tool = run.putObject("tool");
        ObjectNode driver = tool.putObject("driver");
        driver.put("name", TOOL_NAME);
        driver.put("informationUri", "https://github.com/fabianAschwanden/sourcecode_scanner");
        ArrayNode ruleArray = driver.putArray("rules");
        for (DetectorRule rule : rules) {
            ObjectNode r = ruleArray.addObject();
            r.put("id", rule.id());
            r.put("name", rule.name());
            r.putObject("shortDescription").put("text", rule.description());
            r.putObject("defaultConfiguration").put("level", level(rule.defaultSeverity()));
        }

        ArrayNode resultArray = run.putArray("results");
        for (ScanResult result : results) {
            for (Finding finding : result.findings()) {
                resultArray.add(buildResult(finding));
            }
        }
        return run;
    }

    private ObjectNode buildResult(Finding finding) {
        ObjectNode result = json.createObjectNode();
        result.put("ruleId", finding.ruleId());
        result.put("level", level(finding.severity()));
        result.putObject("message")
                .put("text", "Potential " + finding.category() + " finding: " + finding.redactedMatch());

        ArrayNode locations = result.putArray("locations");
        ObjectNode physical = locations.addObject().putObject("physicalLocation");
        physical.putObject("artifactLocation").put("uri", finding.file());
        physical.putObject("region").put("startLine", finding.line());

        result.putObject("partialFingerprints").put("scannerFingerprint/v1", finding.fingerprint());
        return result;
    }

    /** Severity → SARIF-Level (docs: GitHub/GitLab-kompatibel). */
    private String level(Severity severity) {
        if (severity == null) {
            return "warning";
        }
        return switch (severity) {
            case CRITICAL, HIGH -> "error";
            case MEDIUM -> "warning";
            case LOW, INFO -> "note";
        };
    }
}
