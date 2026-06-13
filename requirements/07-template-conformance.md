# 07 — Template-Konformität (TR)

Anforderungen, die sicherstellen, dass der Scanner aus dem Unternehmens-
`sourcecode-scanner` entsteht und dessen Blueprint einhält (siehe
docs/09-template-alignment.md). **Bei Konflikt gewinnt der Blueprint.**

## Herkunft & Scaffolding

| ID | Prio | Anforderung |
|----|------|-------------|
| TR-01 | M | Der Scanner MUSS aus dem `sourcecode-scanner` erzeugt werden („Use this template" + `scripts/init.sh`), nicht als Greenfield-Projekt. |
| TR-02 | M | Der Beispiel-Durchstich „Note" MUSS vollständig durch Scanner-Fachlichkeit ersetzt werden; das Init-Script wird danach entfernt. |
| TR-03 | S | Generische, wiederverwendbare Infrastruktur SOLL ggf. ins Template zurückfliessen; Scanner-Fachcode bleibt im Scanner-Repo. |

## Stack & Build (Blueprint-Vorgabe)

| ID | Prio | Anforderung |
|----|------|-------------|
| TR-10 | M | Das Backend MUSS Quarkus (Java 25, `quarkus-bom`) verwenden — nicht Spring Boot. |
| TR-11 | M | Die UI MUSS als Angular-Feature (standalone, Signals, strict TS, Tailwind) im selben Deployable über Quinoa gebaut werden (BFF, ein Build-Artefakt). |
| TR-12 | M | Persistenz MUSS Hibernate/Panache + PostgreSQL nutzen; Liquibase besitzt das Schema (append-only), Hibernate validiert nur. |
| TR-13 | M | Authentifizierung MUSS dem OIDC-BFF-Pattern folgen (Session-Cookies, kein Token im Browser; SPA spricht nur mit dem eigenen Backend). |
| TR-14 | M | Das Gate `./mvnw verify` (JaCoCo-Coverage, ArchUnit, ESLint, Tests) MUSS grün sein. |
| TR-15 | S | Versionen SOLLEN beim Aufsetzen auf die aktuelle LTS/stabile Version gehoben und im Blueprint dokumentiert werden. |

## Architektur-Invarianten (ArchUnit-erzwungen)

| ID | Prio | Anforderung |
|----|------|-------------|
| TR-20 | M | Die Abhängigkeitsregel `adapter → application → domain` MUSS gelten; ArchUnit bricht den Build bei Verstoss. |
| TR-21 | M | `domain/` MUSS framework-frei sein (keine Quarkus-/JPA-/Jackson-Imports); Domänen-Modelle sind reine `record`s mit Invarianten im Compact-Constructor. |
| TR-22 | M | Technische Belange (JGit, Plattform-REST-APIs, git-filter-repo/BFG, Prometheus-Export) MÜSSEN in Adaptern hinter `domain/port/out`-Interfaces liegen. |
| TR-23 | M | Repositories MÜSSEN Domänen-Modelle nehmen/liefern, nie JPA-Entities; REST-DTOs liegen nur in `adapter/in/rest/dto/`. |
| TR-24 | S | Detektoren SOLLEN framework-frei und ohne Quarkus testbar bleiben (erfüllen `DetectorPort`); externe Plugin-JARs werden im Detector-Adapter via ServiceLoader geladen. |

## Delivery & Aktualität

| ID | Prio | Anforderung |
|----|------|-------------|
| TR-30 | S | Delivery SOLL dem Template folgen: GitHub Actions, Container-Image (Podman), GitOps-ready. |
| TR-31 | S | Dependabot SOLL Maven-/npm-/Actions-Updates aktuell halten; jeder Update-PR durchläuft das Gate. |
| TR-32 | C | Zusätzliche Blueprint-Bausteine (Scheduler für Nightly-Scans, später S3/Kafka) KÖNNEN bei echtem Bedarf blueprint-konform ergänzt werden. |
