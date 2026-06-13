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
}
