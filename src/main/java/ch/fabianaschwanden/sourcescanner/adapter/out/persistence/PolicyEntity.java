package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA-Entity einer Governance-Policy (FR-20). Lebt nur im Persistence-Adapter (Blueprint §4). */
@Entity
@Table(name = "policy")
public class PolicyEntity {

    @Id
    public UUID id;

    /** Organisationseinheit; {@code null} kennzeichnet die Default-Policy. */
    @Column(name = "org_unit", unique = true)
    public String orgUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_on", nullable = false)
    public Severity failOn;

    @Column(name = "fail_on_new_only", nullable = false)
    public boolean failOnNewOnly;

    @Column(name = "soft_fail", nullable = false)
    public boolean softFail;

    @Enumerated(EnumType.STRING)
    @Column(name = "warn_threshold", nullable = false)
    public Severity warnThreshold;

    /** Komma-getrennte Detektor-Gruppen (z. B. {@code secrets,pii}). */
    @Column(name = "enabled_detector_groups")
    public String enabledDetectorGroups;
}
