# 04 — Bestehende Tools als Grundlage (Prior Art)

Bewertung etablierter Tools, die als Bibliothek, Regelbasis oder Referenz für das
eigene Konzept dienen. Stand: 2026.

## 1. Übersicht

| Tool | Ansatz | Lizenz | Rolle im Konzept |
|------|--------|--------|------------------|
| Gitleaks | Regex- + Entropie-Engine, schnell, deterministisch | MIT | **Regelbasis & Referenz-Engine** |
| TruffleHog | Detector-Engine mit Live-Verifikation | Open Source | **Verifikations-Vorbild** |
| detect-secrets | Baseline-orientiert, Brownfield-tauglich | Apache 2.0 | **Baseline-Konzept** |
| Semgrep | Regelbasierte Code-Analyse (YAML) | LGPL/komm. | **Erweiterung über Secrets hinaus** |
| SonarQube | Quality-Gate-/Rule-Engine | LGPL/komm. | **Gate-/Reporting-Vorbild** |
| GitGuardian | SaaS, Enterprise-Governance | kommerziell | **Workflow-/Remediation-Referenz** |

## 2. Detailbewertung

### Gitleaks
Schnelle, MIT-lizenzierte Regex-Engine mit ~150+ Regeln, konfiguriert über
`.gitleaks.toml`. Gitleaks scannt Git-Repositories mit Regex-Mustern und eignet sich besonders als Pre-Commit-Hook, der Secrets in Millisekunden blockt. Liefert
SARIF-Output für GitHub Advanced Security. Die False-Positive-Rate liegt in der Praxis höher als bei TruffleHog, besonders bei entropiebasierten Treffern gegen Test-Fixtures.

**Nutzung im Konzept:** Der Gitleaks-Regelsatz (TOML) dient als initialer
Basisregelsatz für den `secret.regex-ruleset`-Detektor. Das Regelformat ist
etabliert und gut gepflegt.

### TruffleHog
Erkennt 800+ Secret-Typen über Git-Repositories, S3-Buckets, Docker-Images, Slack, Jenkins und weitere Quellen hinaus. Das definierende
Merkmal ist die Verifikation: TruffleHog testet, ob ein erkanntes Secret noch aktiv ist, indem es eine Authentifizierung gegen den Dienst versucht — das eliminiert die Klasse von False Positives, bei denen ein Credential zwar einem Muster entspricht, aber bereits widerrufen wurde.

**Nutzung im Konzept:** Vorbild für die optionale `verify()`-Methode im
Detector-Interface. Hinweis: Verifikations-Aufrufe tauchen selbst in Cloud-Audit-Logs auf und können Detection-Pipelines verwirren, wenn sie nicht auf eine Whitelist gesetzt werden — daher ist Verifikation im Konzept opt-in.

### detect-secrets (Yelp)
Stärke im Umgang mit bestehenden Codebasen. detect-secrets verdient seinen Platz, wenn eine große bestehende Codebasis vorliegt und eine Baseline benötigt wird, um Alert-Flooding zu vermeiden.

**Nutzung im Konzept:** Vorbild für das Baseline-Modell (akzeptierte Altfunde
einchecken, nur Delta bricht das Gate).

### Semgrep
Regelbasierte statische Analyse mit eigenen YAML-Regeln, geht über reine
Secret-Erkennung hinaus (Injection, unsichere APIs, IaC).

**Nutzung im Konzept:** Referenz für Regeldesign jenseits von Secrets; potenzielle
Integration als eigener Detektor-Typ (`CUSTOM`/`IAC`).

### SonarQube
Nicht primär für Secrets, aber starkes Vorbild für Quality-Gate-Mechanik,
Rule-Engine-Struktur und Ergebnis-Dashboards.

**Nutzung im Konzept:** Referenz für `gate.failOn`-Logik und Reporting-Struktur.

## 3. Kern-Erkenntnis für das Design

Über mehrere unabhängige Quellen hinweg ist die Empfehlung konsistent:
Die meisten Sicherheitsteams setzen beide ein — Gitleaks pre-commit für Geschwindigkeit, TruffleHog in CI/CD für Tiefe.

Daraus folgt für das eigene Konzept: **nicht ein Tool nachbauen, sondern eine
Engine, die beide Stärken über Plugins vereint** — schnelle regex-/entropiebasierte
Erkennung als Standard, optionale Verifikation als zuschaltbarer Detektor.

Die eigentliche Herausforderung ist nicht die Erkennung, sondern das Rauschen:
ein Regex-Muster wie password = "..." matcht tausende Zeilen in jeder realen Codebasis — die meisten davon Test-Fixtures, Config-Defaults, Dokumentations-Beispiele. Deshalb sind Baseline,
Suppression und optionale Verifikation im Konzept erstklassige Bausteine, nicht
nachträgliche Ergänzungen.

## 4. Lizenz-Hinweis

Vor produktiver Einbindung externer Regelsätze/Engines ist die jeweilige Lizenz
(MIT, Apache 2.0, LGPL, kommerziell) zu prüfen. Gitleaks (MIT) und detect-secrets
(Apache 2.0) sind für Einbindung/Adaption unkritisch; bei Semgrep/SonarQube/
GitGuardian sind kommerzielle Komponenten bzw. Copyleft zu beachten.
