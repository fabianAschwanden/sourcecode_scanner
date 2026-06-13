# 00 — Requirements-Übersicht

## Notation

Anforderungen sind eindeutig nummeriert und nachverfolgbar:

| Präfix | Kategorie | Datei |
|--------|-----------|-------|
| `FR-` | Funktionale Anforderung | [01-functional.md](01-functional.md) |
| `NFR-` | Nicht-funktionale Anforderung | [02-non-functional.md](02-non-functional.md) |
| `DR-` | Detektor-Anforderung | [03-detectors.md](03-detectors.md) |
| `IR-` | Integrations-/Plattform-Anforderung | [04-integration.md](04-integration.md) |
| `WR-` | Web-UI-Anforderung | [05-web-ui-observability.md](05-web-ui-observability.md) |
| `OR-` | Observability-/Grafana-Anforderung | [05-web-ui-observability.md](05-web-ui-observability.md) |
| `RMR-` | Remediation-Anforderung (Auto-Fix & History-Scrub) | [06-remediation.md](06-remediation.md) |
| `TR-` | Template-Konformität (Blueprint) | [07-template-conformance.md](07-template-conformance.md) |

## Priorisierung (MoSCoW)

- **M** — Must: zwingend für MVP
- **S** — Should: wichtig, aber nicht MVP-blockierend
- **C** — Could: wünschenswert
- **W** — Won't (yet): bewusst zurückgestellt

## Schlüsselwörter

"MUSS" / "SOLL" / "KANN" gemäß RFC-2119-Sinn (verbindlich / empfohlen / optional).

## Traceability

Jede Anforderung verweist auf die umsetzende Architekturkomponente bzw.
Roadmap-Phase, um Abdeckung nachvollziehbar zu machen.
