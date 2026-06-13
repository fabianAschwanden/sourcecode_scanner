package ch.fabianaschwanden.sourcescanner.domain.model;

/** Scan-Tiefe (docs/01 §6). Phase 1 implementiert nur {@link #FULL}. */
public enum HistoryMode {
    FULL,
    INCREMENTAL,
    SINCE_COMMIT,
    DIFF
}
