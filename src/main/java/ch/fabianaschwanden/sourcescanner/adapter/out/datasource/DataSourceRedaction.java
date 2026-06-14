package ch.fabianaschwanden.sourcescanner.adapter.out.datasource;

/**
 * Maskiert Beispielwerte für das Probe-Schema (WR-51, DR-26): zeigt die ersten zwei Zeichen, der Rest
 * wird durch {@code *} ersetzt; kurze Werte vollständig maskiert. Eigene, minimale Variante, um keine
 * Abhängigkeit auf den Detector-Adapter zu erzeugen (Layering, analog Phase 6).
 */
final class DataSourceRedaction {

    private static final int VISIBLE = 2;

    private DataSourceRedaction() {
    }

    static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int len = value.length();
        if (len <= VISIBLE) {
            return "*".repeat(len);
        }
        return value.substring(0, VISIBLE) + "*".repeat(len - VISIBLE);
    }
}
