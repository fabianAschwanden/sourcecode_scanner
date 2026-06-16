package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Framework-frei (TR-24). */
class PiiPatternsDetectorTest {

    private final PiiPatternsDetector detector = new PiiPatternsDetector();

    private ScanUnit unit(String content) {
        return new ScanUnit("repo", "src/Data.java", "c1", "a@b.ch", Instant.now(), content, null);
    }

    private DetectorConfig allPatterns() {
        return new DetectorConfig(true, Map.of());
    }

    private List<Finding> scan(String content, DetectorConfig cfg) {
        return detector.scan(unit(content), cfg);
    }

    @Test
    void gueltige_kreditkarte_wird_gemeldet_und_redigiert() {
        List<Finding> f = scan("card = 4111 1111 1111 1111", allPatterns());
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("creditcard")));
        Finding cc = f.stream().filter(x -> x.ruleId().equals("creditcard")).findFirst().orElseThrow();
        assertFalse(cc.redactedMatch().contains("4111 1111 1111 1111"));
    }

    @Test
    void luhn_ungueltige_kreditkarte_wird_nicht_gemeldet() {
        // 4111...1112 verletzt die Luhn-Prüfsumme
        List<Finding> f = scan("card = 4111 1111 1111 1112", allPatterns());
        assertFalse(f.stream().anyMatch(x -> x.ruleId().equals("creditcard")));
    }

    @Test
    void triviale_ziffernfolgen_sind_keine_kreditkarte() {
        // Null-UUID / Default-ID: nur Nullen bestehen Luhn zufällig, sind aber nie eine Kartennummer.
        String content = String.join(
                "\n",
                "<column name=\"id\" value=\"00000000-0000-0000-0000-000000000000\"/>",
                "zeros = 0000000000000000");
        List<Finding> f = scan(content, allPatterns());
        assertFalse(f.stream().anyMatch(x -> x.ruleId().equals("creditcard")),
                "lauter gleiche Ziffern dürfen keinen Kreditkarten-Fund erzeugen");
    }

    @Test
    void email_und_iban_werden_erkannt() {
        List<Finding> f = scan("mail: john.doe@firma.ch\niban: DE89 3704 0044 0532 0130 00", allPatterns());
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("email")));
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("iban")));
    }

    @Test
    void ungueltige_iban_pruefsumme_wird_nicht_gemeldet() {
        List<Finding> f = scan("iban: DE00 3704 0044 0532 0130 00", allPatterns());
        assertFalse(f.stream().anyMatch(x -> x.ruleId().equals("iban")));
    }

    @Test
    void gueltige_schweizer_iban_wird_erkannt() {
        List<Finding> f = scan("iban: CH93 0076 2011 6238 5295 7", allPatterns());
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("iban")));
    }

    @Test
    void iban_mit_falscher_laenderlaenge_wird_nicht_gemeldet() {
        // DE-IBAN MUSS 22 Zeichen haben; hier 23 (eine Ziffer zu viel) -> keine gültige IBAN.
        List<Finding> f = scan("iban: DE89 3704 0044 0532 0130 000", allPatterns());
        assertFalse(f.stream().anyMatch(x -> x.ruleId().equals("iban")));
    }

    @Test
    void iban_mit_unbekanntem_laendercode_wird_nicht_gemeldet() {
        // ZZ ist kein gültiger ISO-Ländercode -> keine IBAN, auch wenn das Muster passt.
        List<Finding> f = scan("iban: ZZ89 3704 0044 0532 0130 00", allPatterns());
        assertFalse(f.stream().anyMatch(x -> x.ruleId().equals("iban")));
    }

    @Test
    void patterns_filter_begrenzt_auf_email() {
        DetectorConfig onlyEmail = new DetectorConfig(true, Map.of("patterns", List.of("email")));
        List<Finding> f = scan("mail a@b.com card 4111 1111 1111 1111", onlyEmail);
        assertTrue(f.stream().allMatch(x -> x.ruleId().equals("email")));
    }

    @Test
    void deaktiviert_scannt_nicht() {
        assertTrue(scan("a@b.com", new DetectorConfig(false, Map.of())).isEmpty());
    }

    @Test
    void datums_und_zeitstempel_werden_nie_gemeldet() {
        String content = String.join(
                "\n",
                "created = 2024-01-15",
                "ts = 2024-01-15T12:30:45",
                "when: 2024-01-15 12:30",
                "dmy = 15.01.2024",
                "dmy2 = 15/01/2024",
                "time = 12:30:45");
        List<Finding> f = scan(content, allPatterns());
        assertTrue(f.isEmpty(), "Datums-/Zeitstempel sind immer unbedenklich und dürfen keinen Fund erzeugen");
    }

    @Test
    void test_und_platzhalter_emails_werden_nie_gemeldet() {
        // Reservierte Beispiel-/Test-Domains/-TLDs + bekannte Fixture-/Docs-Adressen (DR-57).
        String content = String.join(
                "\n",
                "fabian@example.com",
                "eva@example.org",
                "koenigin@example.com",
                "bot@wm-tippspiel.internal",
                "fabian@googletest.com",
                "eins@googletest.com",
                "onboarding@resend.dev",
                "du@beispiel.com",
                "user@host.test",
                "x@svc.local");
        List<Finding> f = scan(content, allPatterns());
        assertTrue(f.stream().noneMatch(x -> x.ruleId().equals("email")),
                "Test-/Dummy-/Platzhalter-Adressen sind keine echten Nutzerdaten und dürfen keinen Fund erzeugen");
    }

    @Test
    void echte_email_neben_test_domain_wird_weiterhin_erkannt() {
        List<Finding> f = scan("real: anna@firma.de\ndummy: tester@example.com", allPatterns());
        List<Finding> emails = f.stream().filter(x -> x.ruleId().equals("email")).toList();
        assertTrue(emails.size() == 1, "nur die echte Adresse wird gemeldet, die Test-Adresse nicht");
        assertFalse(emails.get(0).redactedMatch().contains("example.com"));
    }

    @Test
    void test_email_filter_ist_per_params_erweiterbar() {
        // Zusätzliche Projekt-Domain als Test-/Platzhalter-Domain markieren (additiv zu den Defaults).
        DetectorConfig cfg = new DetectorConfig(true, Map.of("testDomains", List.of("acme-test.io")));
        List<Finding> f = scan("a@acme-test.io\nb@firma.de", cfg);
        List<Finding> emails = f.stream().filter(x -> x.ruleId().equals("email")).toList();
        assertTrue(emails.size() == 1, "die konfigurierte Test-Domain wird gefiltert, die echte bleibt");
        assertTrue(emails.get(0).redactedMatch().contains("firma") || !emails.get(0).redactedMatch().contains("acme"));
    }

    @Test
    void test_email_filter_kann_per_params_abgeschaltet_werden() {
        DetectorConfig cfg = new DetectorConfig(true, Map.of("testEmailFilter", false));
        List<Finding> f = scan("fabian@example.com", cfg);
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("email")),
                "bei abgeschaltetem Filter wird auch eine example.com-Adresse gemeldet");
    }

    @Test
    void telefon_ist_standardmaessig_aus_und_meldet_nichts() {
        // phone ist defaultOn=false (DR-50) — ohne explizite Aktivierung kein Fund.
        List<Finding> f = scan("phone: +41 44 123 45 67", allPatterns());
        assertTrue(f.stream().noneMatch(x -> x.ruleId().equals("phone")),
                "Telefon-Regel ist standardmässig deaktiviert und darf keinen Fund erzeugen");
    }

    @Test
    void telefon_wird_erkannt_wenn_explizit_in_patterns_aktiviert() {
        DetectorConfig cfg = new DetectorConfig(true, Map.of("patterns", List.of("phone")));
        List<Finding> f = scan("phone: +41 44 123 45 67", cfg);
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("phone")));
    }

    @Test
    void telefon_wird_erkannt_wenn_per_ruleset_aktiviert() {
        DetectorConfig cfg = new DetectorConfig(true, Map.of(
                "ruleOverrides", Map.of("phone", Map.of("enabled", true, "matchMode", "ALWAYS"))));
        List<Finding> f = scan("phone: +41 44 123 45 67", cfg);
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("phone")));
    }
}
