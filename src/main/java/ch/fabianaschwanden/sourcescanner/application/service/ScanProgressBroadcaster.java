package ch.fabianaschwanden.sourcescanner.application.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verteilt Scan-Fortschritts-Events je Lauf an SSE-Abonnenten (WR-04). Ein {@link BroadcastProcessor}
 * pro Scan-ID; Abonnenten erhalten künftige Events. Application-Layer, framework-arm (nur Mutiny).
 */
@ApplicationScoped
public class ScanProgressBroadcaster {

    /** Ein Event im Lebenszyklus eines Scans. */
    public record ScanEvent(UUID scanId, String status, int progress, int findingCount) {
    }

    private final ConcurrentHashMap<UUID, BroadcastProcessor<ScanEvent>> processors = new ConcurrentHashMap<>();

    /** Multi für die SSE-Resource; legt bei Bedarf einen Processor an. */
    public Multi<ScanEvent> stream(UUID scanId) {
        return processors.computeIfAbsent(scanId, id -> BroadcastProcessor.create());
    }

    public void publish(ScanEvent event) {
        BroadcastProcessor<ScanEvent> processor = processors.get(event.scanId());
        if (processor != null) {
            processor.onNext(event);
        }
    }

    /** Schliesst den Stream eines abgeschlossenen Scans und gibt Ressourcen frei. */
    public void complete(UUID scanId) {
        BroadcastProcessor<ScanEvent> processor = processors.remove(scanId);
        if (processor != null) {
            processor.onComplete();
        }
    }
}
