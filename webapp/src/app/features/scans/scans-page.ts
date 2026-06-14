import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ScannerApi } from '../../core/services/scanner-api';
import { RepositorySource, Scan, ScanEvent } from '../../core/models/scanner';
import { I18nService } from '../../core/i18n/i18n.service';

/**
 * Scan-Steuerung (WR-03): starten/abbrechen + Verlauf. Live-Fortschritt als Prozentbalken (WR-04a)
 * über den SSE-Stream je laufendem Scan (WR-04), mit Polling-Fallback. Texte über i18n.
 */
@Component({
  selector: 'app-scans-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">{{ t('scans.title') }}</h2>

      <div class="mb-4 flex items-end gap-2">
        <label class="text-sm text-muted">
          {{ t('scans.source') }}
          <select
            [(ngModel)]="selectedSource"
            [title]="t('scans.source.tooltip')"
            class="ml-2 rounded border border-default px-2 py-1"
          >
            @for (s of sources(); track s.id) {
              <option [ngValue]="s.id">{{ s.name }}</option>
            }
          </select>
        </label>
        <label class="text-sm text-muted">
          {{ t('scans.mode') }}
          <select
            [(ngModel)]="mode"
            [title]="t('scans.mode.tooltip')"
            class="ml-2 rounded border border-default px-2 py-1"
          >
            <option value="full">full</option>
            <option value="incremental">incremental</option>
          </select>
        </label>
        <button
          (click)="start()"
          [disabled]="!selectedSource"
          class="rounded bg-accent px-3 py-1 text-white hover:bg-accent-emphasis disabled:opacity-50"
        >
          {{ t('scans.start') }}
        </button>
      </div>

      <ul class="divide-y divide-default border-t border-default">
        @for (s of scans(); track s.id) {
          <li class="flex items-start justify-between gap-4 py-3">
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <button (click)="openInsights(s)" class="font-semibold text-accent hover:underline">
                  {{ s.repoId }}
                </button>
                <span class="rounded-full border border-default px-2 text-xs text-muted">
                  {{ liveStatus(s) }}
                </span>
                <span
                  class="rounded-full border border-default px-2 text-xs text-muted"
                  [title]="ciTooltip(s)"
                >
                  {{ s.trigger === 'CI' ? t('scans.origin.ci') : t('scans.origin.server') }}
                </span>
              </div>
              <p class="mt-1 text-xs text-muted">
                {{ t('scans.row.meta', { mode: s.mode, findings: liveFindings(s) }) }}
              </p>
              <div class="mt-2 flex items-center gap-2">
                <div class="h-2 w-48 overflow-hidden rounded bg-canvas">
                  <div
                    class="h-full rounded bg-accent transition-all"
                    [style.width.%]="livePercent(s)"
                  ></div>
                </div>
                <span class="text-xs tabular-nums text-muted">{{ livePercent(s) }}%</span>
              </div>
            </div>
            <div class="flex shrink-0 items-center gap-2">
              <button
                (click)="openInsights(s)"
                class="rounded border border-default px-3 py-1.5 text-sm hover:text-accent"
              >
                {{ t('scans.viewFindings') }}
              </button>
              @if (liveStatus(s) === 'RUNNING') {
                <button
                  (click)="cancel(s)"
                  class="rounded border border-default px-3 py-1.5 text-sm text-sev-high hover:underline"
                >
                  {{ t('scans.cancel') }}
                </button>
              }
            </div>
          </li>
        } @empty {
          <li class="py-4 text-muted">{{ t('scans.empty') }}</li>
        }
      </ul>
    </section>
  `,
})
export class ScansPage {
  private readonly api = inject(ScannerApi);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  /** Öffnet die Funde/Code-Scanning-Ansicht, vorgefiltert auf das Repo dieses Scans. */
  protected openInsights(scan: Scan): void {
    this.router.navigate(['/findings'], { queryParams: { repo: scan.repoId } });
  }

  protected readonly sources = signal<RepositorySource[]>([]);
  protected readonly scans = signal<Scan[]>([]);
  protected selectedSource: string | null = null;
  protected mode = 'full';

  /** Live-Events je laufendem Scan (überschreiben die persistierten Werte, WR-04a). */
  protected readonly live = signal<Record<string, ScanEvent>>({});
  private readonly streams = new Map<string, EventSource>();

  constructor() {
    this.api.sources().subscribe((list) => {
      this.sources.set(list);
      this.selectedSource = list[0]?.id ?? null;
    });
    this.reload();
    this.destroyRef.onDestroy(() => this.streams.forEach((es) => es.close()));
  }

  protected t(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }

  protected start(): void {
    if (!this.selectedSource) {
      return;
    }
    this.api.startScan(this.selectedSource, this.mode).subscribe((scan) => {
      this.subscribeProgress(scan.id);
      this.reload();
    });
  }

  protected cancel(scan: Scan): void {
    this.api.cancelScan(scan.id).subscribe(() => this.reload());
  }

  protected livePercent(scan: Scan): number {
    return this.live()[scan.id]?.progress ?? scan.progress;
  }

  protected liveStatus(scan: Scan): string {
    return this.live()[scan.id]?.status ?? scan.status;
  }

  protected liveFindings(scan: Scan): number {
    return this.live()[scan.id]?.findingCount ?? scan.findingCount;
  }

  /** Zeigt die CI-Metadaten als Tooltip auf dem Herkunfts-Badge (WR-69). */
  protected ciTooltip(scan: Scan): string {
    if (scan.trigger !== 'CI') {
      return this.t('scans.origin.server');
    }
    return [
      scan.ciBranch ? `branch: ${scan.ciBranch}` : null,
      scan.ciCommit ? `commit: ${scan.ciCommit}` : null,
      scan.ciActor ? `actor: ${scan.ciActor}` : null,
      scan.ciPipelineUrl ?? null,
    ]
      .filter((x): x is string => !!x)
      .join(' · ');
  }

  private reload(): void {
    this.api.recentScans(50).subscribe((list) => {
      this.scans.set(list);
      // Für jeden noch laufenden Scan einen SSE-Stream abonnieren (idempotent).
      list.filter((s) => s.status === 'RUNNING').forEach((s) => this.subscribeProgress(s.id));
    });
  }

  /** Abonniert den SSE-Fortschritt eines Scans; bei Abschluss wird neu geladen (WR-04). */
  private subscribeProgress(scanId: string): void {
    if (this.streams.has(scanId) || typeof EventSource === 'undefined') {
      return;
    }
    const es = new EventSource(`/api/scans/${scanId}/events`);
    this.streams.set(scanId, es);
    es.onmessage = (msg: MessageEvent<string>) => {
      const event = JSON.parse(msg.data) as ScanEvent;
      this.live.update((m) => ({ ...m, [scanId]: event }));
      if (event.status !== 'RUNNING') {
        this.closeStream(scanId);
        this.reload();
      }
    };
    es.onerror = () => this.closeStream(scanId);
  }

  private closeStream(scanId: string): void {
    this.streams.get(scanId)?.close();
    this.streams.delete(scanId);
  }
}
