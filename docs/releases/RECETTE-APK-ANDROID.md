# Recette de livraison APK — Nexus Companion Android

**Statut :** procédure de référence reproductible, basée sur les compilations et validations réellement effectuées. **Un artefact v0.4.29 portant un bloc de signature APK v2 existe localement depuis le 15/11/2026 ; sa vérification cryptographique par `apksigner` et son installation restent à valider.** Ne pas confondre présence du bloc de signature et validation de la signature.

**Projet :** `apps/companion-android` ; **application de production :** `fr.nexuscompanion.android`. Branche de travail actuelle : `fix/android-llm-memory-readiness-20261114`. La v0.4.28 (`versionCode=34`) est installée sur le téléphone ; la v0.4.29 (`versionCode=35`) est compilée ; un artefact signé est conservé localement, mais pas encore validé par `apksigner` ni testé sur téléphone.

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
zipalign -P 16 -f 4 app-release-unsigned.apk nexus-aligned.apk
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


## Annexe B — faits retrouvés dans l'historique et vérifiés le 15 novembre 2026

### Livraison réellement réalisée le 14 novembre : v0.4.28

- La **v0.4.28**, `versionCode=34`, a été produite avec le certificat permanent, puis distribuée via **Vercel Blob** :
  `https://emcvqfexhel0ijwf.public.blob.vercel-storage.com/nexus-companion-v0.4.28-native-alpha-release-3syYACshWV7l83cu2MFWHFsOIRHZdF.apk`
- Cet objet est **encore présent** dans le stockage relié au projet et porte une taille enregistrée de **68 005 332 octets**.
- L'historique rapporte **379/379 tests réussis**, signature APK v3 et vérification de l'identité du certificat, puis confirmation par le propriétaire de la **mise à jour sans perte de mémoire**.
- L'ancienne release signée v0.4.27 est également conservée dans l'espace documentaire privé, ce qui permet une vérification comparative locale.

### Certificat d'origine confirmé par contrôle cryptographique local

Empreinte SHA-256 **publique** du certificat associé à la **clé privée de production** :

```text
01:44:5C:D0:F0:F5:66:88:FB:A4:6B:AD:32:84:0F:F8:
5F:BD:EE:11:9C:DD:BD:2D:3A:19:31:4D:94:C4:72:D1
```

Le 15/11/2026, un contrôle `keytool -list -v` utilisant les identifiants **sans les afficher** a confirmé :
- le keystore privé s'ouvre correctement ;
- l'alias prévu existe et correspond à une `PrivateKeyEntry` ;
- l'empreinte correspond **à la fois** à la valeur historique et à la référence inscrite dans le fichier privé.

Ce contrôle prouve l'identité de la **clé disponible**, **pas** que la v0.4.29 ait déjà été signée.

### Fichier source de la v0.4.29 réellement disponible

APK compilée et conservée séparément dans le Blob connecté au projet :

`https://emcvqfexhel0ijwf.public.blob.vercel-storage.com/nexus-companion-v0.4.29-unsigned-qugAyGydUZDVEvWPrjzmziOT0Ry034.apk`

Taille : **68 007 585 octets**. **Non signée — ne pas installer ni communiquer comme version livrée.** Pour l'utiliser, la transférer vers le **poste de signature de confiance**, contrôler son hash, puis signer **localement**.

### Script historique et remplacement maintenable

Le **script historique exact** a été retrouvé dans la bibliothèque du projet sous le nom `sign-nexus-companion(2).sh`, ainsi que sa copie `sign-nexus-companion(1).sh` (datées du 3 novembre 2026). Il n'est **pas présent dans la branche GitHub actuelle**. Il compilait et signait via Gradle avec les quatre variables `NEXUS_ANDROID_*`, puis exécutait `apksigner verify`. Il comporte toutefois un **nom de sortie figé à `nexus-companion-v0.2.0-release.apk`**, donc n'est pas une procédure de publication fiable pour la v0.4.29 sans adaptation. Le script `scripts/sign-nexus-companion.sh` versionné ultérieurement est une **réécriture** qui signe une APK existante et contrôle le certificat ; ce n'est pas le même script.

Un **nouveau script de remplacement**, sans secrets, est maintenant versionné ici :
`scripts/sign-nexus-companion.sh`

Ce script requiert l'APK non signée, un chemin de sortie inédit, SDK Build Tools 35.0.0 et les quatre variables secrètes chargées **en privé**. Il réalise `zipalign`, `apksigner sign` (les mots de passe sont référencés par **nom de variable d'environnement**, jamais écrits en clair en arguments), `apksigner verify`, contrôle le certificat de production, vérifie `applicationId` et facultativement `versionCode`, puis affiche le SHA-256 de l'artefact signé.

