# Deploy auf Fly.io

Die App ist ein **zustandsbehafteter Quarkus-Server** (JVM, fast-jar) mit Angular-Frontend (via
Quinoa im selben Image gebaut), externer **PostgreSQL**, asynchronen Scans und SSE-Live-Fortschritt.
Fly.io passt dafür (persistenter Prozess, lange Verbindungen); ein serverloses Vercel/Functions-Modell
nicht.

## Voraussetzungen

- `flyctl` installiert und eingeloggt (`fly auth login`).
- Ein **Neon**-Projekt (serverless PostgreSQL). Liquibase migriert beim Start selbst
  (`%server.quarkus.liquibase.migrate-at-start=true`) — kein manuelles Schema nötig.
- Ein OIDC-Provider (siehe „Authentifizierung").

## 1. App anlegen

Wichtig: Der Docker-Build braucht den **Repo-Root** als Build-Kontext (Quinoa baut `webapp/`).
Vom Repo-Root aus deployen und auf diese Dateien zeigen:

```bash
fly launch --no-deploy \
  --dockerfile deploy/fly/Dockerfile \
  --config deploy/fly/fly.toml \
  --name <deine-app>
```

(`fly launch` ohne diese Flags würde ein eigenes Setup raten — die mitgelieferte `fly.toml` ist die
Referenz; App-Name darin anpassen.)

## 2. PostgreSQL (Neon)

1. In der Neon-Console ein Projekt anlegen (Region nahe der Fly-`primary_region`, z. B. EU → `fra`).
2. Den Connection-String der Datenbank kopieren. Neon zeigt etwas wie:
   `postgresql://<user>:<pass>@<endpoint>.<region>.aws.neon.tech/<db>?sslmode=require`
   - Den **gepoolten** Endpoint verwenden (Host enthält `-pooler`) — passt zu Neons Autosuspend und
     vielen kurzen Verbindungen.
3. Daraus die App-Variablen ableiten. Wichtig: diese App erwartet eine **JDBC-URL** in `DB_URL`,
   Benutzer/Passwort getrennt. Aus dem Neon-String wird:
   - `DB_URL = jdbc:postgresql://<endpoint-pooler>.<region>.aws.neon.tech/<db>?sslmode=require`
   - `DB_USERNAME = <user>`
   - `DB_PASSWORD = <pass>`

```bash
fly secrets set --app <deine-app> \
  DB_URL="jdbc:postgresql://<endpoint-pooler>.eu-central-1.aws.neon.tech/<db>?sslmode=require" \
  DB_USERNAME="<neon-user>" \
  DB_PASSWORD="<neon-pass>"
```

Hinweise:
- **`sslmode=require` ist Pflicht** — Neon akzeptiert nur TLS. Ohne den Parameter scheitert der
  Verbindungsaufbau.
- **Autosuspend:** Ein inaktiver Neon-Endpoint schläft ein und braucht beim ersten Zugriff ~1–2 s zum
  Aufwachen. Die App ist dafür konfiguriert (`%server`: `min-size=0`, `acquisition-timeout=10s`) — bei
  Bedarf via `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_SIZE` / `DB_ACQUISITION_TIMEOUT` justieren.
- Liquibase migriert beim ersten Start automatisch in die leere Neon-DB.

## 3. Secrets / Env-Vars

Alle aus dem `%server`-Profil referenzierten Variablen (siehe `application.properties`):

| Variable | Pflicht | Zweck |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | ja | Neon-PostgreSQL als JDBC-URL (`jdbc:postgresql://…?sslmode=require`) |
| `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_SIZE` / `DB_ACQUISITION_TIMEOUT` | nein | Pool-Tuning für Neon (Defaults 10 / 0 / 10s) |
| `OIDC_AUTH_SERVER_URL` | ja* | OIDC-Issuer-URL |
| `OIDC_CLIENT_ID` | ja* | OIDC-Client (Default `sourcecode-scanner`) |
| `OIDC_CLIENT_SECRET` | ja* | OIDC-Client-Secret |
| `SCANNER_SECRETS_KEY` | empfohlen | AES-Schlüssel für DB-verschlüsselte Secrets (NFR-30). Ohne ihn ist der Modus „Key speichern" inaktiv; `secret:`-Referenzen lassen sich nicht auflösen. 32 Byte, Base64. |
| `DATASOURCE_HASH_PEPPER` | empfohlen | Pepper für Datenquellen-Hashes (NFR-23) |

`*` Auth siehe unten — ohne OIDC startet das `%server`-Profil nicht sinnvoll.

```bash
fly secrets set --app <deine-app> \
  OIDC_AUTH_SERVER_URL="https://<issuer>/realms/<realm>" \
  OIDC_CLIENT_ID="sourcecode-scanner" \
  OIDC_CLIENT_SECRET="<secret>" \
  SCANNER_SECRETS_KEY="$(openssl rand -base64 32)" \
  DATASOURCE_HASH_PEPPER="$(openssl rand -base64 24)"
```

Repo-Zugriffs-Tokens (GitHub/GitLab/Bitbucket) werden **nicht** hier gesetzt, sondern in der UI als
verwaltete Secrets (DB-verschlüsselt) oder als `env:NAME`-Referenz. Für `env:`-Referenzen die
jeweilige Variable zusätzlich als Fly-Secret setzen.

## 4. Deploy

```bash
fly deploy --config deploy/fly/fly.toml --dockerfile deploy/fly/Dockerfile
```

Health-Check läuft gegen `/q/health/ready` (in `%server` ohne Auth erlaubt).

## Authentifizierung (OIDC)

Das `%server`-Profil schützt alle Pfade ausser `/q/health*` und erwartet einen OIDC-Provider
(z. B. Keycloak). Du brauchst also einen erreichbaren Issuer + Client. Optionen:

- **Mit OIDC:** Provider bereitstellen (eigenes Keycloak, Auth0, Entra ID …), Client anlegen,
  obige `OIDC_*`-Secrets setzen. Empfohlen für echten Betrieb.
- **Ohne OIDC (nur zum Ausprobieren):** ein eigenes Profil/Override nötig, das
  `quarkus.oidc.enabled=false` und eine permissive HTTP-Policy setzt. Das ist **nicht** für
  produktiven Einsatz gedacht (keine Zugriffskontrolle) — bei Bedarf richte ich ein separates
  `staging`-Profil ein.

## Hinweise / Grenzen

- **Speicher:** JVM + `git clone` grosser Repos brauchen RAM; bei Bedarf `memory` in `fly.toml` auf
  2gb erhöhen.
- **Persistenz:** Klon-Arbeitsverzeichnisse liegen im ephemeren Container-FS und werden nach dem Scan
  aufgeräumt — kein Volume nötig. Der Zustand liegt in PostgreSQL.
- **Always-on:** Für zuverlässig laufende Hintergrund-Scans `min_machines_running = 1` setzen, sonst
  kann Fly die Maschine bei Inaktivität stoppen.
- **git-filter-repo:** History-Scrub bleibt Dry-Run-only, solange das Tool nicht im Image installiert
  ist (bewusst, RMR-28).
