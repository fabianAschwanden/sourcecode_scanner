package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.port.out.FindingPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Transaktionale Persistenz eines Scan-Ergebnisses, getrennt vom (langlaufenden, async) Scan-Lauf:
 * jeder Schreibvorgang läuft in einer eigenen Transaktion ({@code REQUIRES_NEW}), damit ein Scan die
 * Web-/Aufrufer-Transaktion nicht hält.
 */
@ApplicationScoped
public class ServerScanPersistence {

    private final ScanRecordPort scanRecords;
    private final FindingPort findings;

    @Inject
    public ServerScanPersistence(ScanRecordPort scanRecords, FindingPort findings) {
        this.scanRecords = scanRecords;
        this.findings = findings;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void saveRecord(ScanRecord record) {
        scanRecords.save(record);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void persistResult(ScanRecord record, List<StoredFinding> stored) {
        scanRecords.save(record);
        findings.saveAll(stored);
    }
}
