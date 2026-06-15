import { TRANSLATIONS } from './translations';

/**
 * Stellt die i18n-Vollständigkeit sicher (NFR-27): Englisch (Default) und Deutsch MÜSSEN exakt
 * dieselben Schlüssel tragen. Ein neuer UI-Text ohne Übersetzung in beiden Sprachen bricht hier den
 * Build — so bleibt „alle Labels übersetzt" automatisch gewährleistet.
 */
describe('TRANSLATIONS', () => {
  const enKeys = Object.keys(TRANSLATIONS.en).sort();
  const deKeys = Object.keys(TRANSLATIONS.de).sort();

  it('DE deckt alle EN-Schlüssel ab', () => {
    const missing = enKeys.filter((k) => !(k in TRANSLATIONS.de));
    expect(missing).toEqual([]);
  });

  it('EN deckt alle DE-Schlüssel ab', () => {
    const missing = deKeys.filter((k) => !(k in TRANSLATIONS.en));
    expect(missing).toEqual([]);
  });

  it('kein leerer Übersetzungswert', () => {
    const empty = [...Object.entries(TRANSLATIONS.en), ...Object.entries(TRANSLATIONS.de)].filter(
      ([, v]) => v.trim().length === 0,
    );
    expect(empty).toEqual([]);
  });
});
