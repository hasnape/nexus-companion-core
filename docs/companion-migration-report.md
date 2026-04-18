# Rapport de migration — Nexus Support Hub ➜ Nexus Companion Face V1

Date: 2026-04-18

## 1) Audit ciblé (A/B/C/D)

### A. Réutilisé tel quel
- Monorepo React/TypeScript/Vite et scripts workspace (`npm run dev`, `build`, `typecheck`, `test`).
- Noyau de décision et modèle d’état interne (`packages/decision`).
- Modèle mémoire multi-couches + persistance locale (`packages/memory`).
- Contrats/types/actions abstraites (`packages/shared`).
- Face screen, panneaux de contrôle et thème dark de base (`apps/face-web/src/components/*`, `styles/main.css`).

### B. Adapté
- Orchestrateur front (`apps/face-web/src/services/orchestrator.ts`) :
  - ajout d’une intention simple (greeting/preference/fact/question),
  - ajout d’écriture mémoire automatique sur signaux utilisateur.
- Hook compagnon (`apps/face-web/src/hooks/useCompanion.ts`) :
  - ajout pipeline micro > transcription > envoi message.
- UI App (`apps/face-web/src/App.tsx`) :
  - ajout commandes micro start/stop,
  - affichage statut audio/transcription/erreur.
- Adapters voix (`packages/adapters/src/voice`) :
  - ajout listener Web Speech API avec fallback silencieux.

### C. Isolé / ignoré pour V1
- `apps/brain-service` conservé mais non requis pour exécuter l’écran compagnon V1 local.
- `packages/adapters/src/future-robot` conservé pour extension ultérieure, non utilisé en runtime V1.
- Fonctionnalités robotiques/perception avancée non activées (caméra/vision physique).

### D. Supprimable plus tard (proposition, non supprimé)
- Logs UI très verbeux du panneau contrôle (à simplifier quand le mode dev sera séparé).
- Simulateur de présence aléatoire (à remplacer par vraie perception/caméra).
- Réponses template locales quand un provider LLM stable sera branché.

## 2) Base compagnon autonome livrée

La base autonome existe sous `apps/face-web` avec:
- écran visage vivant et états,
- micro via Web Speech API (quand disponible),
- réponse vocale via speech synthesis (fallback silencieux),
- mémoire locale session/long-terme/comportement,
- structure modulaire prête pour extension (`packages/*` + adapters).

## 3) Nettoyage prudent

Aucune suppression agressive effectuée.
Choix volontaire: préserver l’existant et isoler les extensions futures.

## 4) Validation exécutée

- `npm run lint`
- `npm run typecheck`
- `npm run test`
- `npm run build`

## 5) Risques résiduels

- La Web Speech API varie selon navigateur (micro potentiellement indisponible).
- Le pipeline d’intention V1 est volontairement simple (règles heuristiques).
- Persistance mémoire limitée au local-first pour cette version.

## 6) État final

Projet compagnon V1 opérationnel, maintenable, extensible, sans destruction de la base existante.
