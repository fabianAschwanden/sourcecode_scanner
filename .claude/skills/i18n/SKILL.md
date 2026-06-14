---
name: i18n
description: Mehrsprachigkeit (DE/EN) der Web-UI des sourcecode-scanner. Verbindliche Konvention, dass alle sichtbaren UI-Texte über den zentralen I18nService laufen — kein hartkodierter Anzeigetext, inkl. placeholder und title/Tooltip. Verwende dies bei jeder Änderung an Angular-Templates (`webapp/src/app/**`), beim Anlegen neuer Seiten/Komponenten und wann immer der Nutzer auf "i18n", "Übersetzung", "Mehrsprachigkeit", "Labels" oder "DE/EN" verweist.
---

# i18n der Web-UI (DE/EN)

Verbindliche Konvention für **alle** sichtbaren Texte der Angular-Web-UI. Anzuwenden bei jeder
Template-Änderung und jeder neuen Komponente. Bezug: WR-70..73, NFR-27/27a, FR-26.

## When to use

- Neue Angular-Seite/Komponente oder Erweiterung einer bestehenden in `webapp/src/app/**`
- Hinzufügen/Ändern sichtbarer Texte (Überschriften, Spalten, Buttons, Status-/Fehlermeldungen)
- Hinzufügen von `placeholder`- oder `title`/Tooltip-Attributen
- Nutzer sagt sinngemäss "i18n", "übersetzen", "mehrsprachig", "Labels", "DE/EN"

## Die Regel (kurz)

**Kein hartkodierter Anzeigetext im Markup.** Jeder sichtbare String läuft über `t('key')`; der
Schlüssel existiert in **beiden** Wörterbüchern (`en` UND `de`). Das gilt ausdrücklich auch für
`placeholder=` und `title=` (Tooltips) — diese als `[placeholder]="t('…')"` / `[title]="t('…')"` binden.

## Vorgehen (Checkliste)

1. **Service einbinden:** `private readonly i18n = inject(I18nService);` und eine Helfer-Methode
   `protected t(key: string, params?: Record<string, string | number>) { return this.i18n.t(key, params); }`
2. **Schlüssel ergänzen** in `webapp/src/app/core/i18n/translations.ts` — in `en` **und** `de`, mit
   gleichem Schlüssel. Namensschema: `<feature>.<bereich>` (z. B. `findings.tab.open`,
   `repos.tokenRef.tooltip`, `common.delete`). Wiederverwendbares unter `common.*`.
3. **Template umstellen:**
   - Text: `{{ t('feature.label') }}`
   - Attribute: `[placeholder]="t('feature.x')"`, `[title]="t('feature.x.tooltip')"`
   - Platzhalter-Werte: `t('feature.msg', { count: n, name: x })` → im Wörterbuch `{count}`/`{name}`
4. **Enum/Status lokalisieren** über dynamische Schlüssel, z. B. `t('findings.status.' + f.triageStatus)`.
5. **Default Englisch**, Deutsch gleichwertig. Fehlt eine Übersetzung, fällt der Dienst auf
   Default-Sprache/Schlüssel zurück — verlasse dich nicht darauf, ergänze beide Sprachen.

## Nicht hartkodieren — Beispiele

```html
<!-- falsch -->
<th>Funde</th>
<input placeholder="Name" title="Eindeutiger Name" />

<!-- richtig -->
<th>{{ t('scans.col.findings') }}</th>
<input [placeholder]="t('repos.name')" [title]="t('repos.name.tooltip')" />
```

## Verifikation

- `cd webapp && npm run lint` (keine Template-Fehler) und `npm test`.
- Der **Paritäts-Test** `core/i18n/translations.spec.ts` bricht den Build, wenn ein Schlüssel in `en`
  oder `de` fehlt oder leer ist (NFR-27a) — neue Texte daher immer in beiden Sprachen ergänzen.
- Schnell-Audit nach Resten: in `webapp/` nach `placeholder="`/`title="` mit Literalwert (ohne `t(`)
  und nach Klartext in `>...<` suchen.

## Architektur (Verweise)

- `webapp/src/app/core/i18n/i18n.service.ts` — `lang`-Signal, `t(key, params)`, `localStorage`-Persistenz.
- `webapp/src/app/core/i18n/translations.ts` — Wörterbücher `en`/`de`.
- Sprachumschalter im Header (`app.html`), Default Englisch.
- Kein `@angular/localize`-Mehrfach-Build — die Umschaltung ist reaktiv über Signals (docs/06 §3.8).
