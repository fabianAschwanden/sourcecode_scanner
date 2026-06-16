# 03 — Konfiguration

## 1. Designziele

- **Deklarativ:** Ein YAML beschreibt Quellen, Detektoren, Gate und Output.
- **Erweiterbar:** Neue Detektoren werden über einen Konfig-Block aktiviert.
- **Mehrstufig:** Globale Defaults, pro-Repository-Overrides, Inline-Suppressions.
- **Sicher:** Keine Klartext-Credentials; Referenzen auf Secret-Store/Env.

## 2. Vollständiges Beispiel

```yaml
version: 1

# ---- Quellsysteme -------------------------------------------------
scan:
  repositories:
    - type: bitbucket
      baseUrl: https://bitbucket.company.com
      project: TEAM
      repo: payment-service
      branches: [main, develop]
      auth: { tokenRef: env:BITBUCKET_TOKEN }

    - type: github
      org: my-org
      includeArchived: false
      repoFilter: '^(svc|lib)-.*'        # Regex über Repo-Namen
      auth: { tokenRef: vault:secret/github#token }

    - type: gitlab
      baseUrl: https://gitlab.company.com
      group: platform
      auth: { tokenRef: env:GITLAB_TOKEN }

  # ---- Scan-Verhalten --------------------------------------------
  history:
    mode: full            # full | incremental | sinceCommit | diff  (pr = Alias für diff)
    sinceCommit: null     # nur bei mode=sinceCommit
  concurrency:
    workers: 8
    detectorTimeoutSeconds: 30

  # ---- Detektoren -------------------------------------------------
  detectors:
    secrets:
      enabled: true
      ruleset: gitleaks-default
      verify: false              # aktive Gültigkeitsprüfung
      entropy:
        enabled: true
        threshold: 4.5
        minLength: 20
    pii:
      enabled: true
      patterns: [iban, creditcard, email, phone]
      luhnCheck: true                    # Kreditkarten gegen FP per Luhn validieren (DR-22)
      allowlistFile: config/pii-allowlist.yaml   # bekannte Beispiel-/Test-PII ignorieren (DR-23)
      customRegex:
        - name: customer-id
          pattern: 'CUST-\d{8}'
          severity: HIGH
    license:
      enabled: false
    iac:
      enabled: true
      targets: [terraform, kubernetes, dockerfile]

  # ---- Rauschunterdrückung ---------------------------------------
  baseline: .scanner-baseline.json
  suppress:
    - path: 'test/**/fixtures/*'
      detector: secrets
      reason: 'Bekannte Test-Dummies'
    - path: '**/*.md'
      detector: pii          # Gruppe oder Detektor-ID (z. B. pii.patterns)

  # ---- Quality-Gate ----------------------------------------------
  gate:
    failOn: HIGH          # INFO | LOW | MEDIUM | HIGH | CRITICAL
    failOnNewOnly: true   # nur neue (nicht-baseline) Funde brechen

  # ---- Ausgabe ----------------------------------------------------
  output:
    formats: [sarif, html, json]
    directory: ./scan-reports
    redact: true

  # ---- Integrationen ---------------------------------------------
  integrations:
    prComments: true
    jira:
      enabled: false
      projectKey: SEC
    chat:
      webhookRef: env:TEAMS_WEBHOOK
      notifyOn: [HIGH, CRITICAL]
```

> **Suppression-Granularität.** `detector:` referenziert entweder eine
> Detektor-Gruppe (`secrets`, `pii`, `license`, `iac`) oder eine konkrete
> Detektor-ID (`secret.regex-ruleset`, `pii.patterns`, …). Einzelne PII-Muster
> (z. B. nur E-Mail) sind **kein** eigener Detektor; sie werden über die
> Muster-/Pattern-Konfiguration des jeweiligen Detektors gesteuert.

## 3. Feldreferenz (Auszug)

| Pfad | Typ | Beschreibung |
|------|-----|--------------|
| `scan.repositories[].type` | enum | `bitbucket` \| `github` \| `gitlab` \| `localGit` |
| `scan.repositories[].auth.tokenRef` | string | Referenz auf Secret: `env:NAME`, `vault:path#key` |
| `scan.history.mode` | enum | Scan-Tiefe (siehe Architektur §6) |
| `scan.detectors.*.enabled` | bool | Detektor-Gruppe aktivieren |
| `scan.detectors.secrets.verify` | bool | Aktive Validierung gefundener Secrets |
| `scan.detectors.pii.luhnCheck` | bool | Kreditkartentreffer per Luhn-Prüfung gegen False Positives validieren |
| `scan.detectors.pii.allowlistFile` | path | Datei mit bekannten Beispiel-/Test-PII (IBAN/Kreditkarte), die ignoriert werden |
| `scan.baseline` | path | Datei mit akzeptierten Altfunden |
| `scan.gate.failOn` | enum | Mindest-Severity, die CI rot macht |
| `scan.gate.failOnNewOnly` | bool | Nur Delta gegen Baseline bewerten |
| `scan.output.redact` | bool | Treffer in Ausgaben maskieren |

