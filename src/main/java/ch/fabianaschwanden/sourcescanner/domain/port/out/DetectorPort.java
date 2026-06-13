package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.FileType;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.model.VerificationResult;
import java.util.List;

/**
 * Fachliche Detektor-Schnittstelle, die der Application Service kennt (FR-06, docs/09 §4).
 * Built-in-Detektoren erfüllen sie direkt als CDI-Bean; extern nachgeladene Plugin-JARs werden
 * vom Detector-Adapter über den SPI-{@code Detector}-Vertrag auf diesen Port abgebildet.
 *
 * <p>Detektoren sind framework-frei und ohne Quarkus testbar (TR-24).
 */
public interface DetectorPort {

    /** Stabile, eindeutige ID (z. B. {@code "secret.regex-ruleset"}). */
    String id();

    DetectorCategory category();

    /** Optionaler Vorfilter zur Performance-Optimierung (DR-04). */
    default boolean supports(FileType type) {
        return true;
    }

    /** Prüft eine Einheit und liefert rohe Funde (mit bereits redigiertem Treffer, FR-18). */
    List<Finding> scan(ScanUnit unit, DetectorConfig config);

    /**
     * Optionale aktive Verifikation eines Fundes (DR-05). Default: keine Prüfung. Der Orchestrator
     * ruft dies nur bei {@code detectors.<group>.verify = true} auf; ein als aktiv bestätigtes Secret
     * wird auf CRITICAL hochgestuft (DR-14).
     */
    default VerificationResult verify(Finding finding) {
        return VerificationResult.unverified();
    }

    /** Regel-Metadaten für das Reporting (SARIF {@code tool.driver.rules}); leer = keine Deklaration. */
    default List<DetectorRule> rules() {
        return List.of();
    }
}
