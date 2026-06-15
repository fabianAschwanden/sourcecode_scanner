import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Wiederverwendbares Marken-Logo (SourceScan). Lädt eine der Branding-SVGs aus {@code public/branding}.
 * Variante {@code mark} = nur Schild (farbig, funktioniert auf dem Dark-Theme), {@code horizontal} =
 * Icon + Wortmarke (für die Login-Landing). Grösse über {@code size} (Pixel, quadratisch beim Mark).
 */
@Component({
  selector: 'app-brand-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <img
      [src]="'branding/' + file()"
      [style.height.px]="size()"
      [attr.width]="variant() === 'mark' ? size() : null"
      alt="SourceScan"
      class="select-none"
    />
  `,
})
export class BrandLogo {
  readonly variant = input<'mark' | 'horizontal'>('mark');
  readonly size = input<number>(24);

  protected file(): string {
    return this.variant() === 'horizontal' ? 'logo-horizontal.svg' : 'logo-color.svg';
  }
}
