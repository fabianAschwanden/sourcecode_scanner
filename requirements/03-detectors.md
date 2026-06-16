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
| DR-23 | S | Der PII-Detektor SOLL bekannte Beispiel-/Test-PII (IBAN/Kreditkarte) über eine konfigurierbare Allowlist ignorieren; der Abgleich erfolgt auf normalisierter Form (Whitespace-/Bindestrich-frei, IBAN groß). |

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
