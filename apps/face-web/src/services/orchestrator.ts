import { BrowserVoiceAdapter, SilentVoiceAdapter } from '@nexus/adapters';
import { DecisionEngine, createDefaultState, transitionState } from '@nexus/decision';
import { MemoryEngine, BrowserMemoryStore } from '@nexus/memory';
import { createAction, type CompanionAction, type InternalState, type PersonalityProfile, type TrainingConfig } from '@nexus/shared';

export interface CompanionSnapshot {
  state: InternalState;
  action: CompanionAction;
  logs: string[];
  conversation: Array<{ from: 'user' | 'companion'; text: string }>;
}

export class CompanionRuntime {
  private readonly decision = new DecisionEngine();
  private readonly memory = new MemoryEngine(new BrowserMemoryStore());
  private readonly voice = ('speechSynthesis' in window ? new BrowserVoiceAdapter() : new SilentVoiceAdapter());
  private readonly personality: PersonalityProfile = {
    displayName: 'Nexus Companion', speakingStyle: 'calm', warmth: 0.7, curiosity: 0.7, proactivity: 0.5, humor: 0.2,
    attachmentStyle: 'balanced', silenceTolerance: 0.6, greetingStyle: 'friendly'
  };
  private training: TrainingConfig = { proactivity: 0.5, silenceTolerance: 0.6, greetingFrequency: 0.6, emotionalIntensity: 0.6, chatterCooldownMs: 20000 };
  private state = createDefaultState();
  private action = createAction('wake_up', 0.5);
  private logs: string[] = [];
  private conversation: Array<{ from: 'user' | 'companion'; text: string }> = [];
  private lastUserMessageAt = Date.now();

  async init(): Promise<void> { await this.memory.init(); }

  getSnapshot(): CompanionSnapshot { return { state: this.state, action: this.action, logs: this.logs.slice(-30), conversation: this.conversation.slice(-20) }; }

  getMemory() { return this.memory.listAll(); }

  setTraining(next: TrainingConfig): void { this.training = next; }

  applyPresence(isPresent: boolean): CompanionSnapshot {
    const now = Date.now();
    this.state = transitionState(this.state, { at: now, userSeen: isPresent, mode: isPresent ? 'attentive' : 'idle', attentionTarget: isPresent ? 'user' : 'none' });
    this.action = this.decision.decide({ now, presenceDetected: isPresent, lastUserMessageAt: this.lastUserMessageAt, state: this.state, personality: this.personality, training: this.training, knownPreference: this.memory.listByType('preference')[0]?.content });
    this.log(`event received: presence=${isPresent}`);
    this.log(`action emitted: ${this.action.name}`);
    return this.getSnapshot();
  }

  async handleUserMessage(text: string): Promise<CompanionSnapshot> {
    const now = Date.now();
    this.lastUserMessageAt = now;
    this.state = transitionState(this.state, { at: now, interacted: true, mode: 'thinking', attentionTarget: 'user', mood: 'curious' });
    this.conversation.push({ from: 'user', text });
    const response = `D'accord. Je retiens: "${text}". ${this.personality.curiosity > 0.6 ? 'Tu veux une suite courte ?' : 'Je reste disponible.'}`;
    this.state = transitionState(this.state, { at: now, mode: 'speaking', mood: 'warm' });
    this.action = createAction('speak_calm', this.training.emotionalIntensity);
    this.conversation.push({ from: 'companion', text: response });
    if (this.voice.available) await this.voice.speak(response);
    this.log('response generated');
    return this.getSnapshot();
  }

  async addPreference(content: string): Promise<void> {
    await this.memory.upsert('behavioral', { id: `pref-${Date.now()}`, content, confidence: 0.8, type: 'preference', updatedAt: Date.now() });
    this.log('memory write: preference');
  }

  async removeMemory(id: string): Promise<void> {
    await this.memory.remove(id);
    this.log(`memory write: remove ${id}`);
  }

  trigger(actionName: CompanionAction['name']): CompanionSnapshot {
    this.action = createAction(actionName, this.training.emotionalIntensity);
    if (actionName === 'sleep_mode') this.state = transitionState(this.state, { at: Date.now(), mode: 'sleep', mood: 'tired' });
    if (actionName === 'wake_up') this.state = transitionState(this.state, { at: Date.now(), mode: 'idle', mood: 'warm' });
    this.log(`action emitted: ${actionName}`);
    return this.getSnapshot();
  }

  private log(entry: string): void {
    this.logs.push(`${new Date().toLocaleTimeString()} | ${entry}`);
  }
}
