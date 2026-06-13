package ch.fabianaschwanden.sourcescanner.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.fabianaschwanden.sourcescanner.domain.model.GateConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Policy;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PolicyResolutionTest {

    private Policy policy(String orgUnit, Severity failOn) {
        return new Policy(UUID.randomUUID(), orgUnit, new GateConfig(failOn, false, false),
                Set.of("secrets"), Severity.MEDIUM);
    }

    @Test
    void spezifischste_org_unit_gewinnt() {
        Policy broad = policy("team-a", Severity.MEDIUM);
        Policy specific = policy("team-a/payments", Severity.CRITICAL);
        Policy resolved = PolicyResolution.resolve(List.of(broad, specific), "team-a/payments");
        assertEquals(Severity.CRITICAL, resolved.gate().failOn());
    }

    @Test
    void praefix_match_greift_fuer_unter_org_unit() {
        Policy broad = policy("team-a", Severity.LOW);
        Policy resolved = PolicyResolution.resolve(List.of(broad), "team-a/web");
        assertEquals(Severity.LOW, resolved.gate().failOn());
    }

    @Test
    void default_policy_wenn_keine_passt() {
        Policy def = policy(null, Severity.HIGH);
        Policy other = policy("team-b", Severity.CRITICAL);
        Policy resolved = PolicyResolution.resolve(List.of(def, other), "team-x");
        assertEquals(Severity.HIGH, resolved.gate().failOn());
    }

    @Test
    void fallback_wenn_gar_keine_policy() {
        Policy resolved = PolicyResolution.resolve(List.of(), "team-x");
        assertEquals(Policy.fallback().gate().failOn(), resolved.gate().failOn());
    }
}