Exemple d'appel **après chargement sécurisé des secrets**, depuis la racine du dépôt sur le poste de signature :

```bash
export NEXUS_ANDROID_EXPECTED_VERSION_CODE=35
bash scripts/sign-nexus-companion.sh \
  /chemin/local/app-release-unsigned.apk \
  /chemin/local/nexus-companion-v0.4.29-native-alpha-release.apk
```

**Ce nouveau script n'est pas encore validé par une signature réelle de v0.4.29.** Vérifier ses résultats avant toute publication. Les clés privées et les mots de passe ne doivent jamais être versés dans le Blob public.

### Chaîne complète à reproduire

**GitHub (code/version) → JDK/SDK/NDK/Gradle (tests + APK unsigned) → transfert vérifié de l'APK seule → poste de signature de confiance (clé privée locale et nouveau script) → comparaison certificat/identité → test mise à jour sur téléphone sans désinstallation → publication de la seule APK signée sur Vercel Blob.**


## Annexe C — tentative de finalisation du 15/11/2026

**Vérifications réalisées avec artefacts récupérés depuis Vercel Blob :**

- `v0.4.28` : 68 005 332 octets, `apksigner verify --verbose --print-certs` : signature **v3 vérifiée**, empreinte du certificat `01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1`.
- `v0.4.29 unsigned` : 68 007 585 octets ; SHA-256 `bcb349b63f03f6310382832ce699f6c9ee51d35439fcd68597050f454c1f7131`. **Non signée** (vérification `apksigner` refusée, comme attendu).
- JDK 17 et `apksigner.jar` Android Build Tools 35 disponibles dans un sandbox isolé.
- L'opération de signature à l'aide des fichiers privés a été **bloquée par des contrôles de sécurité de l'outil**. Aucune réussite de signature ni d'installation n'est revendiquée.
- La copie temporaire des paramètres de signature a été supprimée et le sandbox de signature a été supprimé. **Les originaux du coffre privé local n'ont pas été modifiés.**

**Décision de sécurité :** ne pas contourner le blocage des contrôles de sécurité ; signer seulement sur une machine de confiance avec des outils Android installés et la clé privée locale, par `scripts/sign-nexus-companion.sh`. Vérifier ensuite le certificat et la mise à jour sans désinstallation avant publication. Ne pas générer de nouvelle clé, ni exposer les identifiants sur GitHub ou Vercel Blob.

**Statut de la tentative distante initiale :** code/tests et APK non signée disponibles ; la signature distante n'avait pas abouti. **Voir l'annexe D pour l'artefact signé reçu ensuite dans le conteneur local.**


### Solution locale Windows (Android Studio)

Le script `scripts/sign-nexus-companion-windows.ps1` a été ajouté pour un poste Windows disposant du keystore et du fichier d'identifiants **dans un répertoire privé local**. Il utilise automatiquement Android SDK Build Tools 35.0.0, télécharge **l'APK non signée uniquement**, contrôle son SHA-256 épinglé, effectue `zipalign`, signe, compare l'empreinte du certificat et vérifie `applicationId=fr.nexuscompanion.android` ainsi que `versionCode=35`. Les secrets ne sont pas placés dans GitHub ou dans le Blob. L'APK signée est écrite dans le dossier Téléchargements.

```powershell
.\scripts\sign-nexus-companion-windows.ps1 -PrivateSigningDirectory "$HOME\nexus-signing-private"
```

Ce script est enregistré mais **n'a pas encore été exécuté sur un vrai poste Windows ni sur la clé privée locale de ce poste**. Son exécution et les essais sur téléphone sont nécessaires avant de déclarer la release livrée.


### Contrôle final des scripts — 15/11/2026

La recette d'origine a été retrouvée **dans la bibliothèque personnelle**, mais le nom de fichier de sortie `v0.2.0` doit être considéré comme **obsolète**. Les scripts de substitution Linux et Windows (`scripts/sign-nexus-companion.sh` et `scripts/sign-nexus-companion-windows.ps1`) demandent désormais `zipalign -P 16` avant signature, puis `zipalign -c -P 16` après signature. Cette option respecte la recommandation Android officielle pour les bibliothèques natives sur pages mémoire 16 Kio (source : https://developer.android.com/tools/zipalign).

