import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ScannerApi } from '../../core/services/scanner-api';
import { Policy, Severity } from '../../core/models/scanner';

/** Governance-Policies verwalten (FR-20): zentrale Gate-/Detektor-Vorgaben pro Org-Einheit. */
@Component({
  selector: 'app-policies-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="p-6">
      <h2 class="mb-4 text-xl font-semibold">Policies</h2>

      <form (ngSubmit)="create()" class="mb-6 flex flex-wrap items-end gap-2">
        <input
          [(ngModel)]="orgUnit"
          name="orgUnit"
          placeholder="Org-Unit (leer = Default)"
          class="rounded border px-2 py-1"
        />
        <label class="text-sm">
          failOn
          <select [(ngModel)]="failOn" name="failOn" class="ml-1 rounded border px-2 py-1">
            @for (s of severities; track s) {
              <option [ngValue]="s">{{ s }}</option>
            }
          </select>
        </label>
        <label class="text-sm">
          <input type="checkbox" [(ngModel)]="failOnNewOnly" name="failOnNewOnly" /> failOnNewOnly
        </label>
        <input
          [(ngModel)]="groups"
          name="groups"
          placeholder="Detektor-Gruppen (secrets,pii)"
          class="w-56 rounded border px-2 py-1"
        />
        <button type="submit" class="rounded bg-blue-600 px-3 py-1 text-white">Anlegen</button>
      </form>

      <table class="w-full text-sm">
        <thead>
          <tr class="border-b text-left text-gray-500">
            <th class="py-2">Org-Unit</th>
            <th>failOn</th>
            <th>NewOnly</th>
            <th>Gruppen</th>
            <th>Aktionen</th>
          </tr>
        </thead>
        <tbody>
          @for (p of policies(); track p.id) {
            <tr class="border-b">
              <td class="py-2">{{ p.orgUnit ?? '(default)' }}</td>
              <td>{{ p.failOn }}</td>
              <td>{{ p.failOnNewOnly }}</td>
              <td>{{ p.enabledDetectorGroups.join(', ') }}</td>
              <td>
                <button (click)="remove(p)" class="text-red-600 hover:underline">Löschen</button>
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="5" class="py-3 text-gray-500">Keine Policies — Default-Gate gilt.</td>
            </tr>
          }
        </tbody>
      </table>
    </section>
  `,
})
export class PoliciesPage {
  private readonly api = inject(ScannerApi);

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
}
