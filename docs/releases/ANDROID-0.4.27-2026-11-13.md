# Nexus Companion Android 0.4.27 — notes pour le LLM et sauvegarde chiffrée

Cette livraison réalise deux étapes de la finalisation : un accès contrôlé du modèle local aux notes autorisées, et une sauvegarde portable de toutes les notes et de leur historique. L’application reste alpha. L’écoute en arrière-plan, les profils multiples, la perception visuelle, la reconnaissance du locuteur, l’export complet de l’identité et des réglages, et le pilotage matériel ne sont pas livrés par cette version.

## Installer et utiliser les notes avec le modèle

1. Installer `nexus-companion-v0.4.27-native-alpha-release.apk` avec **Mettre à jour**, sans désinstaller l’application. Le certificat reste celui de la version 0.4.26 ; les notes existantes gardent leur clé locale.
2. Ouvrir **Modèle de langage local**. Si nécessaire, télécharger ou importer le modèle officiel LFM2.5 de 731 Mo avec le contrôle SHA-256 existant. Les poids restent séparés de l’APK.
3. Activer **Utiliser le modèle local**, puis **Utiliser mes notes avec le modèle local**. Ce second choix est désactivé au départ et ne remplace pas le code utilisateur.
4. Déverrouiller les informations personnelles avec le code, puis poser une question liée à une note. Une réponse du modèle fondée sur les notes porte la mention **D’après tes notes enregistrées, non vérifiées**, ainsi que les titres des notes utilisées. Le verrouillage retire l’accès.

Le moteur central conserve les commandes, modifications, confirmations, lectures intégrales, contradictions et recherches. Il sélectionne au plus deux notes actuelles et non expirées ; les notes classées comme contenant des identifiants sensibles sont exclues. Au plus 840 caractères de phrases complètes sont transmis au modèle, sans envoyer toute la bibliothèque. Les balises spéciales de conversation et caractères de contrôle sont refusés.

Le modèle choisit une ou deux citations sous une grammaire JSON contrainte. Chaque citation doit correspondre exactement à une phrase fournie, avec une source autorisée ; le code refuse les phrases inventées, les sous-phrases qui changent une négation, les sources inconnues et les réponses mal formées. Il recontrôle ensuite le verrouillage, l’existence, la version et l’expiration des notes. Une modification pendant le calcul invalide la réponse. Ces contrôles garantissent l’appartenance des phrases aux extraits, pas la vérité des notes ni la pertinence de toute sélection.

Les sorties du modèle ne déclenchent aucune commande et ne sont pas mémorisées automatiquement. Si le modèle est absent, désactivé, en erreur, trop lent ou privé de ressources, le moteur central reste disponible. Les réponses générales continuent aussi de fonctionner dans une session déverrouillée sans transmettre de note. Le calcul reste local, borné à vingt secondes, quatre threads et un contexte de 1 024 tokens ; sortie maximale de 224 tokens pour les citations et de 96 pour les questions publiques. Le passage en arrière-plan annule le calcul.

## Sauvegarder et restaurer la mémoire

Dans **Sauvegarde de ma mémoire**, déverrouiller avec le code utilisateur, puis saisir et confirmer une phrase secrète de **12 à 128 caractères**. **Créer une sauvegarde chiffrée** permet de choisir le fichier `.nxb` avec le sélecteur Android. Seule l’archive chiffrée est remise au document choisi. Conserver sa phrase secrète séparément : l’application ne possède aucun moyen de la récupérer.

Pour restaurer : choisir l’archive, déverrouiller, saisir sa phrase, puis **Ouvrir et vérifier la sauvegarde**. L’écran indique le nombre de notes actives et d’anciennes versions. Cet aperçu ne change pas la mémoire. **Restaurer la mémoire** ouvre une confirmation explicite avant de remplacer les notes et leur historique. Une modification concurrente empêche le remplacement. Un point de retour, chiffré sous la clé de cet appareil, permet **Annuler la dernière restauration** tant qu’aucune modification ultérieure des notes ne l’a rendu incompatible.

L’archive contient toutes les notes natives, leurs anciennes versions, leur provenance et leurs métadonnées. Elle ne contient pas le code utilisateur, les clés Android, les modèles, les réglages, le cache d’apprentissage dérivé ni la conversation transitoire. Elle est portable vers un autre appareil : les notes restaurées sont rechiffrées sous la clé du destinataire. Le cache dérivé se reconstruit depuis les notes. Il s’agit d’un export des notes et de leur historique, **pas encore de l’export complet du cerveau et de l’identité d’un compagnon**.

