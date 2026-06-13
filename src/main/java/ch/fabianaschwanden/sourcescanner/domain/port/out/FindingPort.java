package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistenz der (redigierten) Funde + Filter/Triage für die UI (WR-10/11/12). */
public interface FindingPort {

    void saveAll(List<StoredFinding> findings);

    Optional<StoredFinding> byId(UUID id);

    StoredFinding save(StoredFinding finding);

    /** Filterbare Abfrage (alle Filter optional/null). */
    List<StoredFinding> query(FindingQuery query);

    /** Filterkriterien für die Finding-Liste (WR-10). */
    record FindingQuery(String repoId, Severity minSeverity, String detectorId,
                        TriageStatus status, int offset, int limit) {
        public FindingQuery {
            offset = Math.max(0, offset);
            limit = limit <= 0 ? 100 : Math.min(limit, 500);
        }
    }
}
