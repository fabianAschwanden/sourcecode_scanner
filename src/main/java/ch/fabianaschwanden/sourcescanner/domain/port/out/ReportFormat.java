package ch.fabianaschwanden.sourcescanner.domain.port.out;

/** Ausgabeformate für Reports. Phase 1 implementiert nur {@link #SARIF} (FR-03, IR-40). */
public enum ReportFormat {
    SARIF,
    HTML,
    JSON,
    GITLAB,
    TEAMCITY
}
