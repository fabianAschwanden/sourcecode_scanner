// Spiegelt die REST-DTOs des Backends (publizierte Sprache, nicht das Domänenmodell).

export type Severity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ScanStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type TriageStatus = 'OPEN' | 'BASELINE' | 'SUPPRESSED' | 'FALSE_POSITIVE';
export type RemediationStatus = 'OPEN' | 'PR_OPEN' | 'FIXED' | 'ROTATED' | 'SCRUBBED';

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
  readonly remediationStatus: RemediationStatus;
  readonly firstSeen: string;
  readonly lastSeen: string;
}

export type ScanTrigger = 'SERVER' | 'CI';

export interface Scan {
  readonly id: string;
  readonly repoId: string;
  readonly mode: string;
  readonly status: ScanStatus;
  readonly progress: number;
  readonly findingCount: number;
  readonly startedAt: string;
  readonly finishedAt: string | null;
  readonly trigger: ScanTrigger;
  readonly ciPipelineUrl: string | null;
  readonly ciCommit: string | null;
  readonly ciBranch: string | null;
  readonly ciActor: string | null;
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
  readonly remediationEnabled: boolean;
  readonly description: string;
  readonly visibility: string;
}

export interface RepositoryCard {
  readonly id: string;
  readonly name: string;
  readonly type: string;
  readonly visibility: string;
  readonly description: string;
  readonly enabled: boolean;
  readonly language: string;
  readonly lastScanAt: string | null;
  readonly lastStatus: string | null;
  readonly lastError: string | null;
}

export interface PrRef {
  readonly url: string;
  readonly number: number;
}

export interface ScrubDryRun {
  readonly toolAvailable: boolean;
  readonly affectedSecrets: number;
  readonly diffSummary: string;
}

export interface ScrubResult {
  readonly success: boolean;
  readonly remainingFindings: number;
  readonly message: string;
}

export interface SecretRefStatus {
  readonly ref: string;
  readonly resolvable: boolean;
}

export type SecretStorageMode = 'REFERENCE' | 'VAULT_WRITE' | 'DB_ENCRYPTED';

export interface ManagedSecret {
  readonly id: string | null;
  readonly name: string;
  readonly mode: SecretStorageMode;
  readonly reference: string;
  readonly hasStoredValue: boolean;
  readonly resolvable: boolean;
  // Nur Eingabe (transient); wird nie zurückgegeben:
  readonly plaintext?: string | null;
  readonly vaultPath?: string | null;
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

export type DataSourceAuthType = 'NONE' | 'BEARER' | 'BASIC' | 'HEADER';
export type DataSourceKind = 'REST' | 'UPLOAD';
export type AttributeCategory = 'PII' | 'CUSTOM';

export interface AttributeRule {
  readonly field: string;
  readonly check: boolean;
  readonly severity: Severity;
  readonly category: AttributeCategory;
}

export interface DataSource {
  readonly id: string | null;
  readonly name: string;
  readonly kind: DataSourceKind;
  readonly baseUrl: string;
  readonly method: string;
  readonly path: string;
  readonly authType: DataSourceAuthType;
  readonly tokenRef: string | null;
  readonly authHeaderName: string | null;
  readonly recordsPath: string;
  readonly cacheTtlSeconds: number;
  readonly minValueLength: number;
  readonly enabled: boolean;
  readonly attributes: AttributeRule[];
}

export interface DataSourceSchema {
  readonly reachable: boolean;
  readonly sampleRecords: number;
  readonly attributes: { readonly field: string; readonly maskedExample: string }[];
  readonly message: string;
}

/** Ergebnis einer Sammelaktion (WR-23). */
export interface BulkResult {
  readonly total: number;
  readonly succeeded: number;
  readonly failed: { readonly id: string; readonly error: string }[];
}

export type EnforcementStatus = 'DISABLED' | 'ACTIVE';
export type RuleMatchMode = 'ALWAYS' | 'LIST' | 'API';

export interface RuleOverride {
  readonly ruleId: string;
  readonly enabled: boolean;
  readonly severity: Severity | null;
  readonly matchMode: RuleMatchMode;
  readonly dataSourceName: string | null;
}

export interface Ruleset {
  readonly id: string | null;
  readonly name: string;
  readonly enforcement: EnforcementStatus;
  readonly global: boolean;
  readonly repoNames: string[];
  readonly rules: RuleOverride[];
}

/** Verfügbare Einzelregel für den Ruleset-Editor (von /api/detectors/rules). */
export interface RuleInfo {
  readonly id: string;
  readonly title: string;
  readonly category: string;
  readonly defaultSeverity: Severity;
  readonly defaultEnabled: string;
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
