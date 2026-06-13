package ch.fabianaschwanden.sourcescanner.domain.service;

import ch.fabianaschwanden.sourcescanner.domain.model.Policy;
import java.util.List;

/**
 * Wählt die zutreffende {@link Policy} für eine Organisationseinheit (FR-20). Pure Domänen-Logik:
 * die spezifischste passende Org-Unit gewinnt (längster Präfix-Match auf {@code a/b/c}-Pfade);
 * gibt es keine, greift die Default-Policy, sonst {@link Policy#fallback()}.
 */
public final class PolicyResolution {

    private PolicyResolution() {
    }

    public static Policy resolve(List<Policy> policies, String orgUnit) {
        Policy best = null;
        Policy defaultPolicy = null;
        int bestLength = -1;
        for (Policy p : policies) {
            if (p.isDefault()) {
                defaultPolicy = p;
                continue;
            }
            if (orgUnit != null && matches(orgUnit, p.orgUnit()) && p.orgUnit().length() > bestLength) {
                best = p;
                bestLength = p.orgUnit().length();
            }
        }
        if (best != null) {
            return best;
        }
        return defaultPolicy != null ? defaultPolicy : Policy.fallback();
    }

    /** {@code true}, wenn {@code orgUnit} gleich {@code policyOrg} ist oder darunter liegt ({@code policyOrg/...}). */
    private static boolean matches(String orgUnit, String policyOrg) {
        return orgUnit.equals(policyOrg) || orgUnit.startsWith(policyOrg + "/");
    }
}
