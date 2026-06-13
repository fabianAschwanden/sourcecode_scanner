package ch.fabianaschwanden.sourcescanner.domain.model;

/** Lebenszyklus eines server-getriebenen Scan-Laufs (docs/06 §3.3). */
public enum ScanStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
