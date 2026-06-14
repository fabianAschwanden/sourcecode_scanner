package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorRule;
import java.util.List;

/** Driving Port — Use-Case: einen Scan über alle konfigurierten Repositories ausführen (FR-01/02). */
public interface StartScanUseCase {

    /** Scannt alle Repositories der Konfiguration und liefert je Repository ein Ergebnis. */
    default List<ScanResult> scan(ScanConfig config) {
        return scan(config, ScanProgressListener.NONE);
    }

    /** Wie {@link #scan(ScanConfig)}, meldet aber den granularen Fortschritt (WR-04b). */
    List<ScanResult> scan(ScanConfig config, ScanProgressListener onProgress);

    /** Von den aktiven Detektoren deklarierte Regeln — für die SARIF-{@code tool.driver.rules}. */
    List<DetectorRule> declaredRules();
}
