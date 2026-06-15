import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { ScannerApi } from '../../core/services/scanner-api';
import { Finding, Scan, Severity } from '../../core/models/scanner';
import { severityColor } from '../../core/severity-color';
import { I18nService } from '../../core/i18n/i18n.service';

const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

/** Einstiegs-Dashboard (WR-3.1): offene Funde nach Severity, letzte Scans. */
@Component({
  selector: 'app-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">{{ t('dashboard.title') }}</h2>

      <div class="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-5">
        @for (sev of severities; track sev) {
          <div class="rounded border border-default bg-surface p-4">
            <div class="text-xs uppercase text-muted">{{ sev }}</div>
            <div class="text-2xl font-semibold" [style.color]="severityColor(sev)">
              {{ count(sev) }}
            </div>
          </div>
        }
      </div>

      <h3 class="mb-2 font-medium text-fg">{{ t('dashboard.recentScans') }}</h3>
      <ul class="divide-y divide-default border-t border-default">
        @for (s of scans(); track s.id) {
          <li class="flex items-start justify-between gap-4 py-3">
            <div class="min-w-0">
              <div class="flex items-center gap-2">
                <span [style.color]="statusColor(s.status)">●</span>
                <button (click)="openInsights(s)" class="font-semibold text-accent hover:underline">
                  {{ s.repoId }}
                </button>
                <span class="rounded-full border border-default px-2 text-xs text-muted">
                  {{ s.status }}
                </span>
              </div>
              <p class="mt-1 text-xs text-muted">
                {{
                  t('dashboard.row.meta', {
                    findings: s.findingCount,
                    when: relativeTime(s.startedAt),
                  })
                }}
              </p>
            </div>
            <button
              (click)="openInsights(s)"
              class="shrink-0 rounded border border-default px-3 py-1.5 text-sm hover:text-accent"
            >
              {{ t('scans.viewFindings') }}
            </button>
          </li>
        } @empty {
          <li class="py-4 text-muted">{{ t('dashboard.empty') }}</li>
        }
      </ul>
    </section>
  `,
})
export class DashboardPage {
  private readonly api = inject(ScannerApi);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  protected readonly severities = SEVERITIES;

  /** Öffnet die Funde-Ansicht vorgefiltert auf das Repo dieses Scans. */
  protected openInsights(scan: Scan): void {
    this.router.navigate(['/findings'], { queryParams: { repo: scan.repoId } });
  }

  protected t(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }

  /** Statusfarbe für den Listen-Punkt (analog Severity-Farbcodierung der Findings-Liste). */
  protected statusColor(status: string): string {
    switch (status) {
      case 'RUNNING':
        return 'var(--color-accent)';
      case 'FAILED':
        return 'var(--color-sev-high)';
      case 'CANCELLED':
        return 'var(--color-sev-medium)';
      default:
        return 'var(--color-sev-low)';
    }
  }

  /** Relative, lokalisierte Zeit für „gestartet …". */
  protected relativeTime(iso: string | null): string {
    if (!iso) {
      return '—';
    }
    const min = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
    const rtf = new Intl.RelativeTimeFormat(this.i18n.lang(), { numeric: 'auto' });
    if (min < 60) return rtf.format(-min, 'minute');
    const hours = Math.round(min / 60);
    if (hours < 24) return rtf.format(-hours, 'hour');
    return rtf.format(-Math.round(hours / 24), 'day');
  }

  protected readonly scans = toSignal(this.api.recentScans(10), {
    initialValue: [] as Scan[],
  });
  private readonly findings = toSignal(this.api.findings({ status: 'OPEN' }), {
    initialValue: [] as Finding[],
  });

  private readonly countBySeverity = computed(() => {
    const counts: Record<string, number> = {};
    for (const f of this.findings()) {
      counts[f.severity] = (counts[f.severity] ?? 0) + 1;
    }
    return counts;
  });

  protected count(severity: Severity): number {
    return this.countBySeverity()[severity] ?? 0;
  }

  protected severityColor(severity: Severity): string {
    return severityColor(severity);
  }
}
