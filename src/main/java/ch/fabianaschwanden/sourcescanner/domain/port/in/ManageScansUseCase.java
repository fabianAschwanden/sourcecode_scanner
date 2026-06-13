package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import java.util.List;
import java.util.UUID;

/** Driving Port — server-getriebene Scan-Steuerung (WR-03). */
public interface ManageScansUseCase {

    /** Startet einen Scan für eine verwaltete Quelle asynchron und liefert den initialen Datensatz. */
    ScanRecord startScan(UUID sourceId, String mode, String actor);

    List<ScanRecord> recentScans(int limit);

    /** Bricht einen laufenden Scan ab (best effort). */
    void cancelScan(UUID scanId, String actor);
}
