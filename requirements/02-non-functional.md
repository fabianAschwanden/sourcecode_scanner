# 02 — Nicht-funktionale Anforderungen (NFR)

## Performance & Skalierung

| ID | Prio | Anforderung | Bezug |
|----|------|-------------|-------|
| NFR-01 | M | Scans MÜSSEN parallelisiert über einen Worker-Pool laufen. | Orchestration |
| NFR-02 | S | Inkrementelle Scans SOLLEN nur das Commit-Delta verarbeiten (Shallow/Range). | Orchestration |
| NFR-03 | S | Das System SOLL horizontal skalieren (mehrere Repos auf Worker-Knoten). | Server / P5 |
| NFR-04 | C | Ein Einzeldatei-Scan KANN gegen Regex-Backtracking (ReDoS) abgesichert sein. | Detection |
| NFR-21 | S | Der Diff-Modus SOLL schnelles Build-Feedback liefern (Richtwert: typischer MR < 60 s; projektspezifisch zu kalibrieren). | Orchestration |
| NFR-22 | C | Persistierte Findings/Scan-Ergebnisse KÖNNEN einer konfigurierbaren Aufbewahrungsfrist unterliegen (Default-Richtwert: 365 Tage). | Server |

## Robustheit & Verfügbarkeit

| ID | Prio | Anforderung | Bezug |
|----|------|-------------|-------|
| NFR-05 | M | Ein fehlerhaftes/abstürzendes Plugin DARF NICHT den Gesamtscan abbrechen (Isolation). | Plugin |
| NFR-06 | M | Jeder Detektor-Aufruf MUSS einem Timeout unterliegen. | Plugin |
| NFR-07 | S | Fehlgeschlagene Detektoren SOLLEN als Degradation im Report sichtbar sein. | Reporting |

## Sicherheit

| ID | Prio | Anforderung | Bezug |
|----|------|-------------|-------|
| NFR-08 | M | Credentials für Quellsysteme MÜSSEN aus einem Secret-Store/Env stammen, nie im Klartext in der Konfig. | Repository |
| NFR-09 | M | Treffer MÜSSEN in Ausgaben und Logs redigiert werden. | Reporting |
| NFR-10 | S | Discovery-/Clone-Tokens SOLLEN Least-Privilege (read-only) sein. | Repository |
| NFR-11 | S | Durchgeführte Scans und Zugriffe SOLLEN auditierbar protokolliert werden. | Server / P4 |
| NFR-12 | C | Plugin-JARs KÖNNEN signiert und vor Laden verifiziert werden. | Plugin / P5 |

## Erweiterbarkeit & Wartbarkeit

| ID | Prio | Anforderung | Bezug |
|----|------|-------------|-------|
| NFR-13 | M | Neue Detektoren MÜSSEN allein durch Ablegen eines JARs + Konfig-Eintrag aktivierbar sein. | Plugin |
| NFR-14 | S | Regelsätze SOLLEN ohne Neukompilierung des Cores austauschbar sein (externe Regeldateien). | Detection |
| NFR-15 | S | Die Konfiguration SOLL mehrstufig auflösbar sein (global → Repo → CLI → inline). | Orchestration |

## Kompatibilität & Portabilität

| ID | Prio | Anforderung | Bezug |
|----|------|-------------|-------|
| NFR-16 | M | Das System MUSS dem Blueprint des `sourcecode-scanner` folgen (Java 25, Quarkus); die Version wird beim Aufsetzen auf die aktuelle LTS/stabile Version gehoben (siehe docs/09). | Plattform |
| NFR-17 | S | Die SARIF-Ausgabe SOLL konform zu SARIF 2.1.0 sein (GitHub/GitLab/IDE-kompatibel). | Reporting |
| NFR-18 | C | Das System KANN als Container-Image bereitgestellt werden. | Betrieb / P4 |

## Usability

| ID | Prio | Anforderung | Bezug |
|----|------|-------------|-------|
| NFR-19 | S | Fehlermeldungen zur Konfiguration SOLLEN präzise auf Feld/Zeile verweisen. | CLI |
| NFR-20 | C | Inline-Suppressions KÖNNEN eine Pflicht-Begründung erzwingen. | Aggregation |
