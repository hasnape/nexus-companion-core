import { describe, expect, it } from 'vitest';
import { createBrainMigrationManifest, inspectBrainMigration, isBrainMigrationManifest } from './brainPortability';
import type { CognitiveGraph } from './cognitiveGraph';

const graph: CognitiveGraph = {
  companionId: 'individual-nora',
  nodes: [
    { id: 'general-a', region: 'semantic-memory', scope: 'companion-general', label: 'Water', confidence: 0.8, createdAt: 1 },
    { id: 'general-b', region: 'language', scope: 'companion-general', label: 'Word', confidence: 0.8, createdAt: 1 },
    { id: 'private-a', region: 'episodic-memory', scope: 'owner-private', ownerId: 'secret-owner', label: 'PRIVATE_NAME_KEEP_OUT', confidence: 0.8, createdAt: 1 }
  ],
  connections: [
    { fromId: 'general-a', toId: 'general-b', relation: 'related', weight: 0.6, updatedAt: 2 },
    { fromId: 'general-a', toId: 'private-a', relation: 'private-invalid-edge', weight: 0.6, updatedAt: 2 }
  ]
};
const destination = {
  expectedCompanionId: 'individual-nora', sourceState: 'deactivated' as const, now: 1000
};

describe('portable brain preflight (metadata-only)', () => {
  it('counts only general knowledge and never reveals owner IDs, labels or edges', () => {
    const manifest = createBrainMigrationManifest(graph, 'snapshot-one', 100);
    expect(manifest.generalNodeCount).toBe(2);
    expect(manifest.generalConnectionCount).toBe(1);
    expect(manifest.regions).toEqual(['semantic-memory', 'language']);
    const serialized = JSON.stringify(manifest);
    expect(serialized).not.toContain('PRIVATE_NAME_KEEP_OUT');
    expect(serialized).not.toContain('secret-owner');
    expect(serialized).not.toContain('Water');
    expect(serialized).not.toContain('general-a');
    expect(manifest.payload).toBe('metadata-only');
  });

  it('preserves one individual identity and disallows import into another companion', () => {
    const manifest = createBrainMigrationManifest(graph, 's', 100);
    expect(inspectBrainMigration(manifest, destination).metadataCompatible).toBe(true);
    expect(inspectBrainMigration(manifest, {
      ...destination, expectedCompanionId: 'individual-alif'
    }).blockers).toContain('identity-mismatch');
  });

  it('blocks occupied destinations, active sources and unverified source state', () => {
    const manifest = createBrainMigrationManifest(graph, 's', 100);
    expect(inspectBrainMigration(manifest, { ...destination, residentCompanionId: 'other' }).blockers).toContain('destination-occupied');
    expect(inspectBrainMigration(manifest, { ...destination, sourceState: 'active' }).blockers).toContain('source-still-active');
    expect(inspectBrainMigration(manifest, { ...destination, sourceState: 'unknown' }).blockers).toContain('source-state-unverified');
  });

  it('rejects malicious and future-dated untrusted manifests', () => {
    const manifest = createBrainMigrationManifest(graph, 's', 100);
    expect(isBrainMigrationManifest({ ...manifest, ownerSecret: 'token' })).toBe(false);
    expect(isBrainMigrationManifest({ ...manifest, schemaVersion: 2 })).toBe(false);
    expect(isBrainMigrationManifest({ ...manifest, regions: ['safety', 'safety'] })).toBe(false);
    expect(isBrainMigrationManifest({ ...manifest, generalNodeCount: -1 })).toBe(false);
    expect(inspectBrainMigration({ ...manifest, ownerSecret: 'token' }, destination).blockers).toContain('invalid-manifest');
    expect(inspectBrainMigration({ ...manifest, generatedAt: 1_000_000 }, destination).blockers).toContain('future-timestamp');
  });

  it('accepts an empty independent companion but never declares authorization', () => {
    const manifest = createBrainMigrationManifest({ companionId: 'individual-nora', nodes: [], connections: [] }, 's', 100);
    expect(isBrainMigrationManifest(manifest)).toBe(true);
    const result = inspectBrainMigration(manifest, destination);
    expect(result.metadataCompatible).toBe(true);
    expect(result.requiresAuthenticationAndEncryptedTransfer).toBe(true);
  });

  it('rejects unsafe snapshot IDs and timestamps', () => {
    expect(() => createBrainMigrationManifest(graph, ' ', 100)).toThrow();
    expect(() => createBrainMigrationManifest(graph, 's', Number.NaN)).toThrow();
  });
});
