import { Severity } from './models/scanner';

/** Severity → CSS-Variable der GitHub-Dark-Palette (WR-40), konsistent über alle Ansichten. */
export function severityColor(severity: Severity | string): string {
  switch (severity) {
    case 'CRITICAL':
      return 'var(--color-sev-critical)';
    case 'HIGH':
      return 'var(--color-sev-high)';
    case 'MEDIUM':
      return 'var(--color-sev-medium)';
    case 'LOW':
      return 'var(--color-sev-low)';
    default:
      return 'var(--color-sev-info)';
  }
}
