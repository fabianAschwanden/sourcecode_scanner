import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { ScannerApi } from '../../core/services/scanner-api';
import {
  AttributeCategory,
  AttributeRule,
  DataSource,
  DataSourceAuthType,
  DataSourceKind,
  Severity,
} from '../../core/models/scanner';
import { I18nService } from '../../core/i18n/i18n.service';

/**
 * Externe Datenquellen für Kundendaten-Erkennung verwalten (WR-50..54): anlegen, testweise abrufen
 * (redigiertes Attribut-Schema) und je Attribut das Mapping pflegen (prüfen/Severity/Kategorie).
 * Werte werden nie angezeigt — nur Feldnamen + maskierte Beispiele (WR-54). GitHub-Dark + Tooltips.
 */
@Component({
  selector: 'app-datasources-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">{{ t('ds.title') }}</h2>
      <p class="mb-4 text-sm text-muted">{{ t('ds.intro') }}</p>

      <form (ngSubmit)="create()" class="mb-6 flex flex-wrap items-end gap-2">
        <input
          [(ngModel)]="name"
          name="name"
          [placeholder]="t('repos.name')"
          [title]="t('ds.name.tooltip')"
          required
          class="rounded border border-default px-2 py-1"
        />
        <select
          [(ngModel)]="kind"
          name="kind"
          [title]="t('ds.kind.tooltip')"
          class="rounded border border-default px-2 py-1"
        >
          <option value="REST">{{ t('ds.kind.rest') }}</option>
          <option value="UPLOAD">{{ t('ds.kind.upload') }}</option>
        </select>
        @if (kind === 'REST') {
          <input
            [(ngModel)]="baseUrl"
            name="baseUrl"
            [placeholder]="t('ds.baseUrl')"
            [title]="t('ds.baseUrl.tooltip')"
            class="w-64 rounded border border-default px-2 py-1"
          />
          <input
            [(ngModel)]="path"
            name="path"
            [placeholder]="t('ds.path')"
            [title]="t('ds.path.tooltip')"
            class="rounded border border-default px-2 py-1"
          />
          <input
            [(ngModel)]="recordsPath"
            name="recordsPath"
            [placeholder]="t('ds.recordsPath')"
            [title]="t('ds.recordsPath.tooltip')"
            class="rounded border border-default px-2 py-1"
          />
          <select
            [(ngModel)]="authType"
            name="authType"
            [title]="t('ds.auth.tooltip')"
            class="rounded border border-default px-2 py-1"
          >
            <option value="NONE">Keine Auth</option>
            <option value="BEARER">Bearer</option>
            <option value="BASIC">Basic</option>
            <option value="HEADER">Header</option>
          </select>
          <input
            [(ngModel)]="tokenRef"
            name="tokenRef"
            [placeholder]="t('repos.tokenRef')"
            [title]="t('ds.tokenRef.tooltip')"
            class="rounded border border-default px-2 py-1"
          />
        }
        <button
          type="submit"
          class="rounded bg-accent px-3 py-1 text-white hover:bg-accent-emphasis"
        >
          {{ editingId() ? t('common.save') : t('common.create') }}
        </button>
        @if (kind === 'REST') {
          <button
            type="button"
            (click)="probeDraft()"
            [title]="t('ds.probe.tooltip')"
            class="rounded border border-default px-3 py-1 hover:underline"
          >
            {{ t('ds.probe') }}
          </button>
        }
        @if (editingId()) {
          <button
            type="button"
            (click)="cancelEdit()"
            class="rounded border border-default px-3 py-1 hover:text-accent"
          >
            {{ t('repos.cancel') }}
          </button>
        }
      </form>

      @if (kind === 'UPLOAD') {
        <p class="mb-4 text-sm text-muted">{{ t('ds.uploadHint') }}</p>
      }

      @if (schema().length > 0) {
        <div class="mb-6 rounded border border-default p-3">
          <h3 class="mb-2 text-sm font-semibold">{{ t('ds.mapping.title') }}</h3>
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-default text-left text-muted">
                <th class="py-1">{{ t('ds.mapping.attribute') }}</th>
                <th>{{ t('ds.mapping.example') }}</th>
                <th>{{ t('ds.mapping.check') }}</th>
                <th>{{ t('findings.facet.severity') }}</th>
                <th>{{ t('ds.mapping.category') }}</th>
              </tr>
            </thead>
            <tbody>
              @for (a of schema(); track a.field) {
                <tr class="border-b border-default">
                  <td class="py-1 font-mono text-xs">{{ a.field }}</td>
                  <td class="font-mono text-xs text-muted">{{ a.maskedExample }}</td>
                  <td><input type="checkbox" [(ngModel)]="draftMap[a.field].check" /></td>
                  <td>
                    <select
                      [(ngModel)]="draftMap[a.field].severity"
                      class="rounded border border-default px-1"
                    >
                      @for (s of severities; track s) {
                        <option [value]="s">{{ s }}</option>
                      }
                    </select>
                  </td>
                  <td>
                    <select
                      [(ngModel)]="draftMap[a.field].category"
                      class="rounded border border-default px-1"
                    >
                      <option value="PII">PII</option>
                      <option value="CUSTOM">CUSTOM</option>
                    </select>
                  </td>
                </tr>
              }
            </tbody>
          </table>
          <button
            (click)="saveDraftWithMapping()"
            class="mt-2 rounded bg-accent px-3 py-1 text-white hover:bg-accent-emphasis"
          >
            {{ t('ds.mapping.save') }}
          </button>
        </div>
      }

      @if (message()) {
        <p class="mb-4 rounded border border-default px-3 py-2 text-sm text-muted">
          {{ message() }}
        </p>
      }

      @if (selected().size > 0) {
        <div
          class="mb-2 flex flex-wrap items-center gap-2 rounded border border-default bg-surface px-3 py-2 text-sm"
        >
          <span class="font-medium">{{ t('bulk.selected', { count: selected().size }) }}</span>
          <button (click)="bulkDelete()" class="text-sev-high hover:underline">
            {{ t('common.delete') }}
          </button>
          <button (click)="clearSelection()" class="text-muted hover:underline">
            {{ t('bulk.clear') }}
          </button>
        </div>
      }

      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-default text-left text-muted">
            <th class="py-2">
              <input type="checkbox" [checked]="allSelected()" (change)="toggleSelectAll()" />
            </th>
            <th class="py-2">{{ t('repos.name') }}</th>
            <th>{{ t('repos.col.type') }}</th>
            <th>{{ t('ds.col.source') }}</th>
            <th>{{ t('ds.col.active') }}</th>
            <th>{{ t('ds.col.checkedAttrs') }}</th>
            <th>{{ t('repos.col.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          @for (s of sources(); track s.id) {
            <tr class="border-b border-default">
              <td class="py-2">
                <input type="checkbox" [checked]="selected().has(s.id!)" (change)="toggle(s.id!)" />
              </td>
              <td class="py-2">{{ s.name }}</td>
              <td>{{ s.kind }}</td>
              <td class="font-mono text-xs">
                {{ s.kind === 'UPLOAD' ? t('ds.kind.upload') : s.baseUrl + s.path }}
              </td>
              <td>{{ s.enabled ? t('common.yes') : t('common.no') }}</td>
              <td class="text-xs">{{ checkedFields(s) || t('common.none') }}</td>
              <td class="space-x-2">
                @if (s.kind === 'UPLOAD') {
                  <label
                    class="cursor-pointer text-accent hover:underline"
                    [title]="t('ds.uploadAction.tooltip')"
                  >
                    {{ t('ds.upload') }}
                    <input
                      type="file"
                      class="hidden"
                      accept=".csv,.json,.txt"
                      (change)="onUpload(s, $event)"
                    />
                  </label>
                }
                <button (click)="editSource(s)" class="text-accent hover:underline">
                  {{ t('common.edit') }}
                </button>
                <button (click)="remove(s)" class="text-sev-high hover:underline">
                  {{ t('common.delete') }}
                </button>
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="7" class="py-3 text-muted">{{ t('ds.empty') }}</td>
            </tr>
          }
        </tbody>
      </table>
    </section>
  `,
})
export class DataSourcesPage {
  private readonly api = inject(ScannerApi);
  private readonly i18n = inject(I18nService);

  protected t(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }

  protected readonly severities: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
  protected readonly sources = signal<DataSource[]>([]);
  protected readonly schema = signal<{ field: string; maskedExample: string }[]>([]);
  protected readonly message = signal<string>('');

  /** Gesetzt, wenn eine bestehende Datenquelle bearbeitet wird (sonst Anlegen-Modus). */
  protected readonly editingId = signal<string | null>(null);
  protected name = '';
  protected kind: DataSourceKind = 'REST';
  protected baseUrl = '';
  protected path = '';
  protected recordsPath = '$[*]';
  protected authType: DataSourceAuthType = 'NONE';
  protected tokenRef = '';
  protected draftMap: Record<
    string,
    { check: boolean; severity: Severity; category: AttributeCategory }
  > = {};

  constructor() {
    this.reload();
    // Nav-Link erneut anklicken ⇒ ein halbfertiges Bearbeiten-Formular zurücksetzen.
    inject(Router)
      .events.pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        filter((e) => e.urlAfterRedirects.startsWith('/datasources')),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => {
        this.schema.set([]);
        this.resetForm();
      });
  }

  /** Lädt eine bestehende Datenquelle zum Bearbeiten ins Formular. */
  protected editSource(source: DataSource): void {
    this.editingId.set(source.id);
    this.name = source.name;
    this.kind = source.kind;
    this.baseUrl = source.baseUrl;
    this.path = source.path;
    this.recordsPath = source.recordsPath;
    this.authType = source.authType;
    this.tokenRef = source.tokenRef ?? '';
    this.draftMap = {};
    for (const a of source.attributes) {
      this.draftMap[a.field] = { check: a.check, severity: a.severity, category: a.category };
    }
    this.schema.set(source.attributes.map((a) => ({ field: a.field, maskedExample: '••••' })));
    this.message.set('');
  }

  protected cancelEdit(): void {
    this.schema.set([]);
    this.resetForm();
  }

  protected probeDraft(): void {
    this.message.set(this.t('ds.msg.fetching'));
    this.api.probeDataSource(this.draft([])).subscribe({
      next: (s) => {
        if (!s.reachable) {
          this.message.set(this.t('ds.msg.unreachable', { message: s.message }));
          this.schema.set([]);
          return;
        }
        this.draftMap = {};
        for (const a of s.attributes) {
          this.draftMap[a.field] = { check: false, severity: 'MEDIUM', category: 'PII' };
        }
        this.schema.set(s.attributes);
        this.message.set(
          this.t('ds.msg.records', { records: s.sampleRecords, attrs: s.attributes.length }),
        );
      },
      error: (err) =>
        this.message.set(
          this.t('ds.msg.fetchFailed', { error: err?.error?.error ?? this.t('common.error') }),
        ),
    });
  }

  protected saveDraftWithMapping(): void {
    const attributes: AttributeRule[] = this.schema().map((a) => ({
      field: a.field,
      check: this.draftMap[a.field].check,
      severity: this.draftMap[a.field].severity,
      category: this.draftMap[a.field].category,
    }));
    this.api.saveDataSource(this.draft(attributes)).subscribe(() => {
      this.message.set(this.t('ds.msg.saved'));
      this.schema.set([]);
      this.resetForm();
      this.reload();
    });
  }

  protected create(): void {
    if (!this.name || (this.kind === 'REST' && !this.baseUrl)) {
      return;
    }
    this.api.saveDataSource(this.draft([])).subscribe(() => {
      this.resetForm();
      this.reload();
    });
  }

  protected onUpload(source: DataSource, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !source.id) {
      return;
    }
    const id = source.id;
    file.text().then((content) => {
      this.message.set(this.t('ds.msg.uploading'));
      this.api.uploadKeyValues(id, content).subscribe({
        next: (counts) => {
          const total = Object.values(counts).reduce((a, b) => a + b, 0);
          const attrs = Object.keys(counts).join(', ') || this.t('common.none');
          this.message.set(this.t('ds.msg.hashesStored', { count: total, attrs }));
          this.reload();
        },
        error: (err) =>
          this.message.set(
            this.t('ds.msg.uploadFailed', { error: err?.error?.error ?? this.t('common.error') }),
          ),
      });
    });
    input.value = '';
  }

  protected remove(source: DataSource): void {
    if (!source.id) {
      return;
    }
    this.api.deleteDataSource(source.id).subscribe(() => this.reload());
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
    const list = this.sources();
    return list.length > 0 && list.every((s) => !!s.id && this.selected().has(s.id));
  }

  protected toggleSelectAll(): void {
    if (this.allSelected()) {
      this.selected.set(new Set());
    } else {
      this.selected.set(
        new Set(
          this.sources()
            .map((s) => s.id!)
            .filter((id) => !!id),
        ),
      );
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
    this.api.bulkDeleteDataSources(ids).subscribe(() => {
      this.clearSelection();
      this.reload();
    });
  }

  protected checkedFields(source: DataSource): string {
    return source.attributes
      .filter((a) => a.check)
      .map((a) => a.field)
      .join(', ');
  }

  private draft(attributes: AttributeRule[]): DataSource {
    return {
      id: this.editingId(),
      name: this.name,
      kind: this.kind,
      baseUrl: this.baseUrl,
      method: 'GET',
      path: this.path,
      authType: this.authType,
      tokenRef: this.tokenRef || null,
      authHeaderName: null,
      recordsPath: this.recordsPath,
      cacheTtlSeconds: 600,
      minValueLength: 4,
      enabled: true,
      attributes,
    };
  }

  private resetForm(): void {
    this.editingId.set(null);
    this.name = '';
    this.kind = 'REST';
    this.baseUrl = '';
    this.path = '';
    this.recordsPath = '$[*]';
    this.authType = 'NONE';
    this.tokenRef = '';
    this.draftMap = {};
  }

  private reload(): void {
    this.api.dataSources().subscribe((list) => this.sources.set(list));
  }
}
