package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.Ruleset;
import java.util.List;
import java.util.UUID;

/** Verwaltung der Rulesets (FR-27, WR-90..96). Pflege nur Admin (WR-95); jede Änderung auditiert. */
public interface ManageRulesetsUseCase {

    List<Ruleset> list();

    Ruleset save(Ruleset ruleset, String actor);

    void delete(UUID id, String actor);
}
