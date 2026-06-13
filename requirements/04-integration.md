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
| IR-21 | C | Der Build-Step KANN Ergebnisse optional an einen zentralen Server zurückmelden, ohne dass das Gate vom Server abhängt. |

## Benachrichtigung & Ticketing

| ID | Prio | Anforderung |
|----|------|-------------|
| IR-50 | C | Das System KANN Funde als Jira-Tickets anlegen (konfigurierbarer Projekt-Key). |
| IR-51 | C | Das System KANN Chat-Benachrichtigungen (Teams/Slack) ab konfigurierbarer Severity senden. |

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
