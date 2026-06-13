package ch.fabianaschwanden.sourcescanner.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.fabianaschwanden.sourcescanner.domain.model.AggregatedFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FindingAggregationTest {

    private Finding finding(String file, int line, String redacted, String commit) {
        return new Finding("secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH,
                "aws", file, line, redacted, commit, false);
    }

    @Test
    void dedupliziert_gleiche_funde_ueber_commits_mit_first_last_seen() {
        Finding c1 = finding("A.java", 2, "AKIA****KEY1", "c1");
        Finding c2 = finding("A.java", 2, "AKIA****KEY1", "c2"); // gleicher Fingerprint (commit zählt nicht rein)
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-02-01T00:00:00Z");

        List<AggregatedFinding> aggregated = FindingAggregation.aggregate(
                List.of(c1, c2), f -> f.commitId().equals("c1") ? t1 : t2);

        assertEquals(1, aggregated.size());
        AggregatedFinding a = aggregated.getFirst();
        assertEquals(2, a.occurrences());
        assertEquals(t1, a.firstSeen());
        assertEquals(t2, a.lastSeen());
    }

    @Test
    void unterschiedliche_funde_bleiben_getrennt() {
        Finding a = finding("A.java", 2, "AKIA****KEY1", "c1");
        Finding b = finding("B.java", 9, "AKIA****KEY2", "c1");
        List<AggregatedFinding> aggregated = FindingAggregation.aggregate(List.of(a, b), f -> Instant.now());
        assertEquals(2, aggregated.size());
    }
}
