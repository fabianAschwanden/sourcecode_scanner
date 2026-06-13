package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistenz der Scan-Läufe (docs/06). Nimmt/liefert Domänen-Modelle, nie JPA-Entities (TR-23). */
public interface ScanRecordPort {

    ScanRecord save(ScanRecord record);

    Optional<ScanRecord> byId(UUID id);

    /** Läufe absteigend nach Startzeit (für die Scan-Liste der UI). */
    List<ScanRecord> recent(int limit);
}
