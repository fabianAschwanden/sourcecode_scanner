# 04 — Integrations- & Plattform-Anforderungen (IR)

## Repository-Plattformen

| ID | Prio | Anforderung |
|----|------|-------------|
| IR-01 | M | Das System MUSS lokale Git-Repositories über JGit lesen (Clone/Fetch/History/Diff). |
| IR-02 | S | Das System SOLL Bitbucket (Server/Data Center und Cloud) anbinden: Discovery, Auth, Clone. |
| IR-03 | S | Das System SOLL GitHub (Cloud und Enterprise) anbinden: Org-/Repo-Discovery, Auth, Clone. |
| IR-04 | S | Das System SOLL GitLab (SaaS und self-hosted) anbinden: Group-/Repo-Discovery, Auth, Clone. |
| IR-05 | M | Die Authentifizierung MUSS pro Quelle konfigurierbar sein (Token/SSH-Key) und auf Secret-Store referenzieren. |
| IR-06 | C | Das System KANN Repos per Namens-Regex filtern und archivierte Repos optional ausschließen. |
| IR-07 | C | Das System KANN auf Webhooks (Push/PR) reagieren und gezielt scannen. |

## Externe Datenquellen (vertrauliche Kundendaten)

Anbindung einer externen REST-API, deren Antwort vertrauliche Datenwerte liefert, die im
Code gesucht werden (FR-21..FR-23, DR-23..DR-28).

| ID | Prio | Anforderung |
|----|------|-------------|
| IR-60 | S | Das System SOLL eine externe REST-Datenquelle über einen `DataSourcePort` anbinden: konfigurierbare Basis-URL, HTTP-Methode, Pfad, optionale Query-/Header-Parameter und Paginierung. |
| IR-61 | M | Die Authentifizierung gegen die Datenquelle MUSS pro Quelle konfigurierbar sein (z. B. Bearer-Token, Basic, API-Key-Header) und ausschliesslich auf eine Secret-Referenz (`env:`/`vault:`) zeigen, nie Klartext (NFR-08, IR-30). |
| IR-62 | M | Aus der JSON-Antwort MÜSSEN über einen Pfad-Ausdruck (z. B. JSONPath) die Datensätze und je Attribut die Werte extrahiert werden können; die verfügbaren Attribute MÜSSEN für das UI-Mapping (WR-50) auslesbar sein. |
| IR-63 | S | Das System SOLL einen Verbindungs-/Probe-Aufruf gegen die Datenquelle anbieten, der die Erreichbarkeit prüft und ein **redigiertes** Schema (Attributnamen + Beispiel-Maskierung, ohne Klartextwerte) für das Mapping zurückliefert. |
| IR-64 | M | Antwortdaten DÜRFEN nicht roh persistiert oder geloggt werden; sie werden nur im Speicher gehalten (TTL-Cache, DR-27) und ausschliesslich redigiert nach aussen gegeben (FR-18, FR-23). |
| IR-65 | C | Das System KANN mehrere Datenquellen verwalten und je Repository/Org-Unit zuordnen, welche Datenquelle(n) beim Scan herangezogen werden. |
| IR-66 | S | Timeout, Wiederholversuche und Cache-TTL der Datenquelle SOLLEN konfigurierbar sein; Fehler MÜSSEN als Detektor-Degradation behandelt werden (DR-28), nicht als Scan-Abbruch. |
| IR-67 | S | Das System SOLL eine **Key-Value-Liste** als Datei aufnehmen (CSV `key,value` oder JSON, Auto-Erkennung); der Key ist der Attributname, der Value der gesuchte Wert. Es werden nur **Hashes** der Werte persistiert (FR-25, NFR-23). |
| IR-68 | S | Beim Upload SOLL je vorkommendem Key automatisch eine Attribut-Regel (geprüft, Default-Severity) angelegt werden, sofern noch nicht vorhanden; ein erneuter Upload ersetzt die Hashes idempotent. |

## CI/CD-Integration

