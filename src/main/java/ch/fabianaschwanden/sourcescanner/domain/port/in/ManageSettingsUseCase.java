package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.Settings;
import java.util.Map;

/** Driving Port — Verwaltung der systemweiten Einstellungen (WR-15..18). */
public interface ManageSettingsUseCase {

    Settings get();

    Settings update(Settings settings, String actor);

    /**
     * Auflösbarkeits-Status der konfigurierten Secret-Referenzen (WR-17): je Referenz {@code true},
     * wenn sie aktuell auflösbar ist — nie der Klartext-Wert.
     */
    Map<String, Boolean> secretRefStatus();
}
