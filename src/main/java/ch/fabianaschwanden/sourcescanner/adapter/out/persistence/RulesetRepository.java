package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.RuleMatchMode;
import ch.fabianaschwanden.sourcescanner.domain.model.RuleOverride;
import ch.fabianaschwanden.sourcescanner.domain.model.Ruleset;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RulesetPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Panache-Repository für Rulesets (FR-27); Overrides/Repo-Liste als JSON bzw. CSV. */
@ApplicationScoped
public class RulesetRepository
        implements PanacheRepositoryBase<RulesetEntity, UUID>, RulesetPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> RULES_TYPE = new TypeReference<>() {
    };

    @Override
    @Transactional
    public Ruleset save(Ruleset ruleset) {
        UUID id = ruleset.id() == null ? UUID.randomUUID() : ruleset.id();
        RulesetEntity e = findById(id);
        if (e == null) {
            e = new RulesetEntity();
            e.id = id;
        }
        e.name = ruleset.name();
        e.enforcement = ruleset.enforcement();
        e.global = ruleset.global();
        e.repoNames = String.join(",", ruleset.repoNames());
        e.rulesJson = writeRules(ruleset.rules());
        persist(e);
        return toDomain(e);
    }

    @Override
    public Optional<Ruleset> byId(UUID id) {
        return Optional.ofNullable(findById(id)).map(RulesetRepository::toDomain);
    }

    @Override
    public List<Ruleset> all() {
        return listAll().stream().map(RulesetRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        deleteById(id);
    }

    private static String writeRules(List<RuleOverride> rules) {
        try {
            List<Map<String, Object>> raw = rules.stream().map(r -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("ruleId", r.ruleId());
                m.put("enabled", r.enabled());
                m.put("severity", r.severity() == null ? null : r.severity().name());
                m.put("matchMode", r.matchMode().name());
                m.put("dataSourceName", r.dataSourceName());
                return m;
            }).toList();
            return MAPPER.writeValueAsString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize ruleset rules", e);
        }
    }

    static Ruleset toDomain(RulesetEntity e) {
        return new Ruleset(e.id, e.name, e.enforcement, e.global, csv(e.repoNames), readRules(e.rulesJson));
    }

    private static List<RuleOverride> readRules(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(json, RULES_TYPE);
            List<RuleOverride> rules = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                rules.add(new RuleOverride(
                        String.valueOf(m.get("ruleId")),
                        Boolean.TRUE.equals(m.get("enabled")),
                        parseSeverity(m.get("severity")),
                        parseMode(m.get("matchMode")),
                        m.get("dataSourceName") == null ? null : String.valueOf(m.get("dataSourceName"))));
            }
            return rules;
        } catch (Exception e) {
            throw new IllegalStateException("failed to read ruleset rules", e);
        }
    }

    private static Severity parseSeverity(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Severity.valueOf(raw.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static RuleMatchMode parseMode(Object raw) {
        try {
            return raw == null ? RuleMatchMode.ALWAYS
                    : RuleMatchMode.valueOf(raw.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RuleMatchMode.ALWAYS;
        }
    }

    private static List<String> csv(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }
}