| ID | Prio | Anforderung |
|----|------|-------------|
| IR-10 | M | Das System MUSS als CLI mit definiertem Exit-Code in CI lauffähig sein. |
| IR-11 | S | Das System SOLL fertige Templates für GitHub Actions, GitLab CI, TeamCity, Bitbucket Pipelines und Jenkins liefern. |
| IR-12 | S | Die SARIF-Ausgabe SOLL in GitHub/GitLab Security-Tabs darstellbar sein. |
| IR-13 | C | Das System KANN Inline-Kommentare an PRs/MRs schreiben. |
| IR-14 | M | Das System MUSS einen verbindlichen Exit-Code-Vertrag einhalten (0=pass, 1=Gate verletzt, 2=Fehler, 3=softFail), damit der Build wie bei einem Linter angehalten werden kann. |
| IR-15 | M | Das System MUSS bei Funden ≥ `gate.failOn` den Build durch Exit-Code 1 blockieren (Gate-Fail). |
| IR-16 | S | Das System SOLL einen `softFail`-Modus bereitstellen, der Funde meldet, ohne den Build abzubrechen (Einführungsphase). |
| IR-17 | S | Das System SOLL im Build standardmäßig im Diff-Modus laufen (schnelles, blockierendes Feedback für MRs/PRs). |
| IR-18 | S | Das System SOLL für GitLab ein natives Secret-Detection-Report-Artefakt erzeugen (MR-Widget/Security-Tab). |
| IR-19 | S | Das System SOLL für TeamCity Service Messages ausgeben (Build Problem / Build Status), sodass der Build als fehlgeschlagen markiert und gestoppt wird. |
| IR-20 | C | Das System KANN build-native Annotationen je System ausgeben (GitHub `::error`, Bitbucket Code Insights, Jenkins Warnings-NG). |
| IR-21 | S | Der Build-Step SOLL seine Ergebnisse optional an einen zentralen Server zurückmelden (opt-in über `output.reportBack`), ohne dass das Gate vom Server abhängt — fällt der Server aus, bleibt der Build über den Exit-Code funktionsfähig (Entkopplung). |
| IR-22 | S | Der Server MUSS einen **Ingest-Endpoint** (`POST /api/ingest`) bereitstellen, der einen abgeschlossenen CI-Lauf (Repo, Modus, Status, redigierte Funde + CI-Metadaten) in der **zentralen DB** ablegt, sodass CI-Läufe in der UI dieselbe Sicht/Trend wie Server-Läufe erhalten. |
| IR-23 | M | Der Ingest-Endpoint MUSS authentifiziert sein: ein CI-Service-Account über OIDC (Client-Credentials) mit eigener Rolle (`ci`); nur diese Rolle darf Ergebnisse einliefern (analog WR-31). |
| IR-24 | M | Eingelieferte Funde MÜSSEN **redigiert** sein (FR-18); der Server lehnt Klartext-Treffer ab bzw. speichert ausschliesslich den redigierten Treffer. |
| IR-25 | S | Jeder Lauf MUSS seine **Herkunft** tragen (`trigger`: `SERVER` \| `CI`) samt CI-Metadaten (Pipeline/Job-URL, Commit, Branch, Aktor); die Einlieferung SOLL über eine externe Lauf-Referenz **idempotent** sein (erneutes Einliefern desselben Laufs ersetzt statt dupliziert). |
| IR-26 | S | Die CLI SOLL den `reportBack`-Push **gate-entkoppelt** ausführen: ein Fehler beim Push DARF den Exit-Code des Gates nicht verändern (IR-14); Server-URL und Token nur als Secret-Referenz (NFR-08). |

## Benachrichtigung & Ticketing

| ID | Prio | Anforderung |
|----|------|-------------|
| IR-50 | C | Das System KANN Funde als Jira-Tickets anlegen (konfigurierbarer Projekt-Key). |
| IR-51 | C | Das System KANN Chat-Benachrichtigungen (Teams/Slack) ab konfigurierbarer Severity senden. |
| IR-52 | S | Das System SOLL E-Mail-Benachrichtigungen über SMTP versenden können; Empfänger, Absender und SMTP-Verbindung sind konfigurierbar, Credentials nur als Secret-Referenz (NFR-08). |
| IR-53 | S | Das System SOLL nach einem Scan einen Report (Zusammenfassung + redigierte Funde, FR-18) per E-Mail an die für das jeweilige Repository hinterlegten Empfänger senden können (opt-in je Repo). |
| IR-54 | C | Das System KANN systemweite Sammel-/Betriebsmeldungen an eine allgemeine Benachrichtigungs-E-Mail-Adresse senden. |

## Secret-Management & Betrieb

| ID | Prio | Anforderung |
|----|------|-------------|
| IR-30 | M | Credential-Referenzen MÜSSEN `env:`- und Secret-Store-Schemata (z. B. `vault:`) unterstützen. |
| IR-31 | C | Das System KANN als Container-Image und als Quarkus-Service betrieben werden. |
| IR-32 | C | Das System KANN ein zentrales Dashboard mit Trend-/Verlaufsdaten anbieten. |

## Ausgabe-Formate

| ID | Prio | Anforderung |
|----|------|-------------|
| IR-40 | M | SARIF 2.1.0 MUSS unterstützt werden. |
| IR-41 | C | HTML- und JSON-Ausgabe KÖNNEN zusätzlich erzeugt werden. |
