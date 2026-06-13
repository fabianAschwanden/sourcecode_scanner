package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.Policy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistenz der zentralen Governance-Policies (FR-20). Nimmt/liefert Domänen-Modelle (TR-23). */
public interface PolicyPort {

    Policy save(Policy policy);

    Optional<Policy> byId(UUID id);

    List<Policy> all();

    void delete(UUID id);
}
