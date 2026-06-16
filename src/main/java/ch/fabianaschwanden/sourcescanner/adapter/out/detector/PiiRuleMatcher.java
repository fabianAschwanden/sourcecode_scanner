package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.util.regex.Pattern;

/**
 * Ein einzelnes PII-Muster (IBAN, Kreditkarte, E-Mail, Telefon) mit eigener Erkennung und
 * Plausibilitätsprüfung. Pro Typ eine Implementierung — der {@link PiiPatternsDetector} ist nur noch
 * der Orchestrator, der die Matcher über die Zeilen laufen lässt und die gemeinsame Config-/Severity-/
 * Redaktions-Logik kapselt.
 */
interface PiiRuleMatcher {

    /** Stabiler Regel-Schlüssel (z. B. {@code iban}, {@code creditcard}, {@code email}, {@code phone}). */
    String key();

    /** Erkennungsmuster für diesen PII-Typ. */
    Pattern pattern();

    /** Default-Severity der Regel (ohne Ruleset-Override). */
    Severity defaultSeverity();

    /** Ob die Regel standardmässig läuft (z. B. {@code phone} ist Default aus, DR-50). */
    boolean defaultOn();

    /**
     * Entscheidet, ob ein Regex-Treffer als echter Fund zählt (Plausibilität + FP-Filter). {@code line}
     * sowie {@code start}/{@code end} liefern den Kontext im Quelltext (z. B. um eine Kartennummer zu
     * verwerfen, die in einem UUID-Token steckt). {@code emailFilter} ist die pro Scan aufgelöste
     * Test-/Platzhalter-E-Mail-Konfiguration (nur für {@link EmailMatcher} relevant).
     */
    boolean accepts(String match, String line, int start, int end, EmailMatcher.TestEmailFilter emailFilter);
}
