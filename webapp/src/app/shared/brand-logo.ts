import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Wiederverwendbares Marken-Logo (SourceScan).
 *
 * <p>Variante {@code mark}: inline gerendetes Schild — helles Schild (sichtbar auf dem Dark-Theme),
 * dunkle Code-Klammern, grüner Scan-Strich. Inline (statt {@code <img>}), damit es bei jeder Grösse
 * scharf ist und zum Theme passt. Variante {@code horizontal}: die SVG-Wortmarke aus
 * {@code public/branding} (für die Login-Landing).
 */
@Component({
  selector: 'app-brand-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (variant() === 'horizontal') {
      <img
        src="branding/logo-horizontal.svg"
        [style.height.px]="size()"
        alt="SourceScan"
        class="select-none"
      />
    } @else {
      <svg
        [attr.width]="size()"
        [attr.height]="size()"
        viewBox="0 0 100 100"
        role="img"
        aria-label="SourceScan"
        class="select-none"
      >
        <path
          d="M30 18 H70 A8 8 0 0 1 78 26 V46 C78 64 66 78 50 84 C34 78 22 64 22 46 V26 A8 8 0 0 1 30 18 Z"
          fill="#e6edf3"
        />
        <path
          d="M45 38 L37 50 L45 62"
          fill="none"
          stroke="#1F2328"
          stroke-width="6"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <path
          d="M55 38 L63 50 L55 62"
          fill="none"
          stroke="#1F2328"
          stroke-width="6"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <path
          d="M53 37 L47 63"
          fill="none"
          stroke="#2DA44E"
          stroke-width="6"
          stroke-linecap="round"
        />
      </svg>
    }
  `,
})
export class BrandLogo {
  readonly variant = input<'mark' | 'horizontal'>('mark');
  readonly size = input<number>(24);
}
