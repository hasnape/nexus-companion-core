# Nexus Companion Android — 0.4.5 native alpha

Application Kotlin native, sans WebView, navigateur, serveur ni permission INTERNET. Android 12 minimum (API 31), cible Android 15 (API 35). Le produit Android reste une **alpha** : un APK signé ne prouve pas que le compagnon complet est terminé.

## Fonctions actuelles

- Conversation texte locale utilisant les règles partagées Nexus (salutations, soutien simple, mémoire explicite et limites de sécurité), dans un interpréteur Java embarqué. Aucun WebView ni moteur cloud.
- Écoute automatique à l'ouverture si l'autorisation micro a été accordée et si l'utilisateur ne l'a pas désactivée. Bouton d'arrêt visible, préférence conservée. Le micro et la synthèse sont arrêtés dès `onPause`.
- Reconnaissance française sur le téléphone via le modèle Vosk inclus dans l’APK. Dire « Nexus, bonjour » ou « Nexus », puis la commande après « Je t'écoute ». Les silences ordinaires relancent l'écoute sans épuiser le budget de reprise des pannes.
- « Parler maintenant » lance une dictée directe sans mot d'activation. Le bouton reste désactivé si le micro est arrêté ou pendant un calcul. La dictée peut interrompre une réponse vocale.
- Aperçu « En cours » puis « Entendu », conservé uniquement en mémoire, effacé après 15 secondes sans transcription et à l'arrêt du micro, en arrière-plan ou dans un dialogue privé. Seul le résultat final peut déclencher une commande ; le texte saisi au clavier reste indépendant.
- Le mot d’activation et ses variantes restent reconnus uniquement en début de phrase, sur un mot complet. La dictée directe utilise le résultat du décodeur sans exiger de mot d’activation.
- Le délai de commande de 12 secondes commence après les premiers échantillons du micro. Il limite l’attente avant de parler et se désarme quand un résultat partiel arrive. Le chargement initial dispose de 30 secondes et les captures silencieuses se renouvellent après 20 secondes. L’interface distingue la préparation, l’écoute et la pause pendant la réponse.
- Chaque capture libère le micro et le décodeur avant de transmettre le résultat final. La reprise réutilise le modèle chargé et ignore les événements de l’ancienne capture.
- Micro suspendu pendant le calcul local, la synthèse et les dialogues de notes. La préparation du micro redémarre 200 ms après la fin confirmée de la lecture. Un délai de 700 ms s’applique après une erreur ou une expiration de la lecture. La bouche s’ouvre au signal de début de la synthèse.
- Les réponses utilisent une voix française installée sur l’appareil. Après un envoi au clavier, la lecture reste disponible en mode vocal quand la dictée Android est indisponible. Nexus attend au plus cinq secondes que le moteur vocal soit prêt avant de laisser la réponse affichée et de reprendre l’écoute.
- Propositions à retenir avec confirmation explicite ; consultation et suppression dans « Mes notes » ; rappel conversationnel des notes non sensibles, avec extraits courts et statut non vérifié. Notes privées, sauvegarde atomique, recherche sans accents et provenance ; chiffrement AES-256-GCM avec clé Android Keystore liée au compagnon. Les anciens fichiers JSON v1 reconnus sont chiffrés lors de la lecture. Les données illisibles ne sont pas remplacées.
- Sauvegarde système désactivée, exclusions explicites de sauvegarde cloud et de transfert entre appareils. Aucun audio ou historique de transcription enregistré par l'application.
- Écran natif « Mises à jour » : choix explicite d'un APK local, contrôle du package, de la version, des signataires courants et du niveau Android, puis transmission d'une copie privée en lecture seule à l'installateur Android. Aucun téléchargement automatique ni permission de stockage étendue.
- « Voix et diagnostic » indique la présence du modèle français intégré. « Tester la voix locale » lit une phrase sur demande. Le panneau garde le microphone fermé et le diagnostic copié contient seulement les versions, les identifiants d’application, le niveau Android et les signatures publiques.
- Le témoin du signal micro mesure le niveau des échantillons reçus et revient à zéro pendant les pauses.

La reconnaissance locale fonctionne par sessions finies et reprises. Le déclenchement attend une transcription finale contenant « Nexus ». La précision et l’autonomie restent à mesurer sur téléphone.

## Compilation sur Windows / Android Studio

1. Ouvrir le dossier `apps/companion-android` dans Android Studio.
2. Installer Android SDK Platform 35 et Build-Tools 35.0.0. Choisir un **JDK 17 complet** comme Gradle JDK.
3. Laisser Gradle se synchroniser. Le wrapper Windows et Linux/macOS est inclus ; Gradle 8.10.2 et son SHA-256 sont fixés.
4. Pour les tests et un APK de développement, dans le terminal de ce dossier :

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

L'APK debug est créé dans `app/build/outputs/apk/debug/app-debug.apk`. Il utilise une clé de développement et ne remplace pas une application déjà signée avec la clé de diffusion.

## APK signé et mises à jour

Conserver la clé permanente existante ; ne pas en recréer une pour chaque version. Ne jamais déposer de keystore ou mot de passe dans Git ou dans une archive publique.

La signature Gradle utilise ces quatre variables :

