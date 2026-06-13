package ch.fabianaschwanden.sourcescanner.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ergebnis eines Scan-Laufs. {@code findings} sind die für das Reporting bestimmten (nicht
 * unterdrückten) rohen Funde; {@code aggregated} trägt die deduplizierten, baseline-/suppression-
 * getriagten Funde für die Gate-Bewertung (FR-09/FR-10/FR-11). {@code degradations} hält
 * Detektor-Ausfälle/Timeouts fest, die den Lauf nicht abgebrochen haben (NFR-07).
 */
public record ScanResult(
        String repoId,
        Instant startedAt,
        Instant finishedAt,
        List<Finding> findings,
        List<AggregatedFinding> aggregated,
        List<String> degradations) {

    public ScanResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
        aggregated = aggregated == null ? List.of() : List.copyOf(aggregated);
        degradations = degradations == null ? List.of() : List.copyOf(degradations);
    }

    /** Phase-1-kompatibler Konstruktor (ohne Aggregation): leitet die aggregierte Liste leer ab. */
    public ScanResult(String repoId, Instant startedAt, Instant finishedAt,
                      List<Finding> findings, List<String> degradations) {
        this(repoId, startedAt, finishedAt, findings, List.of(), degradations);
    }

    /** Anzahl Funde je Severity — für Stats/Reporting. */
    public Map<Severity, Long> countsBySeverity() {
        return findings.stream().collect(Collectors.groupingBy(Finding::severity, Collectors.counting()));
    }
}
