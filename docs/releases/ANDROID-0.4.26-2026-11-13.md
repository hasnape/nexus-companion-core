# Nexus Companion Android 0.4.26 — modèle local et diagnostic vocal

Le moteur local peut désormais répondre aux questions générales que le moteur central ne sait pas traiter. Le modèle est installé séparément ; les notes, confirmations et commandes conservent leur routage déterministe. Un écran permet de vérifier le cycle vocal sur le téléphone et d’exporter les observations.

## Installation sur le téléphone

1. Installer `nexus-companion-v0.4.26-native-alpha-release.apk` au-dessus de la version principale, avec **Mettre à jour**, sans désinstallation.
2. Dans **Modèle de langage local**, télécharger le modèle officiel de 731 Mo ou importer le fichier GGUF officiel. Attendre la fin du contrôle SHA-256, puis activer **Utiliser le modèle local**.
3. Utiliser le Wi-Fi pour le premier téléchargement. Le modèle peut ensuite fonctionner sans Internet. Le calcul nécessite Android 64 bits, au moins 1,5 Gio de RAM disponible, une chauffe faible et l’absence de mode économie. Brancher le téléphone si sa batterie est à 15 % ou moins.
4. Dans **Test vocal sur ce téléphone**, lancer le test et prononcer les deux phrases affichées. Vérifier à l’oreille la réponse, puis exporter le rapport JSON si nécessaire. Le mode avion permet de contrôler le fonctionnement hors ligne après installation.

Les notes primaires chiffrées, l’historique, le code utilisateur, le visage et le modèle de reconnaissance française sont conservés. Aucune permission n’est ajoutée.

## Fonctionnement du modèle

- Modèle officiel : [Liquid AI LFM2.5-1.2B-Instruct-GGUF](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF), révision `8ed288026e23958ad9dfa92d53ed773a8eee7125`.
- Fichier : `LFM2.5-1.2B-Instruct-Q4_K_M.gguf`, **730 895 168 octets**, SHA-256 `b1b3de114215d9507409a662a501a631095a479a419584e8a2ded6304b19b4f5`.
- Moteur : llama.cpp `5193ef1d4f7c95b006ab3b9f857ed27be9a63235`, source téléchargée avec contrôle d’empreinte. Calcul CPU, quatre threads au maximum, contexte 1 024 tokens, sortie limitée à 96 tokens et calcul limité à vingt secondes.
- Le modèle est désactivé par défaut. L’import vérifie le fichier complet avant remplacement atomique ; un import corrompu ou annulé conserve le fichier précédent. Les poids sont stockés dans l’espace privé sans sauvegarde Android et peuvent être supprimés dans les réglages.
- Seule une proposition de recherche générale validée par le moteur central peut devenir une requête LLM. Aucune note, aucun code utilisateur, aucun résultat Chrome et aucun outil ne sont transmis au modèle. Les commandes, confirmations, demandes personnelles et réponses déjà fondées sur une source gardent priorité.
- Les réponses LLM portent la mention **Réponse du modèle local · à vérifier**. Elles peuvent être inexactes. Elles ne sont ni exécutées comme commandes ni apprises automatiquement.
- Modèle absent, ressources insuffisantes, sortie rejetée, erreur ou délai dépassé : retour au comportement existant. Le passage en arrière-plan annule l’inférence et invalide son affichage tardif. Le micro reste suspendu pendant la réponse ; le délai du contrôleur vocal est porté à 45 secondes.
- Les registres JNI utilisent des identifiants et des références partagées : annuler ou libérer un identifiant pendant une génération ne libère pas sa mémoire prématurément. Les licences complètes sont incluses dans les notices.

## Reprise après réinitialisation

Le moteur JavaScript de l’APK 0.4.25 est conservé octet par octet. Le Kotlin de recherche et de conversation a été reconstitué, puis validé. Les sources typées et les tests supplémentaires non publiés des versions 0.4.24/0.4.25 n’ont pas été retrouvés. La provenance et la différence entre source récupérée et source typée sont documentées dans [le snapshot](../../packages/companion-core/recovered-native/0.4.25/README.md).

