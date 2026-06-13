# 05 — Web-UI- & Observability-Anforderungen (WR / OR)

Anforderungen an die Management-Weboberfläche (`WR-`) und die Metrik-/Dashboard-
Schicht (`OR-`). Umsetzung primär in Roadmap-Phase 4–5.

## Web-UI (WR)

### Steuerung & Verwaltung

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-01 | S | Die Web-UI SOLL die vollständige Steuerung ohne CLI ermöglichen (Quellen, Detektoren, Scans, Baseline, Gate). |
| WR-02 | S | Die UI SOLL Repository-Quellen anlegen, bearbeiten, löschen und die Verbindung testen können. |
| WR-03 | S | Die UI SOLL Scans manuell starten (Repo, Branch, Modus) und abbrechen können. |
| WR-04 | S | Die UI SOLL laufende Scans live mit Fortschritt anzeigen (WebSocket/SSE). |
| WR-05 | C | Die UI KANN periodische/geplante Scans konfigurieren. |
| WR-06 | S | Die UI SOLL Detektoren aktivieren/deaktivieren und deren Parameter pflegen, mit Validierung vor dem Speichern. |
| WR-07 | C | Die UI KANN geladene Plugins mit ID, Kategorie und Version anzeigen. |
| WR-08 | S | Die UI SOLL beim Anlegen/Bearbeiten einer Repository-Quelle optional eine oder mehrere Report-E-Mail-Adressen erfassen, an die nach Scans dieses Repos ein Report versendet wird (IR-53). |

### Einstellungen (Administration)

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-15 | S | Die UI SOLL eine Einstellungs-Ansicht bereitstellen, in der allgemeine Systemeinstellungen ohne Neustart geändert werden können (nur Rolle Admin, WR-31). |
| WR-16 | S | Die Einstellungen SOLLEN eine allgemeine Benachrichtigungs-E-Mail-Adresse umfassen, an die systemweite Meldungen (z. B. Betriebs-/Sammelreports) gesendet werden (IR-52). |
| WR-17 | S | Die UI SOLL Credential-/Secret-Referenzen (z. B. Environment-Variablen wie `env:GITHUB_TOKEN`) als verwaltbare Einträge anzeigen und pflegen können — ausschliesslich als Referenz, nie als Klartext (WR-32). |
| WR-18 | C | Die UI KANN nicht-geheime Betriebsparameter pflegen (z. B. Standard-Gate-Severity, Aufbewahrungsfrist, Standard-Scan-Modus), mit Validierung vor dem Speichern. |

### Finding-Workflow

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-10 | S | Die UI SOLL Funde filter- und sortierbar darstellen (Repo, Severity, Detektor, Status). |
| WR-11 | S | Die UI SOLL Funddetails mit redigiertem Treffer und Code-Kontext anzeigen. |
| WR-12 | S | Die UI SOLL Funde triagieren: als Baseline akzeptieren, unterdrücken (mit Pflichtbegründung), als False Positive markieren. |
| WR-13 | C | Die UI KANN aus einem Fund ein Ticket (Jira) erzeugen. |
| WR-14 | C | Die UI KANN Baseline-Einträge einsehen und entfernen. |

### Darstellung & Usability

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-40 | S | Die UI SOLL durchgängig in einem dunklen Erscheinungsbild im Stil von GitHub (Dark Mode) gestaltet sein (Hintergründe, Flächen, Text, Akzent- und Severity-Farben), konsistent über alle Ansichten. |
| WR-41 | S | Eingabe-Bedienelemente (Felder, Auswahllisten) SOLLEN eine Hover-Hilfe (Tooltip) mit einem konkreten Eingabe-Beispiel anzeigen (z. B. Pfad `/Users/me/git/projekt`, Clone-URL `https://github.com/org/repo.git`, Token-Referenz `env:GITHUB_TOKEN`, Org-Unit `team-a/payments`). |

### Schnittstelle

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-20 | M | Die UI MUSS ausschließlich über eine dokumentierte REST-API mit dem Backend kommunizieren. |
| WR-21 | C | Die REST-API KANN per OpenAPI-Spezifikation versioniert und extern nutzbar sein. |

### Sicherheit

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-30 | M | Die UI MUSS Authentifizierung über den Unternehmens-IdP erzwingen (OIDC/SSO gemäß Blueprint; SAML nur, falls der Blueprint es aufnimmt — TR-13). |
| WR-31 | M | Die UI MUSS rollenbasierte Autorisierung (Viewer/Operator/Admin) durchsetzen. |
| WR-32 | M | Credentials DÜRFEN in der UI nur als Secret-Store-Referenz eingegeben werden; Klartext wird nie zurückgegeben. |
| WR-33 | M | Treffer MÜSSEN serverseitig redigiert werden; Klartext-Secrets verlassen das Backend nie. |
| WR-34 | S | Jede steuernde Aktion (Scan-Start, Suppression, Konfig-Änderung) SOLL auditierbar protokolliert werden. |
| WR-35 | S | Die UI SOLL Transport-Verschlüsselung (TLS), CSRF-Schutz und Session-Timeout durchsetzen. |

## Observability / Grafana (OR)

| ID | Prio | Anforderung |
|----|------|-------------|
| OR-01 | S | Das System SOLL Metriken im Prometheus-Format über einen HTTP-Endpoint exponieren (Micrometer). |
| OR-02 | S | Das System SOLL Scan-Metriken liefern: Anzahl, Status, Dauer (Histogram). |
| OR-03 | S | Das System SOLL Fund-Metriken liefern: offene Funde nach Repo/Severity/Detektor sowie neue Funde (Delta). |
| OR-04 | S | Das System SOLL den Gate-Status je Repository als Metrik bereitstellen. |
| OR-05 | C | Das System KANN Detektor-Metriken liefern: Laufzeit und Fehler-/Degradationsrate je Detektor. |
| OR-06 | S | Es SOLLEN vorkonfigurierte Grafana-Dashboards mitgeliefert werden (Security Overview, Operations, Detector Health, Coverage). |
| OR-07 | C | Ausgewählte Grafana-Panels KÖNNEN sicher in die Web-UI eingebettet werden (signierte Tokens/Proxy). |
| OR-08 | C | Es KÖNNEN Alerts definiert werden: neuer CRITICAL-Fund, Gate-Fail auf geschütztem Branch, erhöhte Detektor-Fehlerrate, veraltete Scans. |
| OR-09 | M | Grafana/Prometheus MÜSSEN optional sein; der Core (CLI-Pfad) MUSS auch ohne sie funktionieren. |
