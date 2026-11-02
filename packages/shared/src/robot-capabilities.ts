/**
 * Platform-neutral, data-only contracts for Nexus Companion host capabilities.
 * No physical actuator should execute a request based on these types alone.
 */
export const ROBOT_PROTOCOL_VERSION = 1 as const;

export type NexusCapability =
  | 'display.face'
  | 'audio.input'
  | 'audio.output'
  | 'camera.front'
  | 'camera.rear'
  | 'sensor.position'
  | 'sensor.distance'
  | 'compute.inference'
  | 'robot.motion';

export type NexusNodeRole = 'mobile-brain' | 'compute-node' | 'robot-controller' | 'face-display';

export interface NexusCapabilityManifest {
  protocolVersion: typeof ROBOT_PROTOCOL_VERSION;
  nodeId: string;
  role: NexusNodeRole;
  capabilities: readonly NexusCapability[];
}

export interface NexusEnvelope<T = unknown> {
  protocolVersion: typeof ROBOT_PROTOCOL_VERSION;
  messageId: string;
  companionId: string;
  sessionId: string;
  sequence: number;
  timestampMs: number;
  messageType: string;
  payload: T;
}

export type NexusLinkState = 'disconnected' | 'pairing' | 'authenticated' | 'ready' | 'fault';

export interface NexusMotionRequest {
  motionGroup: string;
  action: string;
  durationMs: number;
  normalizedSpeed: number;
}

const validIdentifier = (value: unknown): value is string =>
  typeof value === 'string' && value.length > 0 && value.length <= 128 && /^[a-zA-Z0-9._:-]+$/.test(value);

const capabilities: readonly NexusCapability[] = [
  'display.face', 'audio.input', 'audio.output', 'camera.front', 'camera.rear',
  'sensor.position', 'sensor.distance', 'compute.inference', 'robot.motion'
];

export function isNexusCapabilityManifest(value: unknown): value is NexusCapabilityManifest {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<NexusCapabilityManifest>;
  return item.protocolVersion === ROBOT_PROTOCOL_VERSION
    && validIdentifier(item.nodeId)
    && ['mobile-brain', 'compute-node', 'robot-controller', 'face-display'].includes(item.role ?? '')
    && Array.isArray(item.capabilities)
    && item.capabilities.length <= capabilities.length
    && new Set(item.capabilities).size === item.capabilities.length
    && item.capabilities.every((capability: unknown) => capabilities.includes(capability as NexusCapability));
}

/** Structural validation only. Authentication, replay windows and consent belong to the host. */
export function isNexusEnvelope(value: unknown): value is NexusEnvelope {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<NexusEnvelope>;
  return item.protocolVersion === ROBOT_PROTOCOL_VERSION
    && validIdentifier(item.messageId)
    && validIdentifier(item.companionId)
    && validIdentifier(item.sessionId)
    && typeof item.sequence === 'number' && Number.isSafeInteger(item.sequence) && item.sequence >= 0
    && typeof item.timestampMs === 'number' && Number.isSafeInteger(item.timestampMs) && item.timestampMs >= 0
    && typeof item.messageType === 'string' && item.messageType.length > 0 && item.messageType.length <= 128
    && Object.prototype.hasOwnProperty.call(item, 'payload');
}

/** Conservative data validation; does NOT authorize or execute robot movement. */
export function isBoundedMotionRequest(value: unknown): value is NexusMotionRequest {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<NexusMotionRequest>;
  return validIdentifier(item.motionGroup)
    && validIdentifier(item.action)
    && typeof item.durationMs === 'number' && Number.isSafeInteger(item.durationMs)
    && item.durationMs > 0 && item.durationMs <= 5000
    && typeof item.normalizedSpeed === 'number' && Number.isFinite(item.normalizedSpeed)
    && item.normalizedSpeed >= 0 && item.normalizedSpeed <= 1;
}
