# Nexus Companion Android — 0.4.1 native alpha

Application Kotlin native, sans WebView, navigateur, serveur ni permission INTERNET. Android 12 minimum (API 31), cible Android 15 (API 35). Le produit Android reste une **alpha** : un APK signé ne prouve pas que le compagnon complet est terminé.

## Fonctions actuelles

- Conversation texte locale utilisant les règles partagées Nexus (salutations, soutien simple, mémoire explicite et limites de sécurité), dans un interpréteur Java embarqué. Aucun WebView ni moteur cloud.
- Écoute automatique à l'ouverture si l'autorisation micro a été accordée et si l'utilisateur ne l'a pas désactivée. Bouton d'arrêt visible, préférence conservée. Le micro et la synthèse sont arrêtés dès `onPause`.
- Reconnaissance exclusivement locale via le service Android installé. « Nexus, bonjour » ou « Nexus », puis la commande après « Je t'écoute ». Les silences ordinaires relancent l'écoute sans épuiser le budget de reprise des pannes.
- « Parler maintenant » lance une dictée directe sans mot d'activation. Le bouton reste désactivé si le micro est arrêté ou pendant un calcul. La dictée peut interrompre une réponse vocale.
- Aperçu « En cours » puis « Entendu », conservé uniquement en mémoire, effacé après 15 secondes sans transcription et à l'arrêt du micro, en arrière-plan ou dans un dialogue privé. Seul le résultat final peut déclencher une commande ; le texte saisi au clavier reste indépendant.
- Reconnaissance française avec résultats partiels, cinq hypothèses au maximum et indications publiques « Nexus » / « Nexus Companion » sur Android 13+. Préfixes « Hey/Hé/OK Nexus », variantes françaises et « Nex us » reconnus uniquement en début de phrase, sur un mot complet. Une hypothèse secondaire ne récupère le mot d'activation que si son score est présent, au moins 0,65 et à 0,15 au maximum du meilleur score ; en dictée, la première hypothèse est conservée.
- Le délai de commande de 12 secondes limite l'attente avant de parler, sans couper une phrase commencée. Récupération bornée si le service ne devient pas prêt sous 8 secondes, reste silencieux sans événement pendant 45 secondes, dépasse 60 secondes de capture ou ne rend pas de résultat sous 8 secondes après la fin de parole. Le statut distingue préparation, écoute, analyse, reprise et pause pendant la réponse.
- Micro suspendu pendant le calcul local, la synthèse et les dialogues de notes. Événements périmés ignorés, reprise après la fin ou l'interruption, délai de récupération si le moteur TTS ne répond plus. Bouche ouverte seulement après le signal audio de la synthèse.
- Voix française installée hors ligne uniquement. Sans service vocal ou modèle français, le texte reste disponible avec un message explicite.
- Propositions à retenir avec confirmation explicite ; consultation et suppression dans « Mes notes » ; rappel conversationnel des notes non sensibles, avec extraits courts et statut non vérifié. Notes privées, sauvegarde atomique, recherche sans accents et provenance ; chiffrement AES-256-GCM avec clé Android Keystore liée au compagnon. Les anciens fichiers JSON v1 reconnus sont chiffrés lors de la lecture. Les données illisibles ne sont pas remplacées.
- Sauvegarde système désactivée, exclusions explicites de sauvegarde cloud et de transfert entre appareils. Aucun audio ou historique de transcription enregistré par l'application.
- Écran natif « Mises à jour » : choix explicite d'un APK local, contrôle du package, de la version, des signataires courants et du niveau Android, puis transmission d'une copie privée en lecture seule à l'installateur Android. Aucun téléchargement automatique ni permission de stockage étendue.
- « Voix et diagnostic » : vérification explicite du français hors ligne sur API 33+, demande de modèle uniquement après action de l'utilisateur, accès aux réglages vocaux Android et diagnostic limité aux versions et signatures publiques. Le panneau n'ouvre pas l'audio et ne collecte pas de transcription.
- Témoin indicatif du signal micro, borné et remis à zéro pendant les pauses. Les erreurs Android « occupé » et « trop de demandes » ont des reprises espacées, avec 15 secondes de repos pour une limitation de débit.

La reconnaissance Android fonctionne par sessions finies et reprises. Elle ne constitue pas un moteur dédié de détection de mot-clé, et sa consommation et sa fiabilité sur téléphone restent à mesurer.

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

Le script valide la configuration avant de compiler, nettoie les sorties de construction, exécute les tests et Android Lint, vérifie la signature et produit les APK dans `apk-dist/0.4.1-native-alpha/` avec `SHA256SUMS.txt`. Sans variables de signature, il produit uniquement l'APK debug. Le fichier de sommes ne référence que les APK produits par cette exécution.

Le script optionnel `scripts/build-android-apk-vercel.sh` prépare les outils Linux et appelle le même script. Aucun workflow GitHub Actions n'est nécessaire. Ce bootstrap optionnel n'a pas été validé dans l'environnement Vercel dans cette livraison.

Pour installer une mise à jour, l'application doit garder `fr.nexuscompanion.android`, utiliser le même certificat et augmenter `versionCode` (7 pour cette version). L'installation demande la confirmation Android. Une application précédemment signée avec une clé debug différente ne peut pas être remplacée par cet APK signé. **Ne pas désinstaller une version contenant des notes à conserver** : l'export Android portable et la restauration de sa clé Keystore ne sont pas encore disponibles.

