package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.RemediationStatus;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA-Entity eines persistierten (redigierten) Fundes (FR-18, WR-33). */
@Entity
@Table(name = "finding", indexes = {
        @Index(name = "idx_finding_repo", columnList = "repo_id"),
        @Index(name = "idx_finding_status", columnList = "triage_status")
})
public class FindingEntity {

    @Id
    public UUID id;

    @Column(name = "scan_id", nullable = false)
    public UUID scanId;

    @Column(name = "repo_id", nullable = false)
    public String repoId;

    @Column(name = "detector_id", nullable = false)
    public String detectorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public DetectorCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Severity severity;

    @Column(name = "rule_id", nullable = false)
    public String ruleId;

    @Column(name = "file_path", nullable = false)
    public String file;

    @Column(name = "line_number", nullable = false)
    public int line;

    /** Niemals Klartext — nur der redigierte Treffer (FR-18). */
    @Column(name = "redacted_match", nullable = false)
    public String redactedMatch;

    @Column(nullable = false)
    public String fingerprint;

    @Column(nullable = false)
    public boolean verified;

    @Enumerated(EnumType.STRING)
    @Column(name = "triage_status", nullable = false)
    public TriageStatus triageStatus;

    @Column(name = "triage_reason")
    public String triageReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "remediation_status", nullable = false)
    public RemediationStatus remediationStatus;

    @Column(name = "first_seen", nullable = false)
    public Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    public Instant lastSeen;
}
