import { useEffect, useMemo, useState } from 'react';
import { CompanionRuntime } from '../services/orchestrator';
import type { TrainingConfig } from '@nexus/shared';
import { BrowserVoiceListenerAdapter, SilentVoiceListenerAdapter } from '@nexus/adapters';

export const useCompanion = () => {
  const runtime = useMemo(() => new CompanionRuntime(), []);
  const listener = useMemo(() => {
    const adapter = new BrowserVoiceListenerAdapter();
    return adapter.available ? adapter : new SilentVoiceListenerAdapter();
  }, []);
  const [snapshot, setSnapshot] = useState(runtime.getSnapshot());
  const [memory, setMemory] = useState(runtime.getMemory());
  const [transcript, setTranscript] = useState('');
  const [listenerError, setListenerError] = useState<string | null>(null);
  const [isListening, setListening] = useState(false);

  useEffect(() => {
    runtime.init().then(() => setMemory(runtime.getMemory()));
    const interval = window.setInterval(() => setSnapshot(runtime.applyPresence(Math.random() > 0.45)), 9000);
    return () => {
      window.clearInterval(interval);
      listener.stop();
    };
  }, [runtime, listener]);

  return {
    snapshot,
    memory,
    transcript,
    listenerError,
    voiceInputAvailable: listener.available,
    isListening,
    sendMessage: async (text: string) => setSnapshot(await runtime.handleUserMessage(text)),
    setTraining: (config: TrainingConfig) => runtime.setTraining(config),
    triggerAction: (name: (typeof snapshot.action)['name']) => setSnapshot(runtime.trigger(name)),
    addPreference: async (text: string) => {
      await runtime.addPreference(text);
      setMemory(runtime.getMemory());
    },
    removeMemory: async (id: string) => {
      await runtime.removeMemory(id);
      setMemory(runtime.getMemory());
    },
    startVoiceInput: () => {
      setListenerError(null);
      setTranscript('');
      setSnapshot(runtime.trigger('listen_attentive'));
      listener.start(async ({ transcript: nextTranscript, isFinal }) => {
        setTranscript(nextTranscript);
        if (!isFinal || !nextTranscript) return;
        setListening(false);
        setSnapshot(await runtime.handleUserMessage(nextTranscript));
      }, (message) => {
        setListenerError(message);
        setListening(false);
      });
      setListening(listener.listening);
    },
    stopVoiceInput: () => {
      listener.stop();
      setListening(false);
    }
  };
};