| Variable | Contenu |
| --- | --- |
| `NEXUS_ANDROID_KEYSTORE_PATH` | Chemin absolu de la clé `.jks` existante |
| `NEXUS_ANDROID_STORE_PASSWORD` | Mot de passe du keystore |
| `NEXUS_ANDROID_KEY_ALIAS` | Alias existant |
| `NEXUS_ANDROID_KEY_PASSWORD` | Mot de passe de la clé |

Une configuration partielle est refusée. Une compilation `assembleRelease` sans configuration de signature produit seulement un APK non signé ; ne pas le distribuer. Android Studio permet aussi de sélectionner la clé existante avec **Build → Generate Signed App Bundle / APK → APK**.

Sous Linux, depuis la racine, avec JDK 17 complet, Android SDK et les variables de signature :

```bash
bash scripts/build-android-apk.sh
```

Le script valide la configuration avant de compiler, nettoie les sorties de construction, exécute les tests et Android Lint, vérifie la signature et produit les APK dans `apk-dist/0.4.5-native-alpha/` avec `SHA256SUMS.txt`. Sans variables de signature, il produit uniquement l'APK debug. Le fichier de sommes référence les APK produits par cette exécution.

Le script optionnel `scripts/build-android-apk-vercel.sh` prépare les outils Linux et appelle le même script. Aucun workflow GitHub Actions n'est nécessaire. Ce bootstrap optionnel n'a pas été validé dans l'environnement Vercel dans cette livraison.

Pour installer une mise à jour, l'application doit garder `fr.nexuscompanion.android`, utiliser le même certificat et augmenter `versionCode` (11 pour cette version). L'installation demande la confirmation Android. Installe l’APK signé par-dessus l’application principale existante pour conserver les notes et la clé Keystore associée.

Distribuer uniquement `nexus-companion-v0.4.5-native-alpha-release.apk`, signé avec la clé permanente du paquet principal.

### Français hors ligne

Gradle récupère `vosk-model-small-fr-0.22`, vérifie sa taille et son SHA-256, puis l’inclut dans l’APK. Nexus le prépare dans l’espace privé lors du premier démarrage du micro. Cette préparation est locale et peut durer quelques secondes. La lecture des réponses utilise toujours la voix française installée sur Android.

### Installation depuis l’application

Après l'installation de 0.4.0, les prochains APK peuvent être sélectionnés dans **Mises à jour → Choisir un APK**. L'écran affiche la version vérifiée ; **Installer cette version** ouvre les réglages d'autorisation si nécessaire. Au retour, un nouveau tap est obligatoire : aucune installation automatique. Android vérifie ensuite le paquet et demande sa confirmation. La validation locale ne remplace pas la vérification cryptographique finale effectuée par Android.

La permission spéciale `REQUEST_INSTALL_PACKAGES` permet ce passage à l'installateur ; elle ne donne pas le droit de modifier les notes ni de procéder à une installation silencieuse. `INTERNET`, les autorisations générales de stockage et la désinstallation ne sont pas utilisés par ce parcours. L'APK doit être un paquet autonome, pas une archive XAPK ou un ensemble de splits. La rotation de clé est refusée par la comparaison stricte des signataires ; elle nécessiterait une migration distincte.

Le cache privé ne contient que des copies APK, limitées à 64 Mio par fichier et 128 Mio au total. Leur SHA-256 est recalculé après une recréation de l'écran et avant la transmission. Les fichiers non transmis sont supprimés à la fermeture ; ceux transmis à Android restent lisibles pour l'installation, puis sont supprimés à l'import suivant une fois âgés de 24 heures. Le fournisseur non exporté expose uniquement `cache/updates/` et interdit l'écriture, sans accès aux notes.

## Limites et prochaine validation

Le premier pont vers les règles TypeScript est intégré. Le cerveau cognitif complet persistant, l'identité native, l'avatar web complet, l'export/import Android chiffré et la recherche en ligne de nouvelles versions restent à intégrer.

Voir les rapports de validation [0.4.5](../../docs/releases/ANDROID-0.4.5-2026-11-07.md) et [0.4.4](../../docs/releases/ANDROID-0.4.4-2026-11-07.md). Les tests exécutent le bundle partagé dans Rhino, l’interface et le câblage vocal dans Robolectric, et la persistance avec AES-GCM de JVM. Les tests Android simulent la source micro. Le modèle français est également vérifié séparément sur des enregistrements réels. Le microphone, le Bluetooth et l’autonomie restent à vérifier sur téléphone.

## Développement du moteur partagé

Les assets JavaScript et leur manifeste SHA-256 sont inclus. Gradle vérifie qu'ils correspondent aux sources du dépôt complet avant de compiler. Après une modification du moteur TypeScript ou de son compilateur, exécuter depuis la racine :

```bash
npm ci
npm run build:native-brain
npm run check:native-brain
```

Voir la [décision d'architecture](../../docs/architecture/ADR-0002-android-shared-brain.md). Une conversation ne sauvegarde pas automatiquement des informations ; le bouton « Retenir cette information » propose une relecture et une confirmation. Dans « Mes notes », saisir d'abord un sujet dans le champ texte pour rechercher ; sans sujet, les cinquante notes les plus récentes sont affichées.
