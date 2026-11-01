import { describe, expect, it, vi } from 'vitest';
import {
  assertLocalPerceptionAllowed,
  observeLocally,
  PerceptionPermissionError,
  transcribeLocally,
  type PerceptionCapability,
  type SpeechRecognitionEngine,
  type VisualRecognitionEngine
} from './localPerception';

const speech: PerceptionCapability = {
  id: 'test-speech', modality: 'speech', processing: 'on-device',
  offlineCapable: true, modelVersion: 'test'
};
const vision: PerceptionCapability = { ...speech, id: 'test-vision', modality: 'vision' };

describe('local perception privacy boundary', () => {
  it('requires consent matching the modality', () => {
    expect(() => assertLocalPerceptionAllowed(speech, { modality: 'speech', granted: false, grantedAt: null }))
      .toThrow(PerceptionPermissionError);
    expect(() => assertLocalPerceptionAllowed(speech, { modality: 'vision', granted: true, grantedAt: 1 }))
      .toThrow(PerceptionPermissionError);
  });

  it('rejects remote and non-offline engines even with consent', () => {
    const consent = { modality: 'speech' as const, granted: true, grantedAt: 1 };
    expect(() => assertLocalPerceptionAllowed({ ...speech, processing: 'remote' }, consent)).toThrow();
    expect(() => assertLocalPerceptionAllowed({ ...speech, offlineCapable: false }, consent)).toThrow();
  });

  it('transcribes only with local consent and valid sample rate', async () => {
    const transcribe = vi.fn(async () => [{ text: 'bonjour', language: 'fr', isFinal: true }]);
    const engine: SpeechRecognitionEngine = { capability: speech, transcribe, dispose: async () => {} };
    const consent = { modality: 'speech' as const, granted: true, grantedAt: 1 };
    await expect(transcribeLocally(engine, consent, new Float32Array(2), 16000)).resolves.toHaveLength(1);
    await expect(transcribeLocally(engine, consent, new Float32Array(2), 0)).rejects.toThrow(RangeError);
    expect(transcribe).toHaveBeenCalledTimes(1);
  });

  it('never calls visual engine without consent or after cancellation', async () => {
    const observe = vi.fn(async () => [{ category: 'cup', confidence: 0.9, timestamp: 1 }]);
    const engine: VisualRecognitionEngine<string> = { capability: vision, observe, dispose: async () => {} };
    await expect(observeLocally(engine, { modality: 'vision', granted: false, grantedAt: null }, 'frame'))
      .rejects.toThrow(PerceptionPermissionError);
    const controller = new AbortController();
    controller.abort();
    await expect(observeLocally(engine, { modality: 'vision', granted: true, grantedAt: 1 }, 'frame', controller.signal))
      .rejects.toThrow('cancelled');
    expect(observe).not.toHaveBeenCalled();
  });
});
