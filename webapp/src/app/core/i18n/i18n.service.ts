import { Injectable, signal } from '@angular/core';
import { Dictionary, Lang, TRANSLATIONS } from './translations';

const STORAGE_KEY = 'scanner.lang';
const DEFAULT_LANG: Lang = 'en';

/**
 * Zentraler, Signal-basierter Übersetzungsdienst (WR-70..73, NFR-27/28). Komponenten geben Text über
 * {@link I18nService.t} aus; ein Sprachwechsel (`lang`-Signal) aktualisiert die UI reaktiv ohne
 * Neuladen. Die Wahl wird in {@code localStorage} persistiert. Fehlt eine Übersetzung, fällt der
 * Dienst auf die Default-Sprache und zuletzt auf den Schlüssel zurück (WR-73).
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly lang = signal<Lang>(this.initial());

  /** Verfügbare Sprachen (für den Umschalter). */
  readonly available: readonly Lang[] = ['en', 'de'];

  setLang(lang: Lang): void {
    this.lang.set(lang);
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // localStorage nicht verfügbar (z. B. SSR/Tests) — Persistenz ist optional (NFR-28).
    }
  }

  /** Übersetzt einen Schlüssel; {@code params} ersetzen Platzhalter {@code &#123;name&#125;}. */
  t(key: string, params?: Record<string, string | number>): string {
    const dict = this.dict(this.lang());
    const text = dict[key] ?? this.dict(DEFAULT_LANG)[key] ?? key;
    return params ? this.interpolate(text, params) : text;
  }

  private interpolate(text: string, params: Record<string, string | number>): string {
    return text.replace(/\{(\w+)\}/g, (match, name: string) =>
      name in params ? String(params[name]) : match,
    );
  }

  private dict(lang: Lang): Dictionary {
    return TRANSLATIONS[lang];
  }

  private initial(): Lang {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'en' || stored === 'de') {
        return stored;
      }
    } catch {
      // ignorieren — Default greift.
    }
    return DEFAULT_LANG;
  }
}
