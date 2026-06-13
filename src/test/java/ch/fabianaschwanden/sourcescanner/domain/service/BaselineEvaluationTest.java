package ch.fabianaschwanden.sourcescanner.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.AggregatedFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.Baseline;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.GateConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaselineEvaluationTest {

    private AggregatedFinding agg(String file) {
        Finding f = new Finding("d", DetectorCategory.SECRET, Severity.HIGH, "aws", file, 1, "***", "c", false);
        return AggregatedFinding.of(f, Instant.now());
    }

    @Test
    void bekannte_fingerprints_werden_als_baseline_markiert() {
        AggregatedFinding old = agg("Old.java");
        AggregatedFinding fresh = agg("New.java");
        Baseline baseline = BaselineEvaluation.generate(List.of(old), "team", "accepted");

        List<AggregatedFinding> result = BaselineEvaluation.applyBaseline(List.of(old, fresh), baseline);

        assertTrue(result.stream().filter(a -> a.fingerprint().equals(old.fingerprint())).findFirst().get().baseline());
        assertFalse(result.stream().filter(a -> a.fingerprint().equals(fresh.fingerprint())).findFirst().get().baseline());
    }

    @Test
    void failOnNewOnly_nur_delta_bricht_gate() {
        AggregatedFinding old = agg("Old.java");
        AggregatedFinding fresh = agg("New.java");
        Baseline baseline = BaselineEvaluation.generate(List.of(old), "team", "accepted");
        List<AggregatedFinding> evaluated = BaselineEvaluation.applyBaseline(List.of(old, fresh), baseline);

        GateConfig newOnly = new GateConfig(Severity.HIGH, true, false);
        assertEquals(1, evaluated.stream().filter(a -> a.countsAgainstGate(Severity.HIGH, true)).count());

        GateConfig all = new GateConfig(Severity.HIGH, false, false);
        assertEquals(2, evaluated.stream().filter(a -> a.countsAgainstGate(Severity.HIGH, false)).count());
        // newOnly/all nur zur Dokumentation der Schwellen verwendet
        assertTrue(newOnly.failOnNewOnly());
        assertFalse(all.failOnNewOnly());
    }
}
