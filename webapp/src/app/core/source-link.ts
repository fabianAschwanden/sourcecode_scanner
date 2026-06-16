/**
 * Baut aus der Repo-Location (Git-URL) + Dateipfad + Zeile einen Web-Deep-Link zur Quelle
 * (WR-66 „Sprung zur Quelle"). Unterstützt GitHub/GitLab/Bitbucket sowie SSH-/`.git`-Formen.
 * Ohne erkennbares Web-Hosting (z. B. lokales `file://`-Repo) liefert die Funktion `null` —
 * dann zeigt die UI keinen Link, nur den Pfad.
 */
export function sourceLink(
  location: string | null | undefined,
  file: string,
  line: number,
  branch?: string,
): string | null {
  if (!location || !file) {
    return null;
  }
  const base = webBase(location.trim());
  if (!base) {
    return null;
  }
  const ref = branch && branch.trim() ? branch.trim() : 'HEAD';
  const path = file.replace(/^\/+/, '');
  const sep = base.host === 'bitbucket.org' ? 'src' : 'blob';
  const fragment = base.host === 'bitbucket.org' ? `#lines-${line}` : `#L${line}`;
  return `${base.url}/${sep}/${ref}/${path}${line > 0 ? fragment : ''}`;
}

/**
 * Normalisiert eine Git-URL (SSH `git@host:org/repo.git`, `https://host/org/repo.git`,
 * mit/ohne `.git`) auf eine Web-Basis-URL `https://host/org/repo`. Nur `http(s)`/SSH zu
 * bekannten Hosts ergeben einen Treffer; alles andere (lokale Pfade, `file://`) → `null`.
 */
function webBase(location: string): { url: string; host: string } | null {
  let host: string;
  let path: string;

  const ssh = /^git@([^:]+):(.+)$/.exec(location);
  if (ssh) {
    host = ssh[1];
    path = ssh[2];
  } else {
    let httpMatch: RegExpExecArray | null = /^https?:\/\/([^/]+)\/(.+)$/.exec(location);
    if (!httpMatch) {
      // `ssh://git@host/org/repo`
      httpMatch = /^ssh:\/\/(?:[^@]+@)?([^/]+)\/(.+)$/.exec(location);
    }
    if (!httpMatch) {
      return null;
    }
    host = httpMatch[1].replace(/:\d+$/, '');
    path = httpMatch[2];
  }

  // Nutzer-Info in der Host-Komponente (`user@host`) entfernen.
  host = host.replace(/^[^@]+@/, '');
  path = path.replace(/\.git$/, '').replace(/\/+$/, '');
  if (!path) {
    return null;
  }
  return { url: `https://${host}/${path}`, host };
}
