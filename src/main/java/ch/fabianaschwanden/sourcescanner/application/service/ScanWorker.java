package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.application.service.ServerScanService.ScanQueuedEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
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
     * Periodischer Tick: claimt wartende Läufe, solange dieser Pod freie Slots hat. Fallback- und
     * Cross-Pod-Sicherung; der Normalfall wird unmittelbar über {@link #onScanQueued} bedient.
     * {@code concurrentExecution = SKIP} verhindert überlappende Ticks.
     */
    @Scheduled(every = "{scanner.scan.poll-interval:3s}", concurrentExecution = ConcurrentExecution.SKIP)
    void claimAndRun() {
        drainQueue();
    }

    /**
     * Reagiert sofort auf einen frisch eingereihten Scan (CDI-Event), damit der Lauf bei freiem Slot
     * praktisch unmittelbar von QUEUED auf RUNNING wechselt — ohne bis zum nächsten Poll-Tick (bis zu
     * {@code poll-interval}) zu warten. Läuft asynchron auf einem Worker-Thread.
     */
    void onScanQueued(@ObservesAsync ScanQueuedEvent event) {
        drainQueue();
    }

    /** Claimt wartende Läufe bis zum Pod-Limit und führt sie asynchron aus. Mehrfach-/Nebenläufiger Aufruf ist sicher. */
    private void drainQueue() {
        if (!persistenceActive) {
            return;
        }
        int max = scans.maxConcurrentScans();
        // Slot optimistisch reservieren (atomar), damit Poll-Tick und Event-Observer nebenläufig
        // nicht gemeinsam über das Limit hinaus claimen. Wird zurückgegeben, wenn nichts wartet.
        while (reserveSlot(max)) {
            Optional<ScanRecord> claimed = scanRecords.claimNextQueued(pod.id());
            if (claimed.isEmpty()) {
                inFlight.decrementAndGet(); // Slot wieder freigeben — Queue ist leer.
                return;
            }
            ScanRecord record = claimed.get();
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

    /** Reserviert atomar einen Slot, falls das Pod-Limit es zulässt; {@code false}, wenn voll. */
    private boolean reserveSlot(int max) {
        int current;
        do {
            current = inFlight.get();
            if (current >= max) {
                return false;
            }
        } while (!inFlight.compareAndSet(current, current + 1));
        return true;
    }
}
