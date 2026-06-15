# 05 — Web-UI- & Observability-Anforderungen (WR / OR)

Anforderungen an die Management-Weboberfläche (`WR-`) und die Metrik-/Dashboard-
Schicht (`OR-`). Umsetzung primär in Roadmap-Phase 4–5.

## Web-UI (WR)

### Steuerung & Verwaltung

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-01 | S | Die Web-UI SOLL die vollständige Steuerung ohne CLI ermöglichen (Quellen, Detektoren, Scans, Baseline, Gate). |
| WR-02 | S | Die UI SOLL Repository-Quellen anlegen, bearbeiten, löschen und die Verbindung testen können; die Listendarstellung folgt der Repo-Übersicht (WR-80..85). |
| WR-02a | S | Die UI SOLL einen **Anlege-Assistenten** bieten: Anbieter wählen (GitHub/GitLab/Bitbucket/Local Git) → anbieter-spezifische Defaults (Typ, URL-Platzhalter, env-Referenz-Vorschlag) vorbelegt → nur Repo-URL/Name und Zugriffs-Key ergänzen, mit Hilfetext + Link, wo der Token zu erstellen ist und welche Scopes nötig sind → Übersicht/Anlegen. |
| WR-02b | S | Im Assistenten SOLL der Key wahlweise (a) **DB-verschlüsselt** als verwaltetes Secret abgelegt und automatisch als `secret:<name>` referenziert werden (WR-19c, Admin) oder (b) als reine **Umgebungs-Referenz** (`env:NAME`) hinterlegt werden; ein Klartext-Key wird nie ins `tokenRef`-Feld geschrieben (WR-32). |
| WR-03 | S | Die UI SOLL Scans manuell starten (Repo, Branch, Modus) und abbrechen können. |
| WR-04 | S | Die UI SOLL laufende Scans live mit Fortschritt anzeigen (WebSocket/SSE). |
| WR-04a | S | Der Scan-Fortschritt MUSS als **Prozentwert (0–100 %)** dargestellt werden, ergänzt um eine visuelle **Fortschrittsleiste**; der Wert SOLL sich während des Laufs live aktualisieren (SSE-Stream je Scan-ID, WR-04), nicht erst am Ende. |
| WR-04b | S | Das Backend SOLL den Fortschritt während des Scans **granular fortschreiben** (z. B. anteilig je abgeschlossenem Repository/Arbeitspaket), nicht nur Start/Ende; bei Abschluss/Abbruch/Fehler endet er definiert bei 100 %. |
| WR-05 | C | Die UI KANN periodische/geplante Scans konfigurieren. |
| WR-06 | S | Die UI SOLL Detektoren aktivieren/deaktivieren und deren Parameter pflegen, mit Validierung vor dem Speichern. |
| WR-07 | C | Die UI KANN geladene Plugins mit ID, Kategorie und Version anzeigen. |
| WR-08 | S | Die UI SOLL beim Anlegen/Bearbeiten einer Repository-Quelle optional eine oder mehrere Report-E-Mail-Adressen erfassen, an die nach Scans dieses Repos ein Report versendet wird (IR-53). |

### Repo-Übersicht (GitHub-Stil)

