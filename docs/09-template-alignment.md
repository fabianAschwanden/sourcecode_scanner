# 09 — Ableitung aus dem app-template (Blueprint-Konformität)

Der Scanner wird **aus dem Unternehmens-`app-template`** erzeugt
(`fabianAschwanden/app-template`, „Use this template") und erbt damit dessen
verbindlichen Blueprint. Dieses Kapitel bindet die in 01–08 beschriebene Fachlichkeit
an die Template-Struktur und -Konventionen. Bei Konflikten gilt: **der Blueprint
gewinnt** (er beschreibt das *Wie*, dieses Konzept das *Was*).

## 1. Was das Template vorgibt

Das Template ist ein lauffähiges Gerüst ohne Fachlichkeit: ein Deployable (BFF) aus Quarkus + Angular via Quinoa, Hexagonal + DDD, erzwungen per ArchUnit.

| Bereich | Vorgabe (Snapshot Stand 2026-06, auf aktuelle Version heben) |
|---------|--------------------------------------------------------------|
| Sprache Backend | Java 25 (`maven.compiler.release`) |
| Framework | Quarkus (`quarkus-bom` 3.36.x) — **nicht Spring Boot** |
| REST | `quarkus-rest` + `quarkus-rest-jackson` (JAX-RS) |
| Persistenz | Hibernate ORM mit Panache, PostgreSQL |
| Schema | Liquibase (append-only, besitzt das Schema; Hibernate validiert) |
| Auth | OIDC (`quarkus-oidc`), BFF-Pattern, Session-Cookies statt Browser-Token |
| Frontend | Angular 22 (standalone, Signals, `inject()`, OnPush), TS strict, Tailwind 4 |
| Frontend-Integration | Quinoa (ein Build, kein CORS, eine Pipeline) |
| Tests/Gate | JUnit 5, Mockito, `@QuarkusTest`+REST-assured, ArchUnit, Vitest, Playwright, JaCoCo-Gate, ESLint |
| Delivery | GitHub Actions (self-hosted ARC), Container-Image (Podman), GitOps/ArgoCD, Dependabot |
| Build/Gate | `./mvnw verify` |

### Konsequenz für die bisherige Spec
Der Server-Betrieb wird **ausschließlich mit Quarkus** umgesetzt (nicht Spring
Boot); die UI ist ein Quinoa-Angular-Feature im selben Deployable, keine separate
SPA. Web-UI-/Observability-/Remediation-Fachlichkeit (docs/06/07) bleibt
unverändert gültig — nur die technische Umsetzung folgt dem Blueprint.

## 2. Scaffolding: So entsteht der Scanner

```
1. GitHub „Use this template" → neues Repo (z. B. source-scanner)
2. ./scripts/init.sh sourcecode-scanner ch.fabianaschwanden.sourcescanner
   → benennt App + Basis-Paket um (ch.example.app → ch.fabianaschwanden.sourcescanner)
3. Beispiel-Durchstich „Note" durch die Scanner-Fachlichkeit ersetzen
4. ./mvnw verify   → Gate grün, dann iterieren
```

Der Beispiel-Durchstich Note existiert nur als Referenz für den Pfad Domäne → Ports → Application Service → REST → Liquibase → Angular-Feature und wird durch echte Fachlichkeit ersetzt; das Init-Script wird danach gelöscht.

## 3. Abbildung der Scanner-Fachlichkeit auf Hexagonal + DDD

Die Schichten aus docs/01 werden auf die unverhandelbare Paketstruktur des
Blueprints abgebildet (`adapter → application → domain`, `domain/` framework-frei).

```
ch.fabianaschwanden.sourcescanner/
├── domain/                          # framework-frei, reine records, Invarianten fail-fast
│   ├── model/                       # Finding, Severity (VO), ScanResult, Baseline,
│   │                                #   RepositoryRef, RemediationProposal, GateResult
│   ├── event/                       # ScanCompleted, CriticalFindingDetected (anlegen wenn gebraucht)
│   ├── service/                     # FindingAggregation, SeverityScoring, GateEvaluation (pur)
│   └── port/
│       ├── in/                      # StartScanUseCase, TriageFindingUseCase,
│       │                            #   RemediateUseCase, ScrubHistoryUseCase
│       └── out/                     # RepositoryConnectorPort, DetectorPort,
│                                    #   FindingRepositoryPort, ReportPort, PrCreationPort,
│                                    #   HistoryRewritePort, MetricsPort
├── application/
│   └── service/                     # ScanOrchestrationService, RemediationService,
│                                    #   Transaktionsgrenze, keine Geschäftsregeln
└── adapter/
    ├── in/
    │   └── rest/                    # JAX-RS Resources: Scans, Findings, Config, Remediation
    │       └── dto/                 # REST-DTOs (nur hier)
    └── out/
        ├── persistence/             # Panache: Scan-/Finding-/Baseline-Entities + Repos
        ├── connector/               # Bitbucket/GitHub/GitLab/LocalGit (JGit) — implementiert RepositoryConnectorPort
        ├── detector/                # Detector-Plugins hinter DetectorPort (siehe §4)
        ├── report/                  # SARIF/HTML/JSON/GitLab/TeamCity hinter ReportPort
        ├── vcsplatform/             # PR/MR-Erstellung, Force-Push hinter PrCreationPort/HistoryRewritePort
        └── scrub/                   # git-filter-repo/BFG-Aufruf hinter HistoryRewritePort
```

Kernregeln des Blueprints, die hier besonders greifen:

- **`domain/` ohne Framework:** `Finding`, `Severity`, `ScanResult` sind reine
  `record`s; Severity-Invarianten (z. B. gültige Stufe) im Compact-Constructor.
  Die Erkennungs-/Aggregationslogik (Scoring, Dedup, Gate-Bewertung) ist pure
  Domain-Logik ohne Quarkus/JGit-Import.
- **Ports trennen Fachlichkeit von Technik:** JGit, REST-APIs der Plattformen,
  git-filter-repo, Prometheus-Export liegen ausschliesslich in Adaptern hinter
  `port/out`-Interfaces. Die Engine (Application Service) kennt nur Ports.
- **Repositories liefern Domänen-Modelle, nie JPA-Entities.** Finding-Persistenz
  mappt zwischen Panache-Entity und Domänen-`record`.
- **ArchUnit erzwingt das:** ein Framework-Import in `domain/` oder ein Adapter,
  der einen anderen Adapter referenziert, **bricht den Build** — genau das schützt
  die Plugin-Erweiterbarkeit langfristig.

## 4. Detector-Plugins im Quarkus/Blueprint-Kontext

Das SPI-Plugin-Konzept (docs/02) bleibt bestehen, wird aber blueprint-konform
eingebettet:

- `DetectorPort` (in `domain/port/out`) ist die fachliche Schnittstelle, die der
  Application Service kennt. Das SPI-`Detector`-Interface aus docs/02 ist der
  **externe Plugin-Vertrag** und liegt im Adapter (`adapter/out/detector/spi`);
  die konkrete Plugin-Lade-Mechanik (ServiceLoader / isolierter ClassLoader) liegt
  ebenfalls dort. Der Detector-Adapter mappt SPI-`Detector`-Plugins auf
  `DetectorPort`. Built-in-Detektoren können `DetectorPort` direkt (als CDI-Bean)
  erfüllen.
- Detektoren bleiben **framework-frei** (sie erhalten `ScanUnit`, liefern
  `Finding`) — passend zur `domain/`-Reinheit. Das macht sie unabhängig von
  Quarkus testbar.
- Die `RemediableDetector`-Erweiterung (docs/07) wird analog als optionale
  Port-Fähigkeit modelliert.

Hinweis CDI vs. ServiceLoader: Built-in-Detektoren können als CDI-Beans (ArC)
registriert werden; extern nachgeladene Plugin-JARs nutzen den ServiceLoader-Pfad
im Adapter. Beide erfüllen denselben `DetectorPort`.

## 5. Web-UI: Angular-Feature statt separater SPA

Die Management-UI (docs/06) wird **nicht** als eigenständige Anwendung gebaut,
sondern als Angular-Feature im `webapp/`-Teil des Deployables:

- `webapp/src/app/features/` je Route (Dashboard, Repositories, Scans, Findings,
  Remediation, Admin); `webapp/src/app/core/` für Models (spiegeln REST-DTOs) und
  Services.
- BFF-Pattern: Die SPA spricht ausschliesslich mit dem eigenen Quarkus-Backend, nie direkt mit Drittsystemen oder dem Identity-Provider — die OIDC-Anbindung (docs/06 §5) ist damit automatisch blueprint-konform (Session-Cookies, kein Token im Browser).
- Tailwind 4 + ESLint-Konventionen des Templates gelten; die UI erbt das
  bestehende Lint-/Test-Gate (Vitest, Playwright).

## 6. Observability im Blueprint

- Health via `quarkus-smallrye-health` ist bereits vorhanden.
- Für Prometheus-Metriken (docs/06 §6) wird Micrometer-Quarkus
  (`quarkus-micrometer-registry-prometheus`) ergänzt — als Adapter hinter
  `MetricsPort`. Das ist eine der „erst hinzufügen, wenn ein Use Case sie braucht"-
  Erweiterungen, die der Blueprint explizit erlaubt.
- Grafana/Prometheus bleiben externe, optionale Begleitkomponenten (Core/CLI
  funktioniert ohne sie, docs/06 §6.4 / OR-09).

## 7. CI/CD & Build-Gate im Blueprint

Das Build-Gate aus docs/08 fügt sich nahtlos ein:

- Das Template liefert **GitHub Actions** (self-hosted ARC) und `./mvnw verify`
  als bestehendes Gate (JaCoCo, ArchUnit, ESLint). Der Secret-Scan wird als
  zusätzlicher Schritt bzw. eigener Job ergänzt.
- Der Exit-Code-Vertrag (docs/08 §7) gilt unverändert; für GitLab/TeamCity werden
  die jeweiligen Reports erzeugt. Wichtig: Der Scanner **scannt auch Apps, die aus
  demselben Template entstehen** — sein eigenes CI ist zugleich Referenz-Integration.
- Dependabot (Maven/npm/Actions) hält den Scanner ohne Handarbeit aktuell; jeder
  Update-PR durchläuft das Gate.

## 8. Was bewusst nicht übernommen wird

Der Blueprint folgt KISS: Kafka/Messaging, S3/Object-Storage, Scheduler und
Cucumber/BDD werden erst hinzugefügt, wenn ein echter Use Case sie braucht.
Für den Scanner heisst das konkret:

- **CLI** (`quarkus-picocli`) wird benötigt (CLI-/CI-Betriebsmodus, docs/01 §6) →
  als Blueprint-konformer Baustein ergänzen.
- **Scheduler** (`quarkus-scheduler`) wird benötigt (periodische/Nightly-Scans,
  docs/08 §2) → bei Bedarf gemäss Blueprint ergänzen.
- **S3/Kafka** zunächst nicht; falls später Report-Archivierung (S3) oder
  Event-Streaming der Findings gewünscht ist, blueprint-konform nachziehen.
- Die Template-Regel **„Verbesserungen zurück ins Template, Fachcode nie"** gilt:
  generische Scanner-Infrastruktur, die anderen Apps nützt, gehört ggf. ins
  Template; die Scanner-Fachlichkeit bleibt im Scanner-Repo.

## 9. Abgrenzung Doppelrolle

Der Scanner steht in zwei Beziehungen zum Template, die nicht verwechselt werden
dürfen:

1. **Er entsteht aus dem Template** (dieses Kapitel) — erbt Stack & Konventionen.
2. **Er scannt Apps, die aus dem Template entstehen** (docs/08) — als Build-Gate.

Beide nutzen dieselbe `./mvnw verify`-Kultur, sind aber getrennte Belange: (1) ist
Bauweise des Scanners, (2) ist sein Einsatzzweck.
