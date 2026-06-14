package ch.fabianaschwanden.sourcescanner.domain.service;

import ch.fabianaschwanden.sourcescanner.domain.model.RuleMatchMode;
import ch.fabianaschwanden.sourcescanner.domain.model.RuleOverride;
import ch.fabianaschwanden.sourcescanner.domain.model.Ruleset;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Führt die für ein Repo zutreffenden Rulesets zu einer effektiven Regel-Konfiguration zusammen
 * (DR-53). Reine Domänen-Logik, framework-frei und ohne Quarkus testbar.
 *
 * <p>Merge-Reihenfolge: zuerst die globalen, dann die repo-spezifischen Rulesets (letztere gewinnen);
 * innerhalb gleicher Stufe gewinnt das später einlaufende. So überschreibt eine repo-spezifische
 * Einstellung eine globale für dieselbe Regel-ID.
 */
public final class RulesetResolution {

    private RulesetResolution() {
    }

    /** Effektive, zusammengeführte Regel-Overrides eines Repos (ruleId → Override). */
    public record Resolved(Map<String, RuleOverride> byRule) {

        public Resolved {
            byRule = byRule == null ? Map.of() : Map.copyOf(byRule);
        }

        /** {@code true}, wenn die Regel laut Ruleset deaktiviert ist; ohne Override gilt {@code defaultOn}. */
        public boolean isEnabled(String ruleId, boolean defaultOn) {
            RuleOverride o = byRule.get(ruleId);
            return o == null ? defaultOn : o.enabled();
        }

        /** Effektive Severity der Regel; ohne Override die übergebene Default-Severity (DR-51). */
        public Severity severity(String ruleId, Severity defaultSeverity) {
            RuleOverride o = byRule.get(ruleId);
            return o != null && o.hasSeverityOverride() ? o.severity() : defaultSeverity;
        }

        /** Abgleichsmodus der Regel (DR-52); ohne Override {@link RuleMatchMode#ALWAYS}. */
        public RuleMatchMode matchMode(String ruleId) {
            RuleOverride o = byRule.get(ruleId);
            return o == null ? RuleMatchMode.ALWAYS : o.matchMode();
        }

        public Optional<RuleOverride> get(String ruleId) {
            return Optional.ofNullable(byRule.get(ruleId));
        }

        public boolean isEmpty() {
            return byRule.isEmpty();
        }
    }

    /** Löst die effektive Regel-Konfiguration für {@code repoName} aus allen Rulesets auf. */
    public static Resolved resolve(List<Ruleset> rulesets, String repoName) {
        Map<String, RuleOverride> merged = new LinkedHashMap<>();
        // 1) globale aktive Rulesets, 2) repo-spezifische aktive Rulesets (gewinnen).
        rulesets.stream()
                .filter(r -> r.appliesTo(repoName) && r.global())
                .forEach(r -> r.rules().forEach(o -> merged.put(o.ruleId(), o)));
        rulesets.stream()
                .filter(r -> r.appliesTo(repoName) && !r.global())
                .forEach(r -> r.rules().forEach(o -> merged.put(o.ruleId(), o)));
        return new Resolved(merged);
    }
}
