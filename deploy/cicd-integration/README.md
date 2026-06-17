# Scanner als CI-Build-Gate einbinden (projektunabhängig)

Wiederverwendbare Vorlage, um den Source Code Scanner als Build-Gate in **ein beliebiges
GitHub-Repo** einzubinden (Konzept: docs/08). Self-contained — **kein** zentraler Server nötig;
das Gate hängt allein am Exit-Code.

**Funde-Anzahl auf einen Blick:** Der Workflow schreibt eine **Step Summary** (oben auf der
Run-Seite, kein Log-Aufklappen nötig): z. B. „✅ Secret-Scan: keine Funde (Gate PASS)" oder
„❌ Secret-Scan: 3 Fund(e) (Gate FAIL)", plus eine kleine Tabelle (berichtete Funde nach
Suppression/Baseline · blockierend · Gate). Dieselbe Info erscheint zusätzlich als `::notice`
in den Annotationen.

**Vollständiger Report:** Das SARIF wird **immer** als Build-Artefakt (`scan-report-sarif`)
abgelegt — im jeweiligen Workflow-Lauf unter „Artifacts" herunterladbar (z. B. mit der VS-Code-
Extension „SARIF Viewer" ansehen).

Der zusätzliche Upload in den **„Code scanning"-Tab** braucht **GitHub Advanced Security**
(öffentliche Repos: gratis; private: nur mit GHAS-Lizenz). Er ist daher **standardmässig aus** —
sonst erzeugt er bei Repos ohne GHAS die Annotation `Resource not accessible by integration`.
Einschalten, wenn GHAS verfügbar ist: Repo-Variable **`SCANNER_UPLOAD_SARIF` = `true`** setzen
(Settings → Secrets and variables → Actions → Variables).

## TL;DR — in 4 Schritten ins nächste Repo

1. **Drei Dateien** ins Ziel-Repo kopieren (siehe Tabelle unten).
2. **CLI-Image lesbar machen** für das Ziel-Repo (Package public ODER GHCR-Login, unten).
3. **Baseline** einmalig erzeugen + einchecken (sonst färben Altfunde jeden Build rot).
4. **Branch-Protection**: Status-Check `scan` als *required* setzen.

Vom Scanner-Repo aus kopieren (Pfade ggf. anpassen):

```bash
TARGET=../mein-repo        # Pfad zum Ziel-Repo
mkdir -p "$TARGET/.github/workflows" "$TARGET/.github/scanner"
cp deploy/cicd-integration/secret-scan.yml      "$TARGET/.github/workflows/secret-scan.yml"
cp deploy/cicd-integration/.scanner.yaml        "$TARGET/.github/scanner/.scanner.yaml"
cp deploy/cicd-integration/.scanner-full.yaml   "$TARGET/.github/scanner/.scanner-full.yaml"
```

## Dateien & Zielorte

| Datei hier | Ziel im Repo | Zweck |
|---|---|---|
| `secret-scan.yml` | `.github/workflows/secret-scan.yml` | Der CI-Workflow (Gate + SARIF) |
| `.scanner.yaml` | `.github/scanner/.scanner.yaml` | Schneller HEAD-Scan für PR/Push |
| `.scanner-full.yaml` | `.github/scanner/.scanner-full.yaml` | Nächtlicher Vollscan der History |

Die Baseline (`.scanner-baseline.json`, s. u.) liegt ebenfalls unter `.github/scanner/`.

**Projektspezifisch anzupassen:** nichts zwingend. `scan.repositories[].path: .` bleibt das
Repo-Root (Scan-Ziel = Arbeitsverzeichnis), unabhängig davon, wo die Config-Datei liegt.
Optional `scan.repositories[].id` auf den Repo-Namen setzen (Anzeige-Label). Detektor-/Gate-
Einstellungen je Repo nach Bedarf justieren (z. B. `pii.enabled`, `gate.failOn`).

## Voraussetzung: CLI-Image ist erreichbar

Der Workflow zieht `ghcr.io/fabianaschwanden/sourcecode-scanner-cli:latest`. Dieses Image
baut/pusht der Workflow **„Publish CLI Image"** im Scanner-Repo (Push auf `main`, `v*`-Tags
oder manuell). Damit das Ziel-Repo es ziehen kann, **eine** der beiden Varianten:

- **Image public** (einfachster Weg): GHCR → Package `sourcecode-scanner-cli` →
  Package settings → Danger Zone → *Change visibility* → **Public**. Workflow läuft ohne Login.
- **Image privat** (z. B. interne Repos): den `credentials:`-Block im Workflow einkommentieren
  und im Ziel-Repo ein Secret **`GHCR_PAT`** (Personal Access Token mit `read:packages`) hinterlegen.

Empfehlung: statt `:latest` einen unveränderlichen Tag (`:sha-…` oder `:<version>`) pinnen —
reproduzierbare Gates. Die Image-Referenz steht an genau einer Stelle (`container.image`).

## Einmalig: Baseline für Bestandsfunde

Damit Altlasten nicht jeden Build sofort rot färben (`gate.failOnNewOnly: true`), einmal eine
Baseline erzeugen und einchecken (lokal im Ziel-Repo, Docker vorausgesetzt):

```bash
# Kein führendes "scan" — das Image-Entrypoint startet bereits den scan-Command,
# die Argumente sind dessen Optionen (--config / --write-baseline / --output …).
docker run --rm -v "$PWD:/work" -w /work \
  ghcr.io/fabianaschwanden/sourcecode-scanner-cli:latest \
  --config .github/scanner/.scanner.yaml --write-baseline
git add .github/scanner/.scanner-baseline.json && git commit -m "scanner: Baseline der Bestandsfunde"
```

Alternativ für eine sanfte Einführungsphase in beiden Configs `gate.softFail: true` setzen:
Funde werden dann nur gemeldet (Exit 3, Build bleibt grün), bis ihr auf `false` umstellt.

## Merge blockieren

Branch-Protection-Regel: Status-Check **`scan`** (Job-Name) als *required* markieren — ein
Gate-Fail (Exit 1) blockiert dann den Merge.

## Modi — Stand heute

- **HEAD** (PR/Push): scannt den aktuellen Checkout ohne History → schnelles, blockierendes Feedback.
- **FULL** (nächtlich, `schedule`): gesamte History aller Branches.
- Ein echter **DIFF**-Modus (nur geänderte Hunks) ist im Scanner-Core noch nicht implementiert;
  sobald vorhanden, kann der PR-Job darauf umgestellt werden.

## Exit-Code-Vertrag (docs/08 §7)

| Exit | Bedeutung | Build |
|---|---|---|
| 0 | keine Funde ≥ `failOn` (bzw. nur Baseline) | grün |
| 1 | Gate verletzt — blockierende Funde | rot |
| 2 | Konfig-/Laufzeitfehler | rot |
| 3 | `softFail` aktiv: Funde, aber nicht blockierend | grün/Warnung |
