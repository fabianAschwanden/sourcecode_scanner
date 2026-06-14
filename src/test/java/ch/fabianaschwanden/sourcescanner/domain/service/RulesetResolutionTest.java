package ch.fabianaschwanden.sourcescanner.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.EnforcementStatus;
import ch.fabianaschwanden.sourcescanner.domain.model.RuleMatchMode;
import ch.fabianaschwanden.sourcescanner.domain.model.RuleOverride;
import ch.fabianaschwanden.sourcescanner.domain.model.Ruleset;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.service.RulesetResolution.Resolved;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Merge-Logik der Rulesets (DR-53/54) — reine Domäne. */
class RulesetResolutionTest {

    private Ruleset rs(String name, EnforcementStatus en, boolean global, List<String> repos,
                       RuleOverride... rules) {
        return new Ruleset(UUID.randomUUID(), name, en, global, repos, List.of(rules));
    }

    @Test
    void disabled_ruleset_wirkt_nicht() {
        Ruleset off = rs("off", EnforcementStatus.DISABLED, true, List.of(),
                new RuleOverride("email", false, null, null, null));
        Resolved r = RulesetResolution.resolve(List.of(off), "repo-x");
        assertTrue(r.isEmpty());
        assertTrue(r.isEnabled("email", true), "ohne wirksames Override gilt der Default");
    }

    @Test
    void global_aktives_ruleset_deaktiviert_regel() {
        Ruleset g = rs("g", EnforcementStatus.ACTIVE, true, List.of(),
                new RuleOverride("email", false, null, null, null));
        Resolved r = RulesetResolution.resolve(List.of(g), "any-repo");
        assertFalse(r.isEnabled("email", true));
    }

    @Test
    void repo_spezifisch_ueberschreibt_global() {
        Ruleset global = rs("g", EnforcementStatus.ACTIVE, true, List.of(),
                new RuleOverride("email", false, Severity.LOW, null, null));
        Ruleset repo = rs("r", EnforcementStatus.ACTIVE, false, List.of("repo-x"),
                new RuleOverride("email", true, Severity.HIGH, RuleMatchMode.LIST, "crm"));
        Resolved r = RulesetResolution.resolve(List.of(global, repo), "repo-x");
        assertTrue(r.isEnabled("email", false), "repo-spezifisch gewinnt");
        assertEquals(Severity.HIGH, r.severity("email", Severity.MEDIUM));
        assertEquals(RuleMatchMode.LIST, r.matchMode("email"));
        assertEquals("crm", r.get("email").orElseThrow().dataSourceName());
    }

    @Test
    void nicht_zutreffendes_repo_bleibt_unberuehrt() {
        Ruleset repo = rs("r", EnforcementStatus.ACTIVE, false, List.of("other"),
                new RuleOverride("iban", false, null, null, null));
        Resolved r = RulesetResolution.resolve(List.of(repo), "repo-x");
        assertTrue(r.isEnabled("iban", true));
    }

    @Test
    void severity_override_ohne_wert_nutzt_default() {
        Ruleset g = rs("g", EnforcementStatus.ACTIVE, true, List.of(),
                new RuleOverride("phone", true, null, null, null));
        Resolved r = RulesetResolution.resolve(List.of(g), "repo-x");
        assertEquals(Severity.MEDIUM, r.severity("phone", Severity.MEDIUM));
    }
}
