# sourcecode-scanner

**Enterprise Source Code Security Scanner**: ein erweiterbarer Scanner zur
Erkennung von Secrets, Passwörtern und vertraulichen Daten (z. B. Kundendaten) in
Git-Repositories (Bitbucket, GitHub, GitLab) inkl. vollständiger History. Über eine
deklarative Konfiguration lassen sich weitere Prüfaspekte ohne Core-Eingriff
ergänzen (Plugin-Architektur).

Dieses Repo entsteht aus dem Unternehmens-`app-template` und setzt dessen Blueprint
([docs/blueprint.md](docs/blueprint.md)) als Code um: ein Deployable (BFF), Quarkus +
Angular via Quinoa, Hexagonal + DDD, erzwungen per ArchUnit. Die Ableitung aus dem
Template ist in [docs/09-template-alignment.md](docs/09-template-alignment.md)
beschrieben.

## Konzept & Spec

### Architektur-Dokumentation (`docs/`)

| Dokument | Inhalt |
|----------|--------|
| [docs/01-architecture.md](docs/01-architecture.md) | Architekturkonzept, Schichten, Komponenten, Diagramme |
| [docs/02-plugin-concept.md](docs/02-plugin-concept.md) | Detector-Plugin-Modell (SPI), Erweiterbarkeit |
| [docs/03-configuration.md](docs/03-configuration.md) | Konfigurationsformat, Beispiele, Referenz |
| [docs/04-prior-art.md](docs/04-prior-art.md) | Bewertung bestehender Tools als Grundlage |
| [docs/05-roadmap.md](docs/05-roadmap.md) | Umsetzungs-Roadmap in Phasen |
| [docs/06-web-ui-observability.md](docs/06-web-ui-observability.md) | Web-UI (Management) & Grafana-Observability |
| [docs/07-remediation.md](docs/07-remediation.md) | Auto-Fix per PR/MR & History-Bereinigung |
| [docs/08-cicd-build-integration.md](docs/08-cicd-build-integration.md) | CI/CD-Build-Gate (GitLab, TeamCity, …) wie ein Linter |
| [docs/09-template-alignment.md](docs/09-template-alignment.md) | Ableitung aus dem app-template (Blueprint-Bindung) |
| [docs/blueprint.md](docs/blueprint.md) | Der Blueprint, den dieses Repo umsetzt |

### Anforderungen (`requirements/`)

| Dokument | Inhalt |
|----------|--------|
| [requirements/00-overview.md](requirements/00-overview.md) | Requirements-Index und Notation (MoSCoW, RFC-2119) |
| [requirements/01-functional.md](requirements/01-functional.md) | Funktionale Anforderungen (FR) |
| [requirements/02-non-functional.md](requirements/02-non-functional.md) | Nicht-funktionale Anforderungen (NFR) |
| [requirements/03-detectors.md](requirements/03-detectors.md) | Detektor-Anforderungen (DR) |
| [requirements/04-integration.md](requirements/04-integration.md) | Integrations-/Plattform-Anforderungen (IR) |
| [requirements/05-web-ui-observability.md](requirements/05-web-ui-observability.md) | Web-UI- (WR) & Observability-Anforderungen (OR) |
| [requirements/06-remediation.md](requirements/06-remediation.md) | Remediation-Anforderungen (RMR) |
| [requirements/07-template-conformance.md](requirements/07-template-conformance.md) | Template-Konformität (TR): Blueprint-Bindung |

Der Abschnitt unten beschreibt das Template-Gerüst und den Entwicklungs-Workflow,
aus dem dieser Scanner entsteht (siehe docs/09).

## Stack

| | |
|---|---|
| Backend | Java 25, Quarkus (REST, Hibernate/Panache, Liquibase, OIDC) |
| Frontend | Angular (standalone, Signals), TypeScript strict, TailwindCSS 4 |
| Datenbank | PostgreSQL (Dev/Test automatisch via Dev Services) |
| Tests & Qualität | JUnit 5, Mockito, @QuarkusTest/REST-assured, ArchUnit, Vitest, Playwright, JaCoCo-Gate, ESLint |
| Delivery | GitHub Actions, Container-Image (Podman), GitOps-ready, Dependabot |

