# CI/CD-Templates (Build-Gate)

Mitgelieferte Vorlagen, um den Scanner als blockierenden Build-Step einzubetten (docs/08). Sie sind
**Beispiele zum Kopieren** ins Ziel-Repo, kein Bestandteil des Scanner-Builds selbst.

| Datei | System | Mechanismus |
|-------|--------|-------------|
| [github-actions-secret-scan.yml](github-actions-secret-scan.yml) | GitHub Actions | SARIF-Upload ins Code-Scanning |
| [gitlab-ci-secret-scan.yml](gitlab-ci-secret-scan.yml) | GitLab CI | natives `secret_detection`-Report-Artefakt (IR-18) |

**Exit-Code-Vertrag** (docs/08 §7, identisch über alle Systeme): `0` pass · `1` Gate verletzt ·
`2` Konfig-/Laufzeitfehler · `3` softFail (Funde, nicht blockierend).

**Build-native Formate** erzeugt der Scanner über `--output`:
- `gitlab` → `gl-secret-detection-report.json` (MR-Widget/Security-Tab)
- `teamcity` → Service Messages (Build Problem / Inspektionen, IR-19)
- `sarif` → GitHub Code-Scanning, IDEs

TeamCity/Bitbucket/Jenkins folgen demselben Muster: Command-Line-Step ruft den Scanner, der Exit-Code
steuert das Gate, das jeweilige `--output`-Format liefert die native Anzeige.
