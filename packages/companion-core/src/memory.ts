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

const PREFERENCE_PATTERNS = [/je préfère|je prefere|j'aime|tu peux me parler|je veux fonctionner|le cr[ée]ateur pr[ée]f[èe]re/i];
const PROJECT_PATTERNS = [/projet|roadmap|produit|feature|release|deadline|objectif|sans internet|offline|le projet actuel est/i];
const RELATIONSHIP_PATTERNS = [
  /parle[- ]moi de mani[èe]re (?:plus )?(?:professionnelle?|directe?|claire?|structur[ée]e?)/i,
  /sois plus (?:professionnel(?:le)?|direct(?:e)?|clair(?:e)?|structur[ée]e?)/i,
  /garde un style (?:clair|structur[ée]|professionnel|direct)/i,
  /notre relation doit rester professionnelle/i
];
const STATIC_USER_PROFILE_PATTERNS = [
  /je m['’]appelle\s+/i,
  /mon nom est\s+/i,
  /mon pr[ée]nom est\s+/i,
  /le cr[ée]ateur est\s+/i,
  /j['’]habite\s+/i,
  /je vis [àa]\s+/i,
  /vous habitez [àa]\s+/i,
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
const CREATOR_CANONICAL_IDENTITY = 'ingénieur Amine 0410';
const MEMORY_WRAPPER_PREFIX = /^(?:(?:nexus(?:\s+companion)?|companion)\s+)?(?:souviens(?:-| )toi(?:\s+que)?|retiens(?:\s+que)?|m[ée]morise(?:\s+que)?|garde en m[ée]moire(?:\s+que)?)\s+/i;
const SENSITIVE_LOCATION_PATTERNS = [
  /j['’]habite\s+[àa]\s+/i,
  /je vis\s+[àa]\s+/i,
  /vous habitez\s+[àa]\s+/i,
  /vous vivez\s+[àa]\s+/i,
  /\bhabite\s+[àa]\s+/i,
  /\bvit\s+[àa]\s+/i,
  /localisation|adresse/i
];

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

const cleanMemoryFactText = (content: string): string => {
  const unwrappedQuotes = content
    .trim()
    .replace(/^["“”'`«»]+/u, '')
    .replace(/["“”'`«»]+$/u, '')
    .trim();
  const dedupedPunctuation = unwrappedQuotes
    .replace(/[!?.,;:]{2,}$/u, (value) => value[0] ?? '')
    .replace(/["“”'`«»]+$/u, '')
    .trim();
  return dedupedPunctuation;
};

const canonicalizeCreatorIdentity = (rawFact: string): string | null => {
  const lower = rawFact.toLowerCase();
  if (!/(je m['’]appelle|mon nom est|mon pr[ée]nom est)/i.test(rawFact)) return null;
  if (/amine\s*0410/i.test(lower)) return `Le créateur est ${CREATOR_CANONICAL_IDENTITY}.`;
  const named = rawFact.match(/(?:je m['’]appelle|mon nom est|mon pr[ée]nom est)\s+(.+)$/i)?.[1]?.trim();
  if (!named) return null;
  return `Le créateur est ${named}.`;
};

const normalizeMemoryFact = (fact: string): string => {
  const cleaned = cleanMemoryFactText(fact).replace(/\s+/g, ' ').trim();
  if (!cleaned) return '';

  const canonicalIdentity = canonicalizeCreatorIdentity(cleaned);
  if (canonicalIdentity) return canonicalIdentity;

  const project = cleaned.match(/^mon projet actuel est\s+(.+)$/i)?.[1]?.trim();
  if (project) return `Le projet actuel est ${cleanMemoryFactText(project)}.`;
  const looseProject = cleaned.match(/^(.+)\s+est mon projet[.!?]?$/i)?.[1]?.trim();
  if (looseProject) return `Le projet actuel est ${cleanMemoryFactText(looseProject)}.`;

  const creatorPref = cleaned.match(/^je pr[ée]f[èe]re\s+(.+)$/i)?.[1]?.trim();
  if (creatorPref) return `Le créateur préfère ${cleanMemoryFactText(creatorPref)}.`;

  const liveAt = cleaned.match(/^j['’]habite\s+[àa]\s+(.+)$/i)?.[1]?.trim();
  if (liveAt) return `Vous habitez à ${cleanMemoryFactText(liveAt)}.`;

  if (/[.!?]$/u.test(cleaned)) return cleaned;
  return `${cleaned}.`;
};

const stripMemoryCommandWrapper = (input: string): string => {
  const wakeStripped = stripWakePrefix(input, { allowFullNameWake: true }).trim();
  return wakeStripped.replace(MEMORY_WRAPPER_PREFIX, '').trim();
};

export const normalizeMemoryCandidateContent = (input: string): string => {
  const stripped = stripMemoryCommandWrapper(input);
  return normalizeMemoryFact(stripped || input);
};

export const normalizeMemoryContentKey = (content: string): string => content
  .toLowerCase()
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
  .replace(/[^\p{L}\p{N}\s]/gu, ' ')
  .replace(/\s+/g, ' ')
  .trim();

export const shouldRequireLocationConfirmation = (content: string): boolean => (
  SENSITIVE_LOCATION_PATTERNS.some((pattern) => pattern.test(content))
);

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
  const raw = stripWakePrefix(userMessage, { allowFullNameWake: true }).trim();
  const rawForDetection = stripMemoryCommandWrapper(raw);
  const content = normalizeMemoryCandidateContent(raw);
  if (!content || content.length < 12) return [];
  if (isWakeFragmentNoise(content) || isIncompleteMemoryCommand(content)) return [];

  const looksMemorable = PREFERENCE_PATTERNS.some((pattern) => pattern.test(rawForDetection) || pattern.test(content))
    || PROJECT_PATTERNS.some((pattern) => pattern.test(rawForDetection) || pattern.test(content))
    || RELATIONSHIP_PATTERNS.some((pattern) => pattern.test(rawForDetection) || pattern.test(content))
    || isStableUserProfileStatement(rawForDetection)
    || isStableUserProfileStatement(content)
    || /mon prénom|mon prenom|souviens-toi|remember my|retiens que|m[ée]morise que|garde en m[ée]moire que/i.test(rawForDetection)
    || PRECISE_LOCATION_PATTERNS.some((pattern) => pattern.test(rawForDetection) || pattern.test(content));

  const likelySmallTalk = /^(ok|merci|salut|hello|bonjour)[!. ]*$/i.test(content);
  if (!looksMemorable || likelySmallTalk) return [];

  const sensitive = isSensitiveMemoryContent(content) || shouldRequireLocationConfirmation(content);
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
