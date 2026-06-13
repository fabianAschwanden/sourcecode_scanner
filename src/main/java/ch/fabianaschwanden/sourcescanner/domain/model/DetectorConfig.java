package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.Map;

/** Teilkonfiguration eines Detektors aus dem {@code detectors}-Block der YAML (docs/03 §2). */
public record DetectorConfig(boolean enabled, Map<String, Object> params) {

    public DetectorConfig {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public static DetectorConfig disabled() {
        return new DetectorConfig(false, Map.of());
    }
}
