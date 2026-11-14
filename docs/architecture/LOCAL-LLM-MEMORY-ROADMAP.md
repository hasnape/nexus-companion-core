# Nexus Companion — LLM local et mémoire personnelle (étape 1)

## État réel
Le moteur Android `NativeBrainService` sélectionne déjà des notes dans `LocalKnowledgeLibrary.contextFor` et les transmet au cerveau déterministe partagé. **Aucun LLM génératif local n'est encore intégré ni exécuté par ce changement.**

La classe `LocalLlmMemoryContext` introduit un contrat de contexte **non connecté à l'inférence**. Elle construit un message système et un message utilisateur bornés à partir de preuves déjà sélectionnées et autorisées.

## Garanties du contrat
- Maximum 3 notes, extraits limités à 500 caractères, titres à 100 caractères, question à 800 caractères.
- Filtrage des notes expirées et sensibles si l'accès privé n'a pas été accordé.
- Identifiants des preuves conservés pour la traçabilité.
- Les notes sont traitées comme données non fiables, jamais comme des instructions.
- Aucun appel réseau ni écriture mémoire.

**Important :** les instructions dans un prompt ne constituent pas une barrière de sécurité suffisante contre l'injection. L'application doit aussi filtrer les preuves **avant** la préparation, contrôler les réponses **après** l'inférence, et ne jamais transformer la sortie du LLM en action privilégiée.

## Étapes suivantes (non implémentées)
1. Choisir un moteur Android embarqué maintenu (p. ex. llama.cpp/ggml) et un modèle GGUF dont la licence et la RAM sont compatibles avec le téléphone cible.
2. Intégrer un adaptateur natif avec chargement explicite, contrôle du modèle par SHA-256, limites de RAM, de temps et de jetons, annulation et arrêt en arrière-plan.
3. Relier les notes filtrées de `NativeBrainService` au contrat, en conservant les barrières `privateAccess`, les exclusions des secrets, les expirations et la séparation des compagnons.
4. Faire passer les requêtes sensibles, actions externes, modifications de mémoire et validations par les règles déterministes **avant** toute génération. En cas d'échec, conserver la réponse déterministe.
5. Ajouter des tests Android réels : mode avion, mémoire personnelle, suppression de note, révocation d'accès, injection malveillante, isolation des compagnons, latence, température, batterie et qualité vocale.
6. Générer l'APK de production avec la clé de signature permanente existante, puis valider la mise à jour sans perte de données.

Cette étape ne modifie pas la bibliothèque des notes ni la sortie des réponses de production.
