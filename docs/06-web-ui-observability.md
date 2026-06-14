# 06 — Web-UI & Observability (Grafana)

Erweitert das Konzept um eine **Management-Weboberfläche** zur vollständigen
Steuerung des Scanners sowie um eine **Metrik-/Dashboard-Schicht** auf Basis von
Prometheus + Grafana.

## 1. Zielsetzung

- Komplette Steuerung ohne CLI: Quellen, Detektoren, Scans, Baseline, Gate über
  eine Web-Oberfläche verwalten.
- Funde sichten, triagieren, unterdrücken und in Tickets überführen.
- Betriebs- und Sicherheitskennzahlen visuell über Grafana-Dashboards.

## 2. Erweitertes Schichtenmodell

Die Web-UI und die Observability-Schicht setzen auf dem bestehenden Server-Betrieb
(Quarkus) auf — der Scan-Core bleibt unverändert.

```
┌──────────────────────────────────────────────────────────────────┐
│  Presentation                                                      │
│  Web-UI (SPA)        │   CLI        │   CI/CD Step                 │
│  Angular (Quinoa) + REST/WebSocket                                │
└───────────────┬──────────────────────────────────────────────────┘
                │ HTTPS / REST + WebSocket
┌───────────────▼──────────────────────────────────────────────────┐
│  Application / API Layer  (Quarkus)                               │
│  REST-API · AuthN/AuthZ (OIDC/RBAC) · WebSocket (Live-Status)     │
│  Scan-Orchestrierung · Konfig-Verwaltung · Finding-Workflow       │
└───────┬───────────────────────────────────────┬──────────────────┘
        │                                       │
┌───────▼───────────────┐          ┌────────────▼─────────────────┐
│  Scan-Core (bestehend)│          │  Persistence                 │
│  Engine · Connectors  │          │  Konfig · Scans · Findings   │
│  Detector-Plugins     │          │  Baseline · Audit (DB)       │
└───────┬───────────────┘          └──────────────────────────────┘
        │ exportiert Metriken
┌───────▼──────────────────────────────────────────────────────────┐
│  Observability                                                     │
│  Micrometer → /q/metrics → Prometheus → Grafana         │
└──────────────────────────────────────────────────────────────────┘
```

## 3. Web-UI — Funktionsumfang

### 3.1 Dashboard (Einstieg)
Überblick: aktive/letzte Scans, offene Funde nach Severity, Trend, Gate-Status
je Repository. Eingebettete Grafana-Panels (siehe §6) für Zeitreihen.

### 3.2 Repository-Verwaltung
Quellen (Bitbucket/GitHub/GitLab/localGit) anlegen, bearbeiten, Verbindung testen.
Credentials werden ausschließlich als Referenz auf den Secret-Store gespeichert,
nie im Klartext eingegeben/angezeigt. Optional je Quelle: eine oder mehrere
**Report-E-Mail-Adressen**, an die nach einem Scan dieses Repos ein Report
(Zusammenfassung + redigierte Funde) gesendet wird (WR-08, IR-53; opt-in).

### 3.3 Scan-Steuerung
Scan manuell starten (Repo, Branch, Modus), laufende Scans live verfolgen
(WebSocket-Fortschritt), Verlauf einsehen, Scan abbrechen, periodische Scans planen.

### 3.4 Detektor- & Konfigurationsverwaltung
Detektoren aktivieren/deaktivieren, Parameter (Entropie-Schwelle, Custom-Regex,
PII-Muster) im UI pflegen. Validierung vor dem Speichern. Geladene Plugins werden
mit ID, Kategorie und Version angezeigt.

### 3.5 Finding-Workflow (Triage)
Funde filtern (Repo, Severity, Detektor, Status), Details mit redigiertem Treffer
und Code-Kontext, Aktionen: als Baseline akzeptieren, unterdrücken (mit Begründung),
als False Positive markieren, Ticket erzeugen, **Fix per PR/MR vorschlagen** und
**History bereinigen** (mit Dry-Run-Vorschau, Rotation-Checkliste und
Freigabe-Dialog; Details in docs/07-remediation.md).

**Code-Scanning-Ansicht (GitHub-Stil, WR-60..68).** Die Fundliste folgt im Aufbau dem
GitHub-„Code scanning"-Tab: Titel, Tool-Status-Banner, Query-Filterleiste mit
Offen/Geschlossen-Tabs und Facetten-Dropdowns, darunter die Ergebniszeilen.

```
Code scanning
┌───────────────────────────────────────────────────────────────────────────┐
│ ✓ Alle Detektoren laufen wie erwartet         🔧 Tools 6   + Detektor …    │  WR-60/61
└───────────────────────────────────────────────────────────────────────────┘
┌───────────────────────────────────────────────────────────────────────────┐
│ 🔍  is:open branch:main severity:high                                      │  WR-62
└───────────────────────────────────────────────────────────────────────────┘
 🛡 12 Offen   ✓ 4 Geschlossen        Sprache▾  Detektor▾  Regel▾  Severity▾  Sortieren▾   WR-63/64
─────────────────────────────────────────────────────────────────────────────
 ⚠  AWS Access Key  (HIGH)                                            [ main ]   WR-65
    #3 offen · vor 1 Minute · erkannt von secret.regex-ruleset in src/Config.java:21
 ⚠  Partnernummer im Code  (MEDIUM)                                   [ main ]
    #2 offen · vor 3 Minuten · erkannt von pii.customer-data-api in src/Kunde.java:14
─────────────────────────────────────────────────────────────────────────────
```

