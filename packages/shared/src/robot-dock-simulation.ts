import {
  isNexusCapabilityManifest,
  type NexusCapability,
  type NexusCapabilityManifest,
  type NexusLinkState
} from './robot-capabilities';

/**
 * Pure state machine for a simulated dock. This is not a transport, a pairing
 * mechanism, a permission grant or a motor-control implementation.
 */
export interface SimulatedDockState {
  state: NexusLinkState;
  node: NexusCapabilityManifest | null;
  enabledCapabilities: readonly NexusCapability[];
  fault: string | null;
}

export const createSimulatedDock = (): SimulatedDockState => ({
  state: 'disconnected',
  node: null,
  enabledCapabilities: [],
  fault: null
});

export type SimulatedDockEvent =
  | { type: 'detected'; manifest: unknown }
  | { type: 'authenticated' }
  | { type: 'ready' }
  | { type: 'disconnected' }
  | { type: 'fault'; reason: string };

const fail = (reason: string): SimulatedDockState => ({
  state: 'fault', node: null, enabledCapabilities: [], fault: reason
});

export function transitionSimulatedDock(
  current: SimulatedDockState,
  event: SimulatedDockEvent
): SimulatedDockState {
  if (event.type === 'disconnected') return createSimulatedDock();
  if (event.type === 'fault') return fail(event.reason.slice(0, 128) || 'unknown');
  if (event.type === 'detected') {
    if (!isNexusCapabilityManifest(event.manifest)) return fail('invalid-capability-manifest');
    if (event.manifest.role === 'mobile-brain') return fail('invalid-dock-node-role');
    return { state: 'pairing', node: event.manifest, enabledCapabilities: [], fault: null };
  }
  if (event.type === 'authenticated') {
    if (current.state !== 'pairing' || !current.node) return fail('invalid-authentication-transition');
    return { ...current, state: 'authenticated' };
  }
  if (current.state !== 'authenticated' || !current.node) return fail('invalid-ready-transition');
  // Motion remains unavailable in simulation even when advertised by the node.
  return {
    ...current,
    state: 'ready',
    enabledCapabilities: current.node.capabilities.filter((capability) => capability !== 'robot.motion')
  };
}
