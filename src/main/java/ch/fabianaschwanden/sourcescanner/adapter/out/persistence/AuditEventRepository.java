package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/** Panache-Repository für Audit-Einträge (WR-34). */
@ApplicationScoped
public class AuditEventRepository implements PanacheRepository<AuditEventEntity>, AuditPort {

    @Override
    @Transactional
    public void record(AuditEvent event) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.actor = event.actor();
        entity.action = event.action();
        entity.target = event.target();
        entity.detail = event.detail();
        entity.occurredAt = event.at();
        persist(entity);
    }

    @Override
    public List<AuditEvent> recent(int limit) {
        return findAll(Sort.by("occurredAt").descending())
                .page(Page.ofSize(limit)).list().stream()
                .map(e -> new AuditEvent(e.actor, e.action, e.target, e.detail, e.occurredAt))
                .toList();
    }
}
