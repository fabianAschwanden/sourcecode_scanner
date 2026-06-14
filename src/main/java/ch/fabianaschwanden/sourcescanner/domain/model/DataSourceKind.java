package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Art einer Kundendaten-Quelle: eine externe REST-API ({@link #REST}, IR-60) oder eine hochgeladene
 * Key-Value-Liste ({@link #UPLOAD}, IR-67). Bei UPLOAD werden ausschliesslich <b>Hashes</b> der Werte
 * persistiert (NFR-23), nie der Klartext.
 */
public enum DataSourceKind {
    REST,
    UPLOAD
}