## 4. Inline-Suppression im Code

Für gezielte Ausnahmen direkt an der Fundstelle:

```java
String example = "AKIA_NOT_A_REAL_KEY"; // scanner:ignore-secret reason="docs example"
```

Unterstützte Direktiven: `scanner:ignore-secret`, `scanner:ignore-pii`,
`scanner:ignore-line`, `scanner:ignore-next-line`. Eine Begründung (`reason=...`)
kann erzwungen werden (`gate.requireSuppressionReason: true`).

## 5. Baseline-Datei (Konzept)

```json
{
  "version": 1,
  "generatedAt": "2026-06-13T10:00:00Z",
  "entries": [
    {
      "fingerprint": "secret.aws-access-key:src/Config.java:a1b2c3",
      "acceptedBy": "security-team",
      "acceptedAt": "2026-06-10",
      "reason": "Rotierter Key, Issue SEC-123"
    }
  ]
}
```

Die Baseline wird beim Erstscan generiert und versioniert eingecheckt. Nur Funde,
deren Fingerprint **nicht** in der Baseline steht, lösen bei `failOnNewOnly: true`
das Gate aus.

## 5a. PII-Allowlist (bekannte Test-Werte)

Beispiel-/Test-IBANs und -Kreditkarten sind absichtlich strukturell gültig
(Mod-97 bzw. Luhn) und damit hartnäckige False Positives. Die unter
`detectors.pii.allowlistFile` referenzierte Datei listet solche Werte; ein Treffer
darauf wird als **False Positive (Allowlist)** markiert statt als Finding gemeldet.

```yaml
# config/pii-allowlist.yaml
version: 1
iban:
  - value: "CH93 0076 2011 6238 5295 7"
    note: "Schweiz – kanonisches Beispiel"
creditCard:
  - value: "4111 1111 1111 1111"
    note: "Visa – Test-PAN"
```

Der Abgleich erfolgt auf **normalisierter Form** (Whitespace/Bindestriche entfernt,
IBAN-Buchstaben groß), damit unterschiedliche Formatierungen denselben Wert treffen.

## 6. Konfigurations-Auflösungsreihenfolge

```
1. Eingebaute Defaults
2. Globale Konfigurationsdatei (scanner.yaml)
3. Pro-Repository-Overrides (.scanner.yaml im Repo)
4. CLI-Flags / Umgebungsvariablen
5. Inline-Suppressions im Code
```

Spätere Stufen überschreiben frühere.

## 7. Server-, Web-UI- & Observability-Konfiguration

Bei Betrieb als Service (statt CLI) ergänzt ein `server`-Block die Konfiguration.
Der Scan-Teil (`scan:`) bleibt unverändert gültig.

```yaml
server:
  http:
    port: 8443
    tls: { enabled: true, certRef: vault:secret/scanner-tls#cert }

  # ---- Authentifizierung / Autorisierung -------------------------
  auth:
    provider: oidc
    issuerUrl: https://idp.company.com
    clientIdRef: env:OIDC_CLIENT_ID
    clientSecretRef: vault:secret/scanner-oidc#secret
    roleMapping:
      admin:    [scanner-admins]      # IdP-Gruppen → Rolle
      operator: [scanner-operators]
      viewer:   [scanner-users]

  # ---- Persistenz ------------------------------------------------
  persistence:
    type: postgres
    urlRef: env:DB_URL
    credentialsRef: vault:secret/scanner-db

  # ---- Web-UI ----------------------------------------------------
  ui:
    enabled: true
    sessionTimeoutMinutes: 30
    embedGrafana: true

  # ---- Observability --------------------------------------------
  observability:
    metrics:
      enabled: true
      endpoint: /q/metrics
    grafana:
      baseUrl: https://grafana.company.com
      embed:
        mode: signed            # signed | proxy
        dashboards:
          securityOverview: uid-sec-overview
          operations:        uid-ops
    alerts:
      newCritical: true
      gateFailOnProtectedBranch: true
      staleRepoDays: 30
```

### Feldreferenz (Auszug)

| Pfad | Typ | Beschreibung |
|------|-----|--------------|
| `server.auth.provider` | enum | `oidc` (Blueprint-Default, `quarkus-oidc`/BFF). `saml` nur, falls der Blueprint später SAML aufnimmt — bei Konflikt gewinnt der Blueprint (docs/09, TR-13). |
| `server.auth.roleMapping` | map | IdP-Gruppen → Rollen (`admin`/`operator`/`viewer`) |
| `server.ui.embedGrafana` | bool | Grafana-Panels im UI-Dashboard einbetten |
| `server.observability.metrics.endpoint` | path | Prometheus-Scrape-Endpoint |
| `server.observability.grafana.embed.mode` | enum | `signed` (Token) \| `proxy` |
| `server.observability.alerts.staleRepoDays` | int | Alarm, wenn Repo länger nicht gescannt |

