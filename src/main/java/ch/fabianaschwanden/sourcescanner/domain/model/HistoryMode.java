package ch.fabianaschwanden.sourcescanner.domain.model;

/** Scan-Tiefe (docs/01 §6). */
public enum HistoryMode {
    /** Nur der aktuelle Stand des Default-Branch (ein Commit, keine History) — schneller, shallow clone. */
    HEAD,
    FULL,
    INCREMENTAL,
    SINCE_COMMIT,
    DIFF
}
