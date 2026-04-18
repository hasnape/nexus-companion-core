import { useEffect, useMemo, useState } from 'react';
import { CompanionRuntime } from '../services/orchestrator';
import type { TrainingConfig } from '@nexus/shared';

export const useCompanion = () => {
  const runtime = useMemo(() => new CompanionRuntime(), []);
  const [snapshot, setSnapshot] = useState(runtime.getSnapshot());
  const [memory, setMemory] = useState(runtime.getMemory());

  useEffect(() => {
    runtime.init().then(() => setMemory(runtime.getMemory()));
    const interval = window.setInterval(() => setSnapshot(runtime.applyPresence(Math.random() > 0.45)), 9000);
    return () => window.clearInterval(interval);
  }, [runtime]);

  return {
    snapshot,
    memory,
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
    }
  };
};
