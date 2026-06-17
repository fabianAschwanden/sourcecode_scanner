# Scanner-Einbindung in das psp-Repo (CI-Build-Gate)

Diese drei Dateien binden den Source Code Scanner als Build-Gate in
`https://github.com/fabianAschwanden/psp` ein (Konzept: docs/08). Self-contained —
**kein** zentraler Server nötig; das Gate hängt allein am Exit-Code, Funde erscheinen
zusätzlich im GitHub-„Code scanning"-Tab.

## Dateien ins psp-Repo kopieren

| Datei hier | Ziel im psp-Repo |
|---|---|
| `secret-scan.yml` | `.github/workflows/secret-scan.yml` |
| `.scanner.yaml` | `.scanner.yaml` (Repo-Wurzel) — schneller HEAD-Scan für PR/Push |
| `.scanner-full.yaml` | `.scanner-full.yaml` (Repo-Wurzel) — nächtlicher Vollscan |

## Voraussetzung: CLI-Image ist veröffentlicht

Der Workflow zieht `ghcr.io/fabianaschwanden/sourcecode-scanner-cli:latest`. Dieses Image
baut/pusht der Workflow **„Publish CLI Image"** im Scanner-Repo (auf Push nach `main`,
auf `v*`-Tags oder manuell). Einmalig sicherstellen, dass das Paket für psp lesbar ist:
- Package in GHCR auf **public** stellen (einfachster Weg), **oder**
- dem psp-Repo Lesezugriff auf das Package geben (GHCR → Package → Settings → Manage Actions access).

Empfehlung: statt `:latest` einen unveränderlichen Tag (`:sha-…` oder `:<version>`) pinnen,
sobald die erste Version veröffentlicht ist — reproduzierbare Gates.

## Einmalig: Baseline für Bestandsfunde

Damit Altlasten nicht jeden Build sofort rot färben (`gate.failOnNewOnly: true`), einmal eine
Baseline erzeugen und einchecken (lokal im psp-Repo, Docker vorausgesetzt):

```bash
# Hinweis: KEIN führendes "scan" — das Image-Entrypoint startet bereits den scan-Command,
# die Argumente sind dessen Optionen (--config / --write-baseline / --output …).
docker run --rm -v "$PWD:/work" -w /work \
  ghcr.io/fabianaschwanden/sourcecode-scanner-cli:latest \
  --config .scanner.yaml --write-baseline
git add .scanner-baseline.json && git commit -m "scanner: Baseline der Bestandsfunde"
```

## Merge blockieren

In den psp-Branch-Protection-Regeln den Status-Check **`scan`** (Job-Name) als *required*
markieren — dann blockiert ein Gate-Fail (Exit 1) den Merge.

## Einführungsphase (optional)

Wenn das Gate anfangs nicht hart brechen soll: in beiden Configs `gate.softFail: true` setzen.
Dann werden Funde nur gemeldet (Exit 3, Build bleibt grün), bis ihr auf `false` umstellt.

## Modi — Stand heute

- **HEAD** (PR/Push): scannt den aktuellen Checkout ohne History → schnelles, blockierendes Feedback.
- **FULL** (nächtlich): gesamte History aller Branches.
- Ein echter **DIFF**-Modus (nur geänderte Hunks) ist im Scanner-Core noch nicht implementiert
  (`UnsupportedOperationException`); sobald vorhanden, kann der PR-Job darauf umgestellt werden.
