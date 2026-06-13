package ch.fabianaschwanden.sourcescanner.domain.model;

/** Kategorie eines Detektors für Gruppierung und Reporting (DR-02). */
public enum DetectorCategory {
    SECRET,
    PII,
    LICENSE,
    IAC,
    CUSTOM
}
