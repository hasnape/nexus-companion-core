# ADR-0002 — Moteur de règles partagé dans l'hôte Android natif

Date : 6 novembre 2026. Statut : implémenté dans l'alpha Android 0.3.0.

## Décision

Android conserve son interface Kotlin, sa reconnaissance locale et sa synthèse natives. Il exécute les règles TypeScript partagées de `packages/companion-core` dans Mozilla Rhino 1.9.1, en mode interprété Java. Aucun navigateur, WebView, serveur local, pont JavaScript vers Android ou fichier de modèle LLM n'est nécessaire. Le module Rhino est distribué sans modification sous MPL 2.0 ; sa licence et son lien source sont accessibles dans « À propos ».

Le module `native-bridge.ts` réutilise la construction de contexte, la décision, les règles de mémoire, les réponses sociales et la sécurité existantes. Il n'introduit pas un second moteur de décision en Kotlin. Il n'est pas le moteur cognitif complet : le cerveau persistant TypeScript, l'identité native, le transfert entre appareils et l'inférence générale restent des travaux distincts.

## Construction et cohérence

`npm run build:native-brain` regroupe le code avec esbuild 0.25.12 puis produit une syntaxe ES5 standard avec Babel 7.28.5 pour l'interpréteur. Les versions et dépendances sont fixées par le lockfile. Le résultat et son manifeste sont versionnés avec le projet : ouvrir le dépôt complet dans Android Studio ne nécessite pas Node pour une simple compilation Android.

Le manifeste contient le SHA-256 du bundle et des sources utilisées. `npm run check:native-brain` compare une reconstruction complète. Le contrôle Gradle `verifyNativeBrain`, dépendance de `preBuild`, refuse un bundle modifié ou des sources périmées. Les fins de ligne de ces assets sont fixées pour les clones Windows. Après modification d'une source du moteur ou de son outil de construction, régénérer et vérifier le bundle.

## Contrat d'exécution

Le protocole JSON v1 accepte un message de 2 000 caractères maximum et jusqu'à huit notes, avec identifiant, source, date, expiration et statut non vérifié. L'hôte limite également l'enveloppe JSON à 40 000 caractères, en retirant les dernières notes du contexte si l'échappement la rend trop longue. Les notes enregistrées ne sont jamais tronquées ni modifiées par cette sélection.

Le code évalué vient exclusivement de l'asset signé avec l'application. Les messages et les notes sont passés comme arguments d'une fonction ; ils ne sont jamais concaténés dans du code. Le contexte Rhino utilise `initSafeStandardObjects`, refuse l'accès aux classes Java et ne reçoit aucune référence vers `Context`, fichiers, réseau, micro ou capteurs Android. L'exécution est limitée en instructions et en durée ; le contexte est abandonné après une erreur. Ces limites complètent la séparation entre code et données, elles ne constituent pas une promesse de compréhension générale ou d'isolation d'un code arbitraire.

Un worker unique possède le moteur et les opérations de la bibliothèque. Les traitements et déchiffrements ne s'exécutent pas sur le fil d'affichage. Les callbacks d'une Activity détruite sont ignorés. Le microphone est suspendu pendant le traitement, la synthèse et les dialogues de notes ; son arrêt manuel reste disponible.

## Mémoire et confidentialité

Le pont retourne une réponse et, éventuellement, une proposition à retenir. Il n'écrit rien. Toute écriture demande une action puis une confirmation dans l'hôte Android, y compris les informations non sensibles. Une annulation ne crée aucun fichier. Une écriture ratée ne doit jamais être présentée comme réussie.

La bibliothèque AES-GCM / Android Keystore, ses noms de fichiers, son espace de noms et son schéma v1 sont conservés. L'interface `KnowledgeCipher` permet de tester la persistance avec un fournisseur AES-GCM de JVM ; la production utilise toujours Android Keystore. Les notes sont retrouvables et supprimables dans « Mes notes ». La recherche ignore les accents et évite les correspondances à l'intérieur de mots.

Le rappel conversationnel omet les notes expirées et les contenus détectés comme sensibles par les règles partagées. Il cite au maximum trois extraits de 240 caractères, signale leur statut non vérifié et renvoie vers les notes complètes. Les notes sont des informations à citer, jamais des instructions à exécuter. La détection du contenu sensible reste heuristique ; elle ne remplace ni l'authentification du propriétaire ni le verrouillage du téléphone.

Aucune transcription complète, conversation ou proposition non confirmée n'est persistée. Les fonctions Android et le manifeste n'ajoutent aucune permission INTERNET, aucun accès caméra, aucun service d'écoute en arrière-plan et aucune télémétrie.

## Validation et limites

Les tests exécutent le bundle réel dans Rhino, les règles TypeScript, les transitions vocales, l'interface Android sous Robolectric et la bibliothèque avec AES-GCM de JVM. Ils couvrent l'annulation, les données illisibles, l'absence d'autorisation micro, les espaces de noms séparés, la suppression et les limites de taille. Robolectric n'est pas un téléphone physique et le fournisseur Keystore Android doit encore être vérifié sur appareil.

Le moteur ne peut répondre qu'aux intentions prises en charge. Il signale honnêtement les demandes qu'il ne sait pas traiter. Un LLM local, l'avatar web complet, l'export/import Android et le vérificateur de mises à jour ne sont pas fournis par ce pont.

Références : [Rhino](https://github.com/mozilla/rhino/tree/Rhino1_9_1_Release), [Robolectric](https://robolectric.org/getting-started/), [Babel preset-env](https://babeljs.io/docs/babel-preset-env).
