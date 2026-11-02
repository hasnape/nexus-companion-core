import { describe, expect, it } from 'vitest';
import { isBoundedMotionRequest, isNexusCapabilityManifest, isNexusEnvelope } from './robot-capabilities';

describe('portable robot capability contracts', () => {
  it('accepts known, non-duplicated capabilities', () => {
    expect(isNexusCapabilityManifest({ protocolVersion: 1, nodeId: 'phone-01', role: 'mobile-brain', capabilities: ['camera.rear', 'display.face'] })).toBe(true);
    expect(isNexusCapabilityManifest({ protocolVersion: 1, nodeId: 'phone-01', role: 'mobile-brain', capabilities: ['robot.motion', 'robot.motion'] })).toBe(false);
    expect(isNexusCapabilityManifest({ protocolVersion: 2, nodeId: 'phone-01', role: 'mobile-brain', capabilities: [] })).toBe(false);
  });
  it('rejects malformed and unversioned messages', () => {
    const valid = { protocolVersion: 1, messageId: 'm-1', companionId: 'nexus-1', sessionId: 's-1', sequence: 0, timestampMs: 100, messageType: 'face.state', payload: {} };
    expect(isNexusEnvelope(valid)).toBe(true);
    expect(isNexusEnvelope({ ...valid, sequence: -1 })).toBe(false);
    expect(isNexusEnvelope({ ...valid, companionId: '' })).toBe(false);
    expect(isNexusEnvelope({ ...valid, protocolVersion: 9 })).toBe(false);
  });
  it('rejects out-of-range motion payloads without executing anything', () => {
    const valid = { motionGroup: 'head', action: 'turn', durationMs: 1000, normalizedSpeed: 0.25 };
    expect(isBoundedMotionRequest(valid)).toBe(true);
    expect(isBoundedMotionRequest({ ...valid, normalizedSpeed: 1.1 })).toBe(false);
    expect(isBoundedMotionRequest({ ...valid, durationMs: 10000 })).toBe(false);
    expect(isBoundedMotionRequest({ ...valid, normalizedSpeed: NaN })).toBe(false);
  });
});
