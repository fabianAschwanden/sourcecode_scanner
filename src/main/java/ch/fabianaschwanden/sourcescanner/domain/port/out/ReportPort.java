package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import java.nio.file.Path;
import java.util.List;

/** Schreibt Scan-Ergebnisse in ein Ausgabeformat (docs/01 §3.5). Phase 1: SARIF 2.1.0. */
public interface ReportPort {

    /** Format, das dieser Writer erzeugt. */
    ReportFormat format();

    /**
     * Schreibt die Ergebnisse in das Zielverzeichnis und liefert den Pfad der erzeugten Datei.
     * {@code rules} sind die deklarierten Detektor-Regeln (SARIF {@code tool.driver.rules}).
     */
    Path write(List<ScanResult> results, List<DetectorRule> rules, Path outputDir);
}
