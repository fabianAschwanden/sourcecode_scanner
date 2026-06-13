package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;

/**
 * Vorschlag eines Detektors, wie ein Fund behoben werden kann (RMR-13, docs/07 §2.4). {@code humanSummary}
 * ist für die PR-Beschreibung gedacht und enthält ausschliesslich redigierte Angaben (RMR-12/FR-18).
 */
public record RemediationProposal(
        RemediationStrategy strategy,
        List<FileEdit> edits,
        Confidence confidence,
        String humanSummary) {

    public RemediationProposal {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }
        edits = edits == null ? List.of() : List.copyOf(edits);
        confidence = confidence == null ? Confidence.LOW : confidence;
        humanSummary = humanSummary == null ? "" : humanSummary;
    }
}