La recherche conversationnelle conserve le classement lexical, les passages de 900 caractères avec recouvrement, BM25 et la fusion des rangs. Les suppressions et expirations invalident les résultats. Les titres des notes utilisées sont affichés suivant les droits actuels. La confiance du mot le moins sûr intervient dans une confirmation ; une alternative de transcription ne peut pas remplacer une commande par une autre.

## Validation

| Contrôle | Résultat |
| --- | --- |
| Android debug et release | 358 tests chacun ; aucun échec, erreur ou test ignoré |
| Audit mémoire précédent | 35 scénarios par variante, dans une session explicitement autorisée |
| Android Lint | Aucune annotation dans chaque variante |
| TypeScript/Web disponibles | 438 tests ; types, ESLint et builds des workspaces réussis |
| Source native | Empreinte exacte du moteur JavaScript 0.4.25 et du modèle Vosk préservée |
| JNI et inférence réels sur Linux | Cinq questions françaises ; retours cohérents ; modèle absent, délai maximal et annulation/libération concurrente vérifiés |
| APK | Signature v2, certificat permanent, ZIP intègre, alignement ZIP et bibliothèques 64 bits compatibles 16 Kio |
| Permissions | Identiques à 0.4.25 |

Les cinq sorties et les durées de 1,6 à 6,9 secondes sont enregistrées dans les preuves. Elles mesurent l’ordinateur Linux avec AVX2, **pas le téléphone** ; elles ne constituent pas un score général de précision ni une mesure de vitesse Android. Les bibliothèques préexistantes conservent leurs sections de code/données et identifiants de construction ; seuls des symboles de diagnostic JNA sont retirés par le nouvel outil de strip. Les bibliothèques 32 bits préexistantes restent prévues pour des appareils à pages de 4 Kio.

Aucun téléphone n’était connecté (`adb devices` vide). Aucun son réel n’a été capturé ou écouté dans cet environnement. Le diagnostic embarqué fournit deux transcriptions et les événements de sortie, de pause et de reprise ; une personne doit encore apprécier l’audibilité et la reconnaissance acoustique sur son appareil. Aucun audio n’est enregistré. Les nouvelles inférences ne réentraînent pas les poids du modèle.

## Construction et preuve du paquet

Préparer les sources avec `python3 scripts/prepare-local-llm.py`, puis `npm ci` et `npm run build:native-brain`. Installer JDK 17, SDK 35, Build Tools 35.0.0, NDK 27.2.12479018 et CMake 3.22.1. Le script habituel `scripts/build-android-apk.sh` prépare aussi la source native avant Gradle. Les poids LFM ne sont pas nécessaires à la compilation. Les builds C++ sont limités à deux compilations simultanées.

L’APK est universel pour le moteur historique ; l’inférence LFM est disponible dans les processus `arm64-v8a` et `x86_64`. Les variantes 32 bits conservent le moteur historique. Les clés de signature restent externes au dépôt. Les actions GitHub ne sont pas réactivées ; la validation est locale.

- Paquet : `fr.nexuscompanion.android` ; version `0.4.26-native-alpha` ; code **32** ; Android **12+**.
- Taille : **67 922 237 octets** ; poids LFM séparés.
- APK SHA-256 : `a973355fed64dbceff854f3d46a98a77ea99c131e4866e35c817ce21b4ef69bd`.
- Certificat SHA-256 : `01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1`.
- Arbre des sources avant documentation : `0d09b07ff23ac79301797a09d836ecfb55e6127d`.

Les preuves JSON se trouvent dans [validation 0.4.26](../validation/0.4.26/release-proof.json). Le harnais reproductible des mêmes points d’entrée JNI est `scripts/native-llm-test/NativeLocalLlm.java`.