- **Banner** spiegelt den Detektor-Zustand (degradierte Detektoren ⇒ Warnung statt
  Haken, OR-05/NFR-07); „Tools N" = Anzahl aktiver Detektoren, „+ Detektor" verlinkt §3.4.
- **Query-Leiste** und Dropdowns sind synchron: ein Dropdown schreibt seinen Token in die
  Query (`severity:high`), eine getippte Query setzt die Dropdowns. Facetten-Optionen
  zeigen nur vorkommende Werte. Sortierung: Severity, zuletzt/zuerst gesehen.
- **Tabs** Offen/Geschlossen filtern nach Triage-Status (geschlossen = Baseline/Suppressed/FP).
- **Zeile**: Severity-Icon + Badge (farbcodiert, WR-40), Regeltitel, Metazeile
  `#N <status> <relative Zeit> · erkannt von <Detektor> in <Datei>:<Zeile>`, Branch-Badge;
  Klick öffnet die Details. Optional Checkbox-Mehrfachauswahl für Sammel-Triage (WR-67).
- Alles bleibt **redigiert** (WR-33/68) — kein Klartext in Treffer, Datei- oder Branch-Angabe.

### 3.6 Baseline- & Policy-Verwaltung
Baseline-Einträge einsehen/entfernen, zentrale Gate-/Policy-Vorgaben pro
Organisationseinheit pflegen (knüpft an FR-20 an).

### 3.7 Administration
Benutzer/Rollen (RBAC), Audit-Log-Ansicht, Integrationskonfiguration
(Jira/Chat/Webhooks), Systemstatus.

### 3.8 Darstellung & Usability
Durchgängig dunkles Erscheinungsbild im Stil von GitHub (Dark Mode) über alle
Ansichten — abgestimmte Hintergrund-/Flächen-/Text- und Akzentfarben sowie
konsistente Severity-Farben (WR-40). Eingabe-Bedienelemente zeigen eine Hover-Hilfe
(Tooltip) mit einem konkreten Eingabe-Beispiel, das das erwartete Format illustriert
(z. B. lokaler Pfad, Clone-URL, Token-Referenz `env:NAME`, Org-Unit) (WR-41). Umsetzung
über die Tailwind-Konventionen des Templates; keine zusätzliche UI-Bibliothek nötig.

**Mehrsprachigkeit (i18n, WR-70..73).** Alle sichtbaren Texte laufen über einen
zentralen, Signal-basierten Übersetzungsdienst (`I18nService` mit Schlüssel→Text-
Wörterbüchern für `en` und `de`); Komponenten geben Text über `t('key')` aus, kein
hartkodierter Anzeigetext (NFR-27). Ein Sprachumschalter im Kopfbereich wechselt die
Sprache zur Laufzeit ohne Neuladen und persistiert die Wahl in `localStorage` (NFR-28);
Default ist Englisch, eine fehlende Übersetzung fällt auf den Schlüssel/die Default-
Sprache zurück. Kein `@angular/localize`-Mehrfach-Build nötig — die Umschaltung ist
reaktiv über Signals.

### 3.9 Einstellungen
Admin-Ansicht für systemweite Einstellungen, die ohne Neustart änderbar sind (WR-15):

- **Allgemeine Benachrichtigungs-E-Mail** für systemweite Meldungen/Sammelreports
  (WR-16, IR-52/54).
- **Credential-/Secret-Referenzen** (z. B. `env:GITHUB_TOKEN`) verwalten — nur als
  Referenz, nie im Klartext; angezeigt wird, welche Referenzen erwartet/auflösbar
  sind (WR-17, WR-32). Hinweis: Environment-Variablen werden zum Prozessstart
  gelesen; die UI pflegt die Referenzen, nicht die OS-Variablen selbst.
- Nicht-geheime Betriebsparameter (Default-Gate-Severity, Aufbewahrungsfrist,
  Standard-Scan-Modus) mit Validierung (WR-18).

E-Mail-Versand erfolgt über einen SMTP-Adapter (IR-52); die SMTP-Verbindung und
Absender werden konfiguriert, Credentials nur als Secret-Referenz (NFR-08).

## 4. API & Echtzeit

- **REST-API** als alleinige Schnittstelle zwischen UI und Backend; dieselbe API
  ist extern nutzbar (Automatisierung).
- **WebSocket/SSE** für Live-Scan-Fortschritt und Status-Push.
- **OpenAPI-Spezifikation** wird generiert und versioniert (Vertrag UI ↔ Backend).

## 5. Sicherheit der Web-UI

