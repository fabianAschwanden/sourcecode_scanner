package ch.fabianaschwanden.sourcescanner.adapter.out.report;

import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorRule;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ReportFormat;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ReportPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Schreibt TeamCity Service Messages (IR-19, docs/08 §4.2): pro Fund eine Inspektion, bei
 * blockierenden Funden ein Build Problem. Die Datei wird zusätzlich auf {@code System.out} gespiegelt,
 * sodass TeamCity sie im Build-Log live parst. Nur redigierte Treffer (FR-18).
 */
@ApplicationScoped
public class TeamCityServiceMessageWriter implements ReportPort {

    @Override
    public ReportFormat format() {
        return ReportFormat.TEAMCITY;
    }

    @Override
    public Path write(List<ScanResult> results, List<DetectorRule> rules, Path outputDir) {
        StringBuilder messages = new StringBuilder();
        int total = 0;
        for (ScanResult result : results) {
            for (Finding f : result.findings()) {
                total++;
                messages.append(inspectionType(f.ruleId()))
                        .append(inspection(f))
                        .append('\n');
            }
        }
        if (total > 0) {
            messages.append("##teamcity[buildProblem description='")
                    .append(escape("Secret scan: " + total + " finding(s)"))
                    .append("' identity='secretScan']\n");
        }
        // Live ins Build-Log (TeamCity parst stdout); zusätzlich als Artefakt ablegen.
        System.out.print(messages);
        try {
            Files.createDirectories(outputDir);
            Path target = outputDir.resolve("teamcity-service-messages.txt");
            Files.writeString(target, messages.toString(), StandardCharsets.UTF_8);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write TeamCity report to " + outputDir, e);
        }
    }

    private String inspectionType(String ruleId) {
        return "##teamcity[inspectionType id='" + escape(ruleId)
                + "' name='" + escape(ruleId) + "' category='secret-scan' description='Secret scan rule']\n";
    }

    private String inspection(Finding f) {
        return "##teamcity[inspection typeId='" + escape(f.ruleId())
                + "' message='" + escape("Potential " + f.category() + ": " + f.redactedMatch())
                + "' file='" + escape(f.file()) + "' line='" + f.line()
                + "' SEVERITY='" + severity(f) + "']";
    }

    private String severity(Finding f) {
        return switch (f.severity()) {
            case CRITICAL, HIGH -> "ERROR";
            case MEDIUM -> "WARNING";
            default -> "INFO";
        };
    }

    /** TeamCity-Service-Message-Escaping (siehe TeamCity-Doku). */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "||").replace("'", "|'").replace("\n", "|n")
                .replace("\r", "|r").replace("[", "|[").replace("]", "|]");
    }
}
