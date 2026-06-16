package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistenz der Scan-Läufe (docs/06). Nimmt/liefert Domänen-Modelle, nie JPA-Entities (TR-23). */
public interface ScanRecordPort {

    ScanRecord save(ScanRecord record);

    Optional<ScanRecord> byId(UUID id);

    /** Lauf zu einer externen CI-Lauf-Referenz (für idempotente Einlieferung, IR-25). */
    Optional<ScanRecord> byCiRunRef(String runRef);

    /** Läufe absteigend nach Startzeit (für die Scan-Liste der UI). */
    List<ScanRecord> recent(int limit);

    // --- Verteilte Ausführung (horizontale Skalierung) -------------------------------------------

    /**
     * Claimt atomar den ältesten {@code QUEUED}-Lauf für {@code podId} und setzt ihn auf
     * {@code RUNNING} (Postgres {@code FOR UPDATE SKIP LOCKED}) — so führt genau ein Pod jeden Lauf
     * aus. Leer, wenn keiner wartet.
     */
    Optional<ScanRecord> claimNextQueued(String podId);

    /** Anzahl der von {@code podId} aktuell ausgeführten ({@code RUNNING}) Läufe — für das Pod-Limit. */
    long countRunningClaimedBy(String podId);

    /** Setzt das pod-übergreifende Abbruch-Flag; {@code true}, wenn der Lauf existierte. */
    boolean requestCancel(UUID id);

    /** Liest das Abbruch-Flag (vom ausführenden Pod während des Laufs gepollt). */
    boolean isCancelRequested(UUID id);

    /** Aktualisiert {@code claimed_at} als Heartbeat des ausführenden Pods. */
    void heartbeat(UUID id);

    /**
     * Setzt verwaiste {@code RUNNING}-Läufe (Heartbeat älter als {@code staleBefore}) zurück auf
     * {@code QUEUED}, damit ein anderer Pod sie übernimmt. Liefert die Zahl der requeueten Läufe.
     */
    int requeueStale(Instant staleBefore);
}
