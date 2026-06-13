package ch.fabianaschwanden.sourcescanner.adapter.out.detector.spi;

import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationProposal;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import java.util.Optional;

/**
 * Optionale Remediation-Fähigkeit eines Detektors (RMR-13, docs/07 §2.4). Ein Detektor ohne dieses
 * Interface bleibt voll nutzbar (kein Fix-Vorschlag). Vorschläge sind mechanisch sicher und redigiert
 * (RMR-12) — bei Unsicherheit niedrige {@code Confidence} (Vorschlags-PR statt stiller Korrektur).
 */
public interface RemediableDetector extends Detector {

    Optional<RemediationProposal> propose(Finding finding, ScanUnit unit);
}
