package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.Confidence;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.FileEdit;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationProposal;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationStrategy;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RemediationProposalPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/**
 * Built-in-Remediation für Secret-Funde (RMR-13/14). Konservativ und mechanisch sicher: schlägt eine
 * Suppression-Annotation (ANNOTATE) an der Fundzeile vor — eine Änderung ohne fachliches Risiko, die
 * den Fund sichtbar als zu behebend markiert. Stärkere Eingriffe (EXTERNALIZE/REMOVE_LINE) bleiben
 * dem Menschen überlassen (niedrige Confidence ⇒ Vorschlags-PR, RMR-15). Trägt nie Klartext (RMR-12).
 *
 * <p>Bridge zum fachlichen {@link RemediationProposalPort}; intern als {@link RemediableDetector}
 * formuliert, sodass dieselbe Logik auch als Plugin-Vertrag dienen kann (docs/07 §2.4).
 */
@ApplicationScoped
public class SecretRemediator implements RemediationProposalPort {

    private static final String IGNORE_DIRECTIVE =
            "// scanner:ignore-secret reason=\"rotate then remove (auto-flagged)\"";

    /**
     * Marker, den {@code FixBranchPusher} erkennt: der Rest wird an die bestehende Datei-Zeile
     * <b>angehängt</b> (statt sie zu ersetzen) — so funktioniert ANNOTATE ohne den Original-Inhalt.
     */
    public static final String APPEND_MARKER = "<<APPEND>>";

    @Override
    public Optional<RemediationProposal> propose(Finding finding, ScanUnit unit) {
        if (finding.category() != DetectorCategory.SECRET) {
            return Optional.empty();
        }
        String line = lineAt(unit, finding.line());
        if (line == null) {
            return Optional.empty();
        }
        String annotated = line.contains("scanner:ignore-") ? line : line + " " + IGNORE_DIRECTIVE;
        FileEdit edit = new FileEdit(finding.file(), finding.line(), finding.line(), annotated);
        return Optional.of(proposal(edit, finding.ruleId(), finding.file(), finding.line(),
                finding.redactedMatch()));
    }

    @Override
    public Optional<RemediationProposal> proposeForStored(StoredFinding finding) {
        if (finding.category() != DetectorCategory.SECRET) {
            return Optional.empty();
        }
        // Ohne Original-Inhalt: Direktive an die Zeile anhängen (APPEND_MARKER, vom Pusher angewandt).
        FileEdit edit = new FileEdit(finding.file(), finding.line(), finding.line(),
                APPEND_MARKER + " " + IGNORE_DIRECTIVE);
        return Optional.of(proposal(edit, finding.ruleId(), finding.file(), finding.line(),
                finding.redactedMatch()));
    }

    private RemediationProposal proposal(FileEdit edit, String ruleId, String file, int line, String redacted) {
        String summary = "Markiere " + ruleId + " in " + file + ":" + line
                + " (redigiert: " + redacted + "). Bitte zuerst rotieren, dann entfernen.";
        return new RemediationProposal(RemediationStrategy.ANNOTATE, List.of(edit), Confidence.MEDIUM, summary);
    }

    private String lineAt(ScanUnit unit, int line) {
        String[] lines = unit.content().split("\n", -1);
        return line >= 1 && line <= lines.length ? lines[line - 1] : null;
    }
}
