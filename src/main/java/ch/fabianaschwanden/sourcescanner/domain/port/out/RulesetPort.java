package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.Ruleset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistenz der Rulesets (FR-27, DR-50..55). Nimmt/liefert Domänen-Modelle (TR-23). */
public interface RulesetPort {

    Ruleset save(Ruleset ruleset);

    Optional<Ruleset> byId(UUID id);

    List<Ruleset> all();

    void delete(UUID id);
}
