package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanConfig;
import java.nio.file.Path;

/** Lädt und löst die Scan-Konfiguration auf (FR-04, docs/03). */
public interface ScanConfigPort {

    /**
     * Lädt die Konfiguration aus der angegebenen Datei. Validierungsfehler werden als
     * {@link ConfigException} mit präzisem Feldbezug gemeldet (NFR-19).
     */
    ScanConfig load(Path configFile);

    /** Fehler beim Laden/Validieren der Konfiguration (führt zu Exit-Code 2). */
    class ConfigException extends RuntimeException {
        public ConfigException(String message) {
            super(message);
        }

        public ConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
