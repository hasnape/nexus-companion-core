import { MemoryEngine, FileMemoryStore } from '@nexus/memory';
import { CompanionOrchestrator } from './orchestrator/orchestrator';
import { LocalTemplateResponseProvider } from './runtime/local-response-provider';
import { personality, trainingConfig } from './api/config';

async function bootstrap(): Promise<void> {
  const memory = new MemoryEngine(new FileMemoryStore('nexus-companion-brain-memory'));
  await memory.init();
  const orchestrator = new CompanionOrchestrator({
    memory,
    responseProvider: new LocalTemplateResponseProvider(),
    personality,
    training: trainingConfig
  });
  const result = orchestrator.onPerception({
    isUserPresent: true,
    attentionTarget: 'user',
    confidence: 0.8,
    observedAt: Date.now()
  });
  console.log('[brain-service]', result.log, result.state.mode);
}

bootstrap();
