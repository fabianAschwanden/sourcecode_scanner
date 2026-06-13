# 07 — Remediation: Auto-Fix per Pull/Merge Request & History-Bereinigung

Erweitert das Konzept um zwei Behebungsfähigkeiten:

1. **Auto-Fix per PR/MR** — der Scanner erzeugt automatisch einen Pull/Merge
   Request, der gefundene Issues im aktuellen Stand behebt.
2. **History-Bereinigung** — Entfernen/Maskieren gefundener Secrets aus der
   gesamten Git-Historie (Rewrite).

Beide sind **eingreifende, opt-in** Funktionen mit strengen Sicherheits- und
Governance-Leitplanken.

## 1. Grundprinzip: Rotation zuerst, Bereinigung zweitens

> **Ein committetes Secret gilt als kompromittiert — auch in privaten Repos.**

Das Entfernen aus der Historie macht eine Offenlegung **nicht** ungeschehen: Der
Wert kann bereits in Klonen, Forks, Caches, CI-Logs oder PR-Referenzen liegen.
Daher gilt im Konzept eine feste Reihenfolge:

```
1. ROTATE   → kompromittiertes Secret beim Anbieter widerrufen/neu erzeugen
2. REMOVE   → Referenz aus aktuellem Code entfernen (Auto-Fix-PR)
3. SCRUB    → optional Historie bereinigen (Rewrite)
4. PREVENT  → .gitignore/Pre-Commit-Hook ergänzen, Re-Scan
```

Die UI/Engine kommuniziert Schritt 1 immer **vor** Schritt 2/3 und kann
History-Bereinigung blockieren, solange die Rotation eines verifizierten aktiven
Secrets nicht bestätigt ist (siehe Gate in §4.4).

## 2. Auto-Fix per Pull/Merge Request

### 2.1 Ablauf

```
Finding(s) ─► RemediationPlanner ─► Branch anlegen (fix/scanner-<id>)
           ─► Fix-Strategy anwenden (Datei-Edit)
           ─► Commit (redigiert) ─► Push ─► PR/MR via Plattform-API
           ─► Reviewer/Labels/Beschreibung setzen ─► Status zurück an UI
```

Der Scanner schreibt **nie** direkt auf geschützte Branches; jede Änderung läuft
über einen PR/MR mit menschlichem Review.

### 2.2 Fix-Strategien (pro Detektor)

Ein Detektor kann optional eine **Remediation-Fähigkeit** anbieten. Strategien:

| Strategie | Beschreibung | Beispiel |
|-----------|--------------|----------|
| `EXTERNALIZE` | Hartkodierten Wert durch Referenz auf Secret-Store/Env ersetzen | `apiKey="AKIA…"` → `apiKey=System.getenv("API_KEY")` + `.env.example`-Eintrag |
| `REMOVE_LINE` | Zeile entfernen (z. B. versehentlich eingecheckte Debug-Credentials) | gelöschte Zeile |
| `REDACT` | Wert durch Platzhalter ersetzen | `password=***REMOVED***` |
| `GITIGNORE` | Datei aus Tracking nehmen + `.gitignore`-Eintrag | `config.local.json` |
| `ANNOTATE` | Suppression-Annotation einfügen (False Positive) | `// scanner:ignore-secret reason="…"` |

Strategien sind konservativ: Bei Unsicherheit wird ein **Vorschlags-PR** mit
Kommentar erzeugt, nicht stillschweigend „repariert". Mehrdeutige Fälle erfordern
menschliche Auswahl in der UI.

### 2.3 PR/MR-Inhalt

- Klare Beschreibung: betroffener Detektor, Datei/Zeile, gewählte Strategie,
  **redigierter** Treffer (nie Klartext-Secret im PR).
- Checkliste mit Erinnerung an Rotation (Schritt 1) und ggf. History-Scrub.
- Labels (`security`, `secret-remediation`), Severity, Link zum Finding in der UI.
- Automatische Reviewer-Zuweisung (CODEOWNERS / konfigurierte Security-Gruppe).

### 2.4 Detector-SPI-Erweiterung

