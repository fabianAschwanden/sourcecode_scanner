# 05 — Umsetzungs-Roadmap

Phasenweise Einführung, jede Phase liefert nutzbaren Mehrwert.

## Phase 1 — MVP / Tracer Bullet (Fundament)

Ziel: Ein lauffähiger End-to-End-Pfad über die schmalste sinnvolle Funktionalität.

- Core-Datenmodell (`ScanUnit`, `Finding`, `DetectorConfig`)
- Detector-SPI + ServiceLoader-Registry
- `LocalGitConnector` (JGit) — lokales Repo, Full-History-Walk
- Ein Detektor: `secret.regex-ruleset` mit Gitleaks-Basisregeln
- CLI (Picocli), YAML-Konfig-Parsing
- SARIF-Output
- Quality-Gate mit Exit-Code

**Ergebnis:** Lokales Repo scannen, Secrets finden, SARIF erzeugen, CI rot/grün.

## Phase 2 — Plattform-Connectoren & Rauschunterdrückung

- `BitbucketConnector`, `GitHubConnector`, `GitLabConnector` (Discovery + Auth)
- Inkrementeller Scan-Modus + Commit-Cache
- Baseline-Generierung und -Abgleich
- Pfad-Suppression + Inline-Annotationen
- Entropie-Detektor (`secret.high-entropy`)
- HTML-Report

**Ergebnis:** Org-weites Scannen, beherrschbares Rauschen, Brownfield-tauglich.

## Phase 3 — Erweiterte Detektoren & PII

- `pii.patterns` (IBAN, Kreditkarte, E-Mail, Telefon)
- `pii.custom-regex` (Kundendaten-Muster über Konfig)
- Optionale Verifikation (`verify()`) für ausgewählte Secret-Typen
- IaC-Detektor (Terraform/K8s/Dockerfile-Defaults)
- Detektor-Isolation: Timeouts, Fehler-Degradation, Regex-Backtracking-Schutz

**Ergebnis:** Abdeckung von Kundendaten/vertraulichen Infos, geringere FP-Rate.

## Phase 4 — Integration, Web-UI & Betrieb

- CI/CD-Templates (GitHub Actions, GitLab CI, TeamCity, Bitbucket Pipelines, Jenkins)
- Build-Gate mit verbindlichem Exit-Code-Vertrag (Linter-Semantik: kritischer Fund → Build stoppt)
- Diff-Modus als schnelles, blockierendes Build-Feedback; `softFail` für Rollout
- Build-native Reports/Annotationen (GitLab Secret-Detection, TeamCity Service Messages, SARIF)
- PR-/MR-Kommentare mit Inline-Findings
- Ticket-Integration (Jira) + Chat-Benachrichtigung (Teams/Slack)
- Server-Betrieb (Quarkus) mit Webhook-getriggerten Scans
- Persistenz (Postgres) für Konfig, Scans, Findings, Baseline, Audit
- REST-API + OpenAPI-Vertrag als alleinige UI-Schnittstelle
- **Web-UI (Angular-Feature, Quinoa):** Repository-/Scan-/Detektor-Verwaltung, Live-Scan-Status,
  Finding-Triage, Baseline-Pflege
- **Observability:** Micrometer-Metriken → Prometheus → Grafana, Basis-Dashboards
- AuthN/AuthZ über Unternehmens-IdP (OIDC/SAML, RBAC), Audit-Log, Vault-Anbindung

**Ergebnis:** Eingebettet in den Entwicklungs-Workflow, vollständig per Web
steuerbar, betriebsreif mit Dashboards.

## Phase 5 — Skalierung & Governance

- Horizontale Verteilung (mehrere Repos auf Worker-Knoten)
- Erweiterte Grafana-Dashboards (Detector Health, Coverage/Compliance) + Alerting
- Eingebettete Grafana-Panels im UI-Dashboard (signierte Tokens/Proxy)
- Policy-Management (zentrale Regel-/Gate-Vorgaben pro Organisationseinheit) über UI
- Plugin-Marketplace-Mechanik (signierte Detektor-JARs)

**Ergebnis:** Enterprise-weiter Rollout mit zentraler Steuerung und Observability.

## Phase 6 — Remediation (Auto-Fix & History-Bereinigung)

Sicherheitskritisch, daher bewusst als letzte Phase nach stabilem Betrieb.
**Standardmäßig deaktiviert, opt-in pro Repo.**

- Rotation-First-Workflow + Rotation-Gate (blockt Scrub bei aktivem Secret)
- `RemediableDetector`-SPI: Fix-Vorschläge als Plugin-Fähigkeit
- Auto-Fix per PR/MR (Strategien: externalize, gitignore, annotate, remove-line, redact)
- Nur PR/MR-Flow, kein Direktschreiben auf geschützte Branches; redigierte PRs
- History-Bereinigung über git-filter-repo (Default) / BFG auf frischem Mirror
- Pflicht-Dry-Run, Backup, Re-Scan-Verifikation, Vier-Augen-Force-Push-Freigabe
- RBAC: Scrub-Freigabe nur Admin/Break-Glass; getrennte privilegierte Token-Scopes
- Re-Sync-Anleitung + Restrisiko-Hinweis (Forks/Caches), `.gitignore`/Hook-Nachsorge
- Remediation-Metriken & Trend offen-vs-behoben

**Ergebnis:** Geschlossener Kreislauf von Erkennung bis Behebung — kontrolliert,
auditierbar und mit korrekter Reihenfolge (Rotation vor Bereinigung).

## Risiken & Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|--------|---------------|
| Hohe False-Positive-Rate → Akzeptanzverlust | Baseline, Suppression, opt. Verifikation ab Phase 2/3 |
| Performance bei großen Historien | Inkrementell + Commit-Cache + Shallow-Clone |
| Fehlerhaftes Plugin bricht Scan | Isolation, Timeouts, Degradation (Phase 3) |
| Credentials des Scanners selbst leaken | Secret-Store, Redaktion, Least-Privilege-Tokens |
| Lizenzkonflikte externer Regelsätze | Lizenzprüfung vor Einbindung (siehe 04-prior-art) |
| Web-UI als neue Angriffsfläche | SSO/OIDC, RBAC, TLS, CSRF-Schutz, serverseitige Redaktion |
| Klartext-Secrets über UI/API exponiert | Treffer nur redigiert ausliefern, Credentials nur als Store-Referenz |
| Grafana/Prometheus als harte Abhängigkeit | Observability optional; Core/CLI bleibt ohne sie lauffähig |
| History-Rewrite zerstört Historie / divergierende Klone | Mirror-Klon, Backup, Pflicht-Dry-Run, Vier-Augen-Force-Push, Re-Sync-Anleitung |
| Trügerische Sicherheit nach Scrub (Forks/Caches) | Rotation-First-Prinzip + Rotation-Gate; Restrisiko-Hinweis in UI |
| Fehlerhafter Auto-Fix führt Bug ein | Nur mechanisch sichere Transformationen, PR-Review-Pflicht, Confidence-Schwelle |
| Missbrauch der Force-Push-Berechtigung | RBAC Admin/Break-Glass, getrennte Token-Scopes, vollständiges Audit |
| Langsames Build-Gate wird umgangen/abgeschaltet | Diff-Modus, Commit-Cache, Detektor-Timeouts, harte Job-Timeout-Grenze |
| Build-Gate bricht Brownfield-Repos sofort rot | `failOnNewOnly` + Baseline, `softFail` für Einführungsphase |