Le format NXB v1 utilise AES-256-GCM, un sel aléatoire de 32 octets, un nonce de 12 octets et PBKDF2-HMAC-SHA-256 avec 600 000 itérations. L’en-tête est authentifié ; les paramètres non reconnus sont rejetés avant la dérivation. Les tailles, le décodage UTF-8, le schéma, les types, la capacité et le graphe de versions sont contrôlés avant toute écriture. La mémoire existante n’est pas remplacée en cas de mauvais secret ou d’archive invalide. Les champs secrets ne sont ni sauvegardés dans l’état Android ni proposés à l’autoremplissage ; quitter l’écran les efface et verrouille la session.

## Validation

| Contrôle | Résultat |
| --- | --- |
| Android debug / release | 377 tests par variante ; aucun échec, erreur ou test ignoré |
| Nouveaux parcours | Citations et droits, suppression/verrouillage pendant l’inférence, conservation des négations, archive chiffrée, restauration sur une autre clé, conflit, échec d’écriture, export/import/confirmation/retour dans l’interface |
| Audit mémoire précédent | 35 scénarios par variante dans une session autorisée |
| Android Lint | Aucune annotation dans les deux variantes |
| Base TypeScript disponible | 438 tests dans 51 suites ; types et ESLint réussis |
| JNI réel sur Linux | Cinq questions publiques et trois questions fondées sur des notes synthétiques ; citations exactes validées, y compris « Je ne bois pas de café. » |
| Cycle JNI | Modèle absent, délai maximal et annulation/libération concurrente vérifiés |
| Paquet | ZIP intègre, signature v2 sous le certificat permanent, alignement ZIP et bibliothèques 64 bits à 16 Kio |
| Conservation | Moteur JS et Vosk français identiques à 0.4.26 ; anciennes bibliothèques JNI/Vosk inchangées ; permissions identiques |

Les trois réponses sur notes prennent 1,6 à 1,9 seconde sur l’ordinateur Linux x86_64 avec AVX2. Ces durées ne mesurent pas le téléphone et ne constituent pas un score général de qualité. Les notes d’essai sont synthétiques. Aucun téléphone n’était connecté à ADB ; aucune mesure acoustique, d’audibilité ou d’autonomie n’a pu être réalisée. Le diagnostic vocal embarqué reste disponible pour le contrôle réel des deux transcriptions, de la pause pendant la réponse et de la reprise.

Le moteur JavaScript exact récupéré de la version 0.4.25 est conservé. Les tests TypeScript portent sur la source disponible 0.4.23 ; les sources typées et tests supplémentaires non publiés des versions 0.4.24/0.4.25 n’ont pas été retrouvés. Cette livraison ne prétend pas les avoir reconstruits. [Provenance du moteur](../../packages/companion-core/recovered-native/0.4.25/README.md).

## Étapes restantes

Le [suivi de finalisation](../implementation/ANDROID-FINALISATION-PAR-ETAPES-2026-11-13.md) distingue les étapes 1 et 2a livrées, le profil et l’export complet à compléter, puis les compagnons multiples, la voix en arrière-plan, la perception et le corps matériel. Les espaces de noms internes ne constituent pas encore des profils accessibles. L’écoute s’arrête toujours en arrière-plan. Le code utilisateur reste la protection disponible ; aucune empreinte vocale ne prétend identifier le propriétaire. Les mouvements du corps virtuel ne commandent pas un robot physique.

La prochaine validation utilisateur est de mettre à jour l’APK, vérifier une note d’essai après activation volontaire, créer et ouvrir une archive, puis lancer **Test vocal sur ce téléphone** en ligne et en mode avion après installation des modèles. Pour le robot, il faudra connaître le matériel et son protocole, puis le connecter afin de vérifier les mouvements et l’arrêt.

## Construction et identité du paquet

Les outils et sources natives restent ceux décrits dans [0.4.26](ANDROID-0.4.26-2026-11-13.md). Le moteur historique couvre quatre ABI ; le LLM fonctionne dans les processus Android 64 bits. Les bibliothèques Vosk 32 bits préexistantes restent à pages de 4 Kio. Les clés de signature sont externes au dépôt et GitHub Actions restent désactivées.

Commandes de validation Android :

```sh
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease --no-daemon --max-workers=2
```

- Paquet : `fr.nexuscompanion.android` ; version `0.4.27-native-alpha` ; code **33** ; Android **12+**.
- Taille : **67 983 825 octets**, sans les poids LFM.
- APK SHA-256 : `980df2a0c3842861c057dbf6ce896e29e555e0a4afbc284ad836061b9b075906`.
- Certificat SHA-256 : `01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1`.
- Arbre des sources avant documentation : `04d21184f2965df86177d0555021ece85f59cdca`.

Les preuves sont dans [validation 0.4.27](../validation/0.4.27/release-proof.json), avec les sorties JNI et les résultats des audits mémoire.
