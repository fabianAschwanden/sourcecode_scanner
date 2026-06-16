package ch.fabianaschwanden.sourcescanner.adapter.in.seed;

import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Reaper für verwaiste Scan-Läufe in der verteilten Ausführung. Ein {@code RUNNING}-Lauf gehört
 * einem Pod, der ihn geclaimt hat und per Heartbeat ({@code claimed_at}) am Leben hält. Stirbt der
 * Pod (Deploy/Crash), bleibt der Heartbeat stehen — dann setzt dieser Reaper den Lauf nach Ablauf
 * des Timeouts wieder auf {@code QUEUED}, sodass ein anderer Pod ihn übernimmt (statt ihn wie früher
 * hart als FAILED zu markieren). So überleben laufende Scans ein Redeploy.
 *
 * <p>Nur bei aktiver Persistenz (Server-Profile); im DB-freien CLI-Profil inaktiv.
 */
@ApplicationScoped
public class OrphanScanReaper {

    private static final Logger LOG = Logger.getLogger(OrphanScanReaper.class);

    private final ScanRecordPort scans;
    private final boolean persistenceActive;
    private final Duration staleAfter;

    @Inject
    public OrphanScanReaper(
            ScanRecordPort scans,
            @ConfigProperty(name = "quarkus.datasource.active", defaultValue = "false") boolean persistenceActive,
            @ConfigProperty(name = "scanner.scan.stale-after", defaultValue = "PT2M") Duration staleAfter) {
        this.scans = scans;
        this.persistenceActive = persistenceActive;
        this.staleAfter = staleAfter;
    }

    /**
     * Periodisch verwaiste RUNNING-Läufe (Heartbeat älter als {@code scanner.scan.stale-after})
     * zurück in die Queue. Läuft auf jedem Pod; das Requeue-UPDATE ist idempotent.
     */
    @Scheduled(every = "{scanner.scan.reap-interval:30s}", concurrentExecution = ConcurrentExecution.SKIP)
    void reap() {
        if (!persistenceActive) {
            return;
        }
        int requeued = scans.requeueStale(Instant.now().minus(staleAfter));
        if (requeued > 0) {
            LOG.warnf("requeued %d orphaned RUNNING scan(s) whose owning pod went away", requeued);
        }
    }
}
