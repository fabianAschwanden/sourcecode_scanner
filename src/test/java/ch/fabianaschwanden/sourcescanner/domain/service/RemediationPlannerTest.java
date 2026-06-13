package ch.fabianaschwanden.sourcescanner.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.Confidence;
import ch.fabianaschwanden.sourcescanner.domain.model.FileEdit;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationProposal;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationStrategy;
import ch.fabianaschwanden.sourcescanner.domain.service.RemediationPlanner.FixPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Planung: Edit-Merge + auto vs. proposal nach Confidence (RMR-15) — reine Domäne. */
class RemediationPlannerTest {

    private RemediationProposal proposal(Confidence c, FileEdit... edits) {
        return new RemediationProposal(RemediationStrategy.ANNOTATE, List.of(edits), c, "summary");
    }

    @Test
    void fuehrt_edits_mehrerer_vorschlaege_zusammen() {
        FixPlan plan = RemediationPlanner.plan(List.of(
                proposal(Confidence.HIGH, new FileEdit("A.java", 1, 1, "x")),
                proposal(Confidence.HIGH, new FileEdit("B.java", 2, 2, "y"))), true);
        assertEquals(2, plan.edits().size());
    }

    @Test
    void auto_modus_mit_allen_high_braucht_kein_review() {
        FixPlan plan = RemediationPlanner.plan(List.of(
                proposal(Confidence.HIGH, new FileEdit("A.java", 1, 1, "x"))), true);
        assertFalse(plan.reviewRequired());
    }

    @Test
    void auto_modus_aber_nicht_alle_high_braucht_review() {
        FixPlan plan = RemediationPlanner.plan(List.of(
                proposal(Confidence.HIGH, new FileEdit("A.java", 1, 1, "x")),
                proposal(Confidence.MEDIUM, new FileEdit("B.java", 2, 2, "y"))), true);
        assertTrue(plan.reviewRequired());
    }

    @Test
    void proposal_modus_braucht_immer_review() {
        FixPlan plan = RemediationPlanner.plan(List.of(
                proposal(Confidence.HIGH, new FileEdit("A.java", 1, 1, "x"))), false);
        assertTrue(plan.reviewRequired());
    }

    @Test
    void leerer_plan_ist_empty() {
        assertTrue(RemediationPlanner.plan(List.of(), true).isEmpty());
    }
}
