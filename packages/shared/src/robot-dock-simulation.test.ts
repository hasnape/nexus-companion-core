import { describe, expect, it } from 'vitest';
import { createSimulatedDock, transitionSimulatedDock } from './robot-dock-simulation';

const manifest = {
  protocolVersion: 1 as const,
  nodeId: 'dorsal-compute-01',
  role: 'compute-node' as const,
  capabilities: ['compute.inference', 'robot.motion'] as const
};

describe('simulated Nexus robot dock', () => {
  it('negotiates capabilities only after simulated authentication and readiness', () => {
    let dock = transitionSimulatedDock(createSimulatedDock(), { type: 'detected', manifest });
    expect(dock.state).toBe('pairing');
    expect(dock.enabledCapabilities).toEqual([]);
    dock = transitionSimulatedDock(dock, { type: 'authenticated' });
    expect(dock.state).toBe('authenticated');
    dock = transitionSimulatedDock(dock, { type: 'ready' });
    expect(dock.state).toBe('ready');
    expect(dock.enabledCapabilities).toEqual(['compute.inference']);
  });
  it('disables all capabilities immediately on disconnect', () => {
    const paired = transitionSimulatedDock(createSimulatedDock(), { type: 'detected', manifest });
    const ready = transitionSimulatedDock(transitionSimulatedDock(paired, { type: 'authenticated' }), { type: 'ready' });
    expect(transitionSimulatedDock(ready, { type: 'disconnected' })).toEqual(createSimulatedDock());
  });
  it('rejects invalid manifests and out-of-order events', () => {
    expect(transitionSimulatedDock(createSimulatedDock(), { type: 'ready' }).state).toBe('fault');
    expect(transitionSimulatedDock(createSimulatedDock(), { type: 'detected', manifest: { ...manifest, protocolVersion: 9 } }).state).toBe('fault');
    expect(transitionSimulatedDock(createSimulatedDock(), { type: 'authenticated' }).enabledCapabilities).toEqual([]);
  });
});
