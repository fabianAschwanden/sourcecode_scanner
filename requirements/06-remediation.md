# 06 — Remediation-Anforderungen (RMR)

Anforderungen an automatische Behebung per PR/MR (`RMR-1x`) und an die
History-Bereinigung (`RMR-2x`). Umsetzung in Roadmap-Phase 6 (siehe docs/05). **Beide
Funktionen sind sicherheitskritisch und standardmäßig deaktiviert.**

## Grundprinzip

| ID | Prio | Anforderung |
|----|------|-------------|
| RMR-01 | M | Das System MUSS bei jedem committeten Secret Rotation/Widerruf als ersten Schritt kommunizieren, vor Entfernung oder History-Bereinigung. |
| RMR-02 | M | Beide Remediation-Funktionen MÜSSEN standardmäßig deaktiviert und pro Repository explizit zu aktivieren sein. |
| RMR-03 | M | Jede Remediation-Aktion MUSS auditierbar protokolliert werden (Akteur, Zeit, Repo, betroffene Funde redigiert, Ergebnis). |

## Auto-Fix per Pull/Merge Request

| ID | Prio | Anforderung |
|----|------|-------------|
| RMR-10 | S | Das System SOLL aus einem oder mehreren Funden automatisch einen PR/MR zur Behebung erzeugen. |
| RMR-11 | M | Das System DARF NICHT direkt auf geschützte Branches schreiben; jede Änderung MUSS über PR/MR mit Review laufen. |
| RMR-12 | M | Ein Auto-Fix-PR DARF KEINEN Klartext-Treffer enthalten; Beschreibungen MÜSSEN redigiert sein. |
| RMR-13 | S | Detektoren SOLLEN über eine SPI-Erweiterung Fix-Vorschläge liefern können (Plugin-erweiterbar). |
| RMR-14 | S | Das System SOLL die Fix-Strategien EXTERNALIZE, REMOVE_LINE, REDACT, GITIGNORE und ANNOTATE unterstützen. |
| RMR-15 | S | Bei niedriger Confidence SOLL ein Vorschlags-PR mit Review erzeugt werden statt einer stillen Korrektur. |
| RMR-16 | C | Das System KANN Reviewer (CODEOWNERS/Security-Gruppe) und Labels automatisch zuweisen. |
| RMR-17 | C | Ein Auto-Fix-PR KANN eine Checkliste mit Rotation und optionalem History-Scrub enthalten. |

## History-Bereinigung (Rewrite)

| ID | Prio | Anforderung |
|----|------|-------------|
| RMR-20 | S | Das System SOLL gefundene Secrets aus der gesamten Git-Historie entfernen/maskieren können. |
| RMR-21 | M | Die Bereinigung MUSS auf einem frischen Mirror-Klon als Wegwerf-Arbeitskopie erfolgen, nie auf einer Live-Arbeitskopie. |
| RMR-22 | M | Vor jedem realen Rewrite MUSS ein Dry-Run mit Diff/Bericht und expliziter Freigabe erfolgen. |
| RMR-23 | M | Das System MUSS vor dem Rewrite ein Backup (Mirror) als Wiederherstellungspunkt anlegen. |
| RMR-24 | M | Nach dem Rewrite MUSS ein Re-Scan des bereinigten Mirrors bestätigen, dass keine Treffer verbleiben. |
| RMR-25 | M | Force-Push MUSS bewusst freigegeben werden und SOLL `force-with-lease` verwenden. |
| RMR-26 | M | Ist ein Fund ein verifiziert aktives Secret, MUSS die Bereinigung blockiert werden, bis der Status auf rotiert/widerrufen steht (Rotation-Gate). |
| RMR-27 | S | Das System SOLL offene PRs vor einem Rewrite erkennen und warnen (SHA-Änderung). |
| RMR-28 | S | Das System SOLL bewährte Werkzeuge (git-filter-repo als Default, BFG als Alternative) nutzen statt Eigenimplementierung. |
| RMR-29 | S | Nach dem Rewrite SOLL das System eine Re-Sync-Anleitung (Hard-Reset) und einen Restrisiko-Hinweis (Forks/Caches) generieren. |
| RMR-30 | C | Nach dem Rewrite KANN das System `.gitignore` ergänzen und einen Pre-Commit-Hook installieren. |

## Governance & Berechtigungen

| ID | Prio | Anforderung |
|----|------|-------------|
| RMR-40 | M | Auto-Fix und Scrub-Start MÜSSEN auf Rollen Operator/Admin beschränkt sein. |
| RMR-41 | M | Force-Push-Freigabe nach Rewrite MUSS auf Admin/Break-Glass beschränkt sein. |
| RMR-42 | S | Die Vier-Augen-Freigabe für Force-Push SOLL konfigurierbar sein. |
| RMR-43 | S | Während eines Rewrites SOLL das Repo gesperrt werden (Maintenance-/Lock-Mode). |
| RMR-44 | S | Höher privilegierte Token-Scopes für PR-Erstellung/Force-Push SOLLEN getrennt von Read-Only-Scan-Tokens verwaltet werden. |

## Observability

| ID | Prio | Anforderung |
|----|------|-------------|
| RMR-50 | C | Das System KANN Remediation-Metriken liefern: erzeugte PRs, gefixte Funde, Scrub-Ergebnisse. |
| RMR-51 | C | Das System KANN den Trend offen-vs-behoben über die Zeit visualisieren. |
