package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;

/** Ausgabe-Konfiguration (docs/03 §2). Phase 1 unterstützt nur das Format {@code sarif}. */
public record OutputConfig(List<String> formats, String directory, boolean redact) {

    public OutputConfig {
        formats = formats == null || formats.isEmpty() ? List.of("sarif") : List.copyOf(formats);
        directory = directory == null || directory.isBlank() ? "./scan-reports" : directory;
    }

    public static OutputConfig defaults() {
        return new OutputConfig(List.of("sarif"), "./scan-reports", true);
    }
}
