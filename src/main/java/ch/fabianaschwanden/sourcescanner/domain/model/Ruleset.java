package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Benannte, eigenständige Sammlung feingranularer Regel-Einstellungen (FR-27, DR-50..55) — unabhängig
 * von der Gate-/Org-Policy (FR-20). {@code global=true} wirkt auf alle Repos; sonst auf die in
 * {@code repoNames} genannten Quellen. Nur {@link EnforcementStatus#ACTIVE} beeinflusst Scans (DR-54).
 */
public record Ruleset(
        UUID id,
        String name,
        EnforcementStatus enforcement,
        boolean global,
        List<String> repoNames,
        List<RuleOverride> rules) {

    public Ruleset {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ruleset name must not be blank");
        }
        enforcement = enforcement == null ? EnforcementStatus.DISABLED : enforcement;
        repoNames = repoNames == null ? List.of() : List.copyOf(repoNames);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /** {@code true}, wenn dieses Ruleset (aktiv) auf das genannte Repo zutrifft (DR-53/54). */
    public boolean appliesTo(String repoName) {
        return enforcement == EnforcementStatus.ACTIVE && (global || repoNames.contains(repoName));
    }
}
