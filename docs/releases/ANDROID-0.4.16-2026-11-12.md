# Nexus Companion Android 0.4.16

Cette mise à jour corrige le blocage « Ce fichier ou le cache de mises à jour dépasse la limite autorisée » et augmente les capacités de la mémoire et des échanges. Le paquet reste `fr.nexuscompanion.android`, signé avec la clé permanente ; son code de version passe à 22. L’APK de diffusion conserve le nom historique `native-alpha`, sans application de test séparée.

## Installation depuis la version bloquée

Télécharger `nexus-companion-v0.4.16-native-alpha-release.apk`, puis l’ouvrir dans **Mes fichiers → Téléchargements**. Confirmer **Mettre à jour** dans Android. Cette voie utilise directement l’installateur système, sans dépendre de l’importeur de 0.4.14/0.4.15. Conserver Nexus installé pour garder ses notes et sa clé Keystore. L’installation sur un téléphone physique n’a pas été exécutée dans cet environnement.

## Correctif des mises à jour

L’ancien importeur conservait les copies confiées à Android pendant 24 heures, sans dédoublonnage ni suppression immédiate des versions déjà installées. L’APK 0.4.15 publié fait 62 433 715 octets, sous la limite individuelle de 64 Mio ; trois copies dépassent cependant l’ancien cache de 128 Mio. Les sélections répétées constituent donc un cas reproductible de blocage, même avec un fichier valide.

L’importeur réutilise désormais une copie identique après contrôle de son SHA-256 et de ses métadonnées. Il élimine les versions déjà installées et conserve les snapshots validés dans un cache commun pour qu’une fermeture d’écran n’interrompe pas un autre lecteur. « Nettoyer les anciens APK » conserve la sélection active et ne parcourt que le répertoire des APK temporaires. Les échecs et interruptions d’import effacent leur copie partielle. Les erreurs distinguent taille du fichier, cache plein et manque d’espace libre. Une marge disque de 16 Mio est contrôlée pendant la copie, sans allocation système qui pourrait évincer le document source.

Les contrôles de signature, package, version croissante, compatibilité Android, intégrité après restauration, lecture seule et chemins restent actifs. Le quota est sérialisé entre importeurs. Aucun nouveau droit Android n’est ajouté.

## Capacités

| Ressource | Avant | Maintenant |
| --- | --- | --- |
| Notes locales | 500 | 5 000 |
| Caractères par note, message ou transcription | 2 000 | 8 000 |
| JSON de mémoire avant compression | 8 Mio | 32 Mio |
| Notes candidates d’une réponse | 8 | 16 |
| Résultats de recherche locale | 5 | 20 |
| Requête native avec échappement JSON | 40 000 caractères | 160 000 caractères |
| Attente avant de commencer la phrase suivante | 12 s | 20 s |
| Dictée continue | 58 s | 118 s |
| Proposition en attente d’approbation | 90 s | 180 s |
| Budget du moteur par appel | 3 millions d’instructions / 2,5 s | 120 millions / 5 s |
| Fichier APK / cache d’APK | 64 Mio / 128 Mio | 512 Mio / 1 Gio |

Le contrat `native-capacity.json` génère les constantes Kotlin et la limite du champ texte ; ces fichiers font partie du manifeste d’intégrité. Les plafonds de nombre de notes et de volume total s’appliquent ensemble. La sélection reste classée par pertinence et limitée par le JSON réellement échappé, sans troncature des notes enregistrées. Le format schema 1 / GZIP / AES-GCM et la clé existante sont conservés. Toutes les notes sont accessibles par pages de 50. Le budget initial de 12 millions d’instructions s’est révélé insuffisant pour les grands contextes dans Rhino ; les scénarios complets passent avec le budget final, avec une échéance temporelle conservée.

Les approbations incertaines sont redemandées pour toutes les formulations reconnues, même avec le préfixe « Nexus ». Une approbation ambiante hors conversation reste ignorée. Le micro demeure fermé pendant les réponses et les dialogues privés. Les confirmations, la sécurité du futur corps et les limites de mouvement sont distinctes des capacités de contenu et restent en place. Le moteur demeure local, déterministe et extractif ; augmenter sa capacité ne le transforme pas en modèle génératif.

## Validation

- 225 tests Android dans chacune des variantes debug et release : aucun échec, aucune erreur ni test ignoré. Android Lint : zéro anomalie.
- 370 tests TypeScript ; contrôle des types, ESLint, compilation de tous les workspaces et vérification du bundle embarqué réussis.
- Cas nouveaux : répétition d’un même APK sans accumulation, nettoyage après installation, conservation de la sélection et des notes, manque d’espace, import supérieur à 64 Mio, fermeture puis restauration, bibliothèque dépassant 500 notes, textes longs conservés, recherche de la seizième note dans Rhino, pagination au-delà de 50 notes, dictée supérieure à une minute et confirmations incertaines avec mot d’activation.
- Les scénarios existants de lecture vocale indépendante du micro, plein visage, pupilles, mode corps, arrêt, confidentialité, chiffrement, anciennes notes et intégrité continuent de passer.
- APK principal : 62 444 047 octets ; version `0.4.16-native-alpha`, code 22, Android minimum 31, cible 35. Signature v2 valide, certificat identique à 0.4.15, permissions inchangées, ZIP intègre et alignement 16 Kio vérifié. Quatre ABI ; modèle Vosk et bibliothèques natives identiques à l’APK précédent.

Le code Android validé correspond à l’arbre Git `4490cee99eebe8aba4984e81af9d0347590addbf` ; seul ce compte rendu est ajouté ensuite. Aucun téléphone ni châssis n’était connecté. La précision acoustique, la sortie sonore, le Bluetooth, l’autonomie et les moteurs nécessitent une vérification matérielle. Les durées de dictée sont vérifiées avec une source PCM simulée, sans mesure acoustique réelle.

```text
684e0cb5abc41a4c064dd6f95c8c96a2e23971250c2361c1d955fa68025ad43f  nexus-companion-v0.4.16-native-alpha-release.apk
```
