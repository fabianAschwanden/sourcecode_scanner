package ch.fabianaschwanden.sourcescanner.adapter.in.cli;

import ch.fabianaschwanden.sourcescanner.domain.model.AggregatedFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.Baseline;
import ch.fabianaschwanden.sourcescanner.domain.model.GateConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.GateResult;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import ch.fabianaschwanden.sourcescanner.domain.port.in.StartScanUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.BaselinePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ReportFormat;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ReportPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanConfigPort;
import ch.fabianaschwanden.sourcescanner.domain.service.BaselineEvaluation;
import ch.fabianaschwanden.sourcescanner.domain.service.GateEvaluation;
import io.quarkus.arc.All;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CLI-Einstieg (IR-10): treibt Scan → Report → Gate und liefert den Exit-Code-Vertrag
 * (docs/08 §7, IR-14): {@code 0} pass · {@code 1} Gate verletzt · {@code 2} Konfig-/Laufzeitfehler ·
 * {@code 3} softFail mit Funden. Das Gate bewertet aggregierte, baseline-/suppression-getriagte Funde.
 */
@TopCommand
@Command(name = "scan", mixinStandardHelpOptions = true,
        description = "Scannt konfigurierte Repositories auf Secrets/PII und schreibt Reports.")
public class ScanCommand implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, required = true, description = "Pfad zur scanner.yaml")
    Path config;

    @Option(names = {"-o", "--output"}, description = "Ausgabeformate (sarif, html)", split = ",")
    List<String> outputFormats = new ArrayList<>();

    @Option(names = {"-b", "--baseline"}, description = "Pfad zur Baseline-Datei (überschreibt scan.baseline)")
    Path baseline;

    @Option(names = "--write-baseline",
            description = "Erzeugt/aktualisiert die Baseline aus diesem Lauf und beendet ohne Gate-Fail.")
    boolean writeBaseline;

    private final StartScanUseCase startScan;
    private final ScanConfigPort configPort;
    private final BaselinePort baselinePort;
    private final List<ReportPort> reportPorts;
    private final ReportBackClient reportBack = new ReportBackClient();

    @Inject
    public ScanCommand(StartScanUseCase startScan, ScanConfigPort configPort,
                       BaselinePort baselinePort, @All List<ReportPort> reportPorts) {
        this.startScan = startScan;
        this.configPort = configPort;
        this.baselinePort = baselinePort;
        this.reportPorts = reportPorts;
    }

    @Override
    public Integer call() {
        ScanConfig scanConfig;
        try {
            scanConfig = configPort.load(config);
        } catch (ScanConfigPort.ConfigException e) {
            System.err.println("Konfigurationsfehler: " + e.getMessage());
            return 2;
        }

        List<ScanResult> results;
        try {
            results = startScan.scan(scanConfig);
        } catch (RuntimeException e) {
            System.err.println("Scan fehlgeschlagen: " + e.getMessage());
            return 2;
        }

        if (writeBaseline) {
            return writeBaselineAndExit(results, scanConfig);
        }

        try {
            writeReports(results, scanConfig);
        } catch (RuntimeException e) {
            System.err.println("Report-Erzeugung fehlgeschlagen: " + e.getMessage());
            return 2;
        }

        // Optionaler Push an den zentralen Server (IR-21); gate-entkoppelt — Fehler ändern den
        // Exit-Code nie (IR-26). Aktiv nur, wenn die nötigen Env-Variablen gesetzt sind.
        if (reportBack.enabled()) {
            reportBack.send(results, scanConfig.mode().name().toLowerCase(Locale.ROOT));
        }

        return evaluateGate(results, scanConfig.gate());
    }

    private int writeBaselineAndExit(List<ScanResult> results, ScanConfig scanConfig) {
        Path target = baselineFile(scanConfig);
        if (target == null) {
            System.err.println("Keine Baseline-Datei angegeben (--baseline oder scan.baseline).");
            return 2;
        }
        List<AggregatedFinding> all = results.stream().flatMap(r -> r.aggregated().stream()).toList();
        Baseline generated = BaselineEvaluation.generate(all, "scanner-cli", "Erstscan-Baseline");
        baselinePort.write(generated, target);
        System.out.printf(Locale.ROOT, "Baseline geschrieben: %s (%d Eintrag/Einträge)%n",
                target, generated.entries().size());
        return 0;
    }

    private Path baselineFile(ScanConfig scanConfig) {
        if (baseline != null) {
            return baseline;
        }
        return scanConfig.baseline() == null ? null : Path.of(scanConfig.baseline());
    }

    private void writeReports(List<ScanResult> results, ScanConfig scanConfig) {
        Path outputDir = Path.of(scanConfig.output().directory());
        List<String> formats = outputFormats == null || outputFormats.isEmpty()
                ? scanConfig.output().formats()
                : outputFormats;
        for (String format : formats) {
            ReportFormat target = parseFormat(format);
            ReportPort writer = reportPorts.stream()
                    .filter(p -> p.format() == target)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("output format not supported: " + format));
            Path written = writer.write(results, startScan.declaredRules(), outputDir);
            System.out.println("Report geschrieben: " + written);
        }
    }

    private ReportFormat parseFormat(String format) {
        try {
            return ReportFormat.valueOf(format.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unbekanntes Ausgabeformat: " + format, e);
        }
    }

    private int evaluateGate(List<ScanResult> results, GateConfig gate) {
        List<AggregatedFinding> all = results.stream()
                .flatMap(r -> r.aggregated().stream())
                .toList();
        GateResult gateResult = GateEvaluation.evaluateAggregated(all, gate);
        System.out.printf(Locale.ROOT,
                "Gate: %s — %d blockierende(r) Fund(e) >= %s (von %d aggregiert)%n",
                gateResult.passed() ? "PASS" : "FAIL",
                gateResult.blockingCount(), gate.failOn(), all.size());
        return gateResult.exitCode();
    }
}
