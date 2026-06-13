package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.Policy;
import java.util.List;
import java.util.UUID;

/** Driving Port — Verwaltung der Governance-Policies (FR-20). */
public interface ManagePoliciesUseCase {

    Policy create(Policy policy, String actor);

    Policy update(UUID id, Policy policy, String actor);

    void delete(UUID id, String actor);

    List<Policy> all();

    /** Löst die für eine Org-Unit gültige Policy auf (spezifischste gewinnt). */
    Policy resolveFor(String orgUnit);
}
