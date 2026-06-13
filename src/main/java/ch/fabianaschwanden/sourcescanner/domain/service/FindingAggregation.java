package ch.fabianaschwanden.sourcescanner.domain.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AggregatedFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Dedupliziert rohe Funde über Commits/Branches zu {@link AggregatedFinding}s (FR-11, DR-41): gleicher
 * {@link Finding#fingerprint()} → ein aggregierter Fund mit erstem/letztem Auftreten und Vorkommenszahl.
 * Pure Domänen-Logik, framework-frei.
 */
public final class FindingAggregation {

    private FindingAggregation() {
    }

    /**
     * @param findings rohe Funde (über alle Commits/Dateien gesammelt)
     * @param seenAt   liefert den Zeitstempel (z. B. Commit-Zeit) eines Fundes für firstSeen/lastSeen
     */
    public static List<AggregatedFinding> aggregate(List<Finding> findings, Function<Finding, Instant> seenAt) {
        Map<String, AggregatedFinding> byFingerprint = new LinkedHashMap<>();
        for (Finding finding : findings) {
            String fp = finding.fingerprint();
            Instant when = seenAt.apply(finding);
            AggregatedFinding existing = byFingerprint.get(fp);
            byFingerprint.put(fp, existing == null
                    ? AggregatedFinding.of(finding, when)
                    : existing.mergeOccurrence(when));
        }
        return new ArrayList<>(byFingerprint.values());
    }
}
