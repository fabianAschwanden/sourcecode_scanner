package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.EnforcementStatus;
import ch.fabianaschwanden.sourcescanner.domain.model.Ruleset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** REST-Transport eines Rulesets (FR-27, WR-90..96). */
public record RulesetDto(
        UUID id,
        String name,
        String enforcement,
        boolean global,
        List<String> repoNames,
        List<RuleOverrideDto> rules) {

    public RulesetDto {
        repoNames = repoNames == null ? List.of() : repoNames;
        rules = rules == null ? List.of() : rules;
    }

    public static RulesetDto from(Ruleset r) {
        return new RulesetDto(r.id(), r.name(), r.enforcement().name(), r.global(), r.repoNames(),
                r.rules().stream().map(RuleOverrideDto::from).toList());
    }

    public Ruleset toDomain() {
        return new Ruleset(id, name, parseEnforcement(enforcement), global, repoNames,
                rules.stream().map(RuleOverrideDto::toDomain).toList());
    }

    private EnforcementStatus parseEnforcement(String s) {
        try {
            return s == null ? EnforcementStatus.DISABLED
                    : EnforcementStatus.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return EnforcementStatus.DISABLED;
        }
    }
}