Die Repository-Liste SOLL im Aufbau der GitHub-Repository-Übersicht gestaltet sein
(Referenz: GitHub „Your repositories"). Konkretisiert WR-02/WR-40. **Lizenz-Anzeige und
Stern-/Star-Vergabe sind bewusst ausgenommen** (nicht relevant für den Scanner).

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-80 | S | Die Übersicht SOLL eine **Suchleiste** („Repository suchen…") sowie eine Aktion **„Neu"** (Repo-Quelle anlegen) bereitstellen. |
| WR-81 | S | Die Übersicht SOLL **Filter-/Sortier-Dropdowns** bieten: `Typ` (localGit/github/gitlab/bitbucket), `Sprache` (dominanter Dateityp) und `Sortieren` (Name, zuletzt aktualisiert); Filter/Sortierung wirken **serverseitig** über `/api/sources` (Query-Parameter). |
| WR-82 | S | Repos SOLLEN als **Karten** dargestellt werden mit: Name (Link zu Details/Scan), **Sichtbarkeits-/Typ-Badge** (z. B. `public`/`private`/`localGit`), optionaler **Beschreibung**, **Sprach-Indikator** (farbiger Punkt + Sprachname) und **„Aktualisiert <relative Zeit>"** (Zeitpunkt des letzten Scans). |
| WR-83 | S | Die Felder **Beschreibung** und **Sichtbarkeit** SOLLEN beim Anlegen/Bearbeiten der Quelle erfassbar sein (eigene Felder, WR-02); **Sprache** und **Aktualisiert** werden serverseitig abgeleitet (dominanter Dateityp der letzten Funde bzw. letzter Scan-Zeitpunkt). |
| WR-84 | S | Suche und Filter SOLLEN auf vorhandene Werte wirken (leere Liste/keine Treffer wird klar dargestellt); alle Texte lokalisiert (WR-70). |
| WR-85 | C | Eine Karte KANN eine kompakte Aktivitäts-/Trend-Andeutung zeigen (z. B. Fund-Trend); Lizenz und Sterne werden nicht angezeigt. |

### Rulesets (feingranulare Regelsteuerung, GitHub-Stil)

Verwaltung benannter Regelsätze analog GitHubs „Rulesets" (FR-27, DR-50..55). Referenz:
die hochgeladenen Screenshots (Übersicht mit „New ruleset", Editor mit Name/Enforcement/
Bypass/Scope, Regel-Liste mit Checkboxen).

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-90 | S | Die UI SOLL eine **Rulesets-Übersicht** zeigen (Liste benannter Rulesets, Enforcement-Status, Scope) mit Aktion **„Neues Ruleset"** und Leerzustand-Hinweis. |
| WR-91 | S | Der Ruleset-Editor SOLL **Name** (Pflicht) und **Enforcement-Status** (`disabled` \| `active`) erfassen. |
| WR-92 | S | Der Editor SOLL den **Geltungsbereich** wählbar machen: **alle Repos** oder eine **Repo-Auswahl** (Liste der verwalteten Quellen). |
| WR-93 | S | Der Editor SOLL je **Regel** eine Checkbox (an/aus) mit Kurzbeschreibung und ein **Severity**-Dropdown anbieten (Liste der verfügbaren Regeln aus den aktiven Detektoren, z. B. `email`, `iban`, `creditcard`, `phone`, `secret.high-entropy`). |
| WR-94 | S | Für wertbezogene Regeln (z. B. `email`) SOLL der Editor einen **Abgleichsmodus** anbieten: `immer` \| `gegen Liste` \| `gegen API`; bei `Liste`/`API` ist eine Datenquelle wählbar (WR-50/IR-67). |
| WR-95 | S | Rulesets SOLLEN angelegt, bearbeitet und gelöscht werden können (nur Admin, WR-31); Änderungen wirken auf künftige Scans (DR-54) und werden auditiert (WR-34). |
| WR-96 | C | Die UI KANN je Repository die zutreffenden/effektiven Rulesets anzeigen (welche Regel/Severity gilt) zur Nachvollziehbarkeit (DR-55). |
| WR-97 | S | Die Rulesets-Übersicht SOLL das ab Start wirksame, automatisch angelegte Ruleset `default` (global, `active`) als regulären, editierbaren Eintrag zeigen (DR-56), damit sichtbar ist, welche Konfiguration ohne weiteres Zutun gilt. |

### Externe Datenquellen & Attribut-Mapping

Verwaltung der externen REST-Datenquelle für vertrauliche Kundendaten und das Mapping,
welche Attribute im Code geprüft werden (FR-21/FR-22, IR-60..IR-66, DR-23..DR-28).

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-50 | S | Die UI SOLL externe REST-Datenquellen anlegen/bearbeiten/löschen: Basis-URL, Methode/Pfad, Auth (nur Secret-Referenz, WR-32), Datensatz-Pfad (JSONPath) und Cache-TTL. |
| WR-51 | S | Die UI SOLL die Datenquelle testweise abrufen (IR-63) und die zurückgelieferten **Attribute** (Feldnamen) **redigiert** auflisten — mit maskierter Beispielausprägung, nie Klartextwerten (WR-33). |
| WR-52 | S | Die UI SOLL je Attribut ein Mapping pflegen: `prüfen` (ja/nein), Severity (INFO..CRITICAL) und Kategorie (PII \| CUSTOM); z. B. `partnernummer → prüfen, HIGH, PII`, `name → prüfen, MEDIUM, PII`. |
| WR-53 | S | Das Mapping SOLL serverseitig persistiert und ohne Neustart wirksam werden; eine Validierung (mind. ein geprüftes Attribut, gültige Severity) SOLL vor dem Speichern erfolgen (analog WR-06). |
| WR-54 | M | Die UI DARF die geladenen Datenwerte nie anzeigen; im Mapping erscheinen ausschliesslich Attributnamen und maskierte Beispiele (WR-33, DR-26). |
| WR-55 | C | Die UI KANN je Repository/Org-Unit zuordnen, welche Datenquelle(n) beim Scan herangezogen werden (IR-65). |
| WR-56 | S | Die UI SOLL den Datenquellen-Typ (REST-API oder Upload) wählbar machen und für Upload-Quellen eine CSV/JSON-Datei hochladen können; angezeigt wird nur die Anzahl gespeicherter Hashes je Attribut, nie die Werte (IR-67, WR-54). |

### Einstellungen (Administration)

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-15 | S | Die UI SOLL eine Einstellungs-Ansicht bereitstellen, in der allgemeine Systemeinstellungen ohne Neustart geändert werden können (nur Rolle Admin, WR-31). |
| WR-16 | S | Die Einstellungen SOLLEN eine allgemeine Benachrichtigungs-E-Mail-Adresse umfassen, an die systemweite Meldungen (z. B. Betriebs-/Sammelreports) gesendet werden (IR-52). |
| WR-17 | S | Die UI SOLL Credential-/Secret-Referenzen (z. B. Environment-Variablen wie `env:GITHUB_TOKEN`) als verwaltbare Einträge anlegen/bearbeiten/löschen und ihre Auflösbarkeit anzeigen — voller CRUD (WR-19). |
| WR-18 | C | Die UI KANN nicht-geheime Betriebsparameter pflegen (z. B. Standard-Gate-Severity, Aufbewahrungsfrist, Standard-Scan-Modus), mit Validierung vor dem Speichern. |
| WR-19 | S | Die UI SOLL beim Anlegen eines Secrets je Eintrag einen **Modus** wählbar machen: (a) **Referenz** (`env:`/`vault:`, kein Wert im Backend, Default — WR-32-konform); (b) **Vault-Write** (Klartext entgegennehmen, an den Secret-Store schreiben, nur die Referenz behalten, Klartext sofort verwerfen, IR-30); (c) **DB-verschlüsselt** (Klartext at-rest verschlüsselt in der zentralen DB, NFR-29). Der Modus bestimmt Speicherung und Anzeige. |
| WR-19a | M | In allen Modi DARF ein Klartext-Wert nie zurückgegeben, nie geloggt und in der Liste nur als Status/Referenz (bzw. maskiert) dargestellt werden (WR-33, NFR-09); Eingabefelder für Klartext sind maskiert. |
| WR-19c | S | Ein **DB-verschlüsseltes** Secret SOLL als `secret:<name>` referenzierbar sein (z. B. als `tokenRef` einer Repo-Quelle); die Auflösung entschlüsselt den Wert transient zur Laufzeit (nur env/dev/server mit Encryption-Key, NFR-30) und gibt ihn nie zurück/loggt ihn nie. |
| WR-19b | M | Secret-Verwaltung (alle Modi) ist nur der Rolle **Admin** zugänglich (WR-31) und jede Änderung wird auditiert (WR-34). |

### Finding-Workflow

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-10 | S | Die UI SOLL Funde filter- und sortierbar darstellen (Repo, Severity, Detektor, Status); die konkrete Darstellung folgt der Code-Scanning-Ansicht (WR-60..68). |
| WR-11 | S | Die UI SOLL Funddetails mit redigiertem Treffer und Code-Kontext anzeigen. |
| WR-12 | S | Die UI SOLL Funde triagieren: als Baseline akzeptieren, unterdrücken (mit Pflichtbegründung), als False Positive markieren. |
| WR-13 | C | Die UI KANN aus einem Fund ein Ticket (Jira) erzeugen. |
| WR-14 | C | Die UI KANN Baseline-Einträge einsehen und entfernen. |

### Code-Scanning-Ansicht (GitHub-Stil)

Die Finding-Liste SOLL im Aufbau der GitHub-„Code scanning"-Ansicht gestaltet sein
(Referenz: GitHub Security-Tab). Sie konkretisiert WR-10/WR-40 für die Funddarstellung.

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-60 | S | Die Ansicht SOLL den Titel „Code scanning" und ein **Status-Banner** zum Tool-/Scan-Zustand zeigen (z. B. „Alle Detektoren laufen wie erwartet" mit grünem Haken, bzw. eine Warnung bei degradierten Detektoren, OR-05/NFR-07). |
| WR-61 | C | Das Banner KANN rechts die Anzahl aktiver Detektoren/Tools (`Tools N`) sowie eine Aktion zum Aktivieren weiterer Detektoren (`Detektor hinzufügen`, verweist auf die Detektor-Verwaltung WR-06) anzeigen. |
| WR-62 | S | Die Ansicht SOLL eine **Such-/Filterleiste** mit Query-Syntax bereitstellen (z. B. `is:open branch:main severity:high detector:secret.regex-ruleset`); die Query SOLL mit den Dropdown-Filtern synchron sein (Änderung am Dropdown aktualisiert die Query und umgekehrt). |
| WR-63 | S | Die Ansicht SOLL **Status-Tabs mit Zähler** zeigen: `N Offen` und `N Geschlossen` (geschlossen = triagiert: Baseline/Suppressed/False-Positive), jeweils mit Icon; der aktive Tab filtert die Liste. |
| WR-64 | S | Die Ansicht SOLL **Facetten-Filter** als Dropdowns bereitstellen — mindestens `Sprache/Dateityp`, `Detektor` (entspr. „Tool"), `Regel`, `Severity` — sowie ein **Sortier**-Dropdown (z. B. Severity, zuletzt gesehen, zuerst gesehen). Optionen SOLLEN nur tatsächlich vorkommende Werte enthalten (Facettierung). |
| WR-65 | S | Jede **Ergebniszeile** SOLL enthalten: Status-/Severity-Icon, Regel-/Fundtitel, ein **Severity-Badge** (farbcodiert, WR-40), eine Metazeile `#<lfd-Nr.> <Status> <relative Zeit> • erkannt von <Detektor> in <Datei>:<Zeile>` und rechts ein **Branch-Badge**; Klick öffnet die Funddetails (WR-11). |
| WR-66 | C | Funde KÖNNEN eine stabile, fortlaufende Anzeigenummer (`#N`) je Repository tragen und einen relativen Zeitstempel („vor 1 Minute") für Erst-/Letztsichtung zeigen. |
| WR-69 | S | Die UI SOLL die **Herkunft** eines Laufs/Funds anzeigen (Server vs. CI/CD) und nach Herkunft filterbar machen; bei CI-Läufen SOLLEN die CI-Metadaten (Pipeline/Job-Link, Commit, Branch) einsehbar sein (IR-22/25). |
| WR-67 | S | Listen mit Zeilen-Aktionen (Funde, Repositories, Scans, Rulesets, Datenquellen) SOLLEN eine **Mehrfachauswahl** (Checkbox je Zeile + Kopfzeile „alle") und eine **Sammelaktions-Leiste** bieten, die genau die für die Liste sinnvollen Aktionen als Bulk anbietet (Funde: Baseline/False-Positive/Unterdrücken mit Pflichtbegründung + Fix-per-PR; Repositories: Scan starten/Remediation an-aus/löschen; Scans: abbrechen; Rulesets & Datenquellen: löschen). Die Auswahl wird nach der Aktion zurückgesetzt; Anzahl ausgewählter Elemente ist sichtbar. |
| WR-68 | S | Treffer-Anzeige bleibt redigiert (WR-33); Branch-, Datei- und Detektorangaben enthalten nie Klartext-Geheimnisse. |

### Darstellung & Usability

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-40 | S | Die UI SOLL durchgängig in einem dunklen Erscheinungsbild im Stil von GitHub (Dark Mode) gestaltet sein (Hintergründe, Flächen, Text, Akzent- und Severity-Farben), konsistent über alle Ansichten. |
| WR-41 | S | Eingabe-Bedienelemente (Felder, Auswahllisten) SOLLEN eine Hover-Hilfe (Tooltip) mit einem konkreten Eingabe-Beispiel anzeigen (z. B. Pfad `/Users/me/git/projekt`, Clone-URL `https://github.com/org/repo.git`, Token-Referenz `env:GITHUB_TOKEN`, Org-Unit `team-a/payments`, Datenquellen-URL `https://crm.intern/api/v1/partners`, Datensatz-Pfad `$.data[*]`, Code-Scanning-Query `is:open branch:main severity:high`). |

### Internationalisierung (i18n)

Die Web-UI ist mehrsprachig (FR-26, NFR-27/28). Mitgeliefert: Englisch (Default) und Deutsch.

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-70 | S | Die UI SOLL alle sichtbaren Texte über einen zentralen Übersetzungsdienst (Schlüssel→Text) ausgeben; **Deutsch und Englisch** sind verfügbar (FR-26). |
| WR-71 | S | Die UI SOLL einen **Sprachumschalter** (z. B. im Kopfbereich) bereitstellen, der die Sprache zur Laufzeit ohne Neuladen wechselt; die Auswahl SOLL clientseitig persistiert werden (NFR-28). |
| WR-72 | S | Auch Tooltips/Hilfetexte (WR-41) und Statusmeldungen (z. B. Triage-/Upload-/Remediation-Rückmeldungen) SOLLEN lokalisiert sein. |
| WR-73 | C | Fehlt eine Übersetzung, KANN die UI nachvollziehbar auf die Default-Sprache bzw. den Schlüssel zurückfallen, ohne zu brechen. |

### Schnittstelle

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-20 | M | Die UI MUSS ausschließlich über eine dokumentierte REST-API mit dem Backend kommunizieren. |
| WR-21 | C | Die REST-API KANN per OpenAPI-Spezifikation versioniert und extern nutzbar sein. |
| WR-22 | S | Die Finding-API SOLL die Code-Scanning-Ansicht (WR-60..68) bedienen: Filter nach Status offen/geschlossen inkl. Zähler, Facetten-Werte (vorkommende Detektoren/Regeln/Dateitypen) und eine Sortierung; die Such-Query (WR-62) wird serverseitig oder clientseitig auf diese Filter abgebildet. |
| WR-23 | S | Die REST-API SOLL **Batch-Endpunkte** für Sammelaktionen (WR-67) bereitstellen (eine Anfrage, mehrere IDs): je Element wird die Aktion ausgeführt und auditiert; die Antwort meldet Erfolg/Fehler je ID. RBAC wie bei der jeweiligen Einzelaktion. |

### Sicherheit

| ID | Prio | Anforderung |
|----|------|-------------|
| WR-30 | M | Die UI MUSS Authentifizierung über den Unternehmens-IdP erzwingen (OIDC/SSO gemäß Blueprint; SAML nur, falls der Blueprint es aufnimmt — TR-13). |
| WR-31 | M | Die UI MUSS rollenbasierte Autorisierung (Viewer/Operator/Admin) durchsetzen. |
| WR-31a | S | Als IdP-Variante KANN GitHub (OAuth2) genutzt werden (Profil `ghauth`, `quarkus.oidc.provider=github`). Da GitHub keine Rollen liefert, leitet ein Augmentor die Rolle aus dem Login ab: Logins in der Allowlist (`scanner.auth.github.admin-logins`) → `admin`, jeder andere authentifizierte Nutzer → `viewer`. |
| WR-30a | S | Die UI SOLL eine eigene Login-Landing-Seite (App-Stil) mit „Sign in"-Aktion bereitstellen, statt direkt zum IdP zu springen; Landing + statische Assets sind öffentlich, geschützte Endpunkte erfordern Login. Nach dem Login zeigt der Header den angemeldeten Nutzer und eine Abmelde-Aktion. |
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
