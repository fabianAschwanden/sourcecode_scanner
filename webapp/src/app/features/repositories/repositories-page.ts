import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
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

        <!-- Sammelaktions-Leiste (WR-67) -->
        @if (selected().size > 0) {
          <div class="mb-2 flex flex-wrap items-center gap-2 rounded border border-default bg-surface px-3 py-2 text-sm">
            <span class="font-medium">{{ t('bulk.selected', { count: selected().size }) }}</span>
            <button (click)="bulkScan()" class="text-accent hover:underline">{{ t('bulk.scan') }}</button>
            <button (click)="bulkRemediation(true)" class="text-accent hover:underline">
              {{ t('bulk.remediationOn') }}
            </button>
            <button (click)="bulkRemediation(false)" class="text-accent hover:underline">
              {{ t('bulk.remediationOff') }}
            </button>
            <button (click)="bulkDelete()" class="text-sev-high hover:underline">{{ t('common.delete') }}</button>
            <button (click)="clearSelection()" class="text-muted hover:underline">{{ t('bulk.clear') }}</button>
          </div>
        }

        <!-- Karten (WR-82): Name · Sichtbarkeits-Badge · Beschreibung · Sprache · Aktualisiert -->
        <ul class="divide-y divide-default border-t border-default">
          @if (cards().length > 0) {
            <li class="flex items-center gap-2 py-2 text-xs text-muted">
              <input type="checkbox" [checked]="allSelected()" (change)="toggleSelectAll()" />
              {{ t('bulk.selectAll') }}
            </li>
          }
          @for (c of cards(); track c.id) {
            <li class="flex items-start justify-between gap-4 py-4">
              <div class="flex min-w-0 items-start gap-2">
                <input
                  type="checkbox"
                  class="mt-1"
                  [checked]="selected().has(c.id)"
                  (change)="toggle(c.id)"
                />
                <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <button
                    (click)="openInsights(c)"
                    class="font-semibold text-accent hover:underline"
                  >
                    {{ c.name }}
                  </button>
                  <span class="rounded-full border border-default px-2 text-xs text-muted">
                    {{ c.visibility }} · {{ c.type }}
                  </span>
                </div>
                @if (c.lastStatus === 'FAILED') {
                  <p class="mt-1 text-sm font-medium" [style.color]="'var(--color-sev-high)'">
                    {{ t('repos.card.lastScanFailed') }}<span class="font-normal">
                      — {{ c.lastError || t('common.error') }}</span
                    >
                  </p>
                }
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
                </div>
              </div>
              <div class="flex shrink-0 items-center gap-2">
                <button
                  (click)="openInsights(c)"
                  class="rounded border border-default px-3 py-1.5 text-sm hover:text-accent"
                >
                  {{ t('repos.card.insights') }}
                </button>
                <button
                  (click)="editCard(c)"
                  class="rounded border border-default px-3 py-1.5 text-sm hover:text-accent"
                >
                  {{ t('common.edit') }}
                </button>
                <button
                  (click)="scanCard(c)"
                  [disabled]="scanning() === c.id"
                  class="rounded bg-accent px-3 py-1.5 text-sm text-white hover:bg-accent-emphasis disabled:opacity-50"
                >
                  {{ scanning() === c.id ? t('repos.card.scanning') : t('repos.card.scan') }}
                </button>
              </div>
            </li>
          } @empty {
            <li class="py-4 text-muted">{{ t('repos.cards.empty') }}</li>
          }
        </ul>

        @if (message()) {
          <p class="mt-3 rounded border border-default px-3 py-2 text-sm text-muted">{{ message() }}</p>
        }
      } @else {
        <!-- Verwaltung: anlegen (GitHub-Stil-Formular) + Liste/löschen/testen -->
        <form (ngSubmit)="create()" class="mb-8 max-w-2xl">
          <div class="rounded-lg border border-default bg-surface p-5">
            <h3 class="text-lg font-semibold">
              {{ editingId() ? t('repos.edit.title') : t('repos.create.title') }}
            </h3>
            <p class="mt-1 mb-4 text-sm text-muted">{{ t('repos.create.intro') }}</p>

            <h4 class="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">
              {{ t('repos.create.section.basics') }}
            </h4>

            <div class="mb-4 grid gap-1">
              <label class="text-sm font-medium" for="repoName">{{ t('repos.name') }}</label>
              <input
                id="repoName"
                [(ngModel)]="name"
                name="name"
                [placeholder]="t('repos.name')"
                required
                class="w-full rounded border border-default bg-canvas px-3 py-2"
              />
              <span class="text-xs text-muted">{{ t('repos.name.help') }}</span>
            </div>

            <div class="mb-4 grid gap-1">
              <label class="text-sm font-medium" for="repoType">{{ t('repos.col.type') }}</label>
              <select
                id="repoType"
                [(ngModel)]="type"
                name="type"
                class="w-full rounded border border-default bg-canvas px-3 py-2"
              >
                <option value="localGit">localGit</option>
                <option value="github">github</option>
                <option value="gitlab">gitlab</option>
                <option value="bitbucket">bitbucket</option>
              </select>
              <span class="text-xs text-muted">{{ t('repos.type.tooltip') }}</span>
            </div>

            <div class="mb-4 grid gap-1">
              <label class="text-sm font-medium" for="repoLocation">{{ t('repos.location') }}</label>
              <input
                id="repoLocation"
                [(ngModel)]="location"
                name="location"
                [placeholder]="t('repos.location')"
                required
                class="w-full rounded border border-default bg-canvas px-3 py-2 font-mono text-sm"
              />
              <span class="text-xs text-muted">{{ t('repos.location.help') }}</span>
            </div>

            <div class="mb-4 grid gap-1">
              <label class="text-sm font-medium" for="repoDescription">
                {{ t('repos.description') }}
              </label>
              <input
                id="repoDescription"
                [(ngModel)]="description"
                name="description"
                [placeholder]="t('repos.description')"
                class="w-full rounded border border-default bg-canvas px-3 py-2"
              />
              <span class="text-xs text-muted">{{ t('repos.description.help') }}</span>
            </div>

            <div class="mb-5 grid gap-1">
              <span class="text-sm font-medium">{{ t('repos.visibility') }}</span>
              <label class="flex items-center gap-2 text-sm">
                <input type="radio" [(ngModel)]="visibility" name="visibility" value="private" />
                private
              </label>
              <label class="flex items-center gap-2 text-sm">
                <input type="radio" [(ngModel)]="visibility" name="visibility" value="public" />
                public
              </label>
              <span class="text-xs text-muted">{{ t('repos.visibility.help') }}</span>
            </div>

            <h4 class="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">
              {{ t('repos.create.section.access') }}
            </h4>

            <div class="mb-4 grid gap-1">
              <label class="text-sm font-medium" for="repoToken">{{ t('repos.tokenRef') }}</label>
              <input
                id="repoToken"
                [(ngModel)]="tokenRef"
                name="tokenRef"
                [placeholder]="t('repos.tokenRef')"
                class="w-full rounded border border-default bg-canvas px-3 py-2 font-mono text-sm"
              />
              <span class="text-xs text-muted">{{ t('repos.tokenRef.help') }}</span>
            </div>

            <div class="mb-4 grid gap-1">
              <label class="text-sm font-medium" for="repoEmails">
                {{ t('repos.reportEmails') }}
              </label>
              <input
                id="repoEmails"
                [(ngModel)]="reportEmails"
                name="reportEmails"
                [placeholder]="t('repos.reportEmails')"
                class="w-full rounded border border-default bg-canvas px-3 py-2"
              />
              <span class="text-xs text-muted">{{ t('repos.reportEmails.help') }}</span>
            </div>

            <label
              class="mb-5 flex items-center gap-2 text-sm"
              [title]="t('repos.remediation.tooltip')"
            >
              <input [(ngModel)]="remediationEnabled" name="remediationEnabled" type="checkbox" />
              {{ t('repos.remediation') }}
            </label>

            <div class="flex justify-end gap-2 border-t border-default pt-4">
              <button
                type="button"
                (click)="cancelEdit()"
                class="rounded border border-default px-3 py-2 text-sm hover:text-accent"
              >
                {{ t('repos.cancel') }}
              </button>
              <button
                type="submit"
                [disabled]="!name || !location"
                class="rounded bg-accent px-4 py-2 text-sm text-white hover:bg-accent-emphasis disabled:opacity-50"
              >
                {{ editingId() ? t('common.save') : t('common.create') }}
              </button>
            </div>
          </div>
        </form>

        <h3 class="mb-2 font-medium text-fg">{{ t('repos.manage.title') }}</h3>

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
                  <button (click)="edit(s)" class="text-accent hover:underline">
                    {{ t('common.edit') }}
                  </button>
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
  private readonly router = inject(Router);

  protected t(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }

  protected readonly view = signal<'cards' | 'list'>('cards');
  protected readonly cards = signal<RepositoryCard[]>([]);
  protected readonly languages = signal<string[]>([]);
  protected readonly scanning = signal<string | null>(null);
  /** Mehrfachauswahl für Sammelaktionen (WR-67). */
  protected readonly selected = signal<Set<string>>(new Set());
  protected q = '';
  protected filterType = '';
  protected filterLanguage = '';
  protected sort = 'name';

  protected readonly sources = signal<RepositorySource[]>([]);
  /** Gesetzt, wenn ein bestehendes Repo bearbeitet wird (sonst Anlegen-Modus). */
  protected readonly editingId = signal<string | null>(null);
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
    // Nav-Link erneut anklicken ⇒ zurück in die Karten-/Listenansicht (onSameUrlNavigation: 'reload').
    inject(Router)
      .events.pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        filter((e) => e.urlAfterRedirects.startsWith('/repositories')),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => this.setView('cards'));
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

  // --- Mehrfachauswahl + Sammelaktionen (WR-67) ------------------------------------------------

  protected toggle(id: string): void {
    const next = new Set(this.selected());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.selected.set(next);
  }

  protected allSelected(): boolean {
    const c = this.cards();
    return c.length > 0 && c.every((x) => this.selected().has(x.id));
  }

  protected toggleSelectAll(): void {
    this.selected.set(this.allSelected() ? new Set() : new Set(this.cards().map((c) => c.id)));
  }

  protected clearSelection(): void {
    this.selected.set(new Set());
  }

  protected bulkScan(): void {
    const ids = [...this.selected()];
    if (ids.length === 0) return;
    this.api.bulkScanRepos(ids, 'full').subscribe((r) => this.afterBulk(r));
  }

  protected bulkRemediation(enabled: boolean): void {
    const ids = [...this.selected()];
    if (ids.length === 0) return;
    this.api.bulkRepoRemediation(ids, enabled).subscribe((r) => this.afterBulk(r));
  }

  protected bulkDelete(): void {
    const ids = [...this.selected()];
    if (ids.length === 0) return;
    this.api.bulkDeleteRepos(ids).subscribe((r) => this.afterBulk(r));
  }

  private afterBulk(r: { succeeded: number; total: number }): void {
    this.message.set(this.t('bulk.done', { succeeded: r.succeeded, total: r.total }));
    this.clearSelection();
    this.reload();
    this.reloadCards();
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

  /** Öffnet die Funde/Code-Scanning-Ansicht, vorgefiltert auf dieses Repo (WR-69/WR-82). */
  protected openInsights(card: RepositoryCard): void {
    this.router.navigate(['/findings'], { queryParams: { repo: card.name } });
  }

  /** Startet einen Full-Scan für das Repo und wechselt zur Scans-Ansicht mit Live-Fortschritt. */
  protected scanCard(card: RepositoryCard): void {
    this.scanning.set(card.id);
    this.api.startScan(card.id, 'full').subscribe({
      next: () => {
        this.scanning.set(null);
        this.message.set(this.t('repos.card.scanStarted', { name: card.name }));
        this.router.navigate(['/scans']);
      },
      error: (err) => {
        this.scanning.set(null);
        this.message.set(err?.error?.error ?? this.t('common.error'));
      },
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
    if (!source.id) {
      return;
    }
    this.api
      .updateSource(source.id, { ...source, remediationEnabled: !source.remediationEnabled })
      .subscribe(() => {
        this.reload();
        this.reloadCards();
      });
  }

  /** Lädt ein bestehendes Repo zum Bearbeiten ins Formular (Verwaltungsansicht). */
  protected edit(source: RepositorySource): void {
    this.editingId.set(source.id);
    this.name = source.name;
    this.type = source.type;
    this.location = source.location;
    this.description = source.description;
    this.visibility = source.visibility;
    this.tokenRef = source.tokenRef ?? '';
    this.reportEmails = source.reportEmails.join(', ');
    this.remediationEnabled = source.remediationEnabled;
    this.message.set('');
    this.view.set('list');
  }

  protected cancelEdit(): void {
    this.resetForm();
    this.setView('cards');
  }

  /**
   * Bearbeiten direkt aus der Karten-Übersicht: löst die vollständige Quelle (mit Location, Token,
   * Report-E-Mails …) anhand der Karten-ID auf und öffnet das Bearbeiten-Formular. Fehlt die Quelle
   * lokal noch, wird sie kurz nachgeladen.
   */
  protected editCard(card: RepositoryCard): void {
    const source = this.sources().find((s) => s.id === card.id);
    if (source) {
      this.edit(source);
      return;
    }
    this.api.sources().subscribe((list) => {
      this.sources.set(list);
      const found = list.find((s) => s.id === card.id);
      if (found) {
        this.edit(found);
      }
    });
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
    const id = this.editingId();
    const source: RepositorySource = {
      id,
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
    const request$ = id ? this.api.updateSource(id, source) : this.api.createSource(source);
    request$.subscribe(() => {
      this.resetForm();
      this.reload();
      // Nach erfolgreichem Anlegen/Bearbeiten zurück in die initiale Karten-/Listenansicht.
      this.setView('cards');
    });
  }

  private resetForm(): void {
    this.editingId.set(null);
    this.name = '';
    this.type = 'localGit';
    this.location = '';
    this.description = '';
    this.visibility = 'private';
    this.tokenRef = '';
    this.reportEmails = '';
    this.remediationEnabled = false;
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
