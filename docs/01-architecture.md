# 01 — Architekturkonzept

## 1. Architekturprinzipien

- **Blueprint-Konformität.** Der Scanner wird aus dem `app-template` erzeugt und
  folgt dessen Vorgaben (Quarkus + Angular BFF, Hexagonal + DDD, ArchUnit-erzwungen).
  Die hier beschriebenen Schichten werden auf die Template-Struktur abgebildet
  (Details: docs/09-template-alignment.md). Bei Konflikt gewinnt der Blueprint.
- **Trennung von Orchestrierung und Erkennung.** Die Scan-Engine kennt keine
  einzelne Regel; sie verteilt nur Arbeit an Detektoren.
- **Erweiterbarkeit über Plugins.** Neue Prüfaspekte = neues Plugin + Konfig-Eintrag,
  kein Core-Change.
- **Plattform-Abstraktion.** Bitbucket/GitHub/GitLab hinter einem gemeinsamen
  Connector-Interface.
- **Stateless Core, externalisierter Zustand.** Baseline, Suppressions und Ergebnisse
  liegen außerhalb des Prozesses (Dateien / DB).
- **Fail-safe statt fail-loud.** Ein fehlerhaftes Plugin darf den Gesamtscan nicht
  abbrechen (Isolation, Timeouts).

## 2. Schichtenmodell

```
┌─────────────────────────────────────────────────────────────────┐
│  Interface Layer                                                  │
│  CLI (Picocli)   │   CI/CD Step   │   REST API (optional Server)  │
└───────────────┬─────────────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────────┐
│  Orchestration Layer                                              │
│  Scan-Engine  ·  Job-Scheduler  ·  Work-Distribution  ·  Timeouts │
└───────┬───────────────────────────────────┬─────────────────────┘
        │                                   │
┌───────▼──────────────┐        ┌───────────▼─────────────────────┐
│  Repository Layer    │        │  Detection Layer (Plugins/SPI)  │
│  Connector-Abstraktion│       │  ┌────────────┬───────────────┐ │
│  ├─ Bitbucket         │       │  │ SecretDet. │ EntropyDet.   │ │
│  ├─ GitHub            │       │  │ PiiDet.    │ LicenseDet.   │ │
│  ├─ GitLab            │       │  │ CustomDet. │ ...           │ │
│  └─ LocalGit (JGit)   │       │  └────────────┴───────────────┘ │
└───────┬──────────────┘        └───────────┬─────────────────────┘
        │                                   │
        └─────────────────┬─────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│  Aggregation Layer                                                │
│  Dedup  ·  Severity-Scoring  ·  Baseline-Abgleich  ·  Suppression │
└─────────────────────────┬───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│  Reporting & Integration Layer                                    │
│  SARIF · HTML · JSON  │  Quality-Gate  │  Ticket-/Chat-Webhooks   │
└──────────────────────────────────────────────────────────────────┘
```

## 3. Komponentenbeschreibung

### 3.1 Repository Layer

Kapselt jeglichen Zugriff auf Quellsysteme hinter einem einheitlichen Interface.

```
RepositoryConnector
 ├─ discover()        → listet Repos einer Org/Project (REST-API)
 ├─ clone()/fetch()   → materialisiert Repo lokal (JGit, shallow optional)
 ├─ walkHistory()     → iteriert Commits (full | incremental | sinceCommit)
 └─ resolveDiff()     → liefert geänderte Hunks pro Commit
```

Konkrete Implementierungen: `BitbucketConnector`, `GitHubConnector`,
`GitLabConnector`, `LocalGitConnector`. Authentifizierung über Token/SSH-Key,
konfigurierbar pro Quelle. Das eigentliche Lesen der Objekte erfolgt einheitlich
über JGit, die REST-APIs dienen v. a. dem Org-weiten Discovery und Webhooks.

### 3.2 Orchestration Layer (Scan-Engine)

- Löst Konfiguration in eine Menge von `ScanTask` auf (Repo × Branch × Mode).
- Erzeugt aus Dateien/Diffs `ScanUnit`-Objekte und verteilt sie auf einen
  Worker-Pool.
- Lädt aktivierte Detektoren via SPI und ruft sie pro `ScanUnit` auf.
- Erzwingt **Timeouts** und **Fehlerisolation** je Detektor (ein Plugin-Crash
  degradiert nur dessen Ergebnisse, nicht den Lauf).

### 3.3 Detection Layer

