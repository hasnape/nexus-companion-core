import type { CompanionMemoryItem, CompanionStoragePreference, MemoryCategory, MemorySource } from './types';

const SENSITIVE_PATTERNS = [
  /santé|maladie|diagnostic|médicament|therapy|health/i,
  /religion|croyance|foi/i,
  /politique|parti|vote/i,
  /adresse|localisation|gps|location/i,
  /carte bancaire|iban|compte|revenu|salaire|crypto wallet|password/i,
  /intime|sexual|orientation/i
];

const PREFERENCE_PATTERNS = [/je préfère|je prefere|j'aime|tu peux me parler/i];
const PROJECT_PATTERNS = [/projet|roadmap|produit|feature|release|deadline|objectif/i];
const PRECISE_LOCATION_PATTERNS = [/adresse exacte|exact address|coordonn[ée]es gps|latitude|longitude|localisation exacte/i];
const CLOUD_EVERYTHING_PATTERNS = [/stocke (toutes|tout) mes donn[ée]es dans le cloud|store everything.*cloud/i];

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
  if (PREFERENCE_PATTERNS.some((pattern) => pattern.test(content))) return 'user_preference';
  if (PROJECT_PATTERNS.some((pattern) => pattern.test(content))) return 'project_context';
  return 'conversation_summary';
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
  const content = userMessage.trim();
  if (!content || content.length < 12) return [];

  const looksMemorable = PREFERENCE_PATTERNS.some((pattern) => pattern.test(content))
    || PROJECT_PATTERNS.some((pattern) => pattern.test(content))
    || /je suis|j'habite|mon prénom|mon prenom|souviens-toi|remember my/i.test(content)
    || PRECISE_LOCATION_PATTERNS.some((pattern) => pattern.test(content));

  if (!looksMemorable) return [];

  const sensitive = isSensitiveMemoryContent(content);
  const storagePreference = inferStoragePreference(content, sensitive);
  const cloudRisk = CLOUD_EVERYTHING_PATTERNS.some((pattern) => pattern.test(content));
  return [createMemoryItem({
    type: inferMemoryType(content),
    content,
    source,
    confidence: sensitive ? 0.52 : 0.78,
    importance: sensitive ? 0.5 : 0.7,
    tags: sensitive ? ['sensitive', 'candidate'] : cloudRisk ? ['candidate', 'cloud-risk'] : ['candidate'],
    requiresConfirmation: sensitive || cloudRisk,
    sensitive,
    storagePreference
  })];
};
