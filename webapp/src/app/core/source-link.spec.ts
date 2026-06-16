import { describe, expect, it } from 'vitest';
import { sourceLink } from './source-link';

describe('sourceLink', () => {
  it('baut einen GitHub-Permalink mit Branch und Zeilenanker', () => {
    expect(
      sourceLink('https://github.com/org/repo', 'src/Main.java', 42, 'main'),
    ).toBe('https://github.com/org/repo/blob/main/src/Main.java#L42');
  });

  it('entfernt das .git-Suffix', () => {
    expect(sourceLink('https://github.com/org/repo.git', 'a.txt', 1)).toBe(
      'https://github.com/org/repo/blob/HEAD/a.txt#L1',
    );
  });

  it('versteht SSH-URLs (git@host:org/repo.git)', () => {
    expect(sourceLink('git@github.com:org/repo.git', 'a.txt', 5)).toBe(
      'https://github.com/org/repo/blob/HEAD/a.txt#L5',
    );
  });

  it('versteht ssh://-URLs', () => {
    expect(sourceLink('ssh://git@gitlab.com/grp/proj.git', 'x.py', 3)).toBe(
      'https://gitlab.com/grp/proj/blob/HEAD/x.py#L3',
    );
  });

  it('nutzt für Bitbucket src/ und den lines-Anker', () => {
    expect(sourceLink('https://bitbucket.org/team/repo', 'f.go', 9, 'develop')).toBe(
      'https://bitbucket.org/team/repo/src/develop/f.go#lines-9',
    );
  });

  it('fällt ohne Branch auf HEAD zurück', () => {
    expect(sourceLink('https://gitlab.com/grp/proj', 'a.ts', 7)).toBe(
      'https://gitlab.com/grp/proj/blob/HEAD/a.ts#L7',
    );
  });

  it('liefert null für lokale Pfade ohne Web-Host', () => {
    expect(sourceLink('/Users/me/repo', 'a.txt', 1)).toBeNull();
    expect(sourceLink('file:///tmp/repo', 'a.txt', 1)).toBeNull();
  });

  it('liefert null ohne Location oder Datei', () => {
    expect(sourceLink(null, 'a.txt', 1)).toBeNull();
    expect(sourceLink('https://github.com/org/repo', '', 1)).toBeNull();
  });
});
