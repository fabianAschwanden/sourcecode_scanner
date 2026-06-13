# 10 — Horizontale Skalierung (NFR-03)

Phase 5 macht den Server-Betrieb horizontal skalierbar. Der Scan-Core ist bereits **stateless**:
aller geteilte Zustand (Scans, Findings, Quellen, Policies, Audit) liegt in PostgreSQL, nicht im
Prozess. Mehrere Server-Instanzen teilen sich dieselbe DB.

## Modell

```
        ┌─────────────┐
        │ Load Balancer│
        └──────┬───────┘
     ┌─────────┼─────────┐
┌────▼───┐ ┌───▼────┐ ┌──▼─────┐
│ Inst. A│ │ Inst. B│ │ Inst. C│   (gleiches Image, %server-Profil)
└────┬───┘ └───┬────┘ └──┬─────┘
     └─────────┼─────────┘
          ┌────▼─────┐
          │ Postgres │  (geteilter Zustand)
          └──────────┘
```

- **Scan-Verteilung:** Jede Instanz kann Scans starten/ausführen; der `ScanRecord`-Status ist in der
  DB instanzübergreifend sichtbar (UI/`GET /api/scans` zeigt alle Läufe, egal welche Instanz sie fährt).
- **Worker-Pool je Instanz:** Der bestehende Worker-Pool (NFR-01) parallelisiert innerhalb einer
  Instanz; horizontale Skalierung verteilt **Repos/Läufe über Instanzen**.
- **Observability:** Jede Instanz exponiert `/q/metrics`; Prometheus scraped alle Instanzen, Grafana
  aggregiert (Phase 5, OR-06).
- **Sessions:** OIDC-BFF nutzt Session-Cookies — bei mehreren Instanzen Sticky-Sessions am LB oder ein
  geteilter Session-Store (Blueprint-konform bei Bedarf ergänzen).

## Bewusst (noch) nicht: verteilte Job-Queue

Eine zentrale Queue (z. B. Kafka), die Scan-Jobs über Instanzen verteilt und gegen Doppelausführung
absichert, ist der nächste Schritt bei echtem Bedarf — gemäss Blueprint („erst hinzufügen, wenn ein
Use Case sie braucht", KISS). Phase 5 liefert die Statelessness-Grundlage; die Queue bleibt
blueprint-konform nachziehbar (SmallRye Reactive Messaging). Bis dahin wird Doppelausführung über
idempotente, abfragbare `ScanRecord`-Status pragmatisch begrenzt.
