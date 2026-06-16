package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanStatus;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanTrigger;

/** JPA-Entity eines Scan-Laufs. Lebt ausschliesslich im Persistence-Adapter (Blueprint §4). */
@Entity
@Table(name = "scan_record")
public class ScanRecordEntity {

    @Id
    public UUID id;

    @Column(name = "repo_id", nullable = false)
    public String repoId;

    @Column(nullable = false)
    public String mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ScanStatus status;

    @Column(nullable = false)
    public int progress;

    @Column(name = "finding_count", nullable = false)
    public int findingCount;

    @Column(name = "started_at", nullable = false)
    public Instant startedAt;

    @Column(name = "finished_at")
    public Instant finishedAt;

    /** Herkunft des Laufs (IR-25); Default SERVER für Bestandsdaten. */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false)
    public ScanTrigger triggerSource;

    /** CI-Metadaten (nur bei trigger=CI gesetzt). */
    @Column(name = "ci_run_ref")
    public String ciRunRef;

    @Column(name = "ci_pipeline_url")
    public String ciPipelineUrl;

    @Column(name = "ci_commit")
    public String ciCommit;

    @Column(name = "ci_branch")
    public String ciBranch;

    @Column(name = "ci_actor")
    public String ciActor;

    /** Kurze Fehlerursache bei fehlgeschlagenem Lauf (für die Repo-Karte, WR-82). */
    @Column(name = "error_message", length = 512)
    public String errorMessage;

    /** Pod, der diesen Lauf geclaimt hat (verteilte Ausführung); null solange QUEUED. */
    @Column(name = "claimed_by", length = 64)
    public String claimedBy;

    /** Zeitpunkt des Claims/letzten Heartbeats — Grundlage fürs Reaping verwaister Läufe. */
    @Column(name = "claimed_at")
    public Instant claimedAt;

    /** Pod-übergreifendes Abbruch-Flag; der ausführende Pod prüft es während des Laufs. */
    @Column(name = "cancel_requested", nullable = false)
    public boolean cancelRequested;
}
