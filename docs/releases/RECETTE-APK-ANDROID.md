# Recette de livraison APK — Nexus Companion Android

**Statut :** procédure de référence reproductible, basée sur les compilations et validations réellement effectuées. **La signature et l'installation de la v0.4.29 restent à valider** : ne pas les présenter comme terminées.

**Projet :** `apps/companion-android` ; **application de production :** `fr.nexuscompanion.android`. Branche de travail actuelle : `fix/android-llm-memory-readiness-20261114`. La v0.4.28 (`versionCode=34`) est installée sur le téléphone ; la v0.4.29 (`versionCode=35`) est compilée, mais pas encore distribuée sous forme signée.

## 0. Ingrédients — à réunir avant de commencer

- Dépôt GitHub Nexus Companion à une révision **immuable** (enregistrer le SHA du commit).
- JDK 17 ; Android SDK Platform 35, Build Tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1 ; Gradle Wrapper du dépôt.
- Accès réseau uniquement pour les dépendances vérifiées ; `scripts/prepare-local-llm.py` télécharge une révision épinglée de llama.cpp et contrôle son SHA-256.
- **Clé de signature de production existante**, conservée **hors dépôt** : `nexus-companion-production.jks`, et fichier de paramètres privé. Ne jamais les publier, envoyer dans un ticket, un log, un chat, un blob public, un commit ou un artefact de CI.
- Les quatre variables attendues par Gradle : `NEXUS_ANDROID_KEYSTORE_PATH`, `NEXUS_ANDROID_STORE_PASSWORD`, `NEXUS_ANDROID_KEY_ALIAS`, `NEXUS_ANDROID_KEY_PASSWORD`. Charger les secrets via un coffre ou un fichier privé aux permissions restrictives, jamais en clair dans les commandes enregistrées.
- Un environnement disposant d'assez de mémoire et de temps : la compilation C++ `nexus_llm` pour les quatre ABI peut dépasser dix minutes.

## 1. Préparer la version

1. Vérifier `git status` (arbre propre) et noter `git rev-parse HEAD`.
2. Dans `apps/companion-android/app/build.gradle.kts`, incrémenter **strictement** `versionCode` ; ajuster `versionName`.
3. **Ne pas** activer `-PnexusPreview=true` pour la production : cela change l'ID d'application en `fr.nexuscompanion.android.preview` et ne mettrait pas à jour Alif.
4. Conserver l'identifiant de production, les données privées, les paramètres et la clé de signature.
5. Exécuter `python3 scripts/prepare-local-llm.py` depuis la racine du dépôt.

## 2. Tester puis compiler

Depuis `apps/companion-android`, avec `JAVA_HOME`, `ANDROID_HOME` et `ANDROID_SDK_ROOT` correctement définis :

```bash
./gradlew :app:testDebugUnitTest --no-daemon --console=plain --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8'
./gradlew :app:assembleRelease --no-daemon --console=plain --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8'
```

**Résultats réellement obtenus pour la v0.4.29 :** 393 tests réussis / 0 échec lors de la validation précédente ; `assembleRelease` **BUILD SUCCESSFUL in 12m 18s**, sortie `app/build/outputs/apk/release/app-release-unsigned.apk`, **68 007 585 octets**. Il s'agit d'une **APK non signée**, inutilisable comme mise à jour de production.

Si les quatre variables de signature sont fournies **avant Gradle**, `app/build.gradle.kts` configure automatiquement `signingConfigs.nexusRelease`. Préférer ce mode pour un nouveau build de release **dans un environnement de confiance**, plutôt que copier la clé dans un sandbox distant.

## 3. Signer sans exposer la clé

### Méthode A — recommandée : build signé local

1. Placer le keystore dans un coffre ou un dossier privé **sur le poste de signature**, hors dépôt.
2. Charger les quatre variables de signature depuis le coffre dans le processus de build, **sans affichage dans les logs**.
3. Lancer `./gradlew :app:assembleRelease` ; Gradle utilise la configuration `nexusRelease`.
4. Repérer l'APK de sortie **signée** (ne pas confondre avec `app-release-unsigned.apk`).

### Méthode B — si APK déjà compilée sans clé

Transférer **uniquement l'APK non signée** vers le poste de signature via un canal contrôlé, en vérifiant son SHA-256 et sa provenance. Ne jamais transférer la clé dans un stockage public. Sur le poste de signature, avec Android Build Tools 35 :

```bash
zipalign -f -p 4 app-release-unsigned.apk nexus-aligned.apk
# Fournir les secrets à apksigner via son mécanisme sécurisé d'entrée,
# sans mot de passe dans l'historique shell ou les logs.
apksigner sign --ks /chemin/prive/nexus-companion-production.jks \
  --ks-key-alias nexus-companion-production \
  --out nexus-companion-v0.4.29-release.apk nexus-aligned.apk
apksigner verify --verbose --print-certs nexus-companion-v0.4.29-release.apk
```

`apksigner` peut demander les mots de passe de manière interactive. **Ne jamais les intégrer dans un script versionné.**

## 4. Contrôles obligatoires avant diffusion

- `apksigner verify --verbose --print-certs` : signature valide ; **SHA-256 du certificat identique à celui de l'APK v0.4.28 déjà installée**. Comparer les certificats, **pas** les SHA-256 des fichiers APK (qui diffèrent normalement).
- `aapt dump badging` ou `apkanalyzer manifest application-id/version-code` : `fr.nexuscompanion.android`, `versionCode=35`, `versionName=0.4.29-native-alpha`.
- Vérifier la taille de l'APK et les éventuelles limites de téléchargement de l'outil de mise à jour ; **68 007 585 octets dépassent 64 MiB**. Ne pas supprimer des ABI sans analyse de compatibilité des appareils pris en charge.
- Calculer le SHA-256 de l'APK signée ; le conserver avec le commit source et le rapport de tests.
- Tester **l'installation par-dessus** la version existante, **sans désinstallation ni effacement des données**.
- Après mise à jour : vérifier les notes privées, les notes partagées/révoquées, l'historique, la voix, l'identité Alif, les réponses françaises, le mode avion, l'arrêt vocal et les autorisations.
- Ne diffuser la version qu'après ces contrôles. Si échec, conserver l'APK antérieure et diagnostiquer sans effacer les données utilisateur.

