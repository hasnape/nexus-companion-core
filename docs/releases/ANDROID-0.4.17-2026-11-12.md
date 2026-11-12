# Nexus Companion Android 0.4.17

Cette version accélère la recherche locale, corrige des réponses insuffisamment fondées sur les notes et permet leur lecture vocale complète. Elle augmente les capacités utilisables en conservant le format de mémoire, la signature et les modes plein visage/corps de l’application principale.

## Corrections et utilisation

Trois cas reproduits sur 0.4.16 pouvaient répondre avec une phrase sans rapport parce que le titre de la note correspondait à la question : couleur préférée, lieu de travail et raison d’une préférence. Le titre ne remplace plus une preuve dans la phrase. Une valeur courte explicitement étiquetée, telle que « Couleur préférée : bleu », reste exploitable. Les réponses gardent leur provenance et leur statut non vérifié. Le vocabulaire canonique est préindexé au lieu de reparcourir toutes ses familles pour chaque mot.

« **Nexus, lis ma note sur le robot** » sélectionne et lit une note complète sur le sujet. La sélection ne se rabat pas sur une note récente sans rapport. Cette lecture reste locale, ne modifie pas la note, n’envoie aucune requête Internet et respecte les exclusions des informations sensibles et des notes expirées. Le rappel ordinaire garde ses extraits courts. Le protocole natif conserve sa version 1 avec un indicateur optionnel de lecture explicite.

Les réponses longues dépassant la taille d’un appel TTS Android échouaient avec l’ancien envoi unique ; le cas de régression a été reproduit avec un moteur simulé refusant plus de 4 000 caractères. La lecture est désormais découpée sans retirer de caractères. Les morceaux respectent la limite annoncée par le moteur et un plafond de 1 000 caractères ; ils privilégient phrases et espaces et ne coupent pas les paires UTF-16. Le focus audio et la fermeture du micro sont maintenus entre eux. La fenêtre de réponse ne s’ouvre qu’après le dernier morceau. Erreur, interruption, arrêt, arrière-plan ou désactivation de la voix annulent la suite ; les anciens callbacks ne peuvent pas la relancer. Les délais bornent chaque morceau et la lecture entière. Aucun audio n’est enregistré.

## Mémoire et performance

Un index compact d’empreintes de mots sert de préfiltre en mémoire. Toute correspondance est vérifiée contre le texte original : une collision d’empreinte ne peut pas fournir une réponse. Le classement conserve la couverture, les titres, les dates et l’ordre stable existants. Un cache LRU de huit classements évite leur recalcul pour un même snapshot authentifié. L’expiration est filtrée à chaque appel ; une écriture, une suppression, une modification externe ou une altération du fichier empêche la réutilisation d’un ancien index. Ces structures ne sont pas persistées.

Sur 10 000 notes synthétiques et cinq recherches distinctes, l’ancien parcours effectue 50 000 analyses de texte ; le nouveau en effectue cinq après son préfiltre. Dix répétitions des cinq recherches ne déclenchent aucune analyse supplémentaire. Lors du passage release sur le serveur, les cinq parcours initiaux prennent 213,5 ms, la construction de l’index et les cinq recherches 46,6 ms, et les 50 recherches réutilisées 0,123 ms. Ce microbenchmark exclut le stockage et ne prédit pas la vitesse d’un téléphone ; les tests ne dépendent pas d’un seuil temporel.

La préparation du contexte compte chaque note sérialisée une fois et s’arrête avant le dépassement du budget. Elle évite de construire et sérialiser plusieurs fois un contexte trop volumineux. Les notes stockées ne sont pas tronquées. Le format schema 1 / GZIP / AES-GCM et la clé Keystore sont conservés.

## Capacités

| Ressource | 0.4.16 | 0.4.17 |
| --- | --- | --- |
| Notes locales | 5 000 | 20 000 |
| Caractères par note, message ou transcription | 8 000 | 16 000 |
| Notes candidates par tour | 16 | 32 |
| Résultats d’une recherche locale | 20 | 100 |
| Requête native, échappement compris | 160 000 caractères | 384 000 caractères |
| Attente avant de commencer la phrase suivante | 20 s | 30 s |
| Dictée continue | 118 s | 178 s |
| Proposition en attente de confirmation | 180 s | 300 s |
| Budget du moteur par appel | 120 millions d’instructions / 5 s | 480 millions / 5 s |
| Lecture vocale | Un appel, borne de 90 s | Jusqu’à 20 000 caractères répartis ; récupération globale à 20 min |

Le volume total de mémoire reste borné à **32 Mio de JSON avant compression** pour limiter les allocations sur téléphone ; cette borne s’applique conjointement au nombre de notes. Les 32 notes candidates doivent aussi tenir dans le budget réel de contexte. Le contrat commun génère les constantes Kotlin, la limite de saisie et son message d’erreur. Les contrôles de signature, confidentialité, confirmation et mouvement restent actifs. Le moteur demeure local, déterministe et extractif.

## Validation et APK

- **244 tests Android dans chacune des variantes debug et release**, sans échec, erreur ou test ignoré ; Android Lint : zéro anomalie.
- **376 tests TypeScript**, contrôle de types, ESLint, compilation des workspaces et vérification du bundle réussis.
- Une bibliothèque chiffrée de 20 000 notes est lue, recherchée, modifiée et relue après redémarrage. Les tests vérifient les collisions d’empreintes, le cache borné, l’expiration, les écritures externes et les altérations.
- Le moteur Rhino réel retrouve la trente-deuxième note dans un contexte supérieur à l’ancien plafond. Les grands textes échappés respectent le budget sans altérer le stockage.
- La lecture complète de la note, la lecture TTS par morceaux via l’adaptateur Android simulé, les limites d’un moteur plus petit, les erreurs intermédiaires, l’arrêt, les callbacks périmés et la fermeture du micro sont vérifiés. Les scénarios existants de voix indépendante du micro, pupilles, plein visage, corps roulant, arrêts, chiffrement et mise à jour continuent de passer.
- APK principal : `fr.nexuscompanion.android`, version `0.4.17-native-alpha`, code **23**, Android minimum 31, cible 35. **62 455 307 octets**, sous 64 Mio. Signature v2 valide, certificat permanent identique à 0.4.16, ZIP intègre et alignement 16 Kio vérifié. Permissions, quatre ABI, modèle Vosk et bibliothèques natives inchangés.

L’APK est issu de l’arbre Git `a5913985af7ac307c9268cd8392d8ce7ac2f903f`. Seuls le README et ce compte rendu sont actualisés ensuite. Aucun téléphone ni châssis physique n’était connecté. La qualité acoustique, le son réel, le Bluetooth, l’autonomie et les moteurs restent à vérifier sur le matériel. La durée de capture est testée avec une source PCM simulée.

```text
61b326187c2baf531e73040aad24f48adb80854ca165c20a08534fa326476e3c  nexus-companion-v0.4.17-native-alpha-release.apk
```

Installer cet APK principal par-dessus Nexus, sans désinstallation. Depuis un ancien importeur affichant une erreur de cache, ouvrir le téléchargement dans **Mes fichiers → Téléchargements**, puis confirmer **Mettre à jour**. Le correctif de cache de 0.4.16 est conservé.
