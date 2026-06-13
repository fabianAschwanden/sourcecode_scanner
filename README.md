# app-template

Lauffähiges Gerüst für neue Applikationen — **ohne fachlichen Inhalt**. Setzt den
Unternehmens-Blueprint ([docs/blueprint.md](docs/blueprint.md)) als Code um:
ein Deployable (BFF), Quarkus + Angular via Quinoa, Hexagonal + DDD, erzwungen per ArchUnit.

## Stack

| | |
|---|---|
| Backend | Java 25, Quarkus (REST, Hibernate/Panache, Liquibase, OIDC) |
| Frontend | Angular (standalone, Signals), TypeScript strict, TailwindCSS 4 |
| Datenbank | PostgreSQL (Dev/Test automatisch via Dev Services) |
| Tests & Qualität | JUnit 5, Mockito, @QuarkusTest/REST-assured, ArchUnit, Vitest, Playwright, JaCoCo-Gate, ESLint |
| Delivery | GitHub Actions, Container-Image (Podman), GitOps-ready, Dependabot |

## Neue App erstellen

1. Auf GitHub **„Use this template"** klicken (Repo ist als Template-Repository markiert —
   falls noch nicht: Settings → General → „Template repository" aktivieren).
2. Im neuen Repo App-Namen und Basis-Paket setzen:
   ```bash
   ./scripts/init.sh meine-app ch.meinefirma.meineapp
   ```
3. Beispiel-Durchstich **Note** durch echte Fachlichkeit ersetzen (Suche nach `Note`).
   Er existiert nur als Referenz: Domäne → Ports → Application Service → REST → Liquibase → Angular-Feature.
4. `./mvnw verify` und loslegen.

## Entwickeln

```bash
./mvnw quarkus:dev        # Backend :8080, proxyt auf Angular-Dev-Server (:4200, startet automatisch)
```

- App: http://localhost:8080 · Swagger-UI: /q/swagger-ui · Health: /q/health
- PostgreSQL kommt über Dev Services — eine Container-Runtime (Podman/Docker) genügt.
- Auth ist in `%dev`/`%test` aus, in `%prod` an (OIDC BFF, Konfiguration via Env-Variablen).

```bash
./mvnw verify                  # Backend-Tests, ArchUnit, Coverage-Gate, Frontend-Build
cd webapp && npm test          # Frontend-Unit-Tests (Vitest)
cd webapp && npm run lint      # ESLint (Frontend-Konventionen als Lint-Baseline)
cd webapp && npm run e2e       # Playwright gegen laufende Instanz (E2E_BASE_URL)
```

## Struktur

```
src/main/java/.../domain/        # inneres Hexagon — framework-frei, records, Ports
src/main/java/.../application/   # Application Services — Use-Case-Orchestrierung, Transaktionsgrenze
src/main/java/.../adapter/       # in: REST, Security · out: Persistence (Panache)
src/main/resources/db/changelog/ # Liquibase — besitzt das Schema, append-only
webapp/src/app/core/             # models (spiegeln REST-DTOs) + services
webapp/src/app/features/         # UI-Komponenten je Route
docs/blueprint.md                # der Blueprint, den dieses Template umsetzt
.claude/skills/                  # Claude-Skills (Engineering-Workflows + css-template)
```

Die Abhängigkeitsregel `adapter → application → domain` und weitere Invarianten bricht
der Build (`HexagonalArchitectureTest`).

## Aktuell bleiben

- **Dependabot** hebt wöchentlich Maven-, npm- und Actions-Versionen (gruppiert);
  die CI verifiziert jeden Update-PR — das Template bleibt damit ohne Handarbeit aktuell.
- Verbesserungen aus laufenden Projekten gehören hierher zurück, **fachlicher Code nie**.
- Abgeleitete Apps ziehen Template-Änderungen bei Bedarf gezielt nach (Cherry-Pick/Diff).

## Bewusst nicht enthalten

Kafka/Messaging, S3/Object-Storage, Scheduler, Cucumber/BDD-Runner sowie der Real-OIDC-Smoke-Test
(`quarkus-test-keycloak-server`): gemäss Blueprint (KISS) erst hinzufügen, wenn ein echter
Use Case sie braucht — die Konventionen dafür stehen im Blueprint.
