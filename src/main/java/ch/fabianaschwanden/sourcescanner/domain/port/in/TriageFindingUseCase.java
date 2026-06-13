package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.out.FindingPort;
import java.util.List;
import java.util.UUID;

/** Driving Port — Finding-Triage (WR-12). */
public interface TriageFindingUseCase {

    List<StoredFinding> findings(FindingPort.FindingQuery query);

    StoredFinding byId(UUID findingId);

    /**
     * Setzt den Triage-Status eines Fundes. Für {@code SUPPRESSED}/{@code FALSE_POSITIVE} ist eine
     * Begründung Pflicht (WR-12); fehlt sie, wird abgelehnt.
     */
    StoredFinding triage(UUID findingId, TriageStatus status, String reason, String actor);
}
