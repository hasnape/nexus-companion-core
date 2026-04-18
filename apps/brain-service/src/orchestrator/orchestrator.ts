import { DecisionEngine, transitionState } from '@nexus/decision';
import type { CompanionAction, InternalState, PerceptionSignal, PersonalityProfile, ResponseProvider, TrainingConfig } from '@nexus/shared';
import { createDefaultState } from '@nexus/decision';
import type { MemoryEngine } from '@nexus/memory';

export interface OrchestratorDeps {
  memory: MemoryEngine;
  responseProvider: ResponseProvider;
  personality: PersonalityProfile;
  training: TrainingConfig;
}

export interface OrchestrationResult {
  action: CompanionAction;
  state: InternalState;
  reply?: string;
  log: string;
}

export class CompanionOrchestrator {
  private readonly decisionEngine = new DecisionEngine();
  private state: InternalState = createDefaultState();
  private lastUserMessageAt = Date.now();

  constructor(private readonly deps: OrchestratorDeps) {}

  getState(): InternalState {
    return this.state;
  }

  onPerception(signal: PerceptionSignal): OrchestrationResult {
    this.state = transitionState(this.state, {
      at: signal.observedAt,
      attentionTarget: signal.attentionTarget === 'none' ? 'none' : 'user',
      userSeen: signal.isUserPresent,
      mode: signal.isUserPresent ? 'attentive' : 'idle'
    });
    const action = this.decisionEngine.decide({
      now: signal.observedAt,
      presenceDetected: signal.isUserPresent,
      lastUserMessageAt: this.lastUserMessageAt,
      state: this.state,
      personality: this.deps.personality,
      training: this.deps.training,
      knownPreference: this.deps.memory.listByType('preference')[0]?.content
    });
    return { action, state: this.state, log: `perception -> ${action.name}` };
  }

  onUserMessage(text: string, at: number): OrchestrationResult {
    this.lastUserMessageAt = at;
    this.state = transitionState(this.state, { at, mode: 'thinking', interacted: true, attentionTarget: 'user' });
    const knownPreferences = this.deps.memory.listByType('preference').map((m) => m.content);
    const reply = this.deps.responseProvider.generate({
      state: this.state,
      personality: this.deps.personality,
      userMessage: text,
      knownPreferences
    });
    const action = this.decisionEngine.decide({
      now: at,
      presenceDetected: true,
      lastUserMessageAt: this.lastUserMessageAt,
      state: transitionState(this.state, { at, mode: 'speaking', mood: 'warm' }),
      personality: this.deps.personality,
      training: this.deps.training,
      knownPreference: knownPreferences[0]
    });
    this.state = transitionState(this.state, { at, mode: 'speaking', mood: 'warm' });
    return { action, state: this.state, reply, log: `message -> ${action.name}` };
  }
}
