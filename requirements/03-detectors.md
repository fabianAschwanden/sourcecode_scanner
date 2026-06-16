# 03 — Detektor-Anforderungen (DR)

## Allgemein

| ID | Prio | Anforderung |
|----|------|-------------|
| DR-01 | M | Jeder Detektor MUSS das `Detector`-SPI-Interface implementieren und eine stabile, eindeutige ID liefern. |
| DR-02 | M | Jeder Detektor MUSS einer Kategorie zugeordnet sein (SECRET, PII, LICENSE, IAC, CUSTOM). |
| DR-03 | M | Jeder Fund MUSS Datei, Zeile, Commit, Regel-ID, Severity und einen redigierten Treffer enthalten. |
| DR-04 | S | Ein Detektor SOLL über `supports(FileType)` irrelevante Dateien vorab ausschließen können. |
| DR-05 | C | Ein Detektor KANN `verify()` implementieren, um einen Treffer aktiv zu validieren. |

## Secret-Detektoren

| ID | Prio | Anforderung |
|----|------|-------------|
| DR-10 | M | Der Regel-Detektor MUSS einen Gitleaks-kompatiblen Regelsatz laden können. |
| DR-11 | M | Der Regelsatz MUSS gängige Cloud-Credentials abdecken (AWS, GCP, Azure) sowie generische API-Tokens. |
| DR-12 | S | Ein Entropie-Detektor SOLL hochentropische Strings (Base64/Hex) ab konfigurierbarem Schwellenwert melden. |
| DR-13 | M | Der Entropie-Schwellenwert und die Mindestlänge MÜSSEN konfigurierbar sein. |
| DR-14 | C | Verifizierte aktive Secrets SOLLEN automatisch auf CRITICAL hochgestuft werden. |

## PII- / Kundendaten-Detektoren

| ID | Prio | Anforderung |
|----|------|-------------|
| DR-20 | S | Ein PII-Detektor SOLL Standardmuster erkennen: IBAN, Kreditkartennummer, E-Mail, Telefonnummer. |
| DR-21 | M | Kundendaten-Muster MÜSSEN frei über Konfiguration (Regex + Severity) definierbar sein. |
| DR-22 | C | Kreditkartentreffer KÖNNEN per Luhn-Prüfung gegen False Positives validiert werden; zusätzlich werden triviale Ziffernfolgen (lauter gleiche Ziffern, z. B. `0000…0000` aus einer Null-UUID/Default-ID, die Luhn zufällig bestehen) verworfen. |
| DR-21a | S | IBAN-Treffer MÜSSEN echt validiert werden: korrekter Aufbau (Ländercode + 2 Prüfziffern), **länderkonforme Länge** (ISO-13616-Registry je Ländercode) und ISO-7064-Mod-97-Prüfziffer == 1. Nur länderkonforme, prüfziffergültige IBANs schlagen an (FP-Reduktion). |
| DR-57 | S | Der PII-Detektor SOLL offensichtlich unbedenkliche Treffer ausfiltern (kein Fund): Datums-/Zeitstempel (z. B. `2024-01-15`, `15.01.2024`, `12:30:45`) sowie **Test-/Dummy-/Platzhalter-E-Mails** — reservierte Beispiel-/Test-Domains und -TLDs (RFC 2606/6761: `example.*`, `.test`, `.invalid`, `.localhost`; private/interne `.internal`, `.local`; deutscher Platzhalter `beispiel.*`) und eine kurze Liste bekannter Fixture-/Docs-Adressen (z. B. `*@googletest.com`, `onboarding@resend.dev`). Echte Adressen (z. B. `anna@firma.de`) bleiben unberührt. Der E-Mail-Filter SOLL über die Detektor-/Ruleset-params der `email`-Regel konfigurierbar sein: additive Listen `testDomains`/`testTlds`/`testSlds` (ergänzen die Defaults) und `testEmailFilter: false` (schaltet den Filter ab). |

## API-gespeister Kundendaten-Detektor (externe REST-Datenquelle)

Erkennt **konkrete vertrauliche Werte** (z. B. Partnernummern, Namen, Vornamen), die aus
einer externen REST-API stammen, als Treffer im Code (FR-21..FR-23). Die zu prüfenden
Attribute werden über ein Mapping festgelegt (WR-50).

