package ch.fabianaschwanden.sourcescanner.domain.model;

/** Authentifizierungsart gegen eine externe REST-Datenquelle (IR-61). Token stets nur als Referenz. */
public enum DataSourceAuthType {
    NONE,
    BEARER,
    BASIC,
    HEADER
}
