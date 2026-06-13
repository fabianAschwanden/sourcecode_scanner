package ch.fabianaschwanden.sourcescanner.domain.port.out;

import java.util.Set;

/** Cache bereits gescannter Commit-IDs je Repository, um Re-Scans zu vermeiden (FR-19, NFR-02). */
public interface CommitCachePort {

    /**
     * Aktiviert den Cache für ein Verzeichnis ({@code null}/leer = inaktiv). Der Orchestrator setzt
     * dies aus der Konfiguration, bevor er den Cache nutzt.
     */
    void useDirectory(String directory);

    /** Bereits gescannte Commit-IDs des Repositories (leer, wenn nichts gecacht). */
    Set<String> scanned(String repoId);

    /** Vermerkt weitere Commit-IDs als gescannt (additiv, persistiert). */
    void markScanned(String repoId, Set<String> commitIds);
}