### Canal Test installable à côté

`NEXUS_ANDROID_PREVIEW=true bash scripts/build-android-apk.sh` compile la même base avec le nom **Nexus Companion Test**, l'identifiant `fr.nexuscompanion.android.preview` et le suffixe de version `-preview`. Les APK sont placés dans `apk-dist/0.4.1-native-alpha-preview/`. Dans Android Studio, utiliser la propriété Gradle `-PnexusPreview=true`. Le mode par défaut garde l'identifiant de diffusion ; une valeur de propriété invalide est refusée.

Le canal Test a ses propres notes, préférences, permissions et clés Android Keystore. Il ne remplace pas l'ancienne application et ne transfère pas ses données. Un bandeau le précise dans l'accueil. Chaque canal accepte uniquement les mises à jour de son propre identifiant. La signature permanente est conservée pour les deux variantes, ce qui permet au diagnostic Test de comparer les certificats avec l'application d'origine sans lire ses notes.

En cas de message Android « Application non installée », ce seul écran ne permet pas de conclure à un problème de signature. Le panneau « Voix et diagnostic » de Test affiche la version de l'application d'origine lorsqu'elle est visible et une comparaison des certificats courants. « Copier le diagnostic » copie uniquement versions, identifiants de ces deux applications, niveau Android et empreintes publiques ; aucune note, voix, transcription, identifiant matériel ou journal système.

### Français hors ligne

Ouvrir **Voix et diagnostic → Vérifier le français hors ligne**. Le service doit confirmer un modèle installé pour afficher « prêt ». Les résultats absent, téléchargeable, en attente, non pris en charge ou non vérifiable restent distincts. Sur Android 12/API 31–32, l'API de vérification détaillée n'est pas appelée ; le texte et l'écoute existante restent disponibles selon les services installés.

La demande de modèle nécessite un tap explicite sur le bouton et peut utiliser le réseau via le service Android. Nexus n'ajoute pas de permission INTERNET. Une demande transmise ne prouve pas que le modèle est installé : vérifier de nouveau. Ce panneau ne remplace pas le moteur acoustique Android et aucune précision nouvelle de transcription n'est revendiquée sans essai vocal réel.

Après l'installation de 0.4.0, les prochains APK peuvent être sélectionnés dans **Mises à jour → Choisir un APK**. L'écran affiche la version vérifiée ; **Installer cette version** ouvre les réglages d'autorisation si nécessaire. Au retour, un nouveau tap est obligatoire : aucune installation automatique. Android vérifie ensuite le paquet et demande sa confirmation. La validation locale ne remplace pas la vérification cryptographique finale effectuée par Android.

La permission spéciale `REQUEST_INSTALL_PACKAGES` permet ce passage à l'installateur ; elle ne donne pas le droit de modifier les notes ni de procéder à une installation silencieuse. `INTERNET`, les autorisations générales de stockage et la désinstallation ne sont pas utilisés par ce parcours. L'APK doit être un paquet autonome, pas une archive XAPK ou un ensemble de splits. La rotation de clé est refusée par la comparaison stricte des signataires ; elle nécessiterait une migration distincte.

Le cache privé ne contient que des copies APK, limitées à 64 Mio par fichier et 128 Mio au total. Leur SHA-256 est recalculé après une recréation de l'écran et avant la transmission. Les fichiers non transmis sont supprimés à la fermeture ; ceux transmis à Android restent lisibles pour l'installation, puis sont supprimés à l'import suivant une fois âgés de 24 heures. Le fournisseur non exporté expose uniquement `cache/updates/` et interdit l'écriture, sans accès aux notes.

## Limites et prochaine validation

Le premier pont vers les règles TypeScript est intégré ; le cerveau cognitif complet persistant, l'identité native, l'avatar web complet, un modèle ASR embarqué indépendant du téléphone, l'export/import Android chiffré et la recherche en ligne de nouvelles versions restent à intégrer. Il n'y a ni LLM local embarqué, ni accès à Internet, ni mise à jour silencieuse.

Voir le [rapport de validation 0.4.1](../../docs/releases/ANDROID-0.4.1-2026-11-06.md). Les tests exécutent le bundle partagé dans Rhino, l'interface et le câblage vocal dans Robolectric, et la persistance avec AES-GCM de JVM. Les services vocaux, métadonnées de paquets et écrans système sont simulés ; les fichiers et octets transmis au fournisseur sont réels. Ils ne mesurent pas la qualité du microphone, la reconnaissance française, le Bluetooth ou l'autonomie. Aucun nouveau modèle acoustique n'est livré ; les indications de vocabulaire et résultats partiels dépendent du service Android installé.

## Développement du moteur partagé

Les assets JavaScript et leur manifeste SHA-256 sont inclus. Gradle vérifie qu'ils correspondent aux sources du dépôt complet avant de compiler. Après une modification du moteur TypeScript ou de son compilateur, exécuter depuis la racine :

```bash
npm ci
npm run build:native-brain
npm run check:native-brain
```

Voir la [décision d'architecture](../../docs/architecture/ADR-0002-android-shared-brain.md). Une conversation ne sauvegarde pas automatiquement des informations ; le bouton « Retenir cette information » propose une relecture et une confirmation. Dans « Mes notes », saisir d'abord un sujet dans le champ texte pour rechercher ; sans sujet, les cinquante notes les plus récentes sont affichées.
