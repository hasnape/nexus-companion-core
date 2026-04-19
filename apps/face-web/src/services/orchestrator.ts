import { BrowserVoiceAdapter, SilentVoiceAdapter } from '@nexus/adapters';
import {
  createCompanionEngine,
  createDefaultCompanionProfile,
  isTechnicalMemoryContent,
  LocalMemoryStore,
  type CompanionMemoryItem,
  type MemoryStore,
  isWakeFragmentNoise,
  isWakeOnlyInput,
  stripWakePrefix
} from '@nexus/companion-core';
import { createAction, type CompanionAction, type InternalState, type MemoryRecord, type TrainingConfig } from '@nexus/shared';
import { createDefaultState, transitionState } from '@nexus/decision';
import type { MemoryState } from '@nexus/memory';

export interface CompanionSnapshot {
  state: InternalState;
  action: CompanionAction;
  logs: string[];
  conversation: Array<{ from: 'user' | 'companion'; text: string }>;
  brainSummary?: {
    mode: string;
    focus: string;
    currentUserNeed: string;
    pendingConfirmations: string[];
    nonSensitiveSummary: string[];
  };
}

const browserSafeStore = (): MemoryStore => {
  const storage = typeof window !== 'undefined' && window.localStorage
    ? window.localStorage
    : {
      getItem: () => null,
      setItem: () => {}
    };
  return new LocalMemoryStore('nexus-companion-core-memory-v2a', storage);
};

const toLegacyRecord = (memory: CompanionMemoryItem): MemoryRecord => ({
  id: memory.id,
  type: memory.type === 'user_preference' ? 'preference' : memory.type === 'relationship_context' ? 'relationship' : 'fact',
  content: memory.content,
  confidence: memory.confidence,
  updatedAt: memory.updatedAt
});

const toLegacyMemoryState = (memories: CompanionMemoryItem[]): MemoryState => ({
  session: memories
    .filter((memory) => memory.type === 'conversation_summary' || memory.type === 'system_note')
    .map(toLegacyRecord),
  longTerm: memories
    .filter((memory) => memory.type === 'project_context' || memory.type === 'relationship_context' || memory.type === 'user_profile')
    .map(toLegacyRecord),
  behavioral: memories.filter((memory) => memory.type === 'user_preference').map(toLegacyRecord)
});

export class CompanionRuntime {
  private readonly engine = createCompanionEngine({
    memoryStore: browserSafeStore(),
    profile: createDefaultCompanionProfile()
  });
  private readonly voice = ('speechSynthesis' in window ? new BrowserVoiceAdapter() : new SilentVoiceAdapter());
  private training: TrainingConfig = { proactivity: 0.5, silenceTolerance: 0.6, greetingFrequency: 0.6, emotionalIntensity: 0.6, chatterCooldownMs: 20000 };
  private state = createDefaultState();
  private action = createAction('wake_up', 0.5);
  private logs: string[] = [];
  private conversation: Array<{ from: 'user' | 'companion'; text: string }> = [];
  private memoryState: MemoryState = { session: [], longTerm: [], behavioral: [] };
  private memoryCandidates: CompanionMemoryItem[] = [];
  private brainSummary: CompanionSnapshot['brainSummary'];

  async init(): Promise<void> {
    this.memoryState = toLegacyMemoryState(await this.engine.listMemories());
  }

  getSnapshot(): CompanionSnapshot {
    return {
      state: this.state,
      action: this.action,
      logs: this.logs.slice(-30),
      conversation: this.conversation.slice(-20),
      brainSummary: this.brainSummary
    };
  }

  getMemory() { return this.memoryState; }

  getMemoryCandidates(): CompanionMemoryItem[] { return this.memoryCandidates; }

  async clearCompanionMemory(): Promise<void> {
    await this.engine.clearMemories();
    this.memoryState = { session: [], longTerm: [], behavioral: [] };
    this.memoryCandidates = [];
    this.brainSummary = undefined;
    this.conversation = [];
    this.logs = [];
    this.log('memory cleared by user');
  }

  setTraining(next: TrainingConfig): void { this.training = next; }

  applyPresence(isPresent: boolean): CompanionSnapshot {
    const now = Date.now();
    this.state = transitionState(this.state, { at: now, userSeen: isPresent, mode: isPresent ? 'attentive' : 'idle', attentionTarget: isPresent ? 'user' : 'none' });
    this.log(`event received: presence=${isPresent}`);
    return this.getSnapshot();
  }

  async handleUserMessage(text: string): Promise<CompanionSnapshot> {
    const now = Date.now();
    const normalizedInput = text.trim();

    if (isWakeOnlyInput(normalizedInput)) {
      this.state = transitionState(this.state, { at: now, interacted: true, mode: 'attentive', attentionTarget: 'user', mood: 'warm' });
      this.action = createAction('listen_attentive', this.training.emotionalIntensity);
      this.conversation.push({ from: 'companion', text: 'Je t’écoute.' });
      this.memoryCandidates = [];
      this.log('wake-only input handled locally');
      return this.getSnapshot();
    }

    const strippedInput = stripWakePrefix(normalizedInput);
    this.state = transitionState(this.state, { at: now, interacted: true, mode: 'thinking', attentionTarget: 'user', mood: 'curious' });
    this.conversation.push({ from: 'user', text: strippedInput });

    const reply = await this.engine.processUserMessage({
      userMessage: strippedInput,
      recentConversationSummary: this.conversation.slice(-6).map((line) => `${line.from}: ${line.text}`).join(' | '),
      appState: { isOnline: typeof navigator !== 'undefined' ? navigator.onLine : true, visualMode: this.state.mode }
    });

    this.memoryCandidates = reply.decision.memoryCandidates;
    this.brainSummary = reply.brainSummary;
    this.memoryState = toLegacyMemoryState(await this.engine.listMemories());

    this.state = transitionState(this.state, { at: now, mode: 'speaking', mood: 'warm' });
    this.action = createAction('speak_calm', this.training.emotionalIntensity);
    this.conversation.push({ from: 'companion', text: reply.text });

    if (this.voice.available) {
      await this.voice.speak(reply.text);
    }

    this.log(`decision: ${reply.decision.intent}`);
    return this.getSnapshot();
  }

  async addPreference(content: string): Promise<void> {
    const reply = await this.engine.processUserMessage({ userMessage: `Je préfère ${content}` });
    this.memoryCandidates = reply.decision.memoryCandidates;
    this.memoryState = toLegacyMemoryState(await this.engine.listMemories());
    this.log('memory write: preference');
  }

  async removeMemory(id: string): Promise<void> {
    await this.engine.deleteMemory(id);
    this.memoryState = toLegacyMemoryState(await this.engine.listMemories());
    this.log(`memory write: remove ${id}`);
  }

  async listDisplayableMemories(): Promise<CompanionMemoryItem[]> {
    const memories = await this.engine.listMemories();
    return memories.filter((memory) => !memory.sensitive && !isTechnicalMemoryContent(memory.content) && !isWakeFragmentNoise(memory.content));
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
