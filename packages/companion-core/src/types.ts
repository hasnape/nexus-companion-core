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

export type MemorySource =
  | 'user_message'
  | 'conversation_inference'
  | 'system'
  | 'manual'
  | 'creator_instruction'
  | 'environment_signal';

export type CognitiveMemoryLayer =
  | 'episodic'
  | 'semantic'
  | 'preference'
  | 'habit'
  | 'project_context'
  | 'relationship_context'
  | 'environment_context'
  | 'system_learning';

export type MemorySensitivity = 'low' | 'medium' | 'high' | 'critical';

export interface CognitiveMemoryEvidence {
  id: string;
  type: 'interaction' | 'correction' | 'signal' | 'manual_note' | 'system_rule';
  source: MemorySource;
  detail: string;
  capturedAt: number;
}

export interface CompanionMemoryItem {
  id: string;
  type: MemoryCategory;
  layer?: CognitiveMemoryLayer;
  content: string;
  source: MemorySource;
  confidence: number;
  importance: number;
  stability?: number;
  sensitivity?: MemorySensitivity;
  createdAt: number;
  updatedAt: number;
  lastAccessedAt?: number;
  expiresAt?: number;
  tags?: string[];
  evidence?: CognitiveMemoryEvidence[];
  creatorApproved?: boolean;
  requiresConfirmation?: boolean;
  sensitive?: boolean;
  storagePreference?: CompanionStoragePreference;
  lifecycleState?: MemoryLifecycleState;
  archivedAt?: number;
  outdatedAt?: number;
  conflictWithIds?: string[];
}

export type MemoryLifecycleState =
  | 'active'
  | 'dormant'
  | 'archived'
  | 'outdated'
  | 'conflict'
  | 'pending_confirmation'
  | 'creator_deleted';

export interface EnvironmentSignal {
  id: string;
  type:
  | 'app_online_status'
  | 'app_mode'
  | 'visual_state'
  | 'voice_state'
  | 'local_time_bucket'
  | 'device_capability'
  | 'user_provided_context'
  | 'project_context'
  | 'camera_status'
  | 'microphone_status'
  | 'location_status';
  value: string | number | boolean | Record<string, unknown>;
  source: 'app_state' | 'voice_state' | 'user_input' | 'project_state' | 'device_capability';
  capturedAt: number;
  sensitivity: MemorySensitivity;
  consentRequired: boolean;
  storagePreference: CompanionStoragePreference;
  ttl?: number;
}

export interface LearningEvent {
  id: string;
  type:
  | 'user_correction'
  | 'repeated_preference'
  | 'confirmed_memory'
  | 'environment_signal'
  | 'project_update'
  | 'interaction_outcome'
  | 'safety_warning'
  | 'creator_instruction';
  input: string | EnvironmentSignal | CompanionMemoryItem;
  source: MemorySource | EnvironmentSignal['source'];
  createdAt: number;
  confidence: number;
  importance: number;
  suggestedMemoryLayer: CognitiveMemoryLayer;
  requiresConfirmation: boolean;
  riskFlags: string[];
}

export interface CognitiveMemorySummary {
  creatorUserContext: string[];
  currentProjects: string[];
  preferences: string[];
  habits: string[];
  relationshipContext: string[];
  safetyConstraints: string[];
  recentImportantEpisodic: string[];
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
  cognitiveMemorySummary?: CognitiveMemorySummary;
  environmentSignals?: EnvironmentSignal[];
  recentLearningEvents?: LearningEvent[];
  brainSummary?: BrainStateSummary;
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
  learningEvents?: LearningEvent[];
  suggestedResponseStyle: 'warm' | 'clear' | 'protective' | 'practical' | 'empathetic';
  requiredConfirmations: string[];
  riskFlags: string[];
  nextVisualState: 'idle' | 'listening' | 'thinking' | 'speaking' | 'error';
}

export interface CompanionReply {
  text: string;
  decision: CompanionDecision;
  brainSummary?: BrainStateSummary;
}

export type CompanionMode =
  | 'idle'
  | 'listening'
  | 'thinking'
  | 'responding'
  | 'learning'
  | 'blocked'
  | 'needs_confirmation';

export interface WorkingMemoryState {
  recentUserMessage?: string;
  recentAssistantMessage?: string;
  shortTermFacts: string[];
  activeProjectContext?: string;
  activePreferences: string[];
  unresolvedQuestions: string[];
  pendingActions: string[];
  decay: {
    lastDecayAt: number;
    ttlMs: number;
  };
}

export interface AttentionFocus {
  topic: string;
  project?: string;
  confidence: number;
  source: 'user_message' | 'memory' | 'decision' | 'safety';
  reason: string;
  expiresAt: number;
}

export interface PersonalityState {
  tone: 'warm' | 'clear' | 'protective' | 'practical' | 'empathetic';
  energy: 'low' | 'balanced' | 'high';
  empathyLevel: number;
  cautionLevel: number;
  confidenceLevel: number;
  languagePreference: CompanionLanguagePreference;
  creatorRelationshipLabel: string;
  lastUpdatedAt: number;
}

export interface GoalState {
  id: string;
  title: string;
  type:
  | 'answer_user'
  | 'help_project'
  | 'remember_safely'
  | 'ask_confirmation'
  | 'protect_user'
  | 'refuse_unsafe_action'
  | 'maintain_context';
  status: 'active' | 'completed' | 'blocked' | 'expired';
  priority: number;
  evidence: string[];
  requiresCreatorApproval: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface CompanionBrainState {
  version: string;
  creatorId: string;
  currentMode: CompanionMode;
  activeIntent?: ResponseIntent;
  attentionFocus: AttentionFocus;
  workingMemory: WorkingMemoryState;
  personalityState: PersonalityState;
  activeGoals: GoalState[];
  pendingConfirmations: string[];
  lastLearningEvents: string[];
  lastEnvironmentSignals: string[];
  updatedAt: number;
}

export interface CognitiveLoopResult {
  nextState: CompanionBrainState;
  summary: BrainStateSummary;
  changed: boolean;
}

export interface BrainStateSummary {
  mode: CompanionMode;
  focus: string;
  activeProject?: string;
  currentUserNeed: string;
  safeMemoryHints: string[];
  pendingConfirmations: string[];
  safetyNotes: string[];
  nonSensitiveSummary: string[];
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

export interface LearningStore {
  addLearningEvent?(event: LearningEvent): Promise<void>;
  listLearningEvents?(limit?: number): Promise<LearningEvent[]>;
}

export interface CognitiveMemoryStore {
  consolidateMemories?(candidates: CompanionMemoryItem[]): Promise<CompanionMemoryItem[]>;
}

export interface BrainStateStore {
  getBrainState?(): Promise<CompanionBrainState | undefined>;
  setBrainState?(state: CompanionBrainState): Promise<void>;
  updateBrainState?(updater: (state: CompanionBrainState | undefined) => CompanionBrainState): Promise<CompanionBrainState>;
  clearBrainState?(): Promise<void>;
}
