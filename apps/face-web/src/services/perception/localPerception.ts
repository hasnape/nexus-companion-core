/**
 * Nexus Perception V1: capability-neutral contracts only.
 * No camera or microphone activation, network access, or biometric identity inference.
 * Engines must be explicitly initialized and invoked by a consenting caller.
 */
export type PerceptionModality = 'speech' | 'vision';
export type PerceptionState = 'unavailable' | 'ready' | 'running' | 'error';
export type ProcessingLocation = 'on-device' | 'remote';

export interface PerceptionConsent {
  readonly modality: PerceptionModality;
  readonly granted: boolean;
  readonly grantedAt: number | null;
}

export interface PerceptionCapability {
  readonly id: string;
  readonly modality: PerceptionModality;
  readonly processing: ProcessingLocation;
  readonly offlineCapable: boolean;
  readonly modelVersion: string;
}

export interface SpeechTranscript {
  readonly text: string;
  readonly language: string;
  readonly isFinal: boolean;
  readonly confidence?: number;
}

export interface VisualObservation {
  readonly category: string;
  readonly confidence: number;
  readonly timestamp: number;
  readonly bounds?: Readonly<{ x: number; y: number; width: number; height: number }>;
}

export interface SpeechRecognitionEngine {
  readonly capability: PerceptionCapability;
  transcribe(audio: Float32Array, sampleRate: number, signal?: AbortSignal): Promise<SpeechTranscript[]>;
  dispose(): Promise<void>;
}

export interface VisualRecognitionEngine<TFrame = unknown> {
  readonly capability: PerceptionCapability;
  observe(frame: TFrame, signal?: AbortSignal): Promise<VisualObservation[]>;
  dispose(): Promise<void>;
}

export class PerceptionPermissionError extends Error {
  constructor(modality: PerceptionModality) {
    super(`Consent required for ${modality} perception`);
    this.name = 'PerceptionPermissionError';
  }
}

/** Explicit consent is mandatory; no silent remote fallback. */
export function assertLocalPerceptionAllowed(
  capability: PerceptionCapability,
  consent: PerceptionConsent
): void {
  if (!consent.granted || consent.modality !== capability.modality) {
    throw new PerceptionPermissionError(capability.modality);
  }
  if (capability.processing !== 'on-device' || !capability.offlineCapable) {
    throw new Error('Local perception requires an offline-capable on-device engine');
  }
}

export async function transcribeLocally(
  engine: SpeechRecognitionEngine,
  consent: PerceptionConsent,
  audio: Float32Array,
  sampleRate: number,
  signal?: AbortSignal
): Promise<SpeechTranscript[]> {
  assertLocalPerceptionAllowed(engine.capability, consent);
  if (!Number.isFinite(sampleRate) || sampleRate <= 0) throw new RangeError('Invalid sample rate');
  if (signal?.aborted) throw new Error('Perception cancelled');
  return engine.transcribe(audio, sampleRate, signal);
}

export async function observeLocally<TFrame>(
  engine: VisualRecognitionEngine<TFrame>,
  consent: PerceptionConsent,
  frame: TFrame,
  signal?: AbortSignal
): Promise<VisualObservation[]> {
  assertLocalPerceptionAllowed(engine.capability, consent);
  if (signal?.aborted) throw new Error('Perception cancelled');
  return engine.observe(frame, signal);
}
