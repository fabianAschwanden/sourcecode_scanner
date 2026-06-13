package ch.fabianaschwanden.sourcescanner.domain.model;

import java.time.Instant;

/**
 * Aggregierter Fund (post-Aggregation, docs/01 §4): reichert das rohe {@link Finding} um
 * {@code fingerprint}, erstes/letztes Auftreten und die Zahl der Vorkommen an. {@code suppressed}
 * und {@code baseline} markieren den Triage-Status, der die Gate-Bewertung steuert (FR-09/FR-10).
 */
public record AggregatedFinding(
        Finding finding,
        String fingerprint,
        Instant firstSeen,
        Instant lastSeen,
        int occurrences,
        boolean suppressed,
        boolean baseline) {

    public AggregatedFinding {
        if (finding == null) {
            throw new IllegalArgumentException("finding must not be null");
        }
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        if (occurrences < 1) {
            throw new IllegalArgumentException("occurrences must be >= 1");
        }
    }

    /** Frisch aus einem rohen Finding, noch nicht trotzdem getriagt. */
    public static AggregatedFinding of(Finding finding, Instant seenAt) {
        return new AggregatedFinding(finding, finding.fingerprint(), seenAt, seenAt, 1, false, false);
    }

    public Severity severity() {
        return finding.severity();
    }

    /** Zählt ein weiteres Vorkommen desselben Fundes (Dedup); aktualisiert erstes/letztes Auftreten. */
    public AggregatedFinding mergeOccurrence(Instant seenAt) {
        Instant first = seenAt.isBefore(firstSeen) ? seenAt : firstSeen;
        Instant last = seenAt.isAfter(lastSeen) ? seenAt : lastSeen;
        return new AggregatedFinding(finding, fingerprint, first, last, occurrences + 1, suppressed, baseline);
    }

    public AggregatedFinding asSuppressed() {
        return new AggregatedFinding(finding, fingerprint, firstSeen, lastSeen, occurrences, true, baseline);
    }

    public AggregatedFinding asBaseline() {
        return new AggregatedFinding(finding, fingerprint, firstSeen, lastSeen, occurrences, suppressed, true);
    }

    /** Bricht dieser Fund das Gate? Unterdrückte und (bei failOnNewOnly) Baseline-Funde nicht. */
    public boolean countsAgainstGate(Severity failOn, boolean failOnNewOnly) {
        if (suppressed) {
            return false;
        }
        if (failOnNewOnly && baseline) {
            return false;
        }
        return severity().atLeast(failOn);
    }
}