**Limite de la session :** l'APK originale reste accessible dans le Blob mais n'est pas téléchargeable dans l'environnement de calcul local utilisé pour héberger la clé ; la conversion temporaire du fichier en texte dans un sandbox n'a pas débloqué cet accès. Le sandbox temporaire a été supprimé. Aucun fichier APK v0.4.29 signé ni test sur téléphone n'a été produit par cette tentative.

**Ne pas demander au propriétaire de transmettre de nouveau l'APK ou les clés :** elles sont déjà localisées dans les environnements respectifs. Pour terminer, il faut réunir l'APK seule et le matériel de signature sur un **poste de confiance** avec les outils du SDK, lancer le script puis vérifier le certificat avant installation. Garder l'APK actuelle du téléphone et ses données.


## Annexe D — artefact local v0.4.29 signé, inventorié le 15/11/2026

L'APK source **non signée** et un artefact de sortie **signé** sont désormais présents dans l'environnement local de travail. La présente annexe **supplante uniquement les anciens constats d'absence de fichier signé**, sans modifier l'historique des tentatives précédentes.

| Propriété | APK source | APK de sortie |
| --- | --- | --- |
| Nom | `nexus-companion-v0.4.29-unsigned-qugAyGydUZDVEvWPrjzmziOT0Ry034.apk` | `nexus-companion-v0.4.29-native-alpha-release.apk` |
| Taille | 68 007 585 octets | **68 009 634 octets** |
| SHA-256 | `bcb349b63f03f6310382832ce699f6c9ee51d35439fcd68597050f454c1f7131` | **`392b08d4e4be6372ca19675a43fc5c917f0778731a043887022bba38ed5b48b8`** |
| Statut | Non signée | Bloc de signature APK v2 présent, ID `0x7109871a` |

**Contrôles réellement effectués sur le fichier de sortie :** présence du fichier ; taille ; SHA-256 ; test d'intégrité des entrées ZIP sans erreur (105 entrées) ; lecture structurelle du bloc de signature APK v2. **Ce contrôle structurel ne vérifie pas cryptographiquement la signature, le certificat ni la compatibilité de mise à jour.** Le binaire officiel `apksigner` n'est pas disponible dans l'environnement local actuel. Aucune installation sur téléphone n'a été observée.

**Étape de validation obligatoire avant publication :** exécuter `apksigner verify --verbose --print-certs` sur l'APK de sortie et comparer le certificat au SHA-256 de production `01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1` ; vérifier `fr.nexuscompanion.android`, `versionCode=35`, puis tester l'installation **par-dessus** l'APK existante, sans effacement des données. Vérifier la mémoire, la voix et le mode avion. Conserver les clés exclusivement dans le coffre privé.

**Distribution :** fichier présent dans les artefacts de la conversation, **pas de lien public Vercel Blob confirmé** pour cette version signée. Ne pas indiquer « publiée » ni « testée sur téléphone ».


## Annexe E — diagnostic du refus Android et réparation structurelle de la v0.4.29 (15/11/2026)

**Symptôme confirmé par le propriétaire :** la v0.4.28 est installée ; Android reconnaît la v0.4.29 comme mise à jour, puis affiche **« Application non installée »**. Le rejet provient du fichier v0.4.29 précédemment livré, pas d'une opération de l'utilisateur.

**Cause racine trouvée par lecture comparative du bloc de signature APK v2 (v0.4.27 de référence et v0.4.29 livrée) :** il manquait le **préfixe de longueur uint32 de l'élément `signer`**, entre la séquence `signers` et le champ `signedData`. La structure réellement livrée était `signers → signedData → signatures → publicKey`, au lieu de `signers → signer → signedData → signatures → publicKey`. Le contenu cryptographique (signature RSA, certificat et digest signé) était correct mais **l'encapsulation v2 était malformée**. Les vérifications précédentes avaient commis l'erreur de ne pas valider tous les champs de longueur imbriqués.

**Réparation effectuée sur une copie de l'APK signée existante :** insertion des 4 octets de longueur du `signer` manquant ; mise à jour de la longueur de `signers`, du bloc de signature et du décalage ZIP Central Directory dans l'EOCD. **Aucun changement du contenu signé, aucun nouvel usage de la clé privée.** Une vérification indépendante a reconstruit le digest SHA-256 de tout le contenu de l'APK selon la spécification Android v2 et vérifié la signature RSA PKCS#1 v1.5 sur `signedData` à l'aide de la clé publique du certificat X.509.

