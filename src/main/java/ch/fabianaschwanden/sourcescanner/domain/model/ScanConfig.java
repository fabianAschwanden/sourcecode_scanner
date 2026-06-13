package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Aufgelöste Scan-Konfiguration (docs/03 §2). Reines Domänen-Modell: die Herkunft (YAML/CLI) und die
 * Auflösungsreihenfolge liegen im Config-Adapter.
 *
 * <p>{@code baseline}/{@code cacheDirectory} sind optional ({@code null} = Funktion aus);
 * {@code suppressions} sind Pfad-Regeln (FR-10), {@code requireSuppressionReason} erzwingt eine
 * Begründung bei Inline-Direktiven (NFR-20).
 */
public record ScanConfig(
        List<RepositoryRef> repositories,
        List<DiscoverySpec> discoveries,
        HistoryMode mode,
        int workers,
        int detectorTimeoutSeconds,
        Map<String, DetectorConfig> detectors,
        GateConfig gate,
        OutputConfig output,
        String baseline,
        List<SuppressionRule> suppressions,
        boolean requireSuppressionReason,
        String cacheDirectory) {

    public ScanConfig {
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
        discoveries = discoveries == null ? List.of() : List.copyOf(discoveries);
        if (repositories.isEmpty() && discoveries.isEmpty()) {
            throw new IllegalArgumentException("scan.repositories must contain at least one entry");
        }
        mode = mode == null ? HistoryMode.FULL : mode;
        if (workers < 1) {
            throw new IllegalArgumentException("scan.concurrency.workers must be >= 1");
        }
        if (detectorTimeoutSeconds < 1) {
            throw new IllegalArgumentException("scan.concurrency.detectorTimeoutSeconds must be >= 1");
        }
        detectors = detectors == null ? Map.of() : Map.copyOf(detectors);
        gate = gate == null ? GateConfig.defaults() : gate;
        output = output == null ? OutputConfig.defaults() : output;
        suppressions = suppressions == null ? List.of() : List.copyOf(suppressions);
    }

    /** Kompakter Konstruktor für den MVP-Pfad/Tests ohne Phase-2-Felder. */
    public ScanConfig(List<RepositoryRef> repositories, HistoryMode mode, int workers,
                      int detectorTimeoutSeconds, Map<String, DetectorConfig> detectors,
                      GateConfig gate, OutputConfig output) {
        this(repositories, List.of(), mode, workers, detectorTimeoutSeconds, detectors, gate, output,
                null, List.of(), false, null);
    }

    /** Konfiguration einer Detektor-Gruppe (z. B. {@code secrets}); fehlt sie, gilt sie als deaktiviert. */
    public DetectorConfig detector(String group) {
        return detectors.getOrDefault(group, DetectorConfig.disabled());
    }
}