## 8. Remediation-Konfiguration (Auto-Fix & History-Scrub)

Beide Funktionen sind standardmäßig **deaktiviert** und müssen explizit pro Repo
freigegeben werden (siehe docs/07-remediation.md).

```yaml
remediation:
  # ---- Auto-Fix per Pull/Merge Request ---------------------------
  autoFix:
    enabled: false               # global aus; pro Repo aktivierbar
    mode: proposal               # proposal | auto    (auto nur bei confidence HIGH)
    branchPrefix: fix/scanner-
    targetBranch: null           # null = Default-Branch des Repos
    reviewers: [security-team]    # CODEOWNERS-Override
    labels: [security, secret-remediation]
    strategies:                  # erlaubte Fix-Strategien
      - externalize
      - gitignore
      - annotate
    requireHumanReviewOn: [removeLine, redact]

  # ---- History-Bereinigung (Rewrite) -----------------------------
  historyScrub:
    enabled: false
    engine: git-filter-repo      # git-filter-repo | bfg
    dryRunRequired: true         # Pflicht vor jedem realen Rewrite
    fourEyesApproval: true       # Vier-Augen-Freigabe für Force-Push
    rotationGate: true           # blockt Scrub bei nicht-rotiertem aktivem Secret
    backup:
      enabled: true
      retentionDays: 30          # Mirror-Backup-Aufbewahrung
    forcePush:
      allowed: false             # muss bewusst gesetzt werden
      strategy: force-with-lease
    postScrub:
      addGitignore: true
      installPreCommitHook: true

  # ---- Berechtigungen (knüpft an RBAC der Web-UI an) -------------
  authorization:
    autoFixRoles: [operator, admin]
    scrubStartRoles: [operator, admin]
    scrubApproveRoles: [admin]   # Force-Push-Freigabe nur Admin/Break-Glass
```

### Feldreferenz (Auszug)

| Pfad | Typ | Beschreibung |
|------|-----|--------------|
| `remediation.autoFix.mode` | enum | `proposal` (immer Review) \| `auto` (nur bei hoher Confidence) |
| `remediation.autoFix.strategies` | list | Zugelassene Fix-Strategien |
| `remediation.historyScrub.engine` | enum | `git-filter-repo` (Default) \| `bfg` |
| `remediation.historyScrub.dryRunRequired` | bool | Erzwingt Vorschau vor Rewrite |
| `remediation.historyScrub.rotationGate` | bool | Blockt Scrub, bis aktives Secret rotiert ist |
| `remediation.historyScrub.forcePush.allowed` | bool | Muss bewusst aktiviert werden |
| `remediation.authorization.scrubApproveRoles` | list | Rollen, die Force-Push freigeben dürfen |

## 9. Build-Gate-Konfiguration (CI/CD)

Erweitert den `gate:`-Block um Linter-Semantik für den Build-Einsatz (Details in
docs/08-cicd-build-integration.md).

```yaml
scan:
  gate:
    failOn: HIGH            # ab dieser Severity bricht der Build (Exit 1)
    failOnNewOnly: true     # nur neue (nicht-Baseline) Funde blocken
    warnThreshold: MEDIUM   # darunter nur Warnung, kein Abbruch
    softFail: false         # true = nie abbrechen, nur reporten (Einführungsphase)

  output:
    formats: [sarif, gitlab, teamcity]   # build-native Reports zusätzlich zu SARIF
    reportBack:
      enabled: false                      # optional Ergebnisse an zentralen Server
      serverUrlRef: env:SCANNER_SERVER_URL
      tokenRef: env:SCANNER_REPORT_TOKEN
```

### Feldreferenz (Auszug)

| Pfad | Typ | Beschreibung |
|------|-----|--------------|
| `scan.gate.warnThreshold` | enum | Severity, ab der gewarnt (nicht geblockt) wird |
| `scan.gate.softFail` | bool | Funde melden ohne Build-Abbruch (Rollout-Phase) |
| `scan.output.formats` | list | `sarif` \| `gitlab` \| `teamcity` \| `html` \| `json` |
| `scan.output.reportBack.enabled` | bool | Build-Ergebnisse zusätzlich an Server pushen |

### Exit-Code-Vertrag

| Exit | Bedeutung |
|------|-----------|
| 0 | Kein blockierender Fund |
| 1 | Gate verletzt (blockierende Funde) |
| 2 | Konfigurations-/Laufzeitfehler |
| 3 | `softFail`: Funde vorhanden, nicht blockierend |
