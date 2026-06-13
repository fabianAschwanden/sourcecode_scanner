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
| DR-22 | C | Kreditkartentreffer KÖNNEN per Luhn-Prüfung gegen False Positives validiert werden. |

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
