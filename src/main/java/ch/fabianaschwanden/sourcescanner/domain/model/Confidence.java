package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Confidence eines Fix-Vorschlags (RMR-15): {@code HIGH} erlaubt (bei aktiviertem auto-Modus) einen
 * direkten Fix-PR; darunter wird ein Vorschlags-PR mit Review erzeugt.
 */
public enum Confidence {
    LOW,
    MEDIUM,
    HIGH
}
