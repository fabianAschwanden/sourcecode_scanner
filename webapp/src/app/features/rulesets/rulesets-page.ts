import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { ScannerApi } from '../../core/services/scanner-api';
import {
  DataSource,
  EnforcementStatus,
  RepositorySource,
  RuleInfo,
  RuleMatchMode,
  Ruleset,
  Severity,
} from '../../core/models/scanner';
import { I18nService } from '../../core/i18n/i18n.service';

interface RuleRow {
  ruleId: string;
  title: string;
  enabled: boolean;
  severity: Severity;
  matchMode: RuleMatchMode;
  dataSourceName: string;
}

/**
 * Rulesets verwalten (FR-27, WR-90..96), GitHub-„Rulesets"-Stil: Übersicht + Editor mit Name,
 * Enforcement-Status, Geltungsbereich (global/Repo-Auswahl) und einer Regel-Liste mit Checkbox,
 * Severity und Abgleichsmodus je Regel. Alle Texte über i18n.
 */
@Component({
  selector: 'app-rulesets-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="p-6">
      <div class="mb-4 flex items-center justify-between">
        <h2 class="text-xl font-semibold">{{ t('rulesets.title') }}</h2>
        @if (view() === 'list') {
          <button
            (click)="startNew()"
            class="rounded bg-accent px-3 py-1.5 text-sm text-white hover:bg-accent-emphasis"
          >
            + {{ t('rulesets.new') }}
          </button>
        }
      </div>

      @if (view() === 'list') {
        <p class="mb-4 text-sm text-muted">{{ t('rulesets.intro') }}</p>
        @if (selected().size > 0) {
          <div class="mb-2 flex flex-wrap items-center gap-2 rounded border border-default bg-surface px-3 py-2 text-sm">
            <span class="font-medium">{{ t('bulk.selected', { count: selected().size }) }}</span>
            <button (click)="bulkDelete()" class="text-sev-high hover:underline">{{ t('common.delete') }}</button>
            <button (click)="clearSelection()" class="text-muted hover:underline">{{ t('bulk.clear') }}</button>
          </div>
        }
        <ul class="divide-y divide-default border-t border-default">
          @if (rulesets().length > 0) {
            <li class="flex items-center gap-2 py-2 text-xs text-muted">
              <input type="checkbox" [checked]="allSelected()" (change)="toggleSelectAll()" />
              {{ t('bulk.selectAll') }}
            </li>
          }
          @for (r of rulesets(); track r.id) {
            <li class="flex items-start justify-between gap-4 py-3">
              <div class="flex min-w-0 items-start gap-2">
                <input type="checkbox" class="mt-1" [checked]="selected().has(r.id!)" (change)="toggle(r.id!)" />
                <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <button (click)="edit(r)" class="font-semibold text-accent hover:underline">
                    {{ r.name }}
                  </button>
                  <span class="rounded-full border border-default px-2 text-xs text-muted">
                    {{ t('rulesets.enforcement.' + r.enforcement) }}
                  </span>
                </div>
                <p class="mt-1 text-xs text-muted">
                  {{ r.global ? t('rulesets.scope.global') : t('rulesets.scope.repos') }} ·
                  {{ r.rules.length }} {{ t('rulesets.col.rules') }}
                </p>
                </div>
              </div>
              <button (click)="remove(r)" class="shrink-0 text-sm text-sev-high hover:underline">
                {{ t('common.delete') }}
              </button>
            </li>
          } @empty {
            <li class="py-4 text-muted">{{ t('rulesets.empty') }}</li>
          }
        </ul>
      } @else {
        <!-- Editor -->
        <div class="max-w-2xl space-y-5">
          <div class="grid gap-1">
            <label class="text-sm font-medium" for="rsName">{{ t('rulesets.name') }} *</label>
            <input
              id="rsName"
              [(ngModel)]="name"
              class="w-full rounded border border-default bg-canvas px-3 py-2"
            />
          </div>

          <div class="grid gap-1">
            <label class="text-sm font-medium" for="rsEnf">{{ t('rulesets.enforcement') }}</label>
            <select
              id="rsEnf"
              [(ngModel)]="enforcement"
              class="w-56 rounded border border-default bg-canvas px-3 py-2"
            >
              <option value="DISABLED">{{ t('rulesets.enforcement.DISABLED') }}</option>
              <option value="ACTIVE">{{ t('rulesets.enforcement.ACTIVE') }}</option>
            </select>
          </div>

          <div class="grid gap-1">
            <span class="text-sm font-medium">{{ t('rulesets.scope') }}</span>
            <label class="flex items-center gap-2 text-sm">
              <input type="radio" [(ngModel)]="global" [value]="true" name="scope" />
              {{ t('rulesets.scope.global') }}
            </label>
            <label class="flex items-center gap-2 text-sm">
              <input type="radio" [(ngModel)]="global" [value]="false" name="scope" />
              {{ t('rulesets.scope.repos') }}
            </label>
            @if (!global) {
              <div class="mt-1 flex flex-wrap gap-3 rounded border border-default p-2">
                @for (s of sources(); track s.id) {
                  <label class="flex items-center gap-1 text-xs">
                    <input type="checkbox" [checked]="repoSelected(s.name)" (change)="toggleRepo(s.name)" />
                    {{ s.name }}
                  </label>
                }
              </div>
            }
          </div>

          <div>
            <h3 class="text-sm font-semibold">{{ t('rulesets.rules') }}</h3>
            <p class="mb-2 text-xs text-muted">{{ t('rulesets.rules.intro') }}</p>
            <ul class="divide-y divide-default border-t border-default">
              @for (row of ruleRows(); track row.ruleId) {
                <li class="flex flex-wrap items-center gap-3 py-3">
                  <label class="flex min-w-48 items-center gap-2 text-sm">
                    <input type="checkbox" [(ngModel)]="row.enabled" [name]="'en-' + row.ruleId" />
                    <span class="font-mono text-xs">{{ row.ruleId }}</span>
                  </label>
                  <select
                    [(ngModel)]="row.severity"
                    [name]="'sev-' + row.ruleId"
                    [title]="t('rulesets.rule.severity')"
                    class="rounded border border-default bg-canvas px-2 py-1 text-xs"
                  >
                    @for (sev of severities; track sev) {
                      <option [value]="sev">{{ sev }}</option>
                    }
                  </select>
                  @if (valueRule(row.ruleId)) {
                    <select
                      [(ngModel)]="row.matchMode"
                      [name]="'mode-' + row.ruleId"
                      [title]="t('rulesets.rule.mode')"
                      class="rounded border border-default bg-canvas px-2 py-1 text-xs"
                    >
                      <option value="ALWAYS">{{ t('rulesets.mode.ALWAYS') }}</option>
                      <option value="LIST">{{ t('rulesets.mode.LIST') }}</option>
                      <option value="API">{{ t('rulesets.mode.API') }}</option>
                    </select>
                    @if (row.matchMode !== 'ALWAYS') {
                      <select
                        [(ngModel)]="row.dataSourceName"
                        [name]="'ds-' + row.ruleId"
                        [title]="t('rulesets.mode.dataSource')"
                        class="rounded border border-default bg-canvas px-2 py-1 text-xs"
                      >
                        <option value="">{{ t('rulesets.mode.dataSource') }}</option>
                        @for (d of dataSources(); track d.id) {
                          <option [value]="d.name">{{ d.name }}</option>
                        }
                      </select>
                    }
                  }
                </li>
              }
            </ul>
          </div>

          @if (message()) {
            <p class="rounded border border-default px-3 py-2 text-sm text-muted">{{ message() }}</p>
          }

          <div class="flex gap-2 border-t border-default pt-4">
            <button
              (click)="view.set('list')"
              class="rounded border border-default px-3 py-2 text-sm hover:text-accent"
            >
              {{ t('repos.cancel') }}
            </button>
            <button
              (click)="save()"
              [disabled]="!name.trim()"
              class="rounded bg-accent px-4 py-2 text-sm text-white hover:bg-accent-emphasis disabled:opacity-50"
            >
              {{ t('rulesets.save') }}
            </button>
          </div>
        </div>
      }
    </section>
  `,
})
export class RulesetsPage {
  private readonly api = inject(ScannerApi);
  private readonly i18n = inject(I18nService);

  protected t(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }

  protected readonly severities: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
  protected readonly view = signal<'list' | 'edit'>('list');
  protected readonly rulesets = signal<Ruleset[]>([]);
  protected readonly sources = signal<RepositorySource[]>([]);
  protected readonly dataSources = signal<DataSource[]>([]);
  protected readonly ruleRows = signal<RuleRow[]>([]);
  protected readonly message = signal<string>('');

  protected editingId: string | null = null;
  protected name = '';
  protected enforcement: EnforcementStatus = 'DISABLED';
  protected global = true;
  protected repoNames: string[] = [];

  constructor() {
    this.reload();
    this.api.sources().subscribe((list) => this.sources.set(list));
    this.api.dataSources().subscribe((list) => this.dataSources.set(list));
    // Nav-Link erneut anklicken ⇒ zurück in die Listenansicht (onSameUrlNavigation: 'reload').
    inject(Router)
      .events.pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        filter((e) => e.urlAfterRedirects.startsWith('/rulesets')),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => this.view.set('list'));
  }

  protected startNew(): void {
    this.editingId = null;
    this.name = '';
    this.enforcement = 'DISABLED';
    this.global = true;
    this.repoNames = [];
    this.message.set('');
    this.loadRuleRows(null);
    this.view.set('edit');
  }

  protected edit(r: Ruleset): void {
    this.editingId = r.id;
    this.name = r.name;
    this.enforcement = r.enforcement;
    this.global = r.global;
    this.repoNames = [...r.repoNames];
    this.message.set('');
    this.loadRuleRows(r);
    this.view.set('edit');
  }

  protected remove(r: Ruleset): void {
    if (!r.id) {
      return;
    }
    this.api.deleteRuleset(r.id).subscribe(() => this.reload());
  }

  /** Mehrfachauswahl für Sammelaktionen (WR-67). */
  protected readonly selected = signal<Set<string>>(new Set());

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
    const list = this.rulesets();
    return list.length > 0 && list.every((r) => !!r.id && this.selected().has(r.id));
  }

  protected toggleSelectAll(): void {
    if (this.allSelected()) {
      this.selected.set(new Set());
    } else {
      this.selected.set(new Set(this.rulesets().map((r) => r.id!).filter((id) => !!id)));
    }
  }

  protected clearSelection(): void {
    this.selected.set(new Set());
  }

  protected bulkDelete(): void {
    const ids = [...this.selected()];
    if (ids.length === 0) {
      return;
    }
    this.api.bulkDeleteRulesets(ids).subscribe(() => {
      this.clearSelection();
      this.reload();
    });
  }

  protected valueRule(ruleId: string): boolean {
    // Wertbezogene Regeln, für die ein Abgleichsmodus sinnvoll ist (DR-52).
    return ruleId === 'email' || ruleId === 'phone' || ruleId === 'iban' || ruleId === 'creditcard';
  }

  protected repoSelected(name: string): boolean {
    return this.repoNames.includes(name);
  }

  protected toggleRepo(name: string): void {
    this.repoNames = this.repoNames.includes(name)
      ? this.repoNames.filter((n) => n !== name)
      : [...this.repoNames, name];
  }

  protected save(): void {
    const rules = this.ruleRows().map((row) => ({
      ruleId: row.ruleId,
      enabled: row.enabled,
      severity: row.severity,
      matchMode: row.matchMode,
      dataSourceName: row.dataSourceName || null,
    }));
    const ruleset: Ruleset = {
      id: this.editingId,
      name: this.name.trim(),
      enforcement: this.enforcement,
      global: this.global,
      repoNames: this.global ? [] : this.repoNames,
      rules,
    };
    this.api.saveRuleset(ruleset).subscribe({
      next: () => {
        this.message.set(this.t('rulesets.msg.saved'));
        this.view.set('list');
        this.reload();
      },
      error: (err) =>
        this.message.set(this.t('rulesets.msg.failed', { error: err?.error?.error ?? this.t('common.error') })),
    });
  }

  private loadRuleRows(existing: Ruleset | null): void {
    this.api.detectorRules().subscribe((available: RuleInfo[]) => {
      const rows = available.map((ri) => {
        const o = existing?.rules.find((x) => x.ruleId === ri.id);
        return {
          ruleId: ri.id,
          title: ri.title,
          enabled: o ? o.enabled : ri.defaultEnabled !== 'false',
          severity: (o?.severity ?? ri.defaultSeverity) as Severity,
          matchMode: (o?.matchMode ?? 'ALWAYS') as RuleMatchMode,
          dataSourceName: o?.dataSourceName ?? '',
        };
      });
      this.ruleRows.set(rows);
    });
  }

  private reload(): void {
    this.api.rulesets().subscribe((list) => this.rulesets.set(list));
  }
}
