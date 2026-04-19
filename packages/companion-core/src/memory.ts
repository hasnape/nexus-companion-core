import { isIncompleteMemoryCommand, isWakeFragmentNoise, stripWakePrefix } from './wake';

import type {
  CompanionMemoryItem,
  CompanionStoragePreference,
  CognitiveMemoryLayer,
  MemoryCategory,
  MemorySource,
  MemorySensitivity
} from './types';

const SENSITIVE_PATTERNS = [
  /santé|maladie|diagnostic|médicament|therapy|health/i,
  /religion|croyance|foi/i,
  /politique|parti|vote/i,
  /adresse|localisation|gps|location/i,
  /carte bancaire|iban|compte|revenu|salaire|crypto wallet|password/i,
  /intime|sexual|orientation/i
];

const PREFERENCE_PATTERNS = [/je préfère|je prefere|j'aime|tu peux me parler|je veux fonctionner/i];
const PROJECT_PATTERNS = [/projet|roadmap|produit|feature|release|deadline|objectif|sans internet|offline/i];
const RELATIONSHIP_PATTERNS = [/parle-moi|ton|style|sois plus|communication/i];
const STATIC_USER_PROFILE_PATTERNS = [
  /je m['’]appelle\s+/i,
  /mon nom est\s+/i,
  /mon pr[ée]nom est\s+/i,
  /j['’]habite\s+/i,
  /je vis [àa]\s+/i,
  /mon m[ée]tier est\s+/i,
  /je travaille comme\s+/i,
  /je travaille en tant que\s+/i
];
const TRANSIENT_JE_SUIS_PATTERNS = [
  /^stress[ée]?$/,
  /^fatigu[ée]?$/,
  /^content(?:e)?$/,
  /^malade$/,
  /^triste$/,
  /^en col[èe]re$/,
  /^occup[ée]?$/,
  /^en retard$/,
  /^perdu(?:e)?$/,
  /^inquiet(?:e)?$/,
  /^disponible$/,
  /^indisponible$/,
  /^dans le train$/,
  /^en train de\b/
];
const STABLE_ROLE_KEYWORDS = /(d[ée]veloppeur|developpeur|ing[ée]nieur|ingenieur|technicien|auto-entrepreneur|entrepreneur|fondateur|cofondateur|architecte|consultant|designer|chef de projet|support)/i;
const PRECISE_LOCATION_PATTERNS = [/adresse exacte|exact address|coordonn[ée]es gps|latitude|longitude|localisation exacte/i];
const CLOUD_EVERYTHING_PATTERNS = [/stocke (toutes|tout) mes donn[ée]es dans le cloud|store everything.*cloud/i];

const isStableJeSuisProfile = (content: string): boolean => {
  const match = content.match(/je suis\s+([^,.!?]+)/i);
  if (!match) return false;
  const complement = match[1].trim().toLowerCase();
  if (!complement) return false;
  if (TRANSIENT_JE_SUIS_PATTERNS.some((pattern) => pattern.test(complement))) return false;
  return STABLE_ROLE_KEYWORDS.test(complement);
};

const isStableUserProfileStatement = (content: string): boolean => (
  STATIC_USER_PROFILE_PATTERNS.some((pattern) => pattern.test(content)) || isStableJeSuisProfile(content)
);

export const isSensitiveMemoryContent = (content: string): boolean => SENSITIVE_PATTERNS.some((pattern) => pattern.test(content));

type CreateMemoryItemInput = Omit<CompanionMemoryItem, 'id' | 'createdAt' | 'updatedAt'> & {
  id?: string;
  createdAt?: number;
  updatedAt?: number;
};

export const createMemoryItem = (memory: CreateMemoryItemInput): CompanionMemoryItem => {
  if (!memory.type || !memory.content || !memory.source) {
    throw new Error('Memory item requires type, content and source.');
  }

  const now = Date.now();
  return {
    ...memory,
    id: memory.id ?? `mem-${now}-${Math.random().toString(36).slice(2, 7)}`,
    createdAt: memory.createdAt ?? now,
    updatedAt: memory.updatedAt ?? now
  };
};

const inferMemoryType = (content: string): MemoryCategory => {
  if (PROJECT_PATTERNS.some((pattern) => pattern.test(content))) return 'project_context';
  if (isStableUserProfileStatement(content)) return 'user_profile';
  if (PREFERENCE_PATTERNS.some((pattern) => pattern.test(content))) return 'user_preference';
  if (RELATIONSHIP_PATTERNS.some((pattern) => pattern.test(content))) return 'relationship_context';
  return 'conversation_summary';
};

const inferMemoryLayer = (content: string): CognitiveMemoryLayer => {
  if (PROJECT_PATTERNS.some((pattern) => pattern.test(content))) return 'project_context';
  if (PREFERENCE_PATTERNS.some((pattern) => pattern.test(content))) return 'preference';
  if (RELATIONSHIP_PATTERNS.some((pattern) => pattern.test(content))) return 'relationship_context';
  return 'episodic';
};

const inferSensitivity = (content: string): MemorySensitivity => {
  if (PRECISE_LOCATION_PATTERNS.some((pattern) => pattern.test(content))) return 'critical';
  if (isSensitiveMemoryContent(content)) return 'high';
  return 'low';
};

const inferStoragePreference = (content: string, sensitive: boolean): CompanionStoragePreference => {
  if (sensitive && PRECISE_LOCATION_PATTERNS.some((pattern) => pattern.test(content))) return 'cloud_restricted';
  if (sensitive) return 'cloud_restricted';
  if (CLOUD_EVERYTHING_PATTERNS.some((pattern) => pattern.test(content))) return 'cloud_allowed';
  return 'local';
};

export const extractMemoryCandidates = (
  userMessage: string,
  source: MemorySource = 'user_message'
): CompanionMemoryItem[] => {
  const content = stripWakePrefix(userMessage).trim();
  if (!content || content.length < 12) return [];
  if (isWakeFragmentNoise(content) || isIncompleteMemoryCommand(content)) return [];

  const looksMemorable = PREFERENCE_PATTERNS.some((pattern) => pattern.test(content))
    || PROJECT_PATTERNS.some((pattern) => pattern.test(content))
    || RELATIONSHIP_PATTERNS.some((pattern) => pattern.test(content))
    || isStableUserProfileStatement(content)
    || /mon prénom|mon prenom|souviens-toi|remember my|retiens que/i.test(content)
    || PRECISE_LOCATION_PATTERNS.some((pattern) => pattern.test(content));

  const likelySmallTalk = /^(ok|merci|salut|hello|bonjour)[!. ]*$/i.test(content);
  if (!looksMemorable || likelySmallTalk) return [];

  const sensitive = isSensitiveMemoryContent(content);
  const storagePreference = inferStoragePreference(content, sensitive);
  const cloudRisk = CLOUD_EVERYTHING_PATTERNS.some((pattern) => pattern.test(content));
  const sensitivity = inferSensitivity(content);

  return [createMemoryItem({
    type: inferMemoryType(content),
    layer: inferMemoryLayer(content),
    content,
    source,
    confidence: sensitive ? 0.52 : 0.78,
    importance: sensitive ? 0.5 : 0.7,
    stability: /toujours|par d[ée]faut|habituellement|sans internet/i.test(content) ? 0.7 : 0.52,
    sensitivity,
    tags: sensitive ? ['sensitive', 'candidate'] : cloudRisk ? ['candidate', 'cloud-risk'] : ['candidate'],
    requiresConfirmation: sensitive || cloudRisk || sensitivity === 'critical',
    sensitive,
    storagePreference
  })];
};