```java
public interface RemediableDetector extends Detector {

    /** Liefert einen Vorschlag, wie ein Fund behoben werden kann. */
    Optional<RemediationProposal> propose(Finding finding, ScanUnit unit);
}

public record RemediationProposal(
    RemediationStrategy strategy,   // EXTERNALIZE, REMOVE_LINE, REDACT, ...
    List<FileEdit> edits,           // konkrete, anwendbare Änderungen
    Confidence confidence,          // HIGH → Auto-PR, LOW → Vorschlag mit Review
    String humanSummary             // für PR-Beschreibung (redigiert)
) {}

public record FileEdit(String path, int startLine, int endLine, String newContent) {}
```

Detektoren ohne Remediation-Fähigkeit bleiben unverändert nutzbar (Default: kein
Vorschlag). Auto-Fix ist damit ebenfalls **plugin-erweiterbar**.

## 3. History-Bereinigung (Rewrite)

### 3.1 Eingesetzte Werkzeuge

Etablierte, bewährte Werkzeuge statt Eigenimplementierung. git-filter-repo ist das moderne, schnelle und offiziell empfohlene Werkzeug zum Umschreiben der Git-Historie und ersetzt das ältere git filter-branch. Als schnellere, einfachere Alternative dient der BFG Repo-Cleaner, der speziell für das effiziente Entfernen großer Dateien und sensibler Daten über alle Commits hinweg gebaut ist.

| Werkzeug | Stärke | Einsatz im Konzept |
|----------|--------|--------------------|
| `git-filter-repo` | Moderner Standard, granular, Sicherheitsmechanismen | Default-Engine für Scrub |
| BFG Repo-Cleaner | Sehr schnell bei String-Ersetzung/Datei-Entfernung über große Repos | Alternative für String-Replace at scale |

### 3.2 Sicherheitsmechanismen der Werkzeuge nutzen

git-filter-repo weigert sich, auf Repos zu laufen, die keine frischen Klone sind, was unbeabsichtigtes Überschreiben reduziert. Das Konzept arbeitet daher grundsätzlich auf einem **frischen Mirror-Klon** als Wegwerf-Arbeitskopie und nie auf einer Live-Arbeitskopie.

### 3.3 Ablauf (kontrolliert & reversibel bis zum Push)

```
1. PRECHECK   → offene PRs prüfen (Rewrite ändert SHAs → PRs vorher schließen/mergen)
2. ROTATE-GATE→ verifizierte aktive Secrets müssen als rotiert markiert sein
3. MIRROR     → git clone --mirror (frischer Wegwerf-Klon)
4. BACKUP     → Mirror sichern (Wiederherstellungspunkt vor Rewrite)
5. SCRUB      → git-filter-repo/BFG mit Replacement-Liste (redigiert: ***REMOVED***)
6. VERIFY     → Re-Scan des bereinigten Mirrors: Treffer = 0 ?
7. DRY-RUN    → Diff/Bericht im UI; explizite Freigabe (Admin) erforderlich
8. FORCE-PUSH → erst nach Freigabe; force-with-lease auf Remote
9. POST       → Re-Sync-Anweisung an Team, .gitignore/Hook ergänzen
```

Bis Schritt 8 ist nichts am Remote verändert. Schritt 7 ist ein **harter,
menschlicher Freigabe-Gate** (Vier-Augen-Prinzip konfigurierbar).

> **Umsetzungsstand (Phase 6).** Die gesamte Governance-Orchestrierung ist implementiert und
> getestet (`ScrubWorkflow` erzwingt die Gate-Reihenfolge OPT-IN → ROTATION → DRY-RUN → FORCE-PUSH
> → TOOL; `HistoryScrubService` treibt Dry-Run/Execute; REST `POST /api/repos/{id}/scrub/dry-run`
> Operator+, `…/scrub/execute` Admin). Der eigentliche Rewrite liegt hinter `HistoryRewritePort`;
> der Default-Adapter `GitFilterRepoAdapter` prüft die Werkzeug-Präsenz: ist `git-filter-repo`
> nicht installiert, liefert der Dry-Run weiterhin einen redigierten Bericht, `execute` verweigert
> aber mit klarer Meldung statt eines realen Force-Push. Der scharfe Lauf wird erst mit installiertem
> Werkzeug aktiv (RMR-28); in dieser Phase findet kein realer Force-Push statt.

### 3.4 Replacement-Liste

