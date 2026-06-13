package ch.fabianaschwanden.sourcescanner.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.service.ScrubWorkflow.Decision;
import ch.fabianaschwanden.sourcescanner.domain.service.ScrubWorkflow.Gate;
import ch.fabianaschwanden.sourcescanner.domain.service.ScrubWorkflow.State;
import org.junit.jupiter.api.Test;

/** Prüft die Gate-Reihenfolge der History-Scrub-Governance (RMR-22/25/26/02) — reine Domäne. */
class ScrubWorkflowTest {

    /** Voll-grüner Zustand; einzelne Tests schalten gezielt ein Gate ab. */
    private State green() {
        return new State(true, false, false, true, true, true, true);
    }

    @Test
    void freigegeben_wenn_alle_gates_erfuellt() {
        Decision d = ScrubWorkflow.evaluateExecute(green());
        assertTrue(d.allowed());
    }

    @Test
    void blockiert_ohne_repo_opt_in() {
        Decision d = ScrubWorkflow.evaluateExecute(new State(false, false, false, true, true, true, true));
        assertFalse(d.allowed());
        assertEquals(Gate.OPT_IN, d.blockingGate());
    }

    @Test
    void rotation_gate_blockiert_aktives_secret_ohne_rotation() {
        // verifiziert aktives Secret + keine Rotation bestätigt
        Decision d = ScrubWorkflow.evaluateExecute(new State(true, false, true, false, true, true, true));
        assertFalse(d.allowed());
        assertEquals(Gate.ROTATION, d.blockingGate());
    }

    @Test
    void rotation_gate_durchlaessig_nach_bestaetigter_rotation() {
        Decision d = ScrubWorkflow.evaluateExecute(new State(true, false, true, true, true, true, true));
        assertTrue(d.allowed());
    }

    @Test
    void dry_run_ist_vor_execute_pflicht() {
        Decision d = ScrubWorkflow.evaluateExecute(new State(true, false, false, true, false, true, true));
        assertFalse(d.allowed());
        assertEquals(Gate.DRY_RUN, d.blockingGate());
    }

    @Test
    void force_push_nur_nach_freigabe() {
        Decision d = ScrubWorkflow.evaluateExecute(new State(true, false, false, true, true, false, true));
        assertFalse(d.allowed());
        assertEquals(Gate.FORCE_PUSH, d.blockingGate());
    }

    @Test
    void werkzeug_muss_verfuegbar_sein() {
        Decision d = ScrubWorkflow.evaluateExecute(new State(true, false, false, true, true, true, false));
        assertFalse(d.allowed());
        assertEquals(Gate.TOOL, d.blockingGate());
    }

    @Test
    void rotation_gate_hat_vorrang_vor_dry_run() {
        // Rotation verletzt UND Dry-Run fehlt: das frühere Gate (Rotation) wird gemeldet.
        Decision d = ScrubWorkflow.evaluateExecute(new State(true, false, true, false, false, false, false));
        assertEquals(Gate.ROTATION, d.blockingGate());
    }

    @Test
    void dry_run_braucht_nur_opt_in() {
        assertTrue(ScrubWorkflow.evaluateDryRun(green()).allowed());
        assertFalse(ScrubWorkflow.evaluateDryRun(
                new State(false, false, false, false, false, false, false)).allowed());
    }
}
