# Nexus Companion — méthode de travail durable

## Objectif
Développer et maintenir **toute la famille Nexus Companion**, à commencer par Alif, comme un compagnon personnel Android hors ligne, fiable, accessible et évolutif. Le dépôt Nexus Support Hub reste indépendant et ne doit jamais être modifié dans ce chantier.

## Cadence de travail
1. Reprendre l'état réel du dépôt, de la branche et de la PR avant chaque modification.
2. Corriger les anomalies confirmées et les régressions avant d'ajouter des fonctions.
3. Avancer par changements petits, cohérents et maintenables, avec un commit descriptif par correction.
4. Étendre les tests automatisés et les scénarios de non-régression ; distinguer tests ajoutés, tests réellement exécutés et essais sur téléphone.
5. Vérifier l'accessibilité, la lisibilité, les petits écrans, la mémoire RAM, la batterie et le fonctionnement hors ligne.
6. Documenter le résultat et les blocages sans annoncer une fonctionnalité comme livrée avant sa compilation, signature et validation.
7. Ne fusionner, ne publier et ne remplacer l'APK installée qu'après vérification de la compatibilité, de la signature, de la conservation des données et des tests nécessaires.

## Direction interface
- Inspiration science-fiction **originale** : surfaces bleu nuit, accents cyan/turquoise, présence animée et légère, navigation claire, priorité au visage du compagnon.
- Préserver un vrai mode visage plein écran et les contrôles de sécurité accessibles.
- Éviter les animations lourdes, les effets gênants et les imitations d'interfaces protégées.
- Conserver les actions et identifiants Android existants lors des refontes, sauf migration testée.
- Les souvenirs doivent indiquer clairement « privé avec code » ou « accessible sans code » ; choix explicite, révocable et jamais automatique.

## Sécurité et mémoire
- Cerveau et mémoire chiffrée locaux en priorité ; Internet est facultatif et n'est pas le cerveau.
- Cloisonner chaque compagnon et son propriétaire ; ne pas divulguer les souvenirs verrouillés dans les réponses, traces, preuves ou contextes LLM.
- Authentification et confirmations avant tout changement de confidentialité ; changements de lots atomiques.
- Ne pas dégrader la reconnaissance vocale, l'écoute de premier plan, l'arrêt de l'écoute pendant la réponse ou les sauvegardes.

## Compte rendu de chaque étape
Indiquer précisément : fichiers touchés, corrections, commits/PR, tests exécutés ou non, statut de la compilation, statut de l'APK et prochain blocage prioritaire.
