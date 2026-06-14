package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Abgleichsmodus einer wertbezogenen Regel (DR-52, z. B. {@code email}):
 * <ul>
 *   <li>{@link #ALWAYS} — Muster überall erkennen (Default);</li>
 *   <li>{@link #LIST} — nur gegen eine hochgeladene Werteliste (IR-67) prüfen;</li>
 *   <li>{@link #API} — gegen eine externe Datenquelle (IR-60) prüfen.</li>
 * </ul>
 */
public enum RuleMatchMode {
    ALWAYS,
    LIST,
    API
}