Sammlung austauschbarer Detektoren (siehe [02-plugin-concept.md](02-plugin-concept.md)).
Jeder Detektor erhält eine `ScanUnit` und seine Teilkonfiguration und liefert
`Finding`-Objekte. Detektor-Kategorien: `SECRET`, `PII`, `LICENSE`, `IAC`,
`CUSTOM`.

### 3.4 Aggregation Layer

- **Deduplizierung:** gleicher Fund über mehrere Commits/Branches wird zu einem
  Finding mit erstem/letztem Auftreten zusammengeführt (Fingerprint aus
  Regel-ID + normalisiertem Match + Pfad).
- **Severity-Scoring:** Basis-Severity je Regel, modifiziert durch Verifikation
  (z. B. validierter API-Key → CRITICAL) und Kontext (Produktiv-Branch).
- **Baseline-Abgleich:** bekannte/akzeptierte Funde werden ausgeblendet; nur
  Delta bricht das Gate.
- **Suppression:** Pfad-Globs und Inline-Annotationen.

### 3.5 Reporting & Integration Layer

- **Formate:** SARIF 2.1.0 (für GitHub/GitLab/Azure/IDE), HTML-Report, JSON.
- **Quality-Gate:** konfigurierbarer Schwellenwert (`failOn`) liefert Exit-Code
  für CI.
- **Integrationen:** Webhooks an Ticketsysteme (Jira) und Chat (Teams/Slack),
  optionale PR-Kommentare.

## 4. Datenmodell (Kernobjekte)

```
ScanUnit        { repoId, path, commitId, author, timestamp, content, diffHunk }
Finding         { id, detectorId, category, severity, ruleId, file, line,
                  match(redacted), commitId, verified, firstSeen, lastSeen }
DetectorConfig  { enabled, params: Map<String,Object> }
ScanResult      { repoId, startedAt, finishedAt, findings[], stats }
Baseline        { entries: Set<Fingerprint> }
```

Secret-Werte werden in Findings **redigiert** gespeichert (z. B. erste/letzte
4 Zeichen), niemals im Klartext persistiert.

## 5. Datenfluss (Sequenz, vereinfacht)

```
CLI/CI ─► Engine: load config
Engine ─► Connector: discover + fetch repo
Engine ─► Connector: walkHistory(mode)
loop pro Commit/Datei:
    Engine ─► build ScanUnit
    Engine ─► Detector[*].scan(unit, cfg)   (parallel, isoliert)
    Detector ─► Finding[]
Engine ─► Aggregator: dedup + score + baseline + suppress
Aggregator ─► Reporter: SARIF/HTML/JSON
Reporter ─► Gate: exitCode
Gate ─► CI / Webhooks
```

## 6. Betriebsmodi

| Modus | Beschreibung | History |
|-------|--------------|---------|
| `full` | Gesamte Historie aller Branches | vollständig |
| `incremental` | Nur neue Commits seit letztem Lauf | seit Marker |
| `sinceCommit` | Ab definiertem Commit/Tag | ab Referenz |
| `pr` / `diff` | Nur Änderungen eines PR/MR | nur Diff |

Der `pr`/`diff`-Modus ist für CI-Geschwindigkeit gedacht (Pre-Merge-Gate),
`full` für initiale Audits und periodische Tiefenscans. Die konkrete Einbettung in
GitLab CI, TeamCity, GitHub Actions u. a. inkl. Build-Gate-Verhalten (Build-Abbruch
bei kritischen Funden wie bei einem Linter) ist in docs/08-cicd-build-integration.md
beschrieben.

## 7. Skalierung & Performance

- Worker-Pool pro Repo, optional horizontale Verteilung mehrerer Repos auf
  Worker-Knoten (bei Server-Betrieb).
- Shallow-Clone + Commit-Bereichsbegrenzung für inkrementelle Läufe.
- Caching bereits gescannter Commit-IDs (Re-Scan-Vermeidung).
- Detektor-Vorfilter über `supports(FileType)` zur Reduktion unnötiger Aufrufe.

## 8. Sicherheit des Scanners selbst

- Credentials für Quellsysteme aus Secret-Store (Vault/Env), nie in Konfig im
  Klartext.
- Redaktion von Treffern in allen Ausgaben und Logs.
- Least-Privilege-Tokens (read-only) für Discovery/Clone.
- Audit-Log über durchgeführte Scans und Zugriffe.
