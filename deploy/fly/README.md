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
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | nur ghauth | GitHub OAuth App (Login via GitHub) |
| `GITHUB_ADMIN_LOGINS` | nur ghauth | Kommaseparierte GitHub-Logins → Rolle `admin`; alle anderen → `viewer` |
| `OIDC_AUTH_SERVER_URL` | nur server | OIDC-Issuer-URL (klassisches OIDC, z. B. Keycloak) |
| `OIDC_CLIENT_ID` | nur server | OIDC-Client (Default `sourcecode-scanner`) |
| `OIDC_CLIENT_SECRET` | nur server | OIDC-Client-Secret |
| `SCANNER_SECRETS_KEY` | empfohlen | AES-Schlüssel für DB-verschlüsselte Secrets (NFR-30). Ohne ihn ist der Modus „Key speichern" inaktiv; `secret:`-Referenzen lassen sich nicht auflösen. 32 Byte, Base64. |
| `DATASOURCE_HASH_PEPPER` | empfohlen | Pepper für Datenquellen-Hashes (NFR-23) |

Für den ersten Deploy im **staging**-Profil reichen die DB-Variablen + die empfohlenen Keys:

```bash
fly secrets set --app <deine-app> \
  SCANNER_SECRETS_KEY="$(openssl rand -base64 32)" \
  DATASOURCE_HASH_PEPPER="$(openssl rand -base64 24)"
```

Für **server** (produktiv) zusätzlich die `OIDC_*`-Secrets setzen und in `fly.toml`
`QUARKUS_PROFILE = "prod,server"`.

Repo-Zugriffs-Tokens (GitHub/GitLab/Bitbucket) werden **nicht** hier gesetzt, sondern in der UI als
verwaltete Secrets (DB-verschlüsselt) oder als `env:NAME`-Referenz. Für `env:`-Referenzen die
jeweilige Variable zusätzlich als Fly-Secret setzen.

## 4. Profil: staging / ghauth / server

Standardmässig deployt diese Konfiguration mit `QUARKUS_PROFILE=prod,staging` (in `fly.toml`):

- **staging:** OIDC aus, permissive HTTP-Policy, `StagingAllRolesAugmentor` vergibt alle Rollen. App
  **ohne Login** bedienbar — nur zum Hochfahren/DB-Test. **Keine Zugriffskontrolle; nicht produktiv.**
- **ghauth (empfohlen für privaten Betrieb):** **GitHub als IdP** (OAuth2). Login über GitHub; Rollen
  per Allowlist — siehe „GitHub-Login" unten. Profil in `fly.toml` auf `prod,ghauth`.
- **server:** klassisches OIDC (z. B. Keycloak), Rollen aus dem Token
  (`resource_access/<client>/roles`). Profil `prod,server` + `OIDC_*`-Secrets.

### GitHub-Login (ghauth)

1. **GitHub OAuth App** anlegen: GitHub → Settings → Developer settings → **OAuth Apps** → New.
   - Homepage URL: `https://<deine-app>.fly.dev`
   - **Authorization callback URL:** `https://<deine-app>.fly.dev/q/oidc/github` *(Quarkus-GitHub-
     Provider-Callback; bei „redirect_uri mismatch" den Wert aus der Fehlermeldung exakt übernehmen)*
   - Es entstehen **Client ID** und (unter „Generate a new client secret") **Client Secret**.
2. Secrets + Admin-Allowlist setzen und Profil umstellen:

```bash
fly secrets set --app <deine-app> \
  GITHUB_CLIENT_ID="<client-id>" \
  GITHUB_CLIENT_SECRET="<client-secret>" \
  GITHUB_ADMIN_LOGINS="fabianAschwanden"

# Profil in deploy/fly/fly.toml auf "prod,ghauth" setzen, dann deployen (oder Push auf main).
```

- `GITHUB_ADMIN_LOGINS` = kommaseparierte GitHub-Logins, die **admin** werden (case-insensitiv). Jeder
  andere erfolgreich per GitHub authentifizierte Nutzer erhält **viewer** (nur lesend).
- Möchtest du, dass **niemand ausser den Admins** rein darf, leite das später über eine zusätzliche
  Allowlist; aktuell ist „andere = viewer" gewählt.

## 5. Deploy

### Variante A — automatisch via GitHub Actions (Push auf `main`)

Der Workflow `.github/workflows/fly-deploy.yml` deployt bei jedem Push auf `main` (Fly baut das Image
remote). Einmalig nötig:

1. Deploy-Token erzeugen: `fly tokens create deploy -x 999999h` (oder im Dashboard).
2. Im GitHub-Repo unter **Settings → Secrets and variables → Actions** das Secret **`FLY_API_TOKEN`**
   mit diesem Wert anlegen.
3. App + `fly secrets` müssen vorab existieren (Schritte 1–3 oben, `fly launch --no-deploy` + DB-Secrets).

Danach genügt ein Push auf `main`; den Lauf siehst du im Actions-Tab. Manuell auslösbar über
„Run workflow" (`workflow_dispatch`).

### Variante B — manuell vom Rechner

```bash
fly deploy --config deploy/fly/fly.toml --dockerfile deploy/fly/Dockerfile
```

Health-Check läuft gegen `/q/health/ready` (ohne Auth erlaubt).

## Hinweise / Grenzen

- **Speicher:** JVM + `git clone` grosser Repos brauchen RAM; bei Bedarf `memory` in `fly.toml` auf
  2gb erhöhen.
- **Persistenz:** Klon-Arbeitsverzeichnisse liegen im ephemeren Container-FS und werden nach dem Scan
  aufgeräumt — kein Volume nötig. Der Zustand liegt in PostgreSQL.
- **Always-on:** Für zuverlässig laufende Hintergrund-Scans `min_machines_running = 1` setzen, sonst
  kann Fly die Maschine bei Inaktivität stoppen.
- **git-filter-repo:** History-Scrub bleibt Dry-Run-only, solange das Tool nicht im Image installiert
  ist (bewusst, RMR-28).
