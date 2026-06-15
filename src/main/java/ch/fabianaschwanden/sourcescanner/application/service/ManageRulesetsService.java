package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.Ruleset;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageRulesetsUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RulesetPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

/** Verwaltung der Rulesets (FR-27). Persistiert + auditiert; wirkt auf künftige Scans (DR-54). */
@ApplicationScoped
public class ManageRulesetsService implements ManageRulesetsUseCase {

    private final RulesetPort rulesets;
    private final AuditPort audit;

    @Inject
    public ManageRulesetsService(RulesetPort rulesets, AuditPort audit) {
        this.rulesets = rulesets;
        this.audit = audit;
    }

    @Override
    public List<Ruleset> list() {
        return rulesets.all();
    }

    @Override
    public Ruleset save(Ruleset ruleset, String actor) {
        Ruleset saved = rulesets.save(ruleset);
        audit.record(AuditEvent.of(actor, "ruleset.save", saved.name(),
                "enforcement=" + saved.enforcement() + "; global=" + saved.global()
                        + "; rules=" + saved.rules().size()));
        return saved;
    }

    @Override
    public void delete(UUID id, String actor) {
        String name = rulesets.byId(id).map(Ruleset::name).orElse(id.toString());
        rulesets.delete(id);
        audit.record(AuditEvent.of(actor, "ruleset.delete", name, null));
    }
}
