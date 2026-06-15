import { ChangeDetectionStrategy, Component } from '@angular/core';
import { BrandLogo } from './brand-logo';

/**
 * Einheitliche Seiten-Überschrift mit Marken-Logo (in jeder View). Der Titeltext wird projiziert:
 * {@code <app-page-title>{{ t('...') }}</app-page-title>}. Hält das Logo-Markup DRY statt es in jede
 * Komponente zu duplizieren.
 */
@Component({
  selector: 'app-page-title',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BrandLogo],
  template: `
    <div class="mb-4 flex items-center gap-2">
      <app-brand-logo [size]="22" />
      <h2 class="text-xl font-semibold"><ng-content /></h2>
    </div>
  `,
})
export class PageTitle {}
