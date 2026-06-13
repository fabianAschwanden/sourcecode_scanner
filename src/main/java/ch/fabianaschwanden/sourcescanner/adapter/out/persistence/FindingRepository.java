package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.port.out.FindingPort;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Panache-Repository für persistierte Funde inkl. filterbarer Abfrage (WR-10) und Triage. */
@ApplicationScoped
public class FindingRepository implements PanacheRepositoryBase<FindingEntity, UUID>, FindingPort {

    @Override
    @Transactional
    public void saveAll(List<StoredFinding> findings) {
        for (StoredFinding f : findings) {
            persist(toEntity(f));
        }
    }

    @Override
    @Transactional
    public StoredFinding save(StoredFinding finding) {
        FindingEntity entity = findById(finding.id());
        if (entity == null) {
            entity = new FindingEntity();
        }
        copy(finding, entity);
        persist(entity);
        return finding;
    }

    @Override
    public Optional<StoredFinding> byId(UUID id) {
        return Optional.ofNullable(findById(id)).map(FindingRepository::toDomain);
    }

    @Override
    public List<StoredFinding> query(FindingQuery q) {
        StringBuilder ql = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        if (q.repoId() != null) {
            params.add(q.repoId());
            ql.append(" and repoId = ?").append(params.size());
        }
        if (q.detectorId() != null) {
            params.add(q.detectorId());
            ql.append(" and detectorId = ?").append(params.size());
        }
        if (q.status() != null) {
            params.add(q.status());
            ql.append(" and triageStatus = ?").append(params.size());
        }
        List<FindingEntity> rows = find(ql.toString(), Sort.by("severity").descending(), params.toArray())
                .range(q.offset(), q.offset() + q.limit() - 1).list();
        // Severity-Mindestschwelle in der Domäne filtern (Enum-Ordinalvergleich, kein DB-Detail).
        return rows.stream()
                .filter(e -> q.minSeverity() == null || e.severity.atLeast(q.minSeverity()))
                .map(FindingRepository::toDomain)
                .toList();
    }

    private void copy(StoredFinding f, FindingEntity e) {
        e.id = f.id();
        e.scanId = f.scanId();
        e.repoId = f.repoId();
        e.detectorId = f.detectorId();
        e.category = f.category();
        e.severity = f.severity();
        e.ruleId = f.ruleId();
        e.file = f.file();
        e.line = f.line();
        e.redactedMatch = f.redactedMatch();
        e.fingerprint = f.fingerprint();
        e.verified = f.verified();
        e.triageStatus = f.triageStatus();
        e.triageReason = f.triageReason();
        e.firstSeen = f.firstSeen();
        e.lastSeen = f.lastSeen();
    }

    private FindingEntity toEntity(StoredFinding f) {
        FindingEntity e = new FindingEntity();
        copy(f, e);
        return e;
    }

    static StoredFinding toDomain(FindingEntity e) {
        return new StoredFinding(e.id, e.scanId, e.repoId, e.detectorId, e.category, e.severity,
                e.ruleId, e.file, e.line, e.redactedMatch, e.fingerprint, e.verified,
                e.triageStatus, e.triageReason, e.firstSeen, e.lastSeen);
    }
}
