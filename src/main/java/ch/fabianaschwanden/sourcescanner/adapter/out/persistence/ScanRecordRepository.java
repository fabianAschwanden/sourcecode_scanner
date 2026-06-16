package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.CiMetadata;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Panache-Repository für Scan-Läufe; mappt zwischen Entity und Domänen-{@link ScanRecord} (TR-23). */
@ApplicationScoped
public class ScanRecordRepository implements PanacheRepositoryBase<ScanRecordEntity, UUID>, ScanRecordPort {

    @Override
    @Transactional
    public ScanRecord save(ScanRecord record) {
        ScanRecordEntity entity = findById(record.id());
        if (entity == null) {
            entity = new ScanRecordEntity();
            entity.id = record.id();
        }
        entity.repoId = record.repoId();
        entity.mode = record.mode();
        entity.status = record.status();
        entity.progress = record.progress();
        entity.findingCount = record.findingCount();
        entity.startedAt = record.startedAt();
        entity.finishedAt = record.finishedAt();
        entity.triggerSource = record.trigger();
        CiMetadata ci = record.ci();
        entity.ciRunRef = ci.runRef();
        entity.ciPipelineUrl = ci.pipelineUrl();
        entity.ciCommit = ci.commit();
        entity.ciBranch = ci.branch();
        entity.ciActor = ci.actor();
        entity.errorMessage = record.errorMessage();
        // claimedBy/claimedAt/cancelRequested bleiben unangetastet: sie werden ausschliesslich über
        // die Claim-/Cancel-/Heartbeat-Methoden gepflegt, nicht über den fachlichen Save-Pfad.
        persist(entity);
        return record;
    }

    @Override
    @Transactional
    public Optional<ScanRecord> byId(UUID id) {
        // @Transactional: u. a. vom SSE-Polling (Worker-Thread) und Scan-Worker genutzt.
        return Optional.ofNullable(findById(id)).map(ScanRecordRepository::toDomain);
    }

    @Override
    @Transactional
    public Optional<ScanRecord> byCiRunRef(String runRef) {
        if (runRef == null || runRef.isBlank()) {
            return Optional.empty();
        }
        return find("ciRunRef", runRef).firstResultOptional().map(ScanRecordRepository::toDomain);
    }

    @Override
    @Transactional
    public List<ScanRecord> recent(int limit) {
        return findAll(Sort.by("startedAt").descending())
                .page(Page.ofSize(limit)).list().stream()
                .map(ScanRecordRepository::toDomain)
                .toList();
    }

    // --- Verteilte Ausführung (horizontale Skalierung) -------------------------------------------

    /** Hibernate-Wert für {@code SKIP LOCKED} als Lock-Timeout-Hint (org.hibernate.Timeouts.SKIP_LOCKED). */
    private static final int SKIP_LOCKED = -2;

    @Override
    @Transactional
    public Optional<ScanRecord> claimNextQueued(String podId) {
        // Ältesten wartenden Lauf mit Pessimistic-Write-Lock + SKIP LOCKED holen: andere Pods
        // überspringen die gesperrte Zeile, sodass jeder QUEUED-Lauf genau einmal geclaimt wird.
        List<ScanRecordEntity> rows = getEntityManager()
                .createQuery("from ScanRecordEntity where status = :s order by startedAt asc",
                        ScanRecordEntity.class)
                .setParameter("s", ScanStatus.QUEUED)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", SKIP_LOCKED)
                .setMaxResults(1)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ScanRecordEntity e = rows.getFirst();
        e.status = ScanStatus.RUNNING;
        e.progress = 0;
        e.startedAt = Instant.now();
        e.finishedAt = null;
        e.claimedBy = podId;
        e.claimedAt = Instant.now();
        e.cancelRequested = false;
        persist(e);
        return Optional.of(toDomain(e));
    }

    @Override
    public long countRunningClaimedBy(String podId) {
        return count("status = ?1 and claimedBy = ?2", ScanStatus.RUNNING, podId);
    }

    @Override
    @Transactional
    public boolean requestCancel(UUID id) {
        return update("cancelRequested = true where id = ?1", id) > 0;
    }

    @Override
    @Transactional
    public boolean isCancelRequested(UUID id) {
        // @Transactional: wird aus dem async Scan-Worker-Thread aufgerufen (Abbruch-Check) — ohne
        // lebende Session sonst ContextNotActiveException.
        ScanRecordEntity e = findById(id);
        return e != null && e.cancelRequested;
    }

    @Override
    @Transactional
    public void heartbeat(UUID id) {
        update("claimedAt = ?1 where id = ?2", Instant.now(), id);
    }

    @Override
    @Transactional
    public int requeueStale(Instant staleBefore) {
        // Verwaiste RUNNING-Läufe (Heartbeat zu alt) zurück in die Queue, damit ein anderer Pod
        // sie übernimmt. Claim-Felder zurücksetzen.
        return update("status = ?1, progress = 0, claimedBy = null, claimedAt = null "
                        + "where status = ?2 and (claimedAt is null or claimedAt < ?3)",
                ScanStatus.QUEUED, ScanStatus.RUNNING, staleBefore);
    }

    static ScanRecord toDomain(ScanRecordEntity e) {
        CiMetadata ci = new CiMetadata(e.ciRunRef, e.ciPipelineUrl, e.ciCommit, e.ciBranch, e.ciActor);
        return new ScanRecord(e.id, e.repoId, e.mode, e.status, e.progress, e.findingCount,
                e.startedAt, e.finishedAt, e.triggerSource, ci, e.errorMessage);
    }
}
