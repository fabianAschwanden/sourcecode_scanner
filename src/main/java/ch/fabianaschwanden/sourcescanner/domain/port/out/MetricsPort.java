package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanStatus;
import java.time.Duration;

/**
 * Exponiert Scan-/Fund-Metriken (OR-02..04). Hält die Domäne framework-frei: der Micrometer-/
 * Prometheus-Adapter liegt dahinter (docs/09 §6). Optional — der CLI-Pfad nutzt eine No-op-Variante (OR-09).
 */
public interface MetricsPort {

    /** Ein abgeschlossener Scan-Lauf (Counter {@code scanner_scans_total} + Dauer-Histogram). */
    void recordScan(String repoId, ScanStatus status, Duration duration);

    /** Neue (Delta-)Funde eines Laufs nach Severity (Counter {@code scanner_findings_new_total}). */
    void recordNewFindings(String severity, int count);

    /** Gate-Status je Repository (Gauge {@code scanner_gate_status}: 0=pass, 1=fail). */
    void recordGateStatus(String repoId, boolean passed);

    /**
     * Laufzeit + Fehler/Degradation je Detektor (OR-05): Histogram
     * {@code scanner_detector_duration_seconds} und Counter {@code scanner_detector_errors_total}.
     */
    void recordDetector(String detectorId, Duration duration, boolean error);

    /**
     * Remediation-Aktionen (RMR-50): Counter {@code scanner_remediation_total} nach {@code type}
     * (z. B. {@code auto-fix}, {@code scrub-dry-run}, {@code scrub-execute}) und {@code result}
     * ({@code success}/{@code blocked}/{@code error}).
     */
    void recordRemediation(String type, String result);
}