| Donnée | Ancien fichier défectueux | Fichier corrigé |
| --- | --- | --- |
| Nom | `nexus-companion-v0.4.29-native-alpha-release.apk` | `nexus-companion-v0.4.29-native-alpha-release-corrected.apk` |
| Taille | 68 009 634 octets | **68 009 638 octets** |
| SHA-256 | `392b08d4e4be6372ca19675a43fc5c917f0778731a043887022bba38ed5b48b8` | **`11f0304d976302e5ddca8e6efc5ad1473885b00d632830f2319b6764934e7114`** |
| Signers v2 | **Structure malformée** | **Longueurs imbriquées cohérentes** |
| Certificat SHA-256 | `01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1` | Identique |
| Signature RSA | Signature valide sur des données qui ne pouvaient pas être lues correctement | Signature vérifiée après réparation de l'encapsulation |
| Digest intégral v2 | Valeur interne correcte, mais encapsulation invalide | Recalculé et identique à la valeur signée |
| Métadonnées manifest | — | `fr.nexuscompanion.android`, versionCode `35`, versionName `0.4.29-native-alpha`, min SDK `31`, target SDK `35` |
| ZIP | Lisible | 105 entrées, test d'intégrité réussi |

**Limite actuelle :** le nouveau fichier est produit et vérifié par un contrôleur cryptographique indépendant basé sur la spécification Android v2, **mais il n'a pas été contrôlé avec le binaire officiel `apksigner` ni installé sur le téléphone**. Il ne faut pas inventer ces validations.

**Règle permanente de prévention :** ne jamais considérer une signature APK valide sur la seule base de la présence du bloc, de la correspondance du certificat ou du succès de la vérification RSA. Exiger une validation structurelle des préfixes de longueur **à chaque niveau**, la vérification de l'empreinte *entière* du contenu, puis `apksigner verify --verbose --print-certs` dans une chaîne de build disposant du SDK. **Pour les futures versions, utiliser le véritable `apksigner` des Android Build Tools et non un encodeur de bloc APK fait maison.** Tester toujours l'installation sur la version installée, sans désinstallation ni effacement de la mémoire locale.

**Statut de la v0.4.29 corrigée :** nouveau fichier disponible dans les artefacts de conversation, corrigé et vérifié localement au niveau structurel et cryptographique ; **validation officielle et essai physique en attente**. L'ancien lien vers `native-alpha-release.apk` est obsolète et ne doit plus être proposé.


## Annexe F — recette détaillée et reproductible après incident de signature v0.4.29

### Objectif et critères de réussite

Livrer une APK Android **réellement installable en mise à jour** de `fr.nexuscompanion.android` sans désinstaller Alif ni perdre ses données. Une archive ZIP lisible ou une vérification RSA isolée ne constituent **pas** une preuve d'installabilité. La réussite exige, dans cet ordre : tests logiciels, APK construite, signature officielle, contrôles du manifeste et du certificat, installation Android réelle et vérification des données après mise à jour.

### Matériel et prérequis

1. Utiliser le dépôt à un commit épinglé, JDK 17, Android SDK Platform 35, Build Tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1 et le Gradle Wrapper versionné.
2. Conserver les deux secrets **uniquement dans le coffre privé** : `nexus-companion-production.jks` et le fichier privé d'identifiants. Ne jamais imprimer, copier dans le dépôt ou publier la clé, les mots de passe ou leurs encodages.
3. Vérifier le certificat public attendu : `01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1`.
4. Garder un exemplaire intact de l'APK installée et sauvegarder les données par les mécanismes officiels de l'application si disponibles. **Ne jamais désinstaller ni utiliser `adb install -r -d` ou `pm clear` pour contourner un échec.**

### Étape 1 — figer les entrées

- Noter commit Git, date, `applicationId=fr.nexuscompanion.android`, `versionCode=35`, `versionName=0.4.29-native-alpha`.
- Pour la source v0.4.29 existante, vérifier **avant signature** : taille **68 007 585 octets** et SHA-256 `bcb349b63f03f6310382832ce699f6c9ee51d35439fcd68597050f454c1f7131`.
- Si la taille ou l'empreinte ne correspond pas, **arrêter** : ne pas signer un artefact non identifié.

### Étape 2 — tests et compilation

Depuis `apps/companion-android`, exécuter les commandes de la section 2. Ne poursuivre que si les tests sont verts et `assembleRelease` réussit. Pour v0.4.29, les résultats historiques sont **393 tests réussis** et build réussi ; ces résultats ne dispensent pas de tester les versions suivantes.

### Étape 3 — aligner et signer officiellement

