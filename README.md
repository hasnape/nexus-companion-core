# Nexus Companion Face (V1)

Compagnon IA **local-first** avec visage animé 2D, voix (fallback silencieux), écoute micro (Web Speech API), mémoire utile locale, moteur d'état interne et micro-décisions sociales.

## Objectif
- Construire une présence crédible sur écran (pas un robot complet).
- Conserver un cerveau exportable vers des adapters physiques futurs.
- Séparer strictement le raisonnement métier des détails d’animation.

## Architecture
- `apps/face-web`: UI React + avatar + panneaux dev + input conversationnel.
- `apps/brain-service`: runtime Node isolé (orchestrateur/test hors UI).
- `packages/shared`: types, événements, contrat adapters, catalogue d’actions abstraites.
- `packages/memory`: mémoire session/long-terme/comportement + persistance browser/file.
- `packages/decision`: état interne + transitions + règles de décision.
- `packages/adapters`: adapters visage/voix/perception/futur robot.

## Catalogue d’actions abstraites
Actions centralisées dans `packages/shared/src/actions.ts`:
`wake_up`, `sleep_mode`, `notice_user`, `greet_user`, `look_at_user`, `look_away_soft`, `idle_happy`, `idle_curious`, `listen_attentive`, `thinking_soft`, `speak_calm`, `ask_followup`, `goodbye_soft`, `gentle_reminder`.

Chaque action possède catégorie, priorité, durée indicative, intensité et caractère interruptible.

## État interne (noyau)
`mode`, `mood`, `energy`, `socialDrive`, `attentionTarget`, `lastInteractionAt`, `lastUserSeenAt`, `currentGoal`.

Transitions centralisées dans `packages/decision/src/state.ts`.

## Mémoire
3 couches:
1. session
2. long-term
3. behavioral

Types stockés: `facts`, `preferences`, `routines`, `relationship signals`, `conversation patterns` (modélisés via `MemoryRecord.type`).

Fonctions V1: lister / ajouter / supprimer + score confiance.
Persistance: `localStorage` (web) et JSON file (service Node).

## Décision engine
Règles dans `packages/decision/src/rules.ts` selon:
- présence détectée
- temps depuis interaction
- état interne
- personnalité
- config d’entrainement (cooldown anti-bavardage, proactivité)
- préférence connue

## Conversation
- Input texte toujours disponible (fallback principal).
- Input micro via Web Speech API quand le navigateur le permet.
- Intention simple V1 (greeting / préférence / fait profil / question) + réponse contextuelle.
- Provider local template/règles contextuelles.
- Interface `ResponseProvider` prête pour brancher Ollama (optionnel futur).

## Config (VITE_... côté web)
- `VITE_COMPANION_NAME`
- `VITE_ENABLE_TTS`
- `VITE_ENABLE_CAMERA`
- `VITE_DEV_SIMULATED_PRESENCE`

Config service Node:
- `COMPANION_NAME`
- (prévu extension) `ENABLE_OLLAMA`, `OLLAMA_BASE_URL`, `OLLAMA_MODEL`

## Lancement
```bash
npm install
npm run dev
```

Service cerveau (optionnel):
```bash
npm run dev:brain
```

## Validation
```bash
npm run lint
npm run typecheck
npm run test
npm run build
```

## Roadmap robotique
- Phase 1: visage écran (actuel)
- Phase 2: tête physique (adapter robot)
- Phase 3: haut du corps
- Phase 4: corps robotique complet

Le cœur décisionnel reste basé sur **actions abstraites** pour réutilisation ROS 2 / actuateurs futurs.

## Application Android native

Le projet Android se trouve dans [`apps/companion-android`](apps/companion-android/README.md). La version 0.4.22 ajoute une lecture à la demande des Aperçus IA de Google affichés dans Chrome, après activation explicite d’un accès Android dédié. Une recherche Web peut revenir lire son résultat accessible ; un texte copié peut aussi être relu après aperçu. L’enregistrement en mémoire reste confirmé et conserve la provenance non vérifiée. Le moteur local, le modèle français, la mémoire chiffrée et les modes visage/corps sont conservés. Voir le [rapport de validation](docs/releases/ANDROID-0.4.22-2026-11-12.md) pour les contrôles et les limites sur appareil. Les [mentions de propriété](OWNERSHIP.md) restent distinctes des licences des composants tiers.
