package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

/**
 * Verteilter Scan-Worker (horizontale Skalierung): pollt periodisch die DB nach {@code QUEUED}
 * Läufen und claimt sie atomar ({@code FOR UPDATE SKIP LOCKED}) — so führt jeder Pod nur so viele
 * Scans gleichzeitig aus, wie sein Limit erlaubt, und jeder Lauf wird genau einmal ausgeführt,
 * egal welcher Pod ihn gestartet hat.
 *
 * <p>Aktiv nur bei aktiver Persistenz (Server-Profile); im DB-freien CLI-Profil tut der Tick nichts.
 */
@ApplicationScoped
public class ScanWorker {

    private static final Logger LOG = Logger.getLogger(ScanWorker.class);

    private final ScanRecordPort scanRecords;
    private final ServerScanService scans;
    private final PodIdentity pod;
    private final ManagedExecutor executor;
    private final boolean persistenceActive;

    /** Lokal laufende, vom Executor gestartete Scans dieses Pods — begrenzt das parallele Claimen. */
    private final AtomicInteger inFlight = new AtomicInteger();

    @Inject
    public ScanWorker(ScanRecordPort scanRecords, ServerScanService scans, PodIdentity pod,
                      ManagedExecutor executor,
                      @ConfigProperty(name = "quarkus.datasource.active", defaultValue = "false")
                      boolean persistenceActive) {
        this.scanRecords = scanRecords;
        this.scans = scans;
        this.pod = pod;
        this.executor = executor;
        this.persistenceActive = persistenceActive;
    }

    /**
     * Tick: solange dieser Pod freie Slots hat, je einen wartenden Lauf claimen und asynchron
     * ausführen. {@code concurrentExecution = SKIP} verhindert überlappende Ticks.
     */
    @Scheduled(every = "{scanner.scan.poll-interval:3s}", concurrentExecution = ConcurrentExecution.SKIP)
    void claimAndRun() {
        if (!persistenceActive) {
            return;
        }
        int max = scans.maxConcurrentScans();
        while (inFlight.get() < max) {
            Optional<ScanRecord> claimed = scanRecords.claimNextQueued(pod.id());
            if (claimed.isEmpty()) {
                return; // nichts mehr in der Queue
            }
            ScanRecord record = claimed.get();
            inFlight.incrementAndGet();
            LOG.infof("pod %s claimed scan %s (repo %s)", pod.id(), record.id(), record.repoId());
            executor.runAsync(() -> {
                try {
                    scans.runClaimed(record);
                } catch (RuntimeException e) {
                    LOG.errorf(e, "scan %s failed in worker", record.id());
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        }
    }
}
