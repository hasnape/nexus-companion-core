# Nexus Companion Android — 0.2.1 native alpha

Application Kotlin native, sans WebView, navigateur, serveur ni permission INTERNET. Android 12 minimum (API 31), cible Android 15 (API 35). Le produit Android reste une **alpha** : un APK signé ne prouve pas que le compagnon complet est terminé.

## Fonctions actuelles

- Conversation texte locale simple, avec réponses déterministes aux salutations.
- Écoute automatique à l'ouverture si l'autorisation micro a été accordée et si l'utilisateur ne l'a pas désactivée. Bouton d'arrêt visible, préférence conservée. Le micro et la synthèse sont arrêtés dès `onPause`.
- Reconnaissance exclusivement locale via le service Android installé. « Nexus, bonjour » ou « Nexus », puis la commande après « Je t'écoute ». Les silences ordinaires relancent l'écoute sans épuiser le budget de reprise des pannes.
- Micro suspendu pendant la synthèse. Événements périmés ignorés, reprise après la fin ou l'interruption, délai de récupération si le moteur TTS ne répond plus. Bouche ouverte seulement après le signal audio de la synthèse.
- Voix française installée hors ligne uniquement. Sans service vocal ou modèle français, le texte reste disponible avec un message explicite.
- Notes privées, sauvegarde atomique, recherche et provenance ; chiffrement AES-256-GCM avec clé Android Keystore liée au compagnon. Les anciens fichiers JSON v1 reconnus sont chiffrés lors de la lecture. Les données illisibles ne sont pas remplacées.
- Sauvegarde système désactivée, exclusions explicites de sauvegarde cloud et de transfert entre appareils. Aucun audio ou historique de transcription enregistré par l'application.

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

Le script valide la configuration avant de compiler, nettoie les sorties de construction, exécute les tests et Android Lint, vérifie la signature et produit les APK dans `apk-dist/0.2.1-native-alpha/` avec `SHA256SUMS.txt`. Sans variables de signature, il produit uniquement l'APK debug. Le fichier de sommes ne référence que les APK produits par cette exécution.

Le script optionnel `scripts/build-android-apk-vercel.sh` prépare les outils Linux et appelle le même script. Aucun workflow GitHub Actions n'est nécessaire. Ce bootstrap optionnel n'a pas été validé dans l'environnement Vercel dans cette livraison.

Pour installer une mise à jour, l'application doit garder `fr.nexuscompanion.android`, utiliser le même certificat et augmenter `versionCode` (3 pour cette version). L'installation demande la confirmation Android. Une application précédemment signée avec une clé debug différente ne peut pas être remplacée par cet APK signé. **Ne pas désinstaller une version contenant des notes à conserver** : l'export Android portable et la restauration de sa clé Keystore ne sont pas encore disponibles.

## Limites et prochaine validation

Le cerveau TypeScript, l'avatar web complet, un modèle ASR embarqué indépendant du téléphone, l'export/import Android chiffré et un vérificateur de mises à jour dans l'application restent à intégrer. Il n'y a ni LLM local embarqué, ni accès à Internet, ni mise à jour silencieuse.

Voir le [rapport de validation 0.2.1](../../docs/releases/ANDROID-0.2.1-2026-11-06.md). Les tests JVM de la session vocale utilisent des services simulés ; ils ne mesurent pas la qualité du microphone, la reconnaissance française, le Bluetooth ou l'autonomie sur un vrai appareil.
