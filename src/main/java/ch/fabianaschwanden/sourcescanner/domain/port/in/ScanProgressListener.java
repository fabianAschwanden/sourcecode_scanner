package ch.fabianaschwanden.sourcescanner.domain.port.in;

/**
 * Callback für den granularen Scan-Fortschritt (WR-04b): der Orchestrator meldet während des Laufs
 * Zwischenstände in Prozent (0–100). Framework-frei; der Server-Adapter bildet die Meldungen auf den
 * SSE-Broadcaster ab, der CLI-Pfad nutzt {@link #NONE}.
 */
@FunctionalInterface
public interface ScanProgressListener {

    /** No-op-Listener (CLI/Tests ohne Live-Fortschritt). */
    ScanProgressListener NONE = percent -> {
    };

    /** Meldet den aktuellen Fortschritt in Prozent (0–100). */
    void onProgress(int percent);
}
