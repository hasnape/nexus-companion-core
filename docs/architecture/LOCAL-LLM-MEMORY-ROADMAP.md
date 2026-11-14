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


## Évolutivité vers un téléphone plus puissant

Le cœur cognitif (identité, personnalité, mémoire chiffrée, autorisations et moteur de sécurité) reste indépendant du modèle génératif et du matériel. Un changement de téléphone doit migrer le cerveau par la procédure de sauvegarde/restauration chiffrée existante, **jamais** en dupliquant les notes dans un modèle. Le modèle GGUF est une ressource interchangeable, à acquérir et vérifier séparément.

`LocalLlmResourcePolicy` est un **sélecteur de ressources consultatif**, disponible pour le futur adaptateur d'inférence :
- Budget calculé à partir de la RAM totale **et disponible**, des processeurs, du stockage, du niveau de batterie, du mode d'économie et de la température Android, réévalué à chaque demande
- Paliers indicatifs : moteur de règles seul si mémoire insuffisante, 0,5B Q4 (léger), 1,5B Q4 (compact), 3B Q4 (standard), 7B Q4 (avancé)
- Profils `ECO`, `ADAPTIVE`, `MAXIMUM` ; le mode Maximum n'annule jamais les garde-fous mémoire, batterie, température ou arrière-plan
- Notes contextuelles : 3 à 8 selon le palier, sans modifier le format stocké et sans déplacer les données personnelles vers Internet
- Quand la capacité chute ou l'application passe en arrière-plan, inférence refusée et moteur de règles conservé

Ce sont des **candidats**, non des modèles livrés ni des performances garanties. À l'installation, un futur gestionnaire de modèles devra mesurer la mémoire réellement nécessaire, vérifier signature/empreinte/licence du modèle, sa taille et son empreinte RAM, puis sélectionner uniquement un modèle compatible. Un appareil 4 Go peut rester en mode de règles tant que la RAM réellement disponible est insuffisante ; ajouter de la RAM à un futur appareil ne doit pas exiger une réécriture du code ni de la mémoire.

### Critères de passage en production

1. Tests avec appareils à faible RAM (4 Go) et à plus grande RAM (8–16 Go ou davantage) : chargement à froid, mémoire maximale, pression Android, mode avion, voix, température, batterie
2. Repli immédiat sur le cerveau déterministe en cas d'erreur de modèle, de manque de RAM ou de fermeture forcée
3. Les contextes de conversation et les poids de modèle ne sont **pas** des souvenirs permanents ; seuls les souvenirs confirmés sont stockés dans la bibliothèque chiffrée
4. Identité/personnalité et notes exportables/restaurables indépendamment du modèle ; pas de fuite entre compagnons
5. Les résultats de `LocalLlmResourcePolicy` ne doivent jamais être présentés comme une garantie de mémoire, de latence ou de compatibilité avant les benchmarks sur l'appareil cible

**État :** politique et limites dynamiques ajoutées sur branche de développement ; aucun moteur d'inférence, chargement de modèle, bascule runtime ni réglage utilisateur n'est encore branché.
