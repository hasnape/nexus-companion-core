export type CompanionLanguagePreference = 'fr-FR' | 'en-US' | 'multilingual';

export interface CompanionProfile {
  name: string;
  role: string;
  personalityTraits: string[];
  toneRules: string[];
  languagePreference: CompanionLanguagePreference;
  style: {
    warm: boolean;
    clear: boolean;
    protective: boolean;
    practical: boolean;
    emotionallyAware: boolean;
  };
  behaviorRules: string[];
  privacyRules: string[];
  memoryPolicy: {
    autoSaveCategories: MemoryCategory[];
    sensitiveCategoriesRequireConfirmation: true;
  };
  allowedCapabilities: Array<'chat' | 'memory' | 'voice' | 'project_assistance' | 'safety_refusal'>;
}

export type MemoryCategory =
  | 'user_profile'
  | 'user_preference'
  | 'project_context'
  | 'relationship_context'
  | 'conversation_summary'
  | 'system_note';

export type MemorySource = 'user_message' | 'conversation_inference' | 'system' | 'manual';

export interface CompanionMemoryItem {
  id: string;
  type: MemoryCategory;
  content: string;
  source: MemorySource;
  confidence: number;
  importance: number;
  createdAt: number;
  updatedAt: number;
  expiresAt?: number;
  tags?: string[];
  requiresConfirmation?: boolean;
  sensitive?: boolean;
}

export type ResponseIntent =
  | 'answer'
  | 'ask_clarification'
  | 'remember_candidate'
  | 'emotional_support'
  | 'project_help'
  | 'action_request'
  | 'safety_refusal';

export interface CompanionContext {
  profile: CompanionProfile;
  userMessage: string;
  relevantMemories: CompanionMemoryItem[];
  recentConversationSummary?: string;
  appState?: {
    isOnline?: boolean;
    visualMode?: string;
  };
  voiceState?: {
    wakeState?: string;
    isListening?: boolean;
  };
  createdAt: number;
}

export interface CompanionDecision {
  intent: ResponseIntent;
  memoryCandidates: CompanionMemoryItem[];
  suggestedResponseStyle: 'warm' | 'clear' | 'protective' | 'practical' | 'empathetic';
  requiredConfirmations: string[];
  riskFlags: string[];
  nextVisualState: 'idle' | 'listening' | 'thinking' | 'speaking' | 'error';
}

export interface CompanionReply {
  text: string;
  decision: CompanionDecision;
}

export interface CompanionAiProvider {
  generateCompanionReply(context: CompanionContext, decision: CompanionDecision): Promise<string>;
}

export interface MemoryStore {
  listMemories(): Promise<CompanionMemoryItem[]>;
  addMemory(memory: CompanionMemoryItem): Promise<void>;
  updateMemory(memory: CompanionMemoryItem): Promise<void>;
  deleteMemory(id: string): Promise<void>;
  clearMemories(): Promise<void>;
  searchMemories(query: string): Promise<CompanionMemoryItem[]>;
}
