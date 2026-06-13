package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.in.TriageFindingUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.FindingPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

/** Finding-Triage (WR-12): Status setzen, Pflichtbegründung erzwingen, auditieren. */
@ApplicationScoped
public class TriageFindingService implements TriageFindingUseCase {

    private final FindingPort findings;
    private final AuditPort audit;

    @Inject
    public TriageFindingService(FindingPort findings, AuditPort audit) {
        this.findings = findings;
        this.audit = audit;
    }

    @Override
    public List<StoredFinding> findings(FindingPort.FindingQuery query) {
        return findings.query(query);
    }

    @Override
    public StoredFinding byId(UUID findingId) {
        return findings.byId(findingId)
                .orElseThrow(() -> new IllegalArgumentException("unknown finding: " + findingId));
    }

    @Override
    @Transactional
    public StoredFinding triage(UUID findingId, TriageStatus status, String reason, String actor) {
        if (status == null) {
            throw new IllegalArgumentException("triage status must not be null");
        }
        boolean reasonRequired = status == TriageStatus.SUPPRESSED || status == TriageStatus.FALSE_POSITIVE;
        if (reasonRequired && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("a reason is required to " + status + " a finding");
        }
        StoredFinding existing = byId(findingId);
        StoredFinding updated = findings.save(existing.withTriage(status, reason));
        audit.record(AuditEvent.of(actor, "finding.triage", findingId.toString(),
                "status=" + status + (reason == null ? "" : ", reason set")));
        return updated;
    }
}
