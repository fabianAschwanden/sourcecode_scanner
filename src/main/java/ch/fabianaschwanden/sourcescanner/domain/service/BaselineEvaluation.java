package ch.fabianaschwanden.sourcescanner.domain.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AggregatedFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.Baseline;
import ch.fabianaschwanden.sourcescanner.domain.model.BaselineEntry;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Gleicht aggregierte Funde gegen eine {@link Baseline} ab (FR-09): bekannte Fingerprints werden als
 * {@code baseline} markiert und brechen bei {@code failOnNewOnly} das Gate nicht. Generiert zudem eine
 * Baseline aus dem Erstscan. Pure Domänen-Logik.
 */
public final class BaselineEvaluation {

    private BaselineEvaluation() {
    }

    /** Markiert Funde, deren Fingerprint in der Baseline steht, als baseline-bekannt. */
    public static List<AggregatedFinding> applyBaseline(List<AggregatedFinding> findings, Baseline baseline) {
        Set<String> known = baseline.fingerprints();
        return findings.stream()
                .map(f -> known.contains(f.fingerprint()) ? f.asBaseline() : f)
                .toList();
    }

    /** Erzeugt eine Baseline, die alle übergebenen Funde als akzeptiert führt (Erstscan-Generierung). */
    public static Baseline generate(List<AggregatedFinding> findings, String acceptedBy, String reason) {
        List<BaselineEntry> entries = findings.stream()
                .map(f -> new BaselineEntry(f.fingerprint(), acceptedBy, Instant.now().toString(), reason))
                .toList();
        return new Baseline(1, Instant.now(), entries);
    }
}
