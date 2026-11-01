import type { BrainRegion, CognitiveGraph } from './cognitiveGraph';

/**
 * Portable-brain V1 is deliberately a METADATA-ONLY preflight protocol.
 * It is not an export of memories, an authorization token or a restore mechanism.
 * Real transfer requires authenticated, encrypted storage and source retirement.
 */
const REGIONS: readonly BrainRegion[] = [
  'perception', 'attention', 'working-memory', 'episodic-memory',
  'semantic-memory', 'procedural-memory', 'planning', 'language',
  'social-context', 'safety'
];
const REGION_SET: ReadonlySet<string> = new Set(REGIONS);
const MAX_NODES = 1_000_000;
const MAX_EDGES = 4_000_000;
const MAX_ID_LENGTH = 128;
const CLOCK_SKEW_MS = 5 * 60_000;

export interface BrainMigrationManifestV1 {
  readonly schemaVersion: 1;
  readonly kind: 'nexus-companion-brain-manifest';
  readonly family: 'Nexus Companion';
  readonly format: 'cognitive-graph-v1';
  readonly payload: 'metadata-only';
  readonly companionId: string;
  readonly snapshotId: string;
  readonly generatedAt: number;
  readonly generalNodeCount: number;
  readonly generalConnectionCount: number;
  readonly regions: readonly BrainRegion[];
}

export type MigrationBlocker =
  | 'invalid-manifest'
  | 'unsupported-format'
  | 'identity-mismatch'
  | 'destination-occupied'
  | 'source-still-active'
  | 'source-state-unverified'
  | 'future-timestamp';

export interface MigrationPreflight {
  /** Metadata compatibility only; NEVER owner authentication or transfer approval. */
  readonly metadataCompatible: boolean;
  readonly blockers: readonly MigrationBlocker[];
  readonly requiresAuthenticationAndEncryptedTransfer: true;
}

const validId = (value: unknown): value is string =>
  typeof value === 'string' && value.length > 0 &&
  value.length <= MAX_ID_LENGTH && value === value.trim();
const validCount = (value: unknown, max: number): value is number =>
  typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 && value <= max;
const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const MANIFEST_KEYS = new Set([
  'schemaVersion', 'kind', 'family', 'format', 'payload', 'companionId',
  'snapshotId', 'generatedAt', 'generalNodeCount', 'generalConnectionCount', 'regions'
]);

/** Build a non-sensitive inventory without including labels, owner IDs or node contents. */
export function createBrainMigrationManifest(
  graph: CognitiveGraph,
  snapshotId: string,
  generatedAt: number
): BrainMigrationManifestV1 {
  if (!validId(graph.companionId) || !validId(snapshotId) ||
      !Number.isSafeInteger(generatedAt) || generatedAt < 0) {
    throw new Error('Invalid brain migration manifest inputs');
  }
  // Only public nodes and connections between those nodes contribute to counts.
  const general = graph.nodes.filter((node) => node.scope === 'companion-general');
  const ids = new Set(general.map((node) => node.id));
  const edges = graph.connections.filter((edge) => ids.has(edge.fromId) && ids.has(edge.toId));
  if (general.length > MAX_NODES || edges.length > MAX_EDGES) {
    throw new Error('Brain migration manifest limits exceeded');
  }
  const regions = REGIONS.filter((region) => general.some((node) => node.region === region));
  return {
    schemaVersion: 1,
    kind: 'nexus-companion-brain-manifest',
    family: 'Nexus Companion',
    format: 'cognitive-graph-v1',
    payload: 'metadata-only',
    companionId: graph.companionId,
    snapshotId,
    generatedAt,
    generalNodeCount: general.length,
    generalConnectionCount: edges.length,
    regions
  };
}

/** Strictly parse unknown input: reject additional fields to prevent private-data smuggling. */
export function isBrainMigrationManifest(input: unknown): input is BrainMigrationManifestV1 {
  if (!isRecord(input) || Object.keys(input).length !== MANIFEST_KEYS.size ||
      Object.keys(input).some((key) => !MANIFEST_KEYS.has(key))) return false;
  if (input.schemaVersion !== 1 || input.kind !== 'nexus-companion-brain-manifest' ||
      input.family !== 'Nexus Companion' || input.format !== 'cognitive-graph-v1' ||
      input.payload !== 'metadata-only' || !validId(input.companionId) ||
      !validId(input.snapshotId) || !validCount(input.generatedAt, Number.MAX_SAFE_INTEGER) ||
      !validCount(input.generalNodeCount, MAX_NODES) ||
      !validCount(input.generalConnectionCount, MAX_EDGES) ||
      !Array.isArray(input.regions) || input.regions.length > REGIONS.length ||
      !input.regions.every((region) => typeof region === 'string' && REGION_SET.has(region)) ||
      new Set(input.regions).size !== input.regions.length ||
      (input.generalNodeCount === 0 && (input.generalConnectionCount !== 0 || input.regions.length !== 0))) {
    return false;
  }
  return true;
}

/** Pure dry-run. Does not change local or remote storage, keys, identity or authorization. */
export function inspectBrainMigration(
  input: unknown,
  destination: {
    readonly expectedCompanionId: string;
    readonly residentCompanionId?: string;
    readonly sourceState: 'active' | 'deactivated' | 'unknown';
    readonly now: number;
  }
): MigrationPreflight {
  const blockers: MigrationBlocker[] = [];
  if (!isBrainMigrationManifest(input)) {
    return {
      metadataCompatible: false, blockers: ['invalid-manifest'],
      requiresAuthenticationAndEncryptedTransfer: true
    };
  }
  if (input.companionId !== destination.expectedCompanionId ||
      !validId(destination.expectedCompanionId)) blockers.push('identity-mismatch');
  // Never overwrite an existing companion, even when its ID happens to match.
  if (destination.residentCompanionId !== undefined) blockers.push('destination-occupied');
  if (destination.sourceState === 'active') blockers.push('source-still-active');
  else if (destination.sourceState !== 'deactivated') blockers.push('source-state-unverified');
  if (!Number.isSafeInteger(destination.now) || destination.now < 0 ||
      input.generatedAt > destination.now + CLOCK_SKEW_MS) blockers.push('future-timestamp');
  return {
    metadataCompatible: blockers.length === 0, blockers,
    requiresAuthenticationAndEncryptedTransfer: true
  };
}
