package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
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
        persist(entity);
        return record;
    }

    @Override
    public Optional<ScanRecord> byId(UUID id) {
        return Optional.ofNullable(findById(id)).map(ScanRecordRepository::toDomain);
    }

    @Override
    public List<ScanRecord> recent(int limit) {
        return findAll(Sort.by("startedAt").descending())
                .page(Page.ofSize(limit)).list().stream()
                .map(ScanRecordRepository::toDomain)
                .toList();
    }

    static ScanRecord toDomain(ScanRecordEntity e) {
        return new ScanRecord(e.id, e.repoId, e.mode, e.status, e.progress, e.findingCount,
                e.startedAt, e.finishedAt);
    }
}
