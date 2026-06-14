import { TestBed } from '@angular/core/testing';
import { I18nService } from './i18n.service';

describe('I18nService', () => {
  let i18n: I18nService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    i18n = TestBed.inject(I18nService);
  });

  it('liefert standardmässig Englisch', () => {
    expect(i18n.lang()).toBe('en');
    expect(i18n.t('nav.findings')).toBe('Findings');
  });

  it('wechselt die Sprache zur Laufzeit', () => {
    i18n.setLang('de');
    expect(i18n.lang()).toBe('de');
    expect(i18n.t('nav.findings')).toBe('Funde');
  });

  it('interpoliert Platzhalter', () => {
    expect(i18n.t('findings.tab.open', { count: 3 })).toBe('3 Open');
  });

  it('fällt bei fehlendem Schlüssel auf den Schlüssel zurück', () => {
    expect(i18n.t('does.not.exist')).toBe('does.not.exist');
  });

  it('persistiert die Sprachwahl', () => {
    i18n.setLang('de');
    expect(localStorage.getItem('scanner.lang')).toBe('de');
  });
});