## Herkunft & Stand

Der Scanner wurde aus dem `app-template` erzeugt (GitHub „Use this template") und
wird gemäss [docs/09-template-alignment.md](docs/09-template-alignment.md)
umgesetzt:

1. Basis-Paket auf `ch.fabianaschwanden.sourcescanner` gesetzt (`./scripts/init.sh`, erledigt).
2. Beispiel-Durchstich **Note** durch die Scanner-Fachlichkeit ersetzen
   (Domäne → Ports → Application Service → REST → Liquibase → Angular-Feature);
   das Init-Script wird danach entfernt (TR-02).
3. Iterativ entlang der [Roadmap](docs/05-roadmap.md), Gate `./mvnw verify` grün halten.

## Entwickeln

```bash
./mvnw quarkus:dev        # Backend :8080, proxyt auf Angular-Dev-Server (:4200, startet automatisch)
```

- App: http://localhost:8080 · Swagger-UI: /q/swagger-ui · Health: /q/health
- PostgreSQL kommt über Dev Services — eine Container-Runtime (Podman/Docker) genügt.
- Auth ist in `%dev`/`%test` aus, in `%prod` an (OIDC BFF, Konfiguration via Env-Variablen).

```bash
./mvnw verify                  # Backend-Tests, ArchUnit, Coverage-Gate, Frontend-Build
cd webapp && npm run format:check  # Prettier-Gate (CI bricht sonst — vor dem Push prüfen!)
cd webapp && npm run lint      # ESLint (Frontend-Konventionen als Lint-Baseline)
cd webapp && npm test          # Frontend-Unit-Tests (Vitest)
cd webapp && npm run e2e       # Playwright gegen laufende Instanz (E2E_BASE_URL)
```

> Das Frontend-CI-Gate (`.github/workflows/ci.yml`) läuft `format:check` → `lint` → `test`.
> Einmalig pro Klon `git config core.hooksPath .githooks` setzen — dann prüft der Pre-Commit-Hook
> diese drei Schritte automatisch, sobald `webapp/`-Dateien gestaged sind (Umgehen: `--no-verify`).

## Struktur

```
src/main/java/.../domain/        # inneres Hexagon — framework-frei, records, Ports
src/main/java/.../application/   # Application Services — Use-Case-Orchestrierung, Transaktionsgrenze
src/main/java/.../adapter/       # in: REST, Security · out: Persistence (Panache)
src/main/resources/db/changelog/ # Liquibase — besitzt das Schema, append-only
webapp/src/app/core/             # models (spiegeln REST-DTOs) + services
webapp/src/app/features/         # UI-Komponenten je Route
docs/blueprint.md                # der Blueprint, dem dieser Scanner folgt
.claude/skills/                  # Claude-Skills (Engineering-Workflows + css-template)
```

Die Zielstruktur der Scanner-Pakete (`domain/model`, `port/in`, `port/out`,
`adapter/out/connector|detector|report|…`) ist in
[docs/09-template-alignment.md](docs/09-template-alignment.md) §3 beschrieben. Die
Abhängigkeitsregel `adapter → application → domain` und weitere Invarianten bricht
der Build (`HexagonalArchitectureTest`).

## Aktuell bleiben

- **Dependabot** hebt wöchentlich Maven-, npm- und Actions-Versionen (gruppiert);
  die CI verifiziert jeden Update-PR — der Scanner bleibt damit ohne Handarbeit aktuell.
- Template-Änderungen werden bei Bedarf gezielt nachgezogen (Cherry-Pick/Diff).
- Generische, wiederverwendbare Infrastruktur fliesst ggf. ins `app-template` zurück;
  Scanner-Fachcode bleibt hier (TR-03).

## Bewusst nicht enthalten

Kafka/Messaging, S3/Object-Storage, Scheduler, Cucumber/BDD-Runner sowie der Real-OIDC-Smoke-Test
(`quarkus-test-keycloak-server`): gemäss Blueprint (KISS) erst hinzufügen, wenn ein echter
Use Case sie braucht — die Konventionen dafür stehen im Blueprint.