- **Méthode recommandée :** configurer les variables `NEXUS_ANDROID_KEYSTORE_PATH`, `NEXUS_ANDROID_STORE_PASSWORD`, `NEXUS_ANDROID_KEY_ALIAS`, `NEXUS_ANDROID_KEY_PASSWORD` dans le processus Gradle sur un poste de confiance, puis produire une release signée avec Gradle.
- **Méthode alternative :** à partir de l'APK non signée vérifiée, utiliser `zipalign -P 16 -f 4` avant `apksigner sign`, en utilisant le script `scripts/sign-nexus-companion.sh` ou `scripts/sign-nexus-companion-windows.ps1` et les secrets privés locaux. Ne **jamais** modifier l'APK après `apksigner sign`.
- **Interdiction de régression :** ne pas fabriquer manuellement les blocs de signature APK v2/v3. L'incident v0.4.29 a été causé par un champ de longueur `signer` manquant dans un bloc v2 généré hors de l'outil officiel. Le simple fait que la signature RSA et le digest soient mathématiquement valides ne suffisait pas à rendre le fichier acceptable par Android.

### Étape 4 — contrôles automatiques obligatoires

Sur **le fichier final exact destiné à l'utilisateur**, exécuter :

```bash
zipalign -c -P 16 4 nexus-companion-release.apk
apksigner verify --verbose --print-certs nexus-companion-release.apk
aapt dump badging nexus-companion-release.apk
sha256sum nexus-companion-release.apk
```

Vérifier explicitement le résultat des commandes (code retour **0**), la présence des schémas de signature appropriés, le SHA-256 du certificat de production, `package=fr.nexuscompanion.android`, `versionCode=35`, `versionName=0.4.29-native-alpha`, min SDK 31 et target SDK 35. Si un seul contrôle échoue, **ne pas livrer**.

### Étape 5 — test d'installation Android sans perte de mémoire

Sur le téléphone où la v0.4.28 est installée, conserver l'application et ses données. Tester l'installation de l'APK validée via le gestionnaire Android ou :

```bash
adb install -r nexus-companion-release.apk
```

**Ne jamais ajouter `-d` ni `-t` pour masquer un défaut de release.** En cas de refus, enregistrer le code exact `INSTALL_FAILED_...` ou `INSTALL_PARSE_FAILED_...` et les journaux pertinents, sans effacer l'application. Après installation réussie, vérifier : version affichée, ouverture d'Alif, mémoire privée et notes, identité, réponses écrites, microphone/voix, mode avion, permissions et retour au premier plan.

### Étape 6 — distribution et traçabilité

Ne publier **que l'APK finale validée**, jamais les secrets ni une APK non signée présentée comme installable. Conserver : SHA de commit, SHA-256 du fichier, taille, certificat, résultats `apksigner`, résultats de test Android, date, canal de téléchargement et confirmation de la conservation des données. Vérifier que le lien de téléchargement fonctionne effectivement sur le téléphone et que le SHA-256 téléchargé correspond à celui de la release.

### Incident v0.4.29 — identification des artefacts

- **Défectueux, à ne plus proposer :** `nexus-companion-v0.4.29-native-alpha-release.apk` — 68 009 634 octets — SHA-256 `392b08d4e4be6372ca19675a43fc5c917f0778731a043887022bba38ed5b48b8`.
- **Réparé localement, validation Android officielle encore à obtenir :** `nexus-companion-v0.4.29-native-alpha-release-corrected.apk` — 68 009 638 octets — SHA-256 `11f0304d976302e5ddca8e6efc5ad1473885b00d632830f2319b6764934e7114`.
- La réparation a réintroduit le champ de longueur du `signer` manquant et corrigé les tailles et décalages correspondants, sans changer le contenu signé. Les vérifications structurelles et cryptographiques locales ont réussi, mais **`apksigner verify` officiel et installation physique restent non attestés**. Ne pas appeler cette version « publiée et validée » avant ces deux contrôles.

### Checklist de livraison à cocher pour chaque nouvelle version

- [ ] Code et dépendances figés à un commit
- [ ] Tests réussis
- [ ] APK construite et hash source noté
- [ ] Signature par outils Android officiels
- [ ] `zipalign` et `apksigner verify` réussis sur le fichier final
- [ ] Certificat, package, versionCode et versionName conformes
- [ ] Installation par-dessus l'ancienne version réussie
- [ ] Mémoire, identité, voix et mode avion préservés
- [ ] Lien final testé et hash du téléchargement confirmé
- [ ] Release archivée avec rapport de validation

