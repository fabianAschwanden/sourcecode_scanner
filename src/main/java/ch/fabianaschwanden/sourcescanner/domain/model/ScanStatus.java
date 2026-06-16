package ch.fabianaschwanden.sourcescanner.domain.model;

/** Lebenszyklus eines server-getriebenen Scan-Laufs (docs/06 §3.3). */
public enum ScanStatus {
    /** Eingereiht, wartet auf einen freien Ausführungs-Slot (Parallelitäts-Limit). */
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