| Aspekt | Umsetzung |
|--------|-----------|
| Authentifizierung | OIDC-Anbindung an Unternehmens-IdP (SSO, `quarkus-oidc`/BFF). SAML nur, falls der Blueprint es aufnimmt (docs/09, TR-13). |
| Autorisierung | RBAC: Rollen `Viewer`, `Operator`, `Admin` |
| Transport | TLS erzwungen |
| Credentials | nur Secret-Store-Referenzen, Eingabe maskiert, nie Rückgabe im Klartext |
| Treffer-Anzeige | serverseitig redigiert (Klartext verlässt nie das Backend) |
| Audit | jede steuernde Aktion (Scan-Start, Suppression, Konfig-Änderung) wird protokolliert |
| Schutz | CSRF-Schutz, Rate-Limiting, Session-Timeout |

### Rollenmatrix (Auszug)

| Aktion | Viewer | Operator | Admin |
|--------|:------:|:--------:|:-----:|
| Funde/Dashboards ansehen | ✓ | ✓ | ✓ |
| Scan starten/abbrechen | – | ✓ | ✓ |
| Funde triagieren/unterdrücken | – | ✓ | ✓ |
| Detektoren/Quellen konfigurieren | – | – | ✓ |
| Benutzer/Rollen/Policy verwalten | – | – | ✓ |

## 6. Observability mit Prometheus & Grafana

### 6.1 Metrik-Pipeline
Der Server instrumentiert via **Micrometer** und exponiert Metriken unter
`/q/metrics`. **Prometheus** scraped diesen Endpoint; **Grafana** nutzt
Prometheus als Datenquelle. Funde-/Verlaufsdaten für inhaltliche Panels kommen aus
der Anwendungsdatenbank (Grafana-SQL-Datenquelle) oder über dedizierte Gauge-Metriken.

### 6.2 Kernmetriken

| Metrik | Typ | Zweck |
|--------|-----|-------|
| `scanner_scans_total{repo,status}` | Counter | Anzahl Scans nach Ergebnis |
| `scanner_scan_duration_seconds{repo}` | Histogram | Scan-Dauer-Verteilung |
| `scanner_findings_total{repo,severity,detector}` | Gauge | Offene Funde |
| `scanner_findings_new_total{severity}` | Counter | Neue Funde (Delta gegen Baseline) |
| `scanner_gate_status{repo}` | Gauge | 0=pass / 1=fail |
| `scanner_detector_errors_total{detector}` | Counter | Detektor-Degradationen |
| `scanner_detector_duration_seconds{detector}` | Histogram | Detektor-Laufzeit |
| `scanner_repos_scanned` | Gauge | Abdeckung (gescannte Repos) |
| `scanner_verification_total{result}` | Counter | Verifikationsergebnisse |

### 6.3 Vorgeschlagene Dashboards

1. **Security Overview** – offene Funde nach Severity, neue Funde im Zeitverlauf,
   Top-Repos nach Risiko, Gate-Fail-Quote.
2. **Operations** – Scan-Durchsatz, Scan-Dauer (p50/p95), Worker-Auslastung,
   Fehler-/Degradationsrate.
3. **Detector Health** – Laufzeit und Fehlerrate je Detektor, False-Positive-Trend.
4. **Coverage & Compliance** – Anteil gescannter Repos, Repos mit aktiver Baseline,
   Zeit seit letztem Scan je Repo.

### 6.4 Einbettung in die Web-UI
Ausgewählte Grafana-Panels werden per Embed (Panel-iFrame mit signierten Tokens /
Grafana-Proxy) im UI-Dashboard angezeigt, sodass Nutzer eine integrierte Sicht
haben, ohne Grafana separat öffnen zu müssen. Vollständige Dashboards bleiben in
Grafana per Deep-Link erreichbar.

### 6.5 Alerting
Grafana-Alerts (oder Alertmanager) für: neuer CRITICAL-Fund, Gate-Fail in
Produktiv-Branch, erhöhte Detektor-Fehlerrate, ausbleibende Scans
(Repo seit > N Tagen nicht gescannt). Zustellung an Chat/E-Mail/Ticket.

## 7. Deployment-Sicht

```
┌─────────────┐   ┌──────────────┐   ┌─────────────┐   ┌───────────┐
│  Browser    │──►│  Web-UI/API  │──►│  Database   │   │ Grafana   │
│  (SPA)      │   │  (Quarkus)   │   │ (Postgres)  │◄──│           │
└─────────────┘   └──────┬───────┘   └─────────────┘   └─────┬─────┘
                         │ /q/metrics              │
                         ▼                                   │
                  ┌──────────────┐                           │
                  │ Prometheus   │◄──────────────────────────┘
                  └──────────────┘     (Datenquelle)
```

Bereitstellung als Container (UI/API, Prometheus, Grafana, DB) per
Docker-Compose für kleine Setups bzw. Kubernetes (Helm) für Enterprise-Betrieb.
Grafana/Prometheus sind optionale, aber empfohlene Begleitkomponenten; der Core
funktioniert auch ohne sie (CLI-Pfad bleibt unberührt).
