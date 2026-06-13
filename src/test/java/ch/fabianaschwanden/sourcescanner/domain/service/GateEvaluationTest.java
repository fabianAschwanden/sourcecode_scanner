package ch.fabianaschwanden.sourcescanner.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.GateConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.GateResult;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateEvaluationTest {

    private Finding withSeverity(Severity severity) {
        return new Finding("d", DetectorCategory.SECRET, severity, "rule", "f.txt", 1, "***", "c", false);
    }

    @Test
    void gate_bricht_ab_dem_schwellenwert() {
        GateConfig gate = new GateConfig(Severity.HIGH, false, false);
        GateResult result = GateEvaluation.evaluate(
                List.of(withSeverity(Severity.MEDIUM), withSeverity(Severity.HIGH)), gate);
        assertFalse(result.passed());
        assertEquals(1, result.blockingCount());
        assertEquals(1, result.exitCode());
    }

    @Test
    void gate_passt_wenn_alles_unter_schwellenwert() {
        GateConfig gate = new GateConfig(Severity.HIGH, false, false);
        GateResult result = GateEvaluation.evaluate(
                List.of(withSeverity(Severity.LOW), withSeverity(Severity.MEDIUM)), gate);
        assertTrue(result.passed());
        assertEquals(0, result.exitCode());
    }

    @Test
    void softfail_liefert_exit_3_trotz_funden() {
        GateConfig gate = new GateConfig(Severity.HIGH, false, true);
        GateResult result = GateEvaluation.evaluate(List.of(withSeverity(Severity.CRITICAL)), gate);
        assertFalse(result.passed());
        assertEquals(3, result.exitCode());
    }
}
