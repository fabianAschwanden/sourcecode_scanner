import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ScannerApi } from '../../core/services/scanner-api';
import { Finding, Severity, TriageStatus } from '../../core/models/scanner';
import { severityColor } from '../../core/severity-color';
import { I18nService } from '../../core/i18n/i18n.service';

type SortKey = 'severity' | 'lastSeen' | 'firstSeen';
const SEVERITY_ORDER: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

/**
 * Code-Scanning-Ansicht im GitHub-Stil (WR-60..68): Status-Banner, Query-Filterleiste mit
 * Offen/Geschlossen-Tabs, Facetten-Dropdowns (Severity/Detektor/Regel/Sprache) + Sortierung und
 * GitHub-artige Ergebniszeilen. Alle Texte über i18n (WR-70). Treffer bleiben redigiert (WR-68).
 */
@Component({
  selector: 'app-findings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">{{ t('findings.title') }}</h2>

      <!-- Tool-Status-Banner (WR-60/61) -->
      <div
        class="mb-4 flex items-center justify-between rounded border border-default bg-surface px-4 py-3"
      >
        <span class="flex items-center gap-2 text-sm">
          <span class="text-accent">✓</span>
          {{ t('findings.banner.ok') }}
        </span>
        <a routerLink="/datasources" class="text-sm text-muted hover:text-accent">
          🔧 {{ t('findings.tools', { count: toolCount() }) }} · {{ t('findings.addTool') }}
        </a>
      </div>

      <!-- Query-Filterleiste (WR-62) -->
      <input
        [(ngModel)]="query"
        (ngModelChange)="onQueryChange()"
        [placeholder]="t('findings.search.placeholder')"
        [title]="t('findings.search.tooltip')"
        class="mb-3 w-full rounded border border-default bg-canvas px-3 py-2 font-mono text-sm"
      />

      <!-- Tabs + Facetten (WR-63/64) -->
      <div class="mb-3 flex flex-wrap items-center justify-between gap-2 text-sm">
        <div class="flex gap-4">
          <button
            (click)="setStatusTab('open')"
            class="flex items-center gap-1"
            [class.font-semibold]="statusTab() === 'open'"
            [class.text-fg]="statusTab() === 'open'"
            [class.text-muted]="statusTab() !== 'open'"
          >
            🛡 {{ t('findings.tab.open', { count: openCount() }) }}
          </button>
          <button
            (click)="setStatusTab('closed')"
            class="flex items-center gap-1"
            [class.font-semibold]="statusTab() === 'closed'"
            [class.text-fg]="statusTab() === 'closed'"
            [class.text-muted]="statusTab() !== 'closed'"
          >
            ✓ {{ t('findings.tab.closed', { count: closedCount() }) }}
          </button>
        </div>
        <div class="flex flex-wrap gap-2">
          <select
            [ngModel]="facetFileType()"
            (ngModelChange)="setFacet('fileType', $event)"
            [title]="t('findings.facet.fileType')"
            class="rounded border border-default bg-canvas px-2 py-1"
          >
            <option value="">
              {{ t('findings.facet.fileType') }}: {{ t('findings.facet.all') }}
            </option>
            @for (v of fileTypes(); track v) {
              <option [value]="v">{{ v }}</option>
            }
          </select>
          <select
            [ngModel]="facetDetector()"
            (ngModelChange)="setFacet('detector', $event)"
            [title]="t('findings.facet.detector')"
            class="rounded border border-default bg-canvas px-2 py-1"
          >
            <option value="">
              {{ t('findings.facet.detector') }}: {{ t('findings.facet.all') }}
            </option>
            @for (v of detectorsList(); track v) {
              <option [value]="v">{{ v }}</option>
            }
          </select>
          <select
            [ngModel]="facetRule()"
            (ngModelChange)="setFacet('rule', $event)"
            [title]="t('findings.facet.rule')"
            class="rounded border border-default bg-canvas px-2 py-1"
          >
            <option value="">{{ t('findings.facet.rule') }}: {{ t('findings.facet.all') }}</option>
            @for (v of rules(); track v) {
              <option [value]="v">{{ v }}</option>
            }
          </select>
          <select
            [ngModel]="facetSeverity()"
            (ngModelChange)="setFacet('severity', $event)"
            [title]="t('findings.facet.severity')"
            class="rounded border border-default bg-canvas px-2 py-1"
          >
            <option value="">
              {{ t('findings.facet.severity') }}: {{ t('findings.facet.all') }}
            </option>
            @for (v of severities; track v) {
              <option [value]="v.toLowerCase()">{{ v }}</option>
            }
          </select>
          <select
            [(ngModel)]="sortKey"
            [title]="t('findings.sort.label')"
            class="rounded border border-default bg-canvas px-2 py-1"
          >
            <option value="severity">
              {{ t('findings.sort.label') }}: {{ t('findings.sort.severity') }}
            </option>
            <option value="lastSeen">
              {{ t('findings.sort.label') }}: {{ t('findings.sort.lastSeen') }}
            </option>
            <option value="firstSeen">
              {{ t('findings.sort.label') }}: {{ t('findings.sort.firstSeen') }}
            </option>
          </select>
        </div>
      </div>

      @if (message()) {
        <p class="mb-3 rounded border border-default px-3 py-2 text-sm text-muted">
          {{ message() }}
        </p>
      }

      <!-- Ergebniszeilen (WR-65) -->
      <ul class="divide-y divide-default border-t border-default">
        @for (f of visibleFindings(); track f.id) {
          <li class="flex items-start justify-between gap-4 py-3">
            <div class="min-w-0">
              <div class="flex items-center gap-2">
                <span [style.color]="severityColor(f.severity)">⚠</span>
                <span class="font-semibold">{{ f.ruleId }}</span>
                <span
                  class="rounded-full border px-2 text-xs"
                  [style.color]="severityColor(f.severity)"
                  [style.borderColor]="severityColor(f.severity)"
                  >{{ f.severity }}</span
                >
              </div>
              <p class="mt-1 text-xs text-muted">
                {{ statusLabel(f.triageStatus) }} ·
                {{ t('findings.row.detectedBy', { detector: f.detectorId }) }}
                <span class="font-mono">{{ f.file }}:{{ f.line }}</span>
                · <span class="font-mono">{{ f.redactedMatch }}</span>
              </p>
              <div class="mt-1 space-x-3 text-xs">
                <button (click)="triage(f, 'BASELINE')" class="text-accent hover:underline">
                  {{ t('findings.action.baseline') }}
                </button>
                <button (click)="triage(f, 'FALSE_POSITIVE')" class="text-accent hover:underline">
                  {{ t('findings.action.fp') }}
                </button>
                <button (click)="triage(f, 'SUPPRESSED')" class="text-accent hover:underline">
                  {{ t('findings.action.suppress') }}
                </button>
                @if (f.category === 'SECRET' && f.remediationStatus === 'OPEN') {
                  <button
                    (click)="remediate(f)"
                    [title]="t('findings.action.fixPr.tooltip')"
                    class="text-accent hover:underline"
                  >
                    {{ t('findings.action.fixPr') }}
                  </button>
                }
              </div>
            </div>
            <span class="shrink-0 rounded bg-canvas px-2 py-0.5 text-xs text-muted">{{
              f.repoId
            }}</span>
          </li>
        } @empty {
          <li class="py-4 text-muted">{{ t('findings.empty') }}</li>
        }
      </ul>
    </section>
  `,
})
export class FindingsPage {
  private readonly api = inject(ScannerApi);
  private readonly i18n = inject(I18nService);

  protected readonly severities: Severity[] = SEVERITY_ORDER;

  protected query = 'is:open';
  protected sortKey: SortKey = 'severity';

  protected readonly findings = signal<Finding[]>([]);
  protected readonly message = signal<string>('');
  protected readonly toolCount = signal<number>(0);

  // Aus der Query abgeleitete Filter (Query ist die Quelle der Wahrheit, WR-62).
  protected readonly statusTab = signal<'open' | 'closed'>('open');
  protected readonly facetSeverity = signal<string>('');
  protected readonly facetDetector = signal<string>('');
  protected readonly facetRule = signal<string>('');
  protected readonly facetFileType = signal<string>('');

  // Facetten-Optionen aus den geladenen Funden (nur vorkommende Werte, WR-64).
  protected readonly detectorsList = computed(() =>
    this.distinct(this.findings().map((f) => f.detectorId)),
  );
  protected readonly rules = computed(() => this.distinct(this.findings().map((f) => f.ruleId)));
  protected readonly fileTypes = computed(() =>
    this.distinct(this.findings().map((f) => this.extOf(f.file))),
  );

  protected readonly openCount = computed(
    () => this.findings().filter((f) => f.triageStatus === 'OPEN').length,
  );
  protected readonly closedCount = computed(
    () => this.findings().filter((f) => f.triageStatus !== 'OPEN').length,
  );

  protected readonly visibleFindings = computed(() => {
    const tab = this.statusTab();
    const sev = this.facetSeverity();
    const det = this.facetDetector();
    const rule = this.facetRule();
    const ft = this.facetFileType();
    const list = this.findings().filter((f) => {
      if (tab === 'open' && f.triageStatus !== 'OPEN') return false;
      if (tab === 'closed' && f.triageStatus === 'OPEN') return false;
      if (sev && f.severity.toLowerCase() !== sev) return false;
      if (det && f.detectorId !== det) return false;
      if (rule && f.ruleId !== rule) return false;
      if (ft && this.extOf(f.file) !== ft) return false;
      return true;
    });
    return [...list].sort((a, b) => this.compare(a, b));
  });

  constructor() {
    this.reload();
    this.api.detectors().subscribe((d) => this.toolCount.set(d.length));
    this.onQueryChange();
  }

  protected t(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }

  // --- Query <-> Facetten-Synchronisation (WR-62) ----------------------------------------------

  protected onQueryChange(): void {
    const tokens = this.query.trim().split(/\s+/).filter(Boolean);
    const get = (prefix: string): string => {
      const tok = tokens.find((tk) => tk.startsWith(prefix));
      return tok ? tok.slice(prefix.length) : '';
    };
    const is = get('is:');
    this.statusTab.set(is === 'closed' ? 'closed' : 'open');
    this.facetSeverity.set(get('severity:'));
    this.facetDetector.set(get('detector:'));
    this.facetRule.set(get('rule:'));
    this.facetFileType.set(get('language:'));
  }

  protected setStatusTab(tab: 'open' | 'closed'): void {
    this.statusTab.set(tab);
    this.syncQueryFromFacets();
  }

  protected setFacet(facet: 'severity' | 'detector' | 'rule' | 'fileType', value: string): void {
    if (facet === 'severity') this.facetSeverity.set(value);
    if (facet === 'detector') this.facetDetector.set(value);
    if (facet === 'rule') this.facetRule.set(value);
    if (facet === 'fileType') this.facetFileType.set(value);
    this.syncQueryFromFacets();
  }

  private syncQueryFromFacets(): void {
    const parts = [`is:${this.statusTab()}`];
    if (this.facetSeverity()) parts.push(`severity:${this.facetSeverity()}`);
    if (this.facetDetector()) parts.push(`detector:${this.facetDetector()}`);
    if (this.facetRule()) parts.push(`rule:${this.facetRule()}`);
    if (this.facetFileType()) parts.push(`language:${this.facetFileType()}`);
    this.query = parts.join(' ');
  }

  // --- Aktionen ---------------------------------------------------------------------------------

  protected remediate(finding: Finding): void {
    this.message.set(this.t('findings.msg.creatingPr'));
    this.api.remediate(finding.id).subscribe({
      next: (pr) =>
        this.message.set(this.t('findings.msg.prCreated', { number: pr.number, url: pr.url })),
      error: (err) =>
        this.message.set(
          this.t('findings.msg.prFailed', { error: err?.error?.error ?? this.t('common.error') }),
        ),
    });
  }

  protected triage(finding: Finding, status: TriageStatus): void {
    const needsReason = status === 'SUPPRESSED' || status === 'FALSE_POSITIVE';
    const reason = needsReason ? (prompt(this.t('findings.reason.prompt')) ?? '') : undefined;
    if (needsReason && !reason) {
      return;
    }
    this.api.triage(finding.id, status, reason).subscribe(() => this.reload());
  }

  protected statusLabel(status: TriageStatus): string {
    return this.t('findings.status.' + status);
  }

  protected severityColor(severity: Severity): string {
    return severityColor(severity);
  }

  // --- Helfer -----------------------------------------------------------------------------------

  private reload(): void {
    this.api.findings({}).subscribe((list) => this.findings.set(list));
  }

  private compare(a: Finding, b: Finding): number {
    if (this.sortKey === 'severity') {
      return SEVERITY_ORDER.indexOf(a.severity) - SEVERITY_ORDER.indexOf(b.severity);
    }
    const key = this.sortKey === 'lastSeen' ? 'lastSeen' : 'firstSeen';
    return (b[key] ?? '').localeCompare(a[key] ?? '');
  }

  private distinct(values: string[]): string[] {
    return [...new Set(values.filter(Boolean))].sort();
  }

  private extOf(file: string): string {
    const dot = file.lastIndexOf('.');
    return dot >= 0 ? file.slice(dot + 1) : file;
  }
}
