package ch.fabianaschwanden.sourcescanner.domain.service;

import ch.fabianaschwanden.sourcescanner.domain.model.Confidence;
import ch.fabianaschwanden.sourcescanner.domain.model.FileEdit;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationProposal;
import java.util.ArrayList;
import java.util.List;

/**
 * Bündelt Fix-Vorschläge mehrerer Funde zu einem Fix-Plan (docs/07 §2.1). Pure Domänen-Logik:
 * fasst Edits zusammen und entscheidet anhand der niedrigsten Confidence, ob der PR im Auto-Modus
 * (alle Vorschläge HIGH) oder als Vorschlags-PR mit Review erzeugt wird (RMR-15).
 */
public final class RemediationPlanner {

    private RemediationPlanner() {
    }

    /** Ergebnis der Planung: zusammengeführte Edits + Review-Bedarf + redigierte Zusammenfassung. */
    public record FixPlan(List<FileEdit> edits, boolean reviewRequired, String summary) {
        public FixPlan {
            edits = edits == null ? List.of() : List.copyOf(edits);
        }

        public boolean isEmpty() {
            return edits.isEmpty();
        }
    }

    /**
     * @param autoMode {@code true}, wenn der konfigurierte Modus {@code auto} ist; ein PR wird dennoch
     *                 nur ohne Review-Pflicht erstellt, wenn zusätzlich alle Vorschläge HIGH-Confidence sind.
     */
    public static FixPlan plan(List<RemediationProposal> proposals, boolean autoMode) {
        List<FileEdit> edits = new ArrayList<>();
        boolean allHigh = !proposals.isEmpty();
        StringBuilder summary = new StringBuilder();
        for (RemediationProposal p : proposals) {
            edits.addAll(p.edits());
            allHigh = allHigh && p.confidence() == Confidence.HIGH;
            if (!p.humanSummary().isBlank()) {
                summary.append("- ").append(p.strategy()).append(": ").append(p.humanSummary()).append('\n');
            }
        }
        boolean reviewRequired = !(autoMode && allHigh);
        return new FixPlan(edits, reviewRequired, summary.toString());
    }
}
