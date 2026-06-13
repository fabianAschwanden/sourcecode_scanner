package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.Baseline;
import java.nio.file.Path;
import java.util.Optional;

/** Lädt/schreibt die Baseline-Datei akzeptierter Altfunde (docs/03 §5, FR-09). */
public interface BaselinePort {

    /** Lädt die Baseline; {@code empty()}, wenn die Datei (noch) nicht existiert. */
    Optional<Baseline> load(Path baselineFile);

    /** Schreibt die Baseline (Erstgenerierung / Aktualisierung). */
    void write(Baseline baseline, Path baselineFile);
}
