# 08 — CI/CD-Build-Integration (Build-Gate wie ein Linter)

Erweitert das Konzept um die Einbindung des Scanners **direkt in den Build-Prozess**
(GitLab CI, TeamCity, GitHub Actions, Bitbucket Pipelines, Jenkins). Verhalten wie
ein Linter: Bei kritischen Funden wird der Build **angehalten** (rotes Gate).

## 1. Grundidee: Der Scanner ist ein Build-Step

Der Scanner läuft als regulärer Schritt in der Pipeline und steuert den Build
ausschließlich über seinen **Exit-Code** und maschinenlesbare Reports — genau wie
ESLint, Checkstyle oder SpotBugs. Kein Sonderprotokoll, keep it simple:

```
Exit 0  → keine Funde über Schwellenwert  → Build läuft weiter (grün)
Exit 1  → Gate verletzt (z. B. CRITICAL)  → Build bricht ab (rot)
Exit 2  → Konfigurations-/Laufzeitfehler  → Build bricht ab (rot, Fehlerklasse)
```

Der Schwellenwert ist das bestehende Quality-Gate (`gate.failOn`, siehe
docs/03-configuration.md §2). Damit ist das „Anhalten bei kritischem Fehler"
bereits im Kern verankert; dieses Kapitel beschreibt die Einbettung pro
Build-System.

## 2. Scan-Modus im Build: Diff- vs. Full-Scan

| Trigger | Empfohlener Modus | Begründung |
|---------|-------------------|------------|
| Merge/Pull Request | `diff` (nur Änderung) | schnelles Feedback, blockt neue Secrets vor Merge |
| Push auf Default-Branch | `incremental` | neue Commits seit letztem Lauf |
| Nightly/Scheduled | `full` | Tiefenscan der gesamten Historie |
| Pre-Commit (lokal, optional) | `diff` (staged) | blockt vor dem Commit |

Im Build ist der **Diff-Modus** der Standard für schnelles, blockierendes
Feedback; der Full-Scan läuft zeitgesteuert getrennt, um Build-Zeiten nicht zu
belasten.

## 3. Gate-Verhalten (Linter-Semantik)

### 3.1 Blockierend vs. nicht-blockierend

```yaml
gate:
  failOn: HIGH            # ab dieser Severity bricht der Build
  failOnNewOnly: true     # nur neue (nicht-Baseline) Funde blocken
  warnThreshold: MEDIUM   # darunter nur Warnung, kein Abbruch
  softFail: false         # true = nie abbrechen, nur reporten (Einführungsphase)
```

- **`softFail: true`** erlaubt eine Einführungsphase, in der Funde sichtbar werden,
  ohne Builds zu brechen — wichtig für Akzeptanz in Brownfield-Repos.
- **`failOnNewOnly`** verhindert, dass Altlasten jeden Build sofort rot färben
  (Baseline-Mechanik aus docs/03).

### 3.2 Annotationen im Build

Funde werden zusätzlich zum Exit-Code als build-native Annotationen ausgegeben,
damit sie direkt in der Pipeline-Oberfläche erscheinen:

| System | Mechanismus |
|--------|-------------|
| GitLab | SAST-Report-Artefakt (Secret-Detection-Format) → MR-Widget & Security-Tab |
| TeamCity | Service Messages (`##teamcity[...]`) → Build-Problem & Test-/Inspektionsstatus |
| GitHub Actions | SARIF-Upload → Code-Scanning-Tab + `::error`-Workflow-Commands |
| Bitbucket | Code Insights Report + Annotationen |
| Jenkins | Warnings-NG/SARIF-Plugin |

## 4. Integration je Build-System

### 4.1 GitLab CI

Einbindung als Job in `.gitlab-ci.yml`. Der Job nutzt das Container-Image des
Scanners, läuft im Diff-Modus für MRs und exponiert ein
Secret-Detection-Report-Artefakt, das GitLab nativ im MR-Widget anzeigt.

```yaml
# .gitlab-ci.yml (Auszug)
secret-scan:
  stage: test
  image: registry.company.com/scanner:latest
  script:
    - scanner scan --mode diff --config .scanner.yaml --output sarif,gitlab
  artifacts:
    when: always
    reports:
      secret_detection: gl-secret-detection-report.json
    paths: [scan-reports/]
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
  allow_failure: false      # Gate-Fail bricht die Pipeline
```

`allow_failure: false` macht den Scanner blockierend; bei Exit 1 stoppt die
Pipeline. Für die Einführungsphase kann `allow_failure: true` mit `softFail`
kombiniert werden.

### 4.2 TeamCity

Einbindung als Command-Line-Build-Step. Der Scanner gibt **Service Messages** aus,
damit TeamCity Funde als Build-Probleme erkennt und den Build-Status setzt.

```
# Build Step: Command Line
scanner scan --mode diff --config .scanner.yaml --output sarif,teamcity
```

Service-Message-Beispiele, die der Scanner schreibt:

```
##teamcity[buildProblem description='Secret scan: 2 CRITICAL findings' identity='secretScan']
##teamcity[buildStatus status='FAILURE' text='Secret scan gate failed: {build.status.text}']
```

