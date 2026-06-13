package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Fix-Strategien für die automatische Behebung (RMR-14, docs/07 §2.2). Konservativ: bei Unsicherheit
 * wird ein Vorschlags-PR mit Review erzeugt, nicht stillschweigend „repariert".
 */
public enum RemediationStrategy {
    /** Hartkodierten Wert durch Secret-Store-/Env-Referenz ersetzen. */
    EXTERNALIZE,
    /** Betroffene Zeile entfernen. */
    REMOVE_LINE,
    /** Wert durch Platzhalter ersetzen. */
    REDACT,
    /** Datei aus Tracking nehmen + .gitignore-Eintrag. */
    GITIGNORE,
    /** Suppression-Annotation einfügen (False Positive). */
    ANNOTATE
}
