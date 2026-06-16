package ch.fabianaschwanden.sourcescanner.adapter.in.seed;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Räumt beim Start verwaiste Scan-Läufe auf: Ein Scan läuft asynchron im Speicher der laufenden
 * Instanz. Wird die Instanz mitten im Lauf beendet (Deploy/Neustart/Stop), bliebe der Lauf für immer
 * im Status {@code RUNNING} (0 %) hängen. Dieser Reaper setzt solche Läufe beim Start auf
 * {@code FAILED} mit klarer Meldung, damit die UI nicht endlos „läuft" anzeigt.
 *
 * <p>Nur bei aktiver Persistenz (server/dev); im DB-freien CLI-Profil inaktiv.
 */
@ApplicationScoped
public class OrphanScanReaper {

    private static final Logger LOG = Logger.getLogger(OrphanScanReaper.class);

    private final ScanRecordPort scans;
    private final boolean persistenceActive;

    @Inject
    public OrphanScanReaper(
            ScanRecordPort scans,
            @ConfigProperty(name = "quarkus.datasource.active", defaultValue = "false") boolean persistenceActive) {
        this.scans = scans;
        this.persistenceActive = persistenceActive;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (!persistenceActive) {
            return;
        }
        int reaped = 0;
        for (ScanRecord r : scans.recent(1000)) {
            if (r.status() == ScanStatus.RUNNING) {
                scans.save(r.failed("interrupted by a restart/redeploy"));
                reaped++;
            }
        }
        if (reaped > 0) {
            LOG.warnf("marked %d orphaned RUNNING scan(s) as FAILED at startup", reaped);
        }
    }
}
