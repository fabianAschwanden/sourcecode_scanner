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
 * Erzeugt das GitLab-Secret-Detection-Report-Artefakt (IR-18, docs/08 §4.1): erscheint nativ im
 * MR-Widget/Security-Tab. Nur redigierte Treffer (FR-18). Schema-Version 15.x (Secret Detection).
 */
@ApplicationScoped
public class GitLabSecretReportWriter implements ReportPort {

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public ReportFormat format() {
        return ReportFormat.GITLAB;
    }

    @Override
    public Path write(List<ScanResult> results, List<DetectorRule> rules, Path outputDir) {
        ObjectNode root = json.createObjectNode();
        root.put("version", "15.0.0");
        ObjectNode scan = root.putObject("scan");
        scan.put("type", "secret_detection");
        scan.put("status", "success");
        ArrayNode vulns = root.putArray("vulnerabilities");
        for (ScanResult result : results) {
            for (Finding f : result.findings()) {
                ObjectNode v = vulns.addObject();
                v.put("id", f.fingerprint());
                v.put("category", "secret_detection");
                v.put("name", f.ruleId());
                v.put("message", "Potential " + f.category() + " finding");
                v.put("description", "Redacted match: " + f.redactedMatch());
                v.put("severity", gitlabSeverity(f.severity()));
                ObjectNode location = v.putObject("location");
                location.put("file", f.file());
                location.putObject("start_line").put("line", f.line());
                v.putObject("scanner").put("id", "sourcecode-scanner").put("name", "sourcecode-scanner");
            }
        }
        try {
            Files.createDirectories(outputDir);
            Path target = outputDir.resolve("gl-secret-detection-report.json");
            json.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), root);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write GitLab report to " + outputDir, e);
        }
    }

    private String gitlabSeverity(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "Critical";
            case HIGH -> "High";
            case MEDIUM -> "Medium";
            case LOW -> "Low";
            case INFO -> "Info";
        };
    }
}
