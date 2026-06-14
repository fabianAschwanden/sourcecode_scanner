import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ScannerApi } from '../../core/services/scanner-api';
import { Policy, Severity } from '../../core/models/scanner';
import { severityColor } from '../../core/severity-color';
import { I18nService } from '../../core/i18n/i18n.service';

/** Governance-Policies verwalten (FR-20): zentrale Gate-/Detektor-Vorgaben pro Org-Einheit. */
@Component({
  selector: 'app-policies-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">{{ t('policies.title') }}</h2>

      <form (ngSubmit)="create()" class="mb-6 flex flex-wrap items-end gap-2">
        <input
          [(ngModel)]="orgUnit"
          name="orgUnit"
          [placeholder]="t('policies.orgUnit')"
          [title]="t('policies.orgUnit.tooltip')"
          class="rounded border border-default px-2 py-1"
        />
        <label class="text-sm text-muted">
          failOn
          <select
            [(ngModel)]="failOn"
            name="failOn"
            [title]="t('policies.failOn.tooltip')"
            class="ml-1 rounded border border-default px-2 py-1"
          >
            @for (s of severities; track s) {
              <option [ngValue]="s">{{ s }}</option>
            }
          </select>
        </label>
        <label class="text-sm text-muted" [title]="t('policies.failOnNewOnly.tooltip')">
          <input type="checkbox" [(ngModel)]="failOnNewOnly" name="failOnNewOnly" /> failOnNewOnly
        </label>
        <input
          [(ngModel)]="groups"
          name="groups"
          [placeholder]="t('policies.groups')"
          [title]="t('policies.groups.tooltip')"
          class="w-56 rounded border border-default px-2 py-1"
        />
        <button
          type="submit"
          class="rounded bg-accent px-3 py-1 text-white hover:bg-accent-emphasis"
        >
          {{ t('common.create') }}
        </button>
      </form>

      <ul class="divide-y divide-default border-t border-default">
        @for (p of policies(); track p.id) {
          <li class="flex items-start justify-between gap-4 py-3">
            <div class="min-w-0">
              <div class="flex items-center gap-2">
                <span class="font-semibold">{{ p.orgUnit ?? t('policies.defaultOrgUnit') }}</span>
                <span
                  class="rounded-full border px-2 text-xs"
                  [style.color]="severityColor(p.failOn)"
                  [style.borderColor]="severityColor(p.failOn)"
                >
                  {{ p.failOn }}
                </span>
              </div>
              <p class="mt-1 text-xs text-muted">
                {{
                  t('policies.row.meta', {
                    failOn: p.failOn,
                    newOnly: p.failOnNewOnly ? t('common.yes') : t('common.no'),
                    groups: p.enabledDetectorGroups.join(', ') || t('common.none'),
                  })
                }}
              </p>
            </div>
            <div class="flex shrink-0 items-center gap-2">
              <button
                (click)="remove(p)"
                class="rounded border border-default px-3 py-1.5 text-sm text-sev-high hover:underline"
              >
                {{ t('common.delete') }}
              </button>
            </div>
          </li>
        } @empty {
          <li class="py-4 text-muted">{{ t('policies.empty') }}</li>
        }
      </ul>
    </section>
  `,
})
export class PoliciesPage {
  private readonly api = inject(ScannerApi);
  private readonly i18n = inject(I18nService);

  protected t(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }

  protected readonly severities: Severity[] = ['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  protected readonly policies = signal<Policy[]>([]);
  protected orgUnit = '';
  protected failOn: Severity = 'HIGH';
  protected failOnNewOnly = false;
  protected groups = 'secrets,pii';

  constructor() {
    this.reload();
  }

  protected create(): void {
    const policy: Policy = {
      id: null,
      orgUnit: this.orgUnit.trim() || null,
      failOn: this.failOn,
      failOnNewOnly: this.failOnNewOnly,
      softFail: false,
      warnThreshold: 'MEDIUM',
      enabledDetectorGroups: this.groups
        .split(',')
        .map((g) => g.trim())
        .filter((g) => g.length > 0),
    };
    this.api.createPolicy(policy).subscribe(() => {
      this.orgUnit = '';
      this.reload();
    });
  }

  protected remove(policy: Policy): void {
    if (!policy.id) {
      return;
    }
    this.api.deletePolicy(policy.id).subscribe(() => this.reload());
  }

  private reload(): void {
    this.api.policies().subscribe((list) => this.policies.set(list));
  }

  protected severityColor(severity: Severity): string {
    return severityColor(severity);
  }
}
