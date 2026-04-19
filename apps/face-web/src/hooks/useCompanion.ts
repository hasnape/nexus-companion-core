import { useEffect, useMemo, useState } from 'react';
import { CompanionRuntime } from '../services/orchestrator';
import type { TrainingConfig } from '@nexus/shared';
import { BrowserVoiceListenerAdapter, SilentVoiceListenerAdapter } from '@nexus/adapters';
import type { CompanionMemoryItem } from '@nexus/companion-core';

export const useCompanion = () => {
  const runtime = useMemo(() => new CompanionRuntime(), []);
  const listener = useMemo(() => {
    const adapter = new BrowserVoiceListenerAdapter();
    return adapter.available ? adapter : new SilentVoiceListenerAdapter();
  }, []);
  const [snapshot, setSnapshot] = useState(runtime.getSnapshot());
  const [memory, setMemory] = useState(runtime.getMemory());
  const [displayMemories, setDisplayMemories] = useState<CompanionMemoryItem[]>([]);
  const [memoryCandidates, setMemoryCandidates] = useState<CompanionMemoryItem[]>([]);
  const [transcript, setTranscript] = useState('');
  const [listenerError, setListenerError] = useState<string | null>(null);
  const [isListening, setListening] = useState(false);

  useEffect(() => {
    runtime.init().then(async () => {
      setMemory(runtime.getMemory());
      setDisplayMemories(await runtime.listDisplayableMemories());
      setMemoryCandidates(runtime.getMemoryCandidates());
    });
    const interval = window.setInterval(() => setSnapshot(runtime.applyPresence(Math.random() > 0.45)), 9000);
    return () => {
      window.clearInterval(interval);
      listener.stop();
    };
  }, [runtime, listener]);

  const refreshCompanionMemoryState = async () => {
    setMemory(runtime.getMemory());
    setDisplayMemories(await runtime.listDisplayableMemories());
    setMemoryCandidates(runtime.getMemoryCandidates());
  };

  const handleMessageAndRefreshState = async (message: string) => {
    setSnapshot(await runtime.handleUserMessage(message));
    await refreshCompanionMemoryState();
  };

  return {
    snapshot,
    memory,
    displayMemories,
    memoryCandidates,
    transcript,
    listenerError,
    voiceInputAvailable: listener.available,
    isListening,
    sendMessage: async (text: string) => {
      await handleMessageAndRefreshState(text);
    },
    setTraining: (config: TrainingConfig) => runtime.setTraining(config),
    triggerAction: (name: (typeof snapshot.action)['name']) => setSnapshot(runtime.trigger(name)),
    addPreference: async (text: string) => {
      await runtime.addPreference(text);
      setMemory(runtime.getMemory());
      setDisplayMemories(await runtime.listDisplayableMemories());
      setMemoryCandidates(runtime.getMemoryCandidates());
    },
    removeMemory: async (id: string) => {
      await runtime.removeMemory(id);
      setMemory(runtime.getMemory());
      setDisplayMemories(await runtime.listDisplayableMemories());
    },
    clearCompanionMemory: async () => {
      await runtime.clearCompanionMemory();
      setMemory(runtime.getMemory());
      setDisplayMemories([]);
      setMemoryCandidates([]);
    },
    startVoiceInput: () => {
      setListenerError(null);
      setTranscript('');
      setSnapshot(runtime.trigger('listen_attentive'));
      listener.start(async ({ transcript: nextTranscript, isFinal }) => {
        setTranscript(nextTranscript);
        if (!isFinal || !nextTranscript) return;
        setListening(false);
        await handleMessageAndRefreshState(nextTranscript);
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