| ID | Prio | Anforderung |
|----|------|-------------|
| DR-23 | S | Ein Kundendaten-Detektor SOLL Werte einer konfigurierten externen REST-Datenquelle (DataSourcePort, IR-60) als Suchbegriffe laden und im Code-/Commit-Inhalt erkennen. |
| DR-24 | M | Der Detektor MUSS nur die Attribute auswerten, die im Attribut-Mapping als `geprüft` markiert sind; jedes Attribut trägt eine eigene Severity und Kategorie (PII oder CUSTOM, FR-22). |
| DR-25 | M | Der Wertabgleich MUSS exakt und mit Wortgrenzen erfolgen (kein Teilstring-Treffer mitten in unzusammenhängenden Zeichen), um Rauschen zu begrenzen; sehr kurze/leere Werte (konfigurierbare Mindestlänge) MÜSSEN ignoriert werden. |
| DR-26 | M | Geladene Werte DÜRFEN nicht im Klartext geloggt oder in Funden ausgegeben werden; der Fund trägt nur den redigierten Treffer und den Attributnamen (z. B. `partnernummer`), nie den Klartextwert (FR-18, FR-23). |
| DR-27 | S | Der Detektor SOLL die Wertliste mit konfigurierbarem TTL cachen und nie auf Platte persistieren; Werte werden ausschliesslich als Hash/Fingerprint für Dedup/Baseline gespeichert (DR-41). |
| DR-28 | C | Der Detektor KANN bei nicht erreichbarer Datenquelle degradiert weiterlaufen (Lauf nicht abbrechen) und die Degradation als Detektor-Fehler melden (OR-05). |
| DR-29 | S | Der Detektor SOLL neben der REST-Quelle eine **Upload-Quelle** (hochgeladene Key-Value-Liste, IR-67) unterstützen; bei dieser liegen nur Hashes vor (NFR-23). |
| DR-30 | M | Bei einer Upload-Quelle MUSS der Detektor jedes Code-Token an Wortgrenzen mit demselben Verfahren hashen und gegen die gespeicherten Hashes je Attribut abgleichen — ein exakter Abgleich ohne Kenntnis des Klartexts. |

## License- / IaC-Detektoren

| ID | Prio | Anforderung |
|----|------|-------------|
| DR-30 | C | Ein License-Detektor KANN fehlende/abweichende Lizenz-Header melden. |
| DR-31 | C | Ein IaC-Detektor KANN unsichere Defaults in Terraform/K8s/Dockerfiles erkennen. |

## Qualität & Rauschen

| ID | Prio | Anforderung |
|----|------|-------------|
| DR-40 | M | Detektoren MÜSSEN Inline-Suppression-Direktiven respektieren. |
| DR-41 | S | Detektoren SOLLEN deterministische Fingerprints für Deduplizierung/Baseline erzeugen. |
| DR-42 | S | Die False-Positive-Rate SOLL durch Kontext (Pfad, Dateityp, Verifikation) reduziert werden. |

## Regelsätze (Rulesets) — feingranulare Steuerung

Ein **Ruleset** ist eine benannte, eigenständige Sammlung von Regel-Einstellungen (unabhängig
von der Gate-/Org-Policy FR-20). Es steuert je **einzelner Regel**, ob sie läuft und mit welcher
Severity; sein **Geltungsbereich** ist entweder global (alle Repos) oder eine Repo-Liste (WR-90..96).

| ID | Prio | Anforderung |
|----|------|-------------|
| DR-50 | S | Eine Regel MUSS einzeln aktivierbar/deaktivierbar sein (z. B. `email`, `iban`, `creditcard`, `phone`, `secret.high-entropy`); Default-aktiv ist die eingebaute Vorgabe der Regel. Die Regel `phone` ist **standardmässig deaktiviert** (zu rauschanfällige Heuristik: Versionen, IDs, Beträge) und schlägt nur an, wenn sie explizit (per `patterns`-Liste oder Ruleset-Override) aktiviert wird. |
| DR-51 | S | Je Regel MUSS die **Severity** über das Ruleset überschreibbar sein (INFO..CRITICAL); ohne Override gilt die Default-Severity der Regel. |
| DR-52 | S | Für Erkennungsregeln mit Wertbezug (z. B. `email`) MUSS ein **Abgleichsmodus** wählbar sein: `always` (Muster überall), `list` (nur gegen eine hochgeladene Werteliste, IR-67) oder `api` (gegen eine externe Datenquelle, IR-60). Bei `list`/`api` wird eine Datenquelle referenziert. |
| DR-53 | M | Treffen mehrere Rulesets auf ein Repo zu, MÜSSEN ihre Regel-Overrides deterministisch zusammengeführt werden (Repo-spezifisch vor global; „aus" hat Vorrang vor „an" nur bei explizitem Deaktivieren — definierte Merge-Reihenfolge). |
| DR-54 | M | Nur Rulesets mit Enforcement-Status `active` wirken auf Scans; `disabled` Rulesets bleiben gespeichert, beeinflussen aber keinen Lauf (analog GitHub). |
| DR-55 | S | Die effektive Regel-Konfiguration eines Laufs SOLL nachvollziehbar sein (welches Ruleset welche Regel/Severity/Modus gesetzt hat) — für Audit/Transparenz (WR-34). |
| DR-56 | S | Existiert beim Start (bei aktiver Persistenz) **kein** Ruleset, SOLL ein global **`active`** Ruleset mit Namen `default` angelegt werden — befüllt aus dem Detektor-Regelkatalog (Aktiv-Zustand je Regel-Default, d. h. `phone` aus; Default-Severity je Regel, Abgleichsmodus `always`, DR-50..52). So entspricht die effektive Konfiguration der eingebauten Vorgabe und ist sichtbar/editierbar (WR-97); das Ruleset bleibt änder- und löschbar. Das DB-freie CLI-Profil seedet nicht. |