- Bei Gate-Fail setzt der Scanner einen **Build Problem** und Exit-Code ≠ 0 →
  TeamCity markiert den Build als fehlgeschlagen und stoppt nachfolgende Steps
  (Standardverhalten „Fail build if step exits with non-zero").
- Optional „Fail build on specific text in build log" als zweiter Sicherheitsnetz.
- Findings können zusätzlich als Inspektionen/Tests gemeldet werden, um sie im
  TeamCity-Reiter sichtbar zu machen.

### 4.3 GitHub Actions

Der Scanner wird als CLI-Container-Image aus GHCR gezogen
(`ghcr.io/<owner>/sourcecode-scanner-cli`, veröffentlicht vom Workflow
„Publish CLI Image"); das gescannte Repo wird im Job-Container gescannt:

```yaml
# .github/workflows/secret-scan.yml (Auszug)
jobs:
  scan:
    runs-on: ubuntu-latest
    container:
      image: ghcr.io/<owner>/sourcecode-scanner-cli:latest
    permissions: { contents: read, security-events: write }
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }     # volle Historie für Full-Scans
      - name: Secret scan (Gate)
        # Kein führendes "scan": das Jar ist der scan-Command, die Argumente sind dessen Optionen.
        run: java -jar /app/quarkus-run.jar --config .scanner.yaml --output sarif
      - name: Upload SARIF
        if: always()
        uses: github/codeql-action/upload-sarif@v3
        with: { sarif_file: scan-reports/results.sarif }
```

Ein Exit-Code ≠ 0 lässt den Job — und damit den erforderlichen Status-Check —
fehlschlagen; über Branch-Protection wird der Merge blockiert. **Modus:** Für PR/Push
ist `mode: head` (aktueller Checkout, kein History-Walk) der schnelle, blockierende
Pfad; ein Vollscan (`mode: full`) läuft zeitgesteuert über eine zweite Config. Ein
echter `diff`-Modus ist im Core noch nicht umgesetzt. Eine fertige, kopierbare
Vorlage (Workflow + zwei Configs) liegt projektunabhängig unter `deploy/cicd-integration/`.

### 4.4 Bitbucket Pipelines & Jenkins

- **Bitbucket:** Scanner-Step in `bitbucket-pipelines.yml`, Ausgabe als Code
  Insights Report; non-zero Exit stoppt die Pipeline.
- **Jenkins:** Aufruf in Declarative/Scripted Pipeline; `error`/`unstable` je nach
  Severity, SARIF via Warnings-NG visualisiert.

## 5. Performance im Build

Build-Gates müssen schnell sein, sonst werden sie umgangen oder abgeschaltet:

- **Diff-Modus** als Default (nur geänderte Hunks statt ganzer Historie).
- **Commit-Cache** (FR-19): bereits gescannte Commits werden übersprungen.
- **Detektor-Vorfilter** (`supports(FileType)`) reduziert unnötige Arbeit.
- **Timeouts** je Detektor (NFR-06) verhindern hängende Builds.
- Empfehlung: harte **Job-Timeout-Grenze** in der Pipeline als Schutz.

## 6. Konsistenz mit Server-Betrieb

Der Build-Step ist derselbe Scan-Core wie im Server-/UI-Betrieb (docs/06) — nur
über die CLI getrieben. Optional meldet der Build-Step seine Ergebnisse an den
zentralen Server zurück (zentrale Sicht/Trend, IR-21..26), ohne dass der Build vom
Server abhängt:

```yaml
output:
  formats: [sarif, gitlab]
  reportBack:
    enabled: false                       # opt-in: Ergebnisse an zentralen Server pushen (IR-21)
    serverUrlRef: env:SCANNER_SERVER_URL # Basis-URL des Servers (nur Referenz)
    auth:                                # OIDC Client-Credentials, Service-Account-Rolle 'ci' (IR-23)
      tokenUrlRef: env:SCANNER_OIDC_TOKEN_URL
      clientId: scanner-ci
      clientSecretRef: env:SCANNER_CI_CLIENT_SECRET
    runRef: ${CI_PIPELINE_ID}            # externe Lauf-Referenz für Idempotenz (IR-25)
```

**Ablauf:** Nach dem Scan sendet die CLI einen redigierten Ergebnis-Payload (Repo,
Modus, Status, Funde **ohne Klartext**, CI-Metadaten: Pipeline/Job-URL, Commit, Branch,
Aktor) per `POST /api/ingest` an den Server. Der Server legt daraus einen Scan-Record
mit `trigger=CI` und die Funde in der zentralen DB ab; in der UI sind sie wie
Server-Läufe sicht- und filterbar (Herkunft `CI`, WR-69). Die Einlieferung ist über
`runRef` idempotent (IR-25). **Entkopplung:** schlägt der Push fehl (Server aus, Auth-
Fehler), bleibt das Build-Gate über den Exit-Code unverändert funktionsfähig (IR-26).

## 7. Exit-Code-Vertrag (verbindlich)

| Exit | Bedeutung | Build-Wirkung |
|------|-----------|---------------|
| 0 | Keine Funde ≥ `failOn` (bzw. nur Baseline bei `failOnNewOnly`) | weiter (grün) |
| 1 | Gate verletzt — blockierende Funde vorhanden | Abbruch (rot) |
| 2 | Konfigurations-/Laufzeitfehler (kein gültiger Scan) | Abbruch (rot) |
| 3 | `softFail` aktiv: Funde vorhanden, aber nicht blockierend | weiter (gelb/Warnung) |

Der Exit-Code-Vertrag ist über alle Build-Systeme identisch — die system-
spezifischen Annotationen sind additiv, das Gate hängt allein am Exit-Code.
