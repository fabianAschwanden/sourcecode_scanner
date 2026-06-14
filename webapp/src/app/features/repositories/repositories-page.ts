import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ScannerApi } from '../../core/services/scanner-api';
import { RepositorySource } from '../../core/models/scanner';
import { I18nService } from '../../core/i18n/i18n.service';

/** Repository-Quellen verwalten (WR-02): anlegen, löschen, Verbindung testen. Token nur als Referenz. */
@Component({
  selector: 'app-repositories-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">{{ t('repos.title') }}</h2>

      <form (ngSubmit)="create()" class="mb-6 flex flex-wrap items-end gap-2">
        <input
          [(ngModel)]="name"
          name="name"
          placeholder="Name"
          title="Eindeutiger Name der Quelle, z. B. wm-tippspiel oder team-a/payment-service"
          required
          class="rounded border border-default px-2 py-1"
        />
        <select
          [(ngModel)]="type"
          name="type"
          title="Quellentyp: localGit (lokaler Pfad) oder eine Plattform (github/gitlab/bitbucket)"
          class="rounded border border-default px-2 py-1"
        >
          <option value="localGit">localGit</option>
          <option value="github">github</option>
          <option value="gitlab">gitlab</option>
          <option value="bitbucket">bitbucket</option>
        </select>
        <input
          [(ngModel)]="location"
          name="location"
          placeholder="Pfad / Clone-URL"
          title="localGit: lokaler Pfad, z. B. /Users/me/git/projekt — Plattform: Clone-URL, z. B. https://github.com/org/repo.git"
          required
          class="w-72 rounded border border-default px-2 py-1"
        />
        <input
          [(ngModel)]="tokenRef"
          name="tokenRef"
          placeholder="tokenRef (env:NAME)"
          title="Secret-Referenz, kein Klartext-Token — z. B. env:GITHUB_TOKEN oder vault:secret/scanner#token"
          class="rounded border border-default px-2 py-1"
        />
        <input
          [(ngModel)]="reportEmails"
          name="reportEmails"
          placeholder="Report-E-Mails (komma-getrennt)"
          title="Empfänger für den Report nach Scans dieses Repos, z. B. team@firma.ch, secops@firma.ch"
          class="w-72 rounded border border-default px-2 py-1"
        />
        <label
          class="flex items-center gap-1 text-sm text-muted"
          title="Aktiviert Auto-Fix per PR und History-Scrub für dieses Repo (opt-in, RMR-02). Standardmässig aus."
        >
          <input [(ngModel)]="remediationEnabled" name="remediationEnabled" type="checkbox" />
          {{ t('repos.remediation') }}
        </label>
        <button
          type="submit"
          class="rounded bg-accent px-3 py-1 text-white hover:bg-accent-emphasis"
        >
          {{ t('common.create') }}
        </button>
      </form>

      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-default text-left text-muted">
            <th class="py-2">{{ t('repos.name') }}</th>
            <th>{{ t('repos.col.type') }}</th>
            <th>{{ t('repos.col.location') }}</th>
            <th>{{ t('repos.col.token') }}</th>
            <th>{{ t('repos.col.reportEmails') }}</th>
            <th>{{ t('repos.remediation') }}</th>
            <th>{{ t('repos.col.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          @for (s of sources(); track s.id) {
            <tr class="border-b border-default">
              <td class="py-2">{{ s.name }}</td>
              <td>{{ s.type }}</td>
              <td class="font-mono text-xs">{{ s.location }}</td>
              <td>{{ s.tokenRef ?? '—' }}</td>
              <td class="text-xs">{{ s.reportEmails.length ? s.reportEmails.join(', ') : '—' }}</td>
              <td>
                <button
                  (click)="toggleRemediation(s)"
                  [title]="
                    s.remediationEnabled
                      ? 'Remediation aktiv — klicken zum Deaktivieren'
                      : 'Remediation deaktiviert — klicken zum Aktivieren (opt-in, RMR-02)'
                  "
                  class="hover:underline"
                  [class.text-accent]="s.remediationEnabled"
                  [class.text-muted]="!s.remediationEnabled"
                >
                  {{
                    s.remediationEnabled ? t('repos.remediation.on') : t('repos.remediation.off')
                  }}
                </button>
              </td>
              <td class="space-x-2">
                <button (click)="test(s)" class="text-accent hover:underline">
                  {{ t('common.test') }}
                </button>
                @if (s.remediationEnabled) {
                  <button
                    (click)="scrubDryRun(s)"
                    title="Zeigt redigiert, welche Secrets aus der Git-Historie entfernt würden — ohne Änderung (RMR-22)."
                    class="text-accent hover:underline"
                  >
                    {{ t('repos.scrubPreview') }}
                  </button>
                }
                <button (click)="remove(s)" class="text-sev-high hover:underline">
                  {{ t('common.delete') }}
                </button>
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="7" class="py-3 text-muted">{{ t('repos.empty') }}</td>
            </tr>
          }
        </tbody>
      </table>

      @if (message()) {
        <pre class="mt-4 rounded border border-default p-3 text-xs whitespace-pre-wrap">{{
          message()
        }}</pre>
      }
    </section>
  `,
})
export class RepositoriesPage {
  private readonly api = inject(ScannerApi);
  private readonly i18n = inject(I18nService);

  protected t(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }

  protected readonly sources = signal<RepositorySource[]>([]);
  protected name = '';
  protected type = 'localGit';
  protected location = '';
  protected tokenRef = '';
  protected reportEmails = '';
  protected remediationEnabled = false;
  protected readonly message = signal<string>('');

  constructor() {
    this.reload();
  }

  protected toggleRemediation(source: RepositorySource): void {
    this.api
      .createSource({ ...source, remediationEnabled: !source.remediationEnabled })
      .subscribe(() => this.reload());
  }

  protected scrubDryRun(source: RepositorySource): void {
    if (!source.id) {
      return;
    }
    this.message.set('Berechne Scrub-Vorschau …');
    this.api.scrubDryRun(source.id).subscribe({
      next: (r) =>
        this.message.set(
          `Werkzeug verfügbar: ${r.toolAvailable ? 'ja' : 'nein'} — ${r.affectedSecrets} Secret(s)\n\n${r.diffSummary}`,
        ),
      error: (err) =>
        this.message.set(`Scrub-Vorschau nicht möglich: ${err?.error?.error ?? 'Fehler'}`),
    });
  }

  protected create(): void {
    if (!this.name || !this.location) {
      return;
    }
    const source: RepositorySource = {
      id: null,
      name: this.name,
      type: this.type,
      location: this.location,
      branches: [],
      tokenRef: this.tokenRef || null,
      enabled: true,
      reportEmails: this.reportEmails
        .split(',')
        .map((e) => e.trim())
        .filter((e) => e.length > 0),
      remediationEnabled: this.remediationEnabled,
    };
    this.api.createSource(source).subscribe(() => {
      this.name = '';
      this.location = '';
      this.tokenRef = '';
      this.reportEmails = '';
      this.remediationEnabled = false;
      this.reload();
    });
  }

  protected test(source: RepositorySource): void {
    if (!source.id) {
      return;
    }
    this.api
      .testConnection(source.id)
      .subscribe((r) => alert(r.reachable ? 'Erreichbar' : 'Nicht erreichbar'));
  }

  protected remove(source: RepositorySource): void {
    if (!source.id) {
      return;
    }
    this.api.deleteSource(source.id).subscribe(() => this.reload());
  }

  private reload(): void {
    this.api.sources().subscribe((list) => this.sources.set(list));
  }
}
