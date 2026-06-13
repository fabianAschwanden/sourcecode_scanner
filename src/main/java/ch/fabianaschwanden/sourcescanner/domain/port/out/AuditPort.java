package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import java.util.List;

/** Auditierbare Protokollierung steuernder Aktionen (WR-34). */
public interface AuditPort {

    void record(AuditEvent event);

    List<AuditEvent> recent(int limit);
}
