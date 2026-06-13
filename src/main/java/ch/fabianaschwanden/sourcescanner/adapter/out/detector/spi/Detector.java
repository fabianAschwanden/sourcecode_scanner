package ch.fabianaschwanden.sourcescanner.adapter.out.detector.spi;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.FileType;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import java.util.List;

/**
 * Externer Plugin-Vertrag (docs/02 §2). Plugin-JARs registrieren Implementierungen über
 * {@code META-INF/services/ch.fabianaschwanden.sourcescanner.adapter.out.detector.spi.Detector}
 * und werden vom {@code SpiDetectorRegistry} via {@link java.util.ServiceLoader} geladen und auf
 * den Domain-{@code DetectorPort} abgebildet.
 *
 * <p>Bewusst im Adapter, nicht in der Domäne: er ist die technische Erweiterungsschnittstelle,
 * nicht der fachliche Port.
 */
public interface Detector {

    String id();

    DetectorCategory category();

    default boolean supports(FileType type) {
        return true;
    }

    List<Finding> scan(ScanUnit unit, DetectorConfig config);
}
