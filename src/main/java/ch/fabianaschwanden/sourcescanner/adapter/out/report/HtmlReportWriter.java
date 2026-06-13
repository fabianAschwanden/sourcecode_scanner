package ch.fabianaschwanden.sourcescanner.adapter.out.report;

import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
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
 * HTML-Report (FR-15, IR-41): eigenständige Seite mit Funden gruppiert nach Repository, sortiert nach
 * Severity. Es werden nur redigierte Treffer ausgegeben (FR-18); alle dynamischen Werte sind escaped.
 */
@ApplicationScoped
public class HtmlReportWriter implements ReportPort {

    @Override
    public ReportFormat format() {
        return ReportFormat.HTML;
    }

    @Override
    public Path write(List<ScanResult> results, List<DetectorRule> rules, Path outputDir) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>sourcecode-scanner report</title><style>")
                .append("body{font-family:system-ui,sans-serif;margin:2rem;color:#1f2937}")
                .append("h1{font-size:1.4rem}h2{margin-top:2rem;font-size:1.1rem}")
                .append("table{border-collapse:collapse;width:100%}")
                .append("th,td{text-align:left;padding:.4rem .6rem;border-bottom:1px solid #e5e7eb;font-size:.9rem}")
                .append(".CRITICAL{color:#991b1b;font-weight:600}.HIGH{color:#b91c1c;font-weight:600}")
                .append(".MEDIUM{color:#b45309}.LOW{color:#374151}.INFO{color:#6b7280}")
                .append("code{background:#f3f4f6;padding:.1rem .3rem;border-radius:3px}")
                .append("</style></head><body>");
        html.append("<h1>sourcecode-scanner report</h1>");

        long total = results.stream().mapToLong(r -> r.findings().size()).sum();
        html.append("<p>").append(total).append(" finding(s) across ").append(results.size())
                .append(" repository/repositories.</p>");

        for (ScanResult result : results) {
            html.append("<h2>").append(escape(result.repoId())).append("</h2>");
            if (result.findings().isEmpty()) {
                html.append("<p>No findings.</p>");
            } else {
                html.append("<table><thead><tr><th>Severity</th><th>Detector</th><th>Rule</th>")
                        .append("<th>File</th><th>Line</th><th>Match (redacted)</th></tr></thead><tbody>");
                result.findings().stream()
                        .sorted((a, b) -> b.severity().compareTo(a.severity()))
                        .forEach(f -> appendRow(html, f));
                html.append("</tbody></table>");
            }
            if (!result.degradations().isEmpty()) {
                html.append("<p><strong>Degradations:</strong> ")
                        .append(result.degradations().size()).append("</p>");
            }
        }
        html.append("</body></html>");

        try {
            Files.createDirectories(outputDir);
            Path target = outputDir.resolve("report.html");
            Files.writeString(target, html.toString(), StandardCharsets.UTF_8);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write HTML report to " + outputDir, e);
        }
    }

    private void appendRow(StringBuilder html, Finding f) {
        Severity sev = f.severity();
        html.append("<tr><td class=\"").append(sev).append("\">").append(sev).append("</td>")
                .append("<td>").append(escape(f.detectorId())).append("</td>")
                .append("<td>").append(escape(f.ruleId())).append("</td>")
                .append("<td>").append(escape(f.file())).append("</td>")
                .append("<td>").append(f.line()).append("</td>")
                .append("<td><code>").append(escape(f.redactedMatch())).append("</code></td></tr>");
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
