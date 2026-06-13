import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ScannerApi } from '../../core/services/scanner-api';
import { Finding, Scan, Severity } from '../../core/models/scanner';

const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

/** Einstiegs-Dashboard (WR-3.1): offene Funde nach Severity, letzte Scans. */
@Component({
  selector: 'app-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">Dashboard</h2>

      <div class="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-5">
        @for (sev of severities; track sev) {
          <div class="rounded border border-gray-200 bg-white p-4">
            <div class="text-xs uppercase text-gray-500">{{ sev }}</div>
            <div class="text-2xl font-semibold">{{ count(sev) }}</div>
          </div>
        }
      </div>

      <h3 class="mb-2 font-medium">Letzte Scans</h3>
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b text-left text-gray-500">
            <th class="py-2">Repository</th>
            <th>Status</th>
            <th>Funde</th>
            <th>Gestartet</th>
          </tr>
        </thead>
        <tbody>
          @for (s of scans(); track s.id) {
            <tr class="border-b">
              <td class="py-2">{{ s.repoId }}</td>
              <td>{{ s.status }}</td>
              <td>{{ s.findingCount }}</td>
              <td>{{ s.startedAt }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="4" class="py-3 text-gray-500">Noch keine Scans.</td>
            </tr>
          }
        </tbody>
      </table>
    </section>
  `,
})
export class DashboardPage {
  private readonly api = inject(ScannerApi);
  protected readonly severities = SEVERITIES;

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
}
