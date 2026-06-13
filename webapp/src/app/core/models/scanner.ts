// Spiegelt die REST-DTOs des Backends (publizierte Sprache, nicht das Domänenmodell).

export type Severity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ScanStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type TriageStatus = 'OPEN' | 'BASELINE' | 'SUPPRESSED' | 'FALSE_POSITIVE';

export interface Finding {
  readonly id: string;
  readonly scanId: string;
  readonly repoId: string;
  readonly detectorId: string;
  readonly category: string;
  readonly severity: Severity;
  readonly ruleId: string;
  readonly file: string;
  readonly line: number;
  readonly redactedMatch: string;
  readonly verified: boolean;
  readonly triageStatus: TriageStatus;
  readonly triageReason: string | null;
  readonly firstSeen: string;
  readonly lastSeen: string;
}

export interface Scan {
  readonly id: string;
  readonly repoId: string;
  readonly mode: string;
  readonly status: ScanStatus;
  readonly progress: number;
  readonly findingCount: number;
  readonly startedAt: string;
  readonly finishedAt: string | null;
}

export interface ScanEvent {
  readonly scanId: string;
  readonly status: string;
  readonly progress: number;
  readonly findingCount: number;
}

export interface RepositorySource {
  readonly id: string | null;
  readonly name: string;
  readonly type: string;
  readonly location: string;
  readonly branches: string[];
  readonly tokenRef: string | null;
  readonly enabled: boolean;
  readonly reportEmails: string[];
}

export interface SecretRefStatus {
  readonly ref: string;
  readonly resolvable: boolean;
}

export interface Settings {
  readonly generalNotificationEmail: string | null;
  readonly defaultFailOn: Severity;
  readonly defaultScanMode: string;
  readonly retentionDays: number;
  readonly secretRefs: SecretRefStatus[];
}

export interface DetectorInfo {
  readonly id: string;
  readonly category: string;
}

export interface Policy {
  readonly id: string | null;
  readonly orgUnit: string | null;
  readonly failOn: Severity;
  readonly failOnNewOnly: boolean;
  readonly softFail: boolean;
  readonly warnThreshold: Severity;
  readonly enabledDetectorGroups: string[];
}
