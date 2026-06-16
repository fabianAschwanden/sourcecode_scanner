package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Verteilte Scan-Ausführung gegen Dev-Services-Postgres: atomares Claimen ({@code FOR UPDATE SKIP
 * LOCKED}), pod-übergreifendes Cancel-Flag und Heartbeat-basiertes Requeue verwaister Läufe.
 */
@QuarkusTest
class ScanClaimRepositoryTest {

    @Inject
    ScanRecordPort scans;

    /**
     * Vorbestehende QUEUED/RUNNING-Läufe (aus anderen Tests, da Rows nicht zurückgerollt werden)
     * terminal setzen — sonst claimt {@code claimNextQueued} den global ältesten fremden Lauf statt
     * des hier erzeugten, und die Status-Asserts würden auf der falschen Zeile prüfen.
     */
    @org.junit.jupiter.api.BeforeEach
    void drainExisting() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (ScanRecord r : scans.recent(10_000)) {
                if (r.status() == ScanStatus.QUEUED || r.status() == ScanStatus.RUNNING) {
                    scans.save(r.cancelled());
                }
            }
        });
    }

    private ScanRecord queued() {
        return scans.save(ScanRecord.queued(UUID.randomUUID(), "repo-" + UUID.randomUUID(), "full"));
    }

    /**
     * Liest den Status in einer frischen Transaktion. Nötig, weil der Test-Thread sonst eine zuvor
     * geladene Entity aus dem Persistence-Kontext zurückbekäme statt des frisch committeten Werts —
     * im echten Betrieb hat jeder HTTP-Request ohnehin einen eigenen EntityManager.
     */
    private ScanStatus statusOf(UUID id) {
        return QuarkusTransaction.requiringNew()
                .call(() -> scans.byId(id).orElseThrow().status());
    }

    private boolean cancelOf(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> scans.isCancelRequested(id));
    }

    @Test
    void claim_setzt_queued_auf_running_und_haelt_den_pod_fest() {
        ScanRecord q = queued();
        Optional<ScanRecord> claimed = scans.claimNextQueued("pod-a");
        assertTrue(claimed.isPresent());
        assertEquals(ScanStatus.RUNNING, statusOf(q.id()));
        assertEquals(1, scans.countRunningClaimedBy("pod-a"));
    }

    @Test
    void jeder_queued_lauf_wird_unter_nebenlaeufigkeit_nur_einmal_geclaimt() throws Exception {
        // Mehrere wartende Läufe; viele "Pods" claimen gleichzeitig. SKIP LOCKED garantiert, dass
        // jeder Lauf genau einem Pod zufällt — keine doppelte Ausführung.
        int n = 8;
        for (int i = 0; i < n; i++) {
            queued();
        }
        long queuedBefore = scans.recent(1000).stream()
                .filter(r -> r.status() == ScanStatus.QUEUED).count();
        assertTrue(queuedBefore >= n, "Vorbedingung: mindestens " + n + " QUEUED");

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Optional<UUID>>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < (int) queuedBefore + 4; i++) {
                String pod = "pod-" + (i % 6);
                tasks.add(() -> scans.claimNextQueued(pod).map(ScanRecord::id));
            }
            List<Future<Optional<UUID>>> results = pool.invokeAll(tasks);
            Set<UUID> claimedIds = new HashSet<>();
            int duplicates = 0;
            for (Future<Optional<UUID>> f : results) {
                Optional<UUID> id = f.get();
                if (id.isPresent() && !claimedIds.add(id.get())) {
                    duplicates++;
                }
            }
            assertEquals(0, duplicates, "kein Lauf darf doppelt geclaimt werden");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void cancel_flag_ist_pod_uebergreifend_lesbar() {
        ScanRecord q = queued();
        scans.claimNextQueued("pod-a");
        assertFalse(cancelOf(q.id()));
        assertTrue(scans.requestCancel(q.id()));
        assertTrue(cancelOf(q.id()), "ein anderer Pod liest das Abbruch-Flag aus der DB");
    }

    @Test
    void requeue_holt_verwaiste_running_zurueck_in_die_queue() {
        ScanRecord q = queued();
        scans.claimNextQueued("pod-dead");
        assertEquals(ScanStatus.RUNNING, statusOf(q.id()));

        // Heartbeat-Schwelle in der Zukunft -> der Lauf gilt als verwaist und wird requeued.
        int requeued = scans.requeueStale(Instant.now().plusSeconds(60));
        assertTrue(requeued >= 1);
        assertEquals(ScanStatus.QUEUED, statusOf(q.id()),
                "verwaister RUNNING-Lauf steht wieder als QUEUED bereit");
    }

    @Test
    void frischer_heartbeat_schuetzt_vor_requeue() {
        ScanRecord q = queued();
        scans.claimNextQueued("pod-alive");
        scans.heartbeat(q.id());
        // Schwelle in der Vergangenheit -> frischer Heartbeat ist neuer, kein Requeue.
        scans.requeueStale(Instant.now().minusSeconds(60));
        assertEquals(ScanStatus.RUNNING, statusOf(q.id()));
    }
}
