# 01 — Funktionale Anforderungen (FR)

| ID | Prio | Anforderung | Komponente / Phase |
|----|------|-------------|--------------------|
| FR-01 | M | Das System MUSS Git-Repositories vollständig (inkl. Historie aller Branches) scannen können. | Repository Layer / P1 |
| FR-02 | M | Das System MUSS Secrets, Passwörter und API-Keys in Dateien und Commits erkennen. | Detection Layer / P1 |
| FR-03 | M | Das System MUSS Funde in einem maschinenlesbaren Standardformat (SARIF) ausgeben. | Reporting / P1 |
| FR-04 | M | Das System MUSS über eine deklarative Konfigurationsdatei (YAML) steuerbar sein. | Orchestration / P1 |
| FR-05 | M | Das System MUSS ein Quality-Gate mit konfigurierbarem Schwellenwert bereitstellen, das einen Exit-Code für CI liefert. | Reporting / P1 |
| FR-06 | M | Das System MUSS neue Detektoren über Plugins ohne Core-Änderung aufnehmen können. | Plugin/SPI / P1 |
| FR-07 | S | Das System SOLL Repositories aus Bitbucket, GitHub und GitLab organisationsweit erkennen (Discovery). | Repository Layer / P2 |
| FR-08 | S | Das System SOLL einen inkrementellen Scan-Modus (nur neue Commits) unterstützen. | Orchestration / P2 |
| FR-09 | S | Das System SOLL eine Baseline akzeptierter Altfunde unterstützen, sodass nur neue Funde das Gate brechen. | Aggregation / P2 |
| FR-10 | S | Das System SOLL Funde unterdrücken können — per Pfad-Glob und per Inline-Annotation im Code. | Aggregation / P2 |
| FR-11 | S | Das System SOLL identische Funde über Commits/Branches deduplizieren (erstes/letztes Auftreten). | Aggregation / P2 |
| FR-12 | S | Das System SOLL personenbezogene/vertrauliche Daten (z. B. Kundendaten) über konfigurierbare Muster erkennen. | Detection Layer / P3 |
| FR-13 | C | Das System KANN gefundene Secrets optional aktiv verifizieren (Gültigkeitsprüfung). | Detection Layer / P3 |
| FR-14 | C | Das System KANN einen PR-/Diff-Modus für schnelle Pre-Merge-Scans bereitstellen. | Orchestration / P3 |
| FR-15 | C | Das System KANN HTML- und JSON-Reports zusätzlich zu SARIF erzeugen. | Reporting / P2 |
| FR-16 | C | Das System KANN Funde als Tickets (Jira) anlegen und Chat-Benachrichtigungen senden. | Integration / P4 |
| FR-17 | C | Das System KANN als dauerhafter Service mit Webhook-getriggerten Scans laufen. | Interface / P4 |
| FR-18 | M | Das System MUSS gefundene Secret-Werte in allen Ausgaben und Logs redigieren (nie Klartext). | Reporting / P1 |
| FR-19 | S | Das System SOLL bereits gescannte Commits cachen, um Re-Scans zu vermeiden. | Orchestration / P2 |
| FR-20 | C | Das System KANN zentrale Policy-Vorgaben (Regeln/Gate) pro Organisationseinheit durchsetzen. | Governance / P5 |
