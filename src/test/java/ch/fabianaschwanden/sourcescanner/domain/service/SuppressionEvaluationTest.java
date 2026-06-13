package ch.fabianaschwanden.sourcescanner.domain.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.AggregatedFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.SuppressionRule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuppressionEvaluationTest {

    private AggregatedFinding agg(String file, String detectorId) {
        Finding f = new Finding(detectorId, DetectorCategory.SECRET, Severity.HIGH, "aws", file, 3, "***", "c", false);
        return AggregatedFinding.of(f, Instant.now());
    }

    @Test
    void pfad_glob_unterdrueckt_passende_funde() {
        AggregatedFinding test = agg("test/fixtures/dummy.json", "secret.regex-ruleset");
        AggregatedFinding prod = agg("src/Main.java", "secret.regex-ruleset");
        List<SuppressionRule> rules = List.of(new SuppressionRule("test/**", "secrets", "fixtures"));

        List<AggregatedFinding> result = SuppressionEvaluation.applyPathRules(
                List.of(test, prod), rules, id -> "secrets");

        assertTrue(result.get(0).suppressed());
        assertFalse(result.get(1).suppressed());
    }

    @Test
    void pfad_regel_mit_falschem_detektor_greift_nicht() {
        AggregatedFinding test = agg("test/fixtures/dummy.json", "secret.regex-ruleset");
        List<SuppressionRule> rules = List.of(new SuppressionRule("test/**", "pii", "nur pii"));
        List<AggregatedFinding> result = SuppressionEvaluation.applyPathRules(List.of(test), rules, id -> "secrets");
        assertFalse(result.get(0).suppressed());
    }

    @Test
    void inline_ignore_secret_auf_fundzeile() {
        assertTrue(SuppressionEvaluation.isInlineSuppressed(
                "String k = \"x\"; // scanner:ignore-secret reason=\"test dummy\"", null,
                DetectorCategory.SECRET, false));
    }

    @Test
    void inline_ignore_next_line_auf_vorzeile() {
        assertTrue(SuppressionEvaluation.isInlineSuppressed(
                "String k = \"x\";", "// scanner:ignore-next-line reason=\"r\"",
                DetectorCategory.SECRET, false));
    }

    @Test
    void inline_ignore_line_unabhaengig_von_kategorie() {
        assertTrue(SuppressionEvaluation.isInlineSuppressed(
                "secret // scanner:ignore-line", null, DetectorCategory.PII, false));
    }

    @Test
    void require_reason_blockt_direktive_ohne_begruendung() {
        assertFalse(SuppressionEvaluation.isInlineSuppressed(
                "x // scanner:ignore-secret", null, DetectorCategory.SECRET, true));
        assertTrue(SuppressionEvaluation.isInlineSuppressed(
                "x // scanner:ignore-secret reason=\"ok\"", null, DetectorCategory.SECRET, true));
    }

    @Test
    void ignore_pii_greift_nicht_bei_secret() {
        assertFalse(SuppressionEvaluation.isInlineSuppressed(
                "x // scanner:ignore-pii reason=\"r\"", null, DetectorCategory.SECRET, false));
    }
}
