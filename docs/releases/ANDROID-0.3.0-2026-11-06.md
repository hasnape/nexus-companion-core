# Nexus Companion Android 0.3.0 — validation du 6 novembre 2026

Statut : alpha native signée, moteur de règles partagé intégré. Ce n'est pas encore un compagnon à compréhension générale ni un LLM local.

## Améliorations livrées

- Réutilisation des règles TypeScript existantes pour les salutations, la décision, les réponses de soutien simples, la mémoire et les limites de sécurité, dans un interpréteur Java local sans navigateur ni WebView.
- Proposition « Retenir cette information », relecture et confirmation avant toute écriture. Aucune conversation ou proposition non confirmée n'est enregistrée.
- Rappel de notes avec la demande « De quoi te souviens-tu ? ». Le rappel conversationnel omet les notes expirées et les contenus détectés comme sensibles, cite au maximum trois extraits courts et indique qu'ils ne sont pas vérifiés.
- Consultation, recherche sans accents et suppression avec confirmation dans « Mes notes ». Les notes complètes restent disponibles même si le rappel vocal est abrégé.
- Traitement du moteur et des notes sur un worker unique pour garder l'interface réactive. Requêtes, contexte JSON et exécution du moteur sont bornés. Un calcul ou une lecture ne peut exécuter une instruction provenant d'une note.
- Micro suspendu pendant le calcul, la réponse vocale et les dialogues privés. Bouton micro placé au-dessus des réponses longues, arrêt manuel disponible pendant le traitement. Les dialogues sont fermés lors de la destruction de l'écran.
- Contrôle de cohérence entre les sources du moteur, le bundle embarqué et leurs SHA-256, y compris après un clone Windows. Gradle refuse les sources modifiées sans régénération du bundle.
- Espace de noms, schéma de notes v1, chiffrement Android Keystore, applicationId et clé de signature conservés. `versionCode` 3 → 4, nom `0.3.0-native-alpha`. Aucune permission INTERNET ajoutée.

## Résultats vérifiés

| Vérification | Résultat |
| --- | --- |
| Lint et typecheck TypeScript | Réussis |
| Tests partagés / web | 318 tests réussis dans 44 fichiers |
| Construction de tous les workspaces | Réussie |
| `check:native-brain` | Bundle identique à la reconstruction et aux sources |
| Contrôle négatif Gradle | Source partagée modifiée temporairement : compilation refusée ; source restaurée |
| Tests Android debug | 58 tests réussis, aucune erreur |
| Tests Android release | 58 tests réussis, aucune erreur |
| Android Lint debug et release | Aucune erreur ni avertissement |
| Script complet de distribution | Réussi, APK debug et release produits |
| Certificat de l'APK release | Même certificat permanent que 0.2.1 |
| Bundle dans l'APK | Identique à l'asset validé, SHA-256 contrôlé |
| Manifeste de l'APK | API 31 minimum, cible API 35, permission RECORD_AUDIO seule |

Les 58 tests Android comprennent : 24 tests de session vocale, 9 tests de l'interpréteur avec le bundle réel, 8 parcours d'interface sous Robolectric, 8 tests de bibliothèque, 7 tests de recherche et validation et 2 tests du service reliant les notes chiffrées au moteur. Les tests de bibliothèque utilisent un fournisseur AES-GCM de JVM ; la production utilise toujours Android Keystore.

La validation d'interface couvre notamment : échange écrit sans autorisation micro ni permission Internet, proposition annulée non enregistrée, note manuelle annulée, liste vide, bibliothèque endommagée préservée, limite de saisie, échec d'enregistrement jamais présenté comme réussi et fermeture des dialogues privés lors de la recréation de l'écran.

## Fichier de diffusion

- APK : `nexus-companion-v0.3.0-native-alpha-release.apk`
- Taille : 3540065 octets.
- SHA-256 APK : `ca7b3dc3caa9d166234f9c43c0964596d2095960bb655c910062614aa0d1a3cb`.
- SHA-256 moteur embarqué : `4474fe5f27a051782d51ea0c33ab787bbcd64f12c7ea9487a839af269c96e3b8`.
- Certificat SHA-256 : `01:44:5C:D0:F0:F5:66:88:FB:A4:6B:AD:32:84:0F:F8:5F:BD:EE:11:9C:DD:BD:2D:3A:19:31:4D:94:C4:72:D1`.

## Recette restante sur téléphone

1. Installer par-dessus une version portant le même certificat, sans désinstaller ni effacer ses données. Une ancienne version de test signée avec une autre clé peut refuser cette mise à jour ; ne pas la désinstaller pour contourner le refus si ses notes doivent être conservées.
2. En mode avion, écrire « Bonjour » et « Comment vas-tu ? ».
3. Dire ou écrire « Retiens que je préfère les réponses courtes ». Vérifier l'absence d'enregistrement avant confirmation ; confirmer avec le bouton puis retrouver la note après fermeture et réouverture de l'application.
4. Demander « De quoi te souviens-tu ? », chercher et supprimer une note dans « Mes notes ».
5. Avec les services français hors ligne installés, tester le réveil « Nexus », la pause pendant la réponse et la reprise. Vérifier aussi arrêt manuel, arrière-plan, verrouillage, interruption audio et autonomie.

Cette version 0.3.0 n'a pas été testée sur un téléphone physique ni installée sur l'émulateur Android de la session précédente. Les tests d'interface Robolectric et de session vocale simulée ne prouvent ni la qualité de reconnaissance du français, ni le fonctionnement du fournisseur Keystore d'un téléphone, ni son autonomie. La conservation des notes d'une installation réelle lors de la mise à jour reste à confirmer sur appareil.

## Limites et retour arrière

Le modèle ASR autonome, le LLM local, le cerveau cognitif complet persistant, l'identité et la passation natives, l'avatar complet, l'export/import Android chiffré et la vérification des mises à jour dans l'application restent à intégrer. La détection de données sensibles est heuristique et ne remplace pas l'authentification du propriétaire.

Un retour au comportement 0.2.1 doit se faire par une nouvelle compilation de ce code avec un versionCode supérieur et la même clé, après validation. Le schéma de notes v1 et son chiffrement n'ont pas changé. Ne pas désinstaller l'application contenant des notes : l'export portable Android n'est pas encore disponible.

Voir [ADR-0002](../architecture/ADR-0002-android-shared-brain.md) pour les choix techniques et les limites du pont natif.
