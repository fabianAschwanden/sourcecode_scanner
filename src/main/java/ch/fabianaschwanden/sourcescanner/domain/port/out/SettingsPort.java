package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.Settings;

/** Persistenz der systemweiten Einstellungen (Singleton, WR-15). Liefert Domänen-Modelle (TR-23). */
public interface SettingsPort {

    /** Gespeicherte Einstellungen oder {@link Settings#defaults()}, falls noch keine existieren. */
    Settings get();

    Settings save(Settings settings);
}
