package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.EnforcementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA-Entity eines Rulesets (FR-27). Regel-Overrides und Repo-Liste liegen als JSON-Spalten, um
 * Kind-Tabellen zu sparen (analog Datenquellen-Attribut-Mapping).
 */
@Entity
@Table(name = "ruleset")
public class RulesetEntity {

    @Id
    public UUID id;

    @Column(nullable = false, unique = true)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EnforcementStatus enforcement;

    @Column(nullable = false)
    public boolean global;

    /** Komma-getrennte Repo-Namen (leer = keine; nur relevant wenn global=false). */
    @Column(name = "repo_names", length = 4096)
    public String repoNames;

    /** Regel-Overrides als JSON-Array {@code [{ruleId,enabled,severity,matchMode,dataSourceName}]}. */
    @Column(name = "rules_json", length = 8192)
    public String rulesJson;
}