## 5. Incidents rencontrés et remèdes

| Symptôme | Cause ou constat | Réaction durable |
| --- | --- | --- |
| `assembleRelease` dépasse 300 s / code 137 | Compilation JNI C++ de quatre ABI, temps/ressources insuffisants | Augmenter la durée du job (>= 15 min), surveiller RAM/CPU, conserver les caches vérifiés |
| Tests régressifs sur « Bonjour » | Nouveau routage de dialogue interceptait la salutation existante | Préserver le routage vocal existant ; exécuter **toute** la suite de tests |
| « Comment t'appelles-tu ? » non reconnu | Normalisation des apostrophes/tirets incomplète | Normaliser les variantes et tester les formes orales |
| APK `unsigned` | Variables de signature absentes pendant le build | Signer sur poste de confiance ; vérifier certificat |
| Sandbox de compilation expiré | Session éphémère arrêtée avant récupération de l'artefact | Prévoir l'export contrôlé et vérifier sa réussite **avant** l'arrêt ; conserver hash/manifest |
| Stockage Blob public | Adapté éventuellement à une APK non signée, **jamais** à une clé privée | Ne jamais publier les secrets ; éviter la distribution d'APK non signée |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Certificat ou ID d'application différent | **Ne pas désinstaller** ; vérifier certificat de production et `applicationId` |

## 6. Fiche de traçabilité à compléter pour chaque version

- Version, versionCode, commit exact, date, auteur du build.
- Environnement : JDK, SDK, NDK, CMake, Gradle, ABI, commande.
- Résultats : nombre de tests, erreurs, `BUILD SUCCESSFUL`.
- Artefact : nom, taille, SHA-256, signature vérifiée et empreinte de certificat.
- Compatibilité : ID d'application, certificat antérieur, test de mise à jour **sans perte de données**.
- Essais téléphone : voix, mémoire, mode avion, autorisations, démarrage.
- Statut final : **non signée / signée / vérifiée / testée sur téléphone / publiée** (ne jamais confondre ces étapes).

**Règle définitive :** aucune « APK finale » annoncée tant que l'artefact signé n'existe pas et que sa signature, son identité Android et sa mise à jour préservant les données n'ont pas été vérifiées.


## Annexe A — inventaire exact des fichiers de signature (privés)

Les deux fichiers suivants ont été **vérifiés présents dans l'espace privé de travail** le 15/11/2026 :

| Fichier | Usage | Taille observée | Confidentialité |
| --- | --- | ---: | --- |
| `nexus-companion-production.jks` | Clé Android de signature production | 3 616 octets | **SECRET** : ne jamais versionner |
| `nexus-companion-signature-credentials.txt` | Alias, mot de passe du keystore/de la clé et empreinte de référence | 453 octets | **SECRET** : ne jamais versionner |

Emplacement de travail privé actuellement utilisé : `/mnt/data/nexus-signing-private/`. Ce chemin est **spécifique à l'environnement de travail**, et non un chemin universel garanti sur tout poste. Il faut restaurer les fichiers depuis le coffre privé lorsqu'ils ne sont pas montés.

**Contrôle d'accès local appliqué :** répertoire en mode `0700` ; fichiers en mode `0600`. Ne jamais copier ces deux fichiers dans le dépôt GitHub, une issue, une pull request, un artefact public, un Blob public, un log, un message de conversation ou un APK.

Le fichier d'identifiants contient les libellés `Alias`, `Mot de passe du keystore et de la clé` et `SHA256`. **Leurs valeurs ne figurent pas dans ce document.** L'empreinte de certificat (non secrète) peut être utilisée comme référence après une vérification indépendante par `apksigner`.

### Liaison aux variables Gradle

Les quatre noms attendus par `app/build.gradle.kts` sont :

```text
NEXUS_ANDROID_KEYSTORE_PATH
NEXUS_ANDROID_STORE_PASSWORD
NEXUS_ANDROID_KEY_ALIAS
NEXUS_ANDROID_KEY_PASSWORD
```

Sur un poste de signature fiable, charger les valeurs du coffre privé dans l'environnement du **processus Gradle seulement**, sans les imprimer ni les écrire dans un fichier du dépôt. La variable `NEXUS_ANDROID_KEYSTORE_PATH` doit pointer vers la copie privée du `.jks`. Ne pas supposer que le nom de l'alias correspond au nom du fichier : **lire la valeur exacte dans le fichier privé**.

Le fichier d'identifiants actuel contient une seule mention « mot de passe du keystore et de la clé » ; le renseignement de `NEXUS_ANDROID_STORE_PASSWORD` et `NEXUS_ANDROID_KEY_PASSWORD` à partir de cette valeur doit être confirmé au moyen d'un contrôle local non journalisé avant de signer.

### Sauvegarde durable

Conserver au moins **deux copies chiffrées indépendantes** du keystore et des identifiants, avec contrôle d'accès strict, hors dépôt et hors stockage public. Vérifier périodiquement la restauration. Perdre la clé de signature d'origine empêcherait la mise à jour transparente des APK déjà installées.

**Ne jamais générer une nouvelle clé de production pour « réparer » une signature manquante** : elle ne correspondrait plus au certificat des installations existantes.
