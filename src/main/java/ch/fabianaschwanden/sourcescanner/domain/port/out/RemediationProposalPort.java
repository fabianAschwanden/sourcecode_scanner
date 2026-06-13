package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationProposal;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import java.util.Optional;

/**
 * Fachlicher Zugang zu Fix-Vorschlägen (RMR-13): der Application Service kennt nur diesen Port. Der
 * Detector-Adapter bildet built-in/SPI-{@code RemediableDetector} dahinter ab (analog DetectorPort).
 */
public interface RemediationProposalPort {

    /** Liefert einen Fix-Vorschlag für den Fund, falls ein remediation-fähiger Detektor passt. */
    Optional<RemediationProposal> propose(Finding finding, ScanUnit unit);

    /**
     * Server-Pfad: Vorschlag aus einem persistierten Fund, ohne den ursprünglichen Datei-Inhalt — die
     * konkrete Änderung wird beim Auto-Fix gegen den frisch geklonten Stand angewandt.
     */
    Optional<RemediationProposal> proposeForStored(StoredFinding finding);
}
