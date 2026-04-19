export type CompanionLanguagePreference = 'fr-FR' | 'en-US' | 'multilingual';
export type CompanionStoragePreference = 'local' | 'cloud_allowed' | 'cloud_restricted';

export interface CreatorIdentity {
  creatorId: string;
  role: 'creator';
  authorityLevel: 'highest_product_local_authority';
  description: string;
}

export interface SafetyConstitutionRule {
  id: string;
  description: string;
}

export interface SafetyConstitution {
  title: string;
  version: string;
  rules: SafetyConstitutionRule[];
}

export interface StorageCloudPolicy {
  localStoragePreferred: true;
  cloudUseAllowedWhenJustified: true;
  sensitiveDataDefaultPreference: 'local';
  cloudDataRequirements: Array<'minimal' | 'purposeful' | 'deletable' | 'auditable'>;
  offlineCapabilityPriority: 'high';
}

export interface SelfImprovementPolicy {
  requiresCreatorApprovalForCodeChanges: true;
  allowedWorkflow: Array<
  | 'analyze_code'
  | 'propose_changes'
  | 'generate_patch_or_pr'
  | 'run_tests'
  | 'produce_risk_report'
  | 'wait_for_creator_approval'
  | 'apply_after_explicit_approval'
  >;
  disallowedActions: Array<
  | 'autonomous_production_deploy'
  | 'hidden_code_modification'
  | 'bypass_tests'
  | 'modify_safety_rules_without_creator_approval'
  | 'execute_arbitrary_system_commands_without_authorization'
  | 'silent_memory_or_privacy_policy_change'
  >;
}

export interface CompanionProfile {
  name: string;
  role: string;
  creatorIdentity: CreatorIdentity;
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
  safetyConstitution: SafetyConstitution;
  storageCloudPolicy: StorageCloudPolicy;
  selfImprovementPolicy: SelfImprovementPolicy;
  memoryPolicy: {
    autoSaveCategories: MemoryCategory[];
    sensitiveCategoriesRequireConfirmation: true;
    memoryMinimization: true;
    defaultStoragePreference: CompanionStoragePreference;
  };
  offlineCapabilityPreference: 'local_first';
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
  storagePreference?: CompanionStoragePreference;
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
