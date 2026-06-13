package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.Policy;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManagePoliciesUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.PolicyPort;
import ch.fabianaschwanden.sourcescanner.domain.service.PolicyResolution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

/** Verwaltung + Auflösung der Governance-Policies (FR-20). Audit jeder Änderung (WR-34). */
@ApplicationScoped
public class ManagePoliciesService implements ManagePoliciesUseCase {

    private final PolicyPort policies;
    private final AuditPort audit;

    @Inject
    public ManagePoliciesService(PolicyPort policies, AuditPort audit) {
        this.policies = policies;
        this.audit = audit;
    }

    @Override
    public Policy create(Policy policy, String actor) {
        Policy saved = policies.save(policy);
        audit.record(AuditEvent.of(actor, "policy.create",
                saved.isDefault() ? "default" : saved.orgUnit(), "failOn=" + saved.gate().failOn()));
        return saved;
    }

    @Override
    public Policy update(UUID id, Policy policy, String actor) {
        Policy toSave = new Policy(id, policy.orgUnit(), policy.gate(),
                policy.enabledDetectorGroups(), policy.warnThreshold());
        Policy saved = policies.save(toSave);
        audit.record(AuditEvent.of(actor, "policy.update",
                saved.isDefault() ? "default" : saved.orgUnit(), null));
        return saved;
    }

    @Override
    public void delete(UUID id, String actor) {
        policies.delete(id);
        audit.record(AuditEvent.of(actor, "policy.delete", id.toString(), null));
    }

    @Override
    public List<Policy> all() {
        return policies.all();
    }

    @Override
    public Policy resolveFor(String orgUnit) {
        return PolicyResolution.resolve(policies.all(), orgUnit);
    }
}