Statt Klartext-Secrets wird mit Mustern/Hashes gearbeitet; Treffer werden durch
einen Platzhalter ersetzt (Datei-Entfernung alternativ bei reinen Secret-Dateien):

```
# replacements.txt (intern generiert, redigiert geloggt)
<regex-or-literal> ==> ***REMOVED***
```

### 3.5 Nachsorge (zwingend kommuniziert)

Nach einem Rewrite divergiert jeder lokale Klon; das Team muss neu synchronisieren — und zwar per Hard-Reset auf den aktualisierten Branch, nicht per Merge nach einem erzwungenen History-Rewrite. Bei öffentlichen oder weit geforkten Repos propagiert der Rewrite nicht in Forks; der offengelegte Inhalt ist bereits in Umlauf — Rotation bleibt daher die einzige verlässliche Absicherung.

Die UI generiert die passende Re-Sync-Anleitung und einen Hinweis auf
verbleibende Risiken (Forks/Caches/Klone im Offenlegungsfenster).

## 4. Governance & Sicherheits-Gates

### 4.1 Berechtigungen (RBAC)

| Aktion | Viewer | Operator | Admin |
|--------|:------:|:--------:|:-----:|
| Auto-Fix-PR vorschlagen/erzeugen | – | ✓ | ✓ |
| Auto-Fix-PR auf geschützten Branch direkt mergen | – | – | – (nur regulärer Review-Flow) |
| History-Scrub starten (Dry-Run) | – | ✓ | ✓ |
| History-Scrub **freigeben & Force-Push** | – | – | ✓ |

History-Rewrite mit Force-Push ist die **höchstprivilegierte** Aktion und kann auf
dedizierte Rollen/„Break-Glass" beschränkt werden.

### 4.2 Schutzschalter

- **Default = aus.** Beide Funktionen sind global und pro Repo separat zu aktivieren.
- **Protected-Branch-Schutz.** Kein direkter Schreibzugriff; nur PR/MR.
- **Dry-Run-Pflicht** vor jedem History-Rewrite.
- **Vier-Augen-Freigabe** für Force-Push (konfigurierbar).
- **Zeitfenster/Maintenance-Mode** für Rewrites (Repo-Lock während des Vorgangs).

### 4.3 Audit & Nachvollziehbarkeit

Jede Remediation-Aktion (PR-Erzeugung, Scrub-Start, Freigabe, Force-Push) wird mit
Akteur, Zeit, Repo, betroffenen Findings (redigiert) und Ergebnis auditiert.
Backups der Mirror-Klone werden für eine konfigurierbare Frist aufbewahrt.

### 4.4 Rotation-Gate (Kernschutz)

Ist ein Fund ein **verifiziert aktives** Secret, blockiert das System die
History-Bereinigung, bis der Status auf „rotiert/widerrufen" gesetzt ist —
erzwingt also die korrekte Reihenfolge aus §1.

## 5. Zusammenspiel mit bestehenden Komponenten

- **Detection Layer:** liefert Findings + optionale `RemediationProposal`.
- **Aggregation:** markiert Funde mit Remediation-Status (offen / PR-offen /
  gefixt / rotiert / gescrubbt).
- **Web-UI (06):** Triage-Aktionen „Fix per PR" und „History bereinigen" inkl.
  Dry-Run-Vorschau, Freigabe-Dialog und Rotation-Checkliste.
- **Connectors:** PR/MR-Erstellung über Bitbucket/GitHub/GitLab-APIs;
  Force-Push über denselben authentifizierten Zugang (separate, höher
  privilegierte Token-Scopes erforderlich).
- **Observability (06):** Metriken `scanner_remediation_total{type,result}`,
  `scanner_history_scrub_total{result}`, offene-vs-behobene-Trend.

## 6. Grenzen (bewusst dokumentiert)

- History-Rewrite entfernt Inhalte **nicht** aus fremden Klonen/Forks/Caches.
- Bei breit geteilten/öffentlichen Repos ist Rotation die einzige verlässliche
  Maßnahme; Scrub ist dann kosmetisch/Hygiene, nicht Schadensbegrenzung.
- Auto-Fix ist auf eindeutige, mechanisch sichere Transformationen begrenzt;
  fachliche Korrektheit verbleibt beim Reviewer.
