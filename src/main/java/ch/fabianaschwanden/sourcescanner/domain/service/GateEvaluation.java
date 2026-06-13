package ch.fabianaschwanden.sourcescanner.domain.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AggregatedFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.GateConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.GateResult;
import java.util.List;

/**
 * Pure Domänen-Logik der Quality-Gate-Bewertung (docs/01 §3.4 / docs/08 §3). Zustandslos,
 * framework-frei.
 */
public final class GateEvaluation {

    private GateEvaluation() {
    }

    /** Rohe Funde gegen {@code failOn} bewerten (ohne Baseline/Suppression). */
    public static GateResult evaluate(List<Finding> findings, GateConfig gate) {
        long blocking = findings.stream()
                .filter(f -> f.severity().atLeast(gate.failOn()))
                .count();
        return result(blocking, gate);
    }

    /**
     * Aggregierte Funde bewerten: unterdrückte und (bei {@code failOnNewOnly}) Baseline-Funde
     * brechen das Gate nicht (FR-09/FR-10).
     */
    public static GateResult evaluateAggregated(List<AggregatedFinding> findings, GateConfig gate) {
        long blocking = findings.stream()
                .filter(f -> f.countsAgainstGate(gate.failOn(), gate.failOnNewOnly()))
                .count();
        return result(blocking, gate);
    }

    private static GateResult result(long blocking, GateConfig gate) {
        boolean passed = blocking == 0;
        return new GateResult(passed, (int) blocking, gate.failOn(), gate.softFail());
    }
}
