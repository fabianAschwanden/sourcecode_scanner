import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ScannerApi } from '../../core/services/scanner-api';
import { RepositoryCard, RepositorySource } from '../../core/models/scanner';
import { I18nService } from '../../core/i18n/i18n.service';

/**
 * Repository-Quellen (WR-02). Standard ist die Repo-Übersicht im GitHub-Stil (Karten + Suche/Filter/
 * Sortierung, serverseitig, WR-80..85); die Verwaltung (anlegen/löschen/testen) liegt in einer
 * umschaltbaren Tabellen-Ansicht. Token nur als Referenz; alle Texte über i18n.
 */
@Component({
  selector: 'app-repositories-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="p-6">
      <div class="mb-4 flex items-center justify-between">
        <h2 class="text-xl font-semibold">{{ t('repos.title') }}</h2>
        <button (click)="toggleView()" class="text-sm text-muted hover:text-accent">
          {{ view() === 'cards' ? t('repos.toggleList') : t('repos.toggleCards') }}
        </button>
      </div>

      @if (view() === 'cards') {
        <!-- Toolbar: Suche · Typ · Sprache · Sortieren · Neu (WR-80/81) -->
        <div class="mb-4 flex flex-wrap items-center gap-2">
          <input
            [(ngModel)]="q"
            (ngModelChange)="reloadCards()"
            [placeholder]="t('repos.search')"
            class="min-w-64 flex-1 rounded border border-default bg-canvas px-3 py-2 text-sm"
          />
          <select
            [(ngModel)]="filterType"
            (ngModelChange)="reloadCards()"
            [title]="t('repos.filter.type')"
            class="rounded border border-default bg-canvas px-2 py-2 text-sm"
          >
            <option value="">{{ t('repos.filter.type.all') }}</option>
            <option value="localGit">localGit</option>
            <option value="github">github</option>
            <option value="gitlab">gitlab</option>
            <option value="bitbucket">bitbucket</option>
          </select>
          <select
            [(ngModel)]="filterLanguage"
            (ngModelChange)="reloadCards()"
            [title]="t('repos.filter.language')"
            class="rounded border border-default bg-canvas px-2 py-2 text-sm"
          >
            <option value="">{{ t('repos.filter.language.all') }}</option>
            @for (l of languages(); track l) {
              <option [value]="l">{{ l }}</option>
            }
          </select>
          <select
            [(ngModel)]="sort"
            (ngModelChange)="reloadCards()"
            [title]="t('repos.sort')"
            class="rounded border border-default bg-canvas px-2 py-2 text-sm"
          >
            <option value="name">{{ t('repos.sort') }}: {{ t('repos.sort.name') }}</option>
            <option value="updated">{{ t('repos.sort') }}: {{ t('repos.sort.updated') }}</option>
          </select>
          <button
            (click)="setView('list')"
            class="rounded bg-accent px-3 py-2 text-sm text-white hover:bg-accent-emphasis"
          >
            + {{ t('repos.new') }}
          </button>
        </div>

        <!-- Karten (WR-82): Name · Sichtbarkeits-Badge · Beschreibung · Sprache · Aktualisiert -->
        <ul class="divide-y divide-default border-t border-default">
          @for (c of cards(); track c.id) {
            <li class="py-4">
              <div class="flex items-center gap-2">
                <span class="font-semibold text-accent">{{ c.name }}</span>
                <span class="rounded-full border border-default px-2 text-xs text-muted">
                  {{ c.visibility }} · {{ c.type }}
                </span>
              </div>
              @if (c.description) {
                <p class="mt-1 text-sm text-muted">{{ c.description }}</p>
              }
              <div class="mt-1 flex items-center gap-3 text-xs text-muted">
                @if (c.language) {
                  <span class="flex items-center gap-1">
                    <span
                      class="inline-block h-3 w-3 rounded-full"
                      [style.background]="languageColor(c.language)"
                    ></span>
                    {{ c.language }}
                  </span>
                }
                <span>{{ t('repos.updated', { when: relativeTime(c.lastScanAt) }) }}</span>
              </div>
            </li>
          } @empty {
            <li class="py-4 text-muted">{{ t('repos.cards.empty') }}</li>
          }
        </ul>
      } @else {
        <!-- Verwaltung: anlegen/löschen/testen (Tabelle) -->
        <form (ngSubmit)="create()" class="mb-6 flex flex-wrap items-end gap-2">
          <input
            [(ngModel)]="name"
            name="name"
            [placeholder]="t('repos.name')"
            [title]="t('repos.name.tooltip')"
            required
            class="rounded border border-default px-2 py-1"
          />
          <select
            [(ngModel)]="type"
            name="type"
            [title]="t('repos.type.tooltip')"
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
            [placeholder]="t('repos.location')"
            [title]="t('repos.location.tooltip')"
            required
            class="w-72 rounded border border-default px-2 py-1"
          />
          <input
            [(ngModel)]="description"
            name="description"
            [placeholder]="t('repos.description')"
            class="w-72 rounded border border-default px-2 py-1"
          />
          <select
            [(ngModel)]="visibility"
            name="visibility"
            [title]="t('repos.visibility')"
            class="rounded border border-default px-2 py-1"
          >
            <option value="private">private</option>
            <option value="public">public</option>
          </select>
          <input
            [(ngModel)]="tokenRef"
            name="tokenRef"
            [placeholder]="t('repos.tokenRef')"
            [title]="t('repos.tokenRef.tooltip')"
            class="rounded border border-default px-2 py-1"
          />
          <input
            [(ngModel)]="reportEmails"
            name="reportEmails"
            [placeholder]="t('repos.reportEmails')"
            [title]="t('repos.reportEmails.tooltip')"
            class="w-72 rounded border border-default px-2 py-1"
          />
          <label
            class="flex items-center gap-1 text-sm text-muted"
            [title]="t('repos.remediation.tooltip')"
          >
            <input [(ngModel)]="remediationEnabled" name="remediationEnabled" type="checkbox" />
            {{ t('repos.remediation') }}
          </label>
          <button type="submit" class="rounded bg-accent px-3 py-1 text-white hover:bg-accent-emphasis">
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
                <td>{{ s.tokenRef ?? t('common.none') }}</td>
                <td class="text-xs">
                  {{ s.reportEmails.length ? s.reportEmails.join(', ') : t('common.none') }}
                </td>
                <td>
                  <button
                    (click)="toggleRemediation(s)"
                    [title]="
                      s.remediationEnabled
                        ? t('repos.remediation.toggleOn')
                        : t('repos.remediation.toggleOff')
                    "
                    class="hover:underline"
                    [class.text-accent]="s.remediationEnabled"
                    [class.text-muted]="!s.remediationEnabled"
                  >
                    {{ s.remediationEnabled ? t('repos.remediation.on') : t('repos.remediation.off') }}
                  </button>
                </td>
                <td class="space-x-2">
                  <button (click)="test(s)" class="text-accent hover:underline">
                    {{ t('common.test') }}
                  </button>
                  @if (s.remediationEnabled) {
                    <button
                      (click)="scrubDryRun(s)"
                      [title]="t('repos.scrubPreview.tooltip')"
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

  protected readonly view = signal<'cards' | 'list'>('cards');
  protected readonly cards = signal<RepositoryCard[]>([]);
  protected readonly languages = signal<string[]>([]);
  protected q = '';
  protected filterType = '';
  protected filterLanguage = '';
  protected sort = 'name';

  protected readonly sources = signal<RepositorySource[]>([]);
  protected name = '';
  protected type = 'localGit';
  protected location = '';
  protected description = '';
  protected visibility = 'private';
  protected tokenRef = '';
  protected reportEmails = '';
  protected remediationEnabled = false;
  protected readonly message = signal<string>('');

  constructor() {
    this.reloadCards();
    this.reload();
  }

  protected toggleView(): void {
    this.setView(this.view() === 'cards' ? 'list' : 'cards');
  }

  protected setView(view: 'cards' | 'list'): void {
    this.view.set(view);
    if (view === 'cards') {
      this.reloadCards();
    }
  }

  protected reloadCards(): void {
    this.api
      .repositoryCards({
        q: this.q || undefined,
        type: this.filterType || undefined,
        language: this.filterLanguage || undefined,
        sort: this.sort,
      })
      .subscribe((list) => {
        this.cards.set(list);
        // Sprach-Optionen aus dem vollen (ungefilterten Sprach-)Ergebnis ableiten.
        const langs = [...new Set(list.map((c) => c.language).filter((l) => l.length > 0))].sort();
        if (!this.filterLanguage) {
          this.languages.set(langs);
        }
      });
  }

  /** Relative Zeit (lokalisiert) für „Aktualisiert …"; null ⇒ nie gescannt. */
  protected relativeTime(iso: string | null): string {
    if (!iso) {
      return this.t('repos.neverScanned');
    }
    const diffMs = Date.now() - new Date(iso).getTime();
    const min = Math.round(diffMs / 60000);
    const rtf = new Intl.RelativeTimeFormat(this.i18n.lang(), { numeric: 'auto' });
    if (min < 60) return rtf.format(-min, 'minute');
    const hours = Math.round(min / 60);
    if (hours < 24) return rtf.format(-hours, 'hour');
    return rtf.format(-Math.round(hours / 24), 'day');
  }

  /** Stabile Farbe je Sprache (Hash → Hue) für den GitHub-artigen Punkt. */
  protected languageColor(language: string): string {
    let hash = 0;
    for (let i = 0; i < language.length; i++) {
      hash = language.charCodeAt(i) + ((hash << 5) - hash);
    }
    return `hsl(${Math.abs(hash) % 360} 65% 55%)`;
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
      description: this.description,
      visibility: this.visibility,
    };
    this.api.createSource(source).subscribe(() => {
      this.name = '';
      this.location = '';
      this.description = '';
      this.visibility = 'private';
      this.tokenRef = '';
      this.reportEmails = '';
      this.remediationEnabled = false;
      this.reload();
      this.reloadCards();
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
    this.api.deleteSource(source.id).subscribe(() => {
      this.reload();
      this.reloadCards();
    });
  }

  private reload(): void {
    this.api.sources().subscribe((list) => this.sources.set(list));
  }
}
