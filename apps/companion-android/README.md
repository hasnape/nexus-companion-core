# Nexus Companion Android — 0.4.22 native alpha

Application Kotlin native en version **alpha**. Android 12 minimum (API 31), cible Android 15 (API 35). La conversation et la mémoire fonctionnent sur le téléphone. Les recherches publiques distinguent le Web ouvert dans le navigateur de Wikipédia consulté dans l’application en HTTPS.

## Fonctions actuelles

- Accueil natif avec portrait dessiné, indicateur textuel de l’état et sections distinctes pour la voix, la conversation, la mémoire et les réglages. Le portrait réagit à la disponibilité du micro, au calcul et à la lecture. Les clignements et la bouche animée suivent le réglage d’animation d’Android.
- Les deux pupilles restent visibles dans les quatre états du portrait. Elles se décalent ensemble pendant la réflexion et disparaissent au clignement.
- **Corps roulant virtuel** : deux roues latérales et une roue directrice centrale, configurable avant/arrière. « Avance de vingt centimètres », « recule de dix centimètres », « tourne à gauche de trente degrés » et « stop » pilotent une trajectoire bornée. Le bouton Arrêter reste disponible pendant un calcul. La position simulée et le cap s’affichent ; sortir du mode ou passer en arrière-plan arrête le mouvement. Le plan en unités physiques est réutilisable par un futur adaptateur matériel, qui reste à construire.
- **Mode robot** affiche un corps turquoise, un visage ivoire et des yeux verts avec pupilles. « Nexus, affiche ton corps », « affiche ton visage » et « retour à l’accueil » commandent aussi cet affichage. Le choix est restauré après une rotation.
- **Mode plein visage** sous le portrait. Le dessin se dimensionne selon la place disponible en portrait et en paysage. Le halo se fond dans le fond de l’écran. **Quitter** ou **Retour** réaffiche l’accueil. Le changement d’affichage conserve la session vocale et la saisie en cours.
- Conversation locale dans un interpréteur Java embarqué. Les règles partagées couvrent les salutations, le soutien simple, la mémoire explicite et les limites de sécurité. Une base de dépannage locale reprend de Nexus Support Hub les signaux par famille de panne et le classement par pertinence. Une demande ambiguë appelle une précision.
- Les questions polies sans ponctuation, les demandes de lieu ou de raison et les pronoms personnels utilisent un analyseur commun. Les connaissances générales peuvent aussi être proposées avec « Retiens que le Soleil est une étoile ». Une information absente entraîne une précision.
- Les questions ordinaires exploitent les notes pertinentes : « Quel est mon prénom ? », « Quel est mon métier ? » ou « Quelle boisson je préfère ? ». Le même vocabulaire partagé guide la sélection native et les extraits de réponse. Les informations contradictoires provoquent une question ; les notes sans rapport ne deviennent pas une réponse.
- Une question publique sans réponse locale peut déclencher une **proposition** de recherche. Un « oui » explicite envoie seulement le sujet à Wikipédia. Après lecture, un autre « oui » peut enregistrer le résultat avec sa source dans la mémoire locale chiffrée et compressée. La réponse est ensuite retrouvable hors ligne. Refus, changement de sujet, arrière-plan ou expiration annulent la proposition.
- **Accès en ligne** dans **Réglages**, activé au départ. « Cherche sur Internet la tour Eiffel », « recherche sur le Web… » ou « cherche en ligne… » ouvrent une recherche Web dans Chrome ou un navigateur compatible. « Cherche sur Wikipédia la tour Eiffel » interroge Wikipédia dans l’application et affiche un extrait attribué avec son lien source. Le réglage mémorise le choix. Les notes et l’audio restent locaux. [Conditions de réutilisation des extraits](https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use)
- Écoute automatique à l'ouverture si l'autorisation micro a été accordée et si l'utilisateur ne l'a pas désactivée. Bouton d'arrêt visible, préférence conservée. Le micro et la synthèse sont arrêtés dès `onPause`.
- Reconnaissance française sur le téléphone via le modèle Vosk inclus dans l’APK. Dire « Nexus, bonjour » ou « Nexus », puis la commande après « Je t'écoute ». Les silences ordinaires relancent l'écoute sans épuiser le budget de reprise des pannes.
- Après une réponse, la conversation peut continuer sans répéter « Nexus » pendant 30 secondes à partir du micro prêt. La fenêtre suit la fin effective de la lecture. Une lecture échouée ne l’ouvre pas. Après le délai, le mot d’activation redevient nécessaire.
- « Parler maintenant » lance une dictée directe sans mot d'activation. Le bouton reste désactivé si le micro est arrêté ou pendant un calcul. La dictée peut interrompre une réponse vocale.
- Aperçu « En cours » puis « Entendu », conservé uniquement en mémoire, effacé après 15 secondes sans transcription et à l'arrêt du micro, en arrière-plan ou dans un dialogue privé. Seul le résultat final peut déclencher une commande ; le texte saisi au clavier reste indépendant.
- Le mot d’activation et ses variantes restent reconnus uniquement en début de phrase, sur un mot complet. La dictée directe utilise le résultat du décodeur sans exiger de mot d’activation.
- Le délai de commande de 30 secondes commence après les premiers échantillons du micro. Il limite l’attente avant de parler et se désarme quand un résultat partiel arrive. Le chargement initial dispose de 30 secondes et les captures silencieuses se renouvellent après 20 secondes. L’interface distingue la préparation, l’écoute et la pause pendant la réponse.
- Les blocs audio sont de 100 ms. Les résultats finaux récupèrent aussi une phrase sans résultat partiel préalable. Le décodage de consensus Vosk est conservé ; une confirmation courte à faible confiance demande de répéter.
- Chaque capture libère le micro et le décodeur avant de transmettre son résultat. Après un silence ou une phrase ambiante, le moteur embarqué prépare la capture suivante dès le prochain passage de la boucle Android. La reprise réutilise le modèle chargé et ignore les événements de l’ancienne capture.
- Micro suspendu pendant le calcul local, la synthèse et les dialogues de notes. La préparation du micro redémarre 200 ms après la fin confirmée de la lecture. Un délai de 700 ms s’applique après une erreur ou une expiration de la lecture. La bouche s’ouvre au signal de début de la synthèse.
- L’interrupteur **Réponses vocales** règle la lecture indépendamment du microphone. Une réponse écrite peut être lue avec le micro coupé ou sa permission refusée. La voix française locale utilise le volume multimédia. Au premier lancement après mise à jour, l’ancien choix concernant le micro initialise l’interrupteur, puis chaque réglage est enregistré séparément.
- Propositions non sensibles à retenir avec confirmation vocale explicite (« oui »), consultation et suppression dans **Mes notes**. Un doublon exact réutilise la note existante et l’application l’indique. La recherche sans accents privilégie le nombre de mots trouvés, puis le titre et la date.
- « Qu’est-ce que j’ai noté sur la batterie ? » et « Tu te souviens de ma batterie ? » recherchent des notes sur le sujet. Si la recherche est vide, Nexus l’indique. « Qu’ai-je noté ? » demande un aperçu des notes récentes. Le rappel conversationnel présente des extraits non sensibles et leur statut non vérifié.
- Le cache de mémoire évite de déchiffrer et analyser les fichiers inchangés, en comparant leurs octets plutôt que leur date. Les doublons cosmétiques gardent la note originale. « État de ta mémoire » donne le nombre de notes et l’espace utilisé. La compression choisit le plus petit résultat GZIP standard/fort pour les grandes bibliothèques.
- Les notes sont enregistrées atomiquement avec leur provenance. Le stockage applique GZIP si le contenu diminue, puis chiffre les données avec AES-256-GCM et une clé Android Keystore liée au compagnon. Les anciennes notes chiffrées peuvent être lues et leur prochaine écriture applique la compression. Les anciens fichiers JSON v1 reconnus sont chiffrés lors de la lecture.
- Sauvegarde système désactivée, exclusions explicites de sauvegarde cloud et de transfert entre appareils. Aucun audio ou historique de transcription enregistré par l'application.
- Écran natif « Mises à jour » : choix explicite d'un APK local, contrôle du package, de la version, des signataires courants et du niveau Android, puis transmission d'une copie privée en lecture seule à l'installateur Android. Aucun téléchargement automatique ni permission de stockage étendue.
- « Voix et diagnostic » indique la présence du modèle français intégré. « Tester la voix locale » lit une phrase sur demande. Le panneau garde le microphone fermé et le diagnostic copié contient seulement les versions, les identifiants d’application, le niveau Android et les signatures publiques.
- Le témoin du signal micro mesure le niveau des échantillons reçus et revient à zéro pendant les pauses.

La reconnaissance locale fonctionne par sessions finies et reprises. Le déclenchement attend une transcription finale contenant « Nexus ». La précision et l’autonomie restent à mesurer sur téléphone.

## Compréhension et recherches 0.4.21

Une commande de recherche incomplète telle que « Nexus, cherche sur Internet » peut attendre jusqu’à 1,5 seconde que le sujet commence après une coupure acoustique. Les segments sont réunis sans modifier les mots reconnus. Une nouvelle activation, un arrêt ou un refus restent séparés ; un « oui » ne devient jamais une approbation autonome par cette réunion. Une continuation sans texte exploitable ne réutilise pas un ancien sujet. Le modèle Vosk et son décodage principal restent identiques.

Les déformations observées « cher sur Internet… » et « cher/cherche/recherche sur interdit… » provoquent une demande de confirmation avant toute recherche Web. Une recherche sans sujet peut reprendre la dernière question publique de la session, ou demander le sujet à l’échange suivant. Ce contexte est transitoire, expire et disparaît en arrière-plan. Il ne provient pas du contenu des notes ; les sujets contenant coordonnées, secrets ou formulations personnelles sont refusés à cette étape.

« Que répondre à bonjour ? », « Comment répondre à bonsoir ? » et « Que dire à merci ? » reçoivent des exemples pratiques locaux. Une demande explicite de recherche garde son effet. Wikipédia examine jusqu’à cinq résultats, ignore les pages d’homonymie et refuse les extraits insuffisamment liés au sujet, y compris un film partageant seulement le mot « salut ». La réponse sélectionne une ou deux phrases originales, au plus 650 caractères, avec priorité aux phrases chiffrées pour une question « combien ». Ce classement lexical ne garantit pas une réponse à toute question. L’extrait d’origine et son article restent la preuve proposée à l’enregistrement confirmé.

Sans activation de la lecture Chrome décrite ci-dessous, les recherches Web restent dans le navigateur. Leur texte n’est jamais mémorisé automatiquement. Il n’y a pas de nouveau modèle génératif ni d’amélioration acoustique mesurée sur un téléphone dans cette livraison. Les fonctions de mémoire, de compression, de voix, de plein visage et de corps roulant restent présentes.

## Lire une réponse IA de Chrome — 0.4.22

Dans **Réglages**, active **Lire les réponses IA après une recherche Web**, lis la présentation, puis utilise **Configurer l’accès Chrome**. Android demande d’activer **Nexus — lecture Chrome à la demande**. L’accès reste désactivé par défaut et n’est jamais accordé par l’application. Désactiver l’interrupteur annule la demande en cours. Si le système ne propose pas l’écran dédié, Nexus essaie la liste des réglages d’accessibilité. Si Android bloque cet accès, la lecture d’un texte copié reste disponible.

Avec cet accès actif, « Cherche sur Internet les volcans » ouvre Chrome et attend jusqu’à 25 secondes une réponse pour **ce même sujet**. Si un Aperçu IA est détecté et que son texte reste stable entre deux contrôles, Nexus revient dans l’application pour lire **la partie accessible**, non vérifiée. « Lis la réponse IA de Chrome » ou le bouton **Lire la réponse IA de Chrome** relit le résultat actuellement affiché, sans lancer une nouvelle recherche. Le réglage d’accès en ligne s’applique à ces ouvertures ; les réponses vocales doivent être activées pour entendre la lecture.

La détection concerne les Aperçus IA clairement identifiés sur les résultats Google de `google.com` ou `google.fr` dans Chrome stable. Un simple onglet « Mode IA », des résultats Web ordinaires, une autre adresse ou un ancien sujet ne suffisent pas. Le service ne clique pas, ne déroule pas la page et ne lit pas les zones masquées ou de saisie. Le texte d’un autre navigateur, une réponse sans marqueur reconnaissable ou un document inaccessible appelle la solution par copie. La fonctionnalité ne garantit pas la lecture intégrale d’une réponse longue ni celle de toutes les interfaces d’IA.

Pour cette solution, copie **le texte** de la réponse, reviens dans Nexus, puis appuie sur **Lire un texte copié** et **Lire à voix haute** dans l’aperçu. Le presse-papiers n’est consulté qu’à cette action, pas à l’ouverture de Nexus ; les liens seuls et les éléments non textuels ne sont pas chargés. Cette lecture fonctionne avec l’accès en ligne désactivé. La provenance est indiquée comme **Texte copié · origine non vérifiée**, sans inventer une source Google.

La lecture et la mémorisation sont distinctes. Après lecture, un « oui » explicite ou le bouton de confirmation peut enregistrer le texte dans la bibliothèque chiffrée et compressée, avec sa provenance et `verified=false`. Il redevient consultable hors ligne après redémarrage. Refuser, changer de sujet, passer en arrière-plan ou laisser expirer la proposition annule l’approbation. Un texte importé contenant « ouvre Chrome » ou « avance » est lu comme du texte ; il ne devient pas une commande d’application ou de mouvement.

Le service ne consulte aucune fenêtre sans demande active. Les demandes et leurs résultats restent seulement en mémoire, expirent et ne traversent pas une relance du processus. Un retour anticipé dans Nexus annule la capture ; un résultat tardif ou remplacé ne déclenche pas de lecture. Les fenêtres des autres applications ne sont pas parcourues. Aucun historique Chrome, compte, cookie, audio ou capture d’écran n’est récupéré. Il n’y a ni nouvelle dépendance ni nouveau modèle génératif.

## Propriété du projet et corrections 0.4.20

L’écran **À propos** identifie **Amine HARBI** comme propriétaire du projet et **Rép&Web** comme éditeur. La mention de droits est limitée aux éléments originaux propres au projet, sous réserve des droits des tiers. Une rubrique distincte **Composants tiers et licences** conserve les auteurs, licences et références de source des dépendances. Le corps de ces notices reste inchangé. Voir [OWNERSHIP.md](../../OWNERSHIP.md).

Les formulations personnelles explicites équivalentes, par exemple « Mon prénom est Amine » et « Je m’appelle Amine », ne déclenchent plus une fausse contradiction. La comparaison couvre quelques prédicats de prénom, métier et préférences ; elle conserve les négations et les qualificatifs. Deux valeurs différentes d’un même attribut explicite peuvent aussi être signalées dans une seule note. Les explications complémentaires et les objectifs d’une note ne deviennent pas des contradictions. Ces règles ne fusionnent ni ne modifient les notes.

Lorsque Chrome ou Maps ne peut pas être ouvert et qu’Android accepte une autre application compatible, Nexus indique le repli au lieu d’affirmer que l’application demandée s’est ouverte. L’indisponibilité complète reste une erreur récupérable et la conversation peut continuer. Une répétition ne provoque pas une nouvelle ouverture.

## Compréhension de la mémoire et ouverture des applications conservées depuis 0.4.19

« Quel est mon prénom et mon métier ? » peut réunir des faits enregistrés dans plusieurs notes. Chaque partie garde ses preuves et sa provenance ; Nexus signale une information manquante ou contradictoire. Cette composition est bornée à quatre questions personnelles explicites parmi les attributs pris en charge : prénom, nom, métier/profession, âge, voiture, couleur ou boisson préférée. Elle ne transforme pas une déclaration en question. Un fait explicitement attribué à un proche n’est plus utilisé comme s’il concernait le propriétaire. Les libellés français de secrets tels que « mot de passe » et « code PIN » sont exclus de la lecture conversationnelle automatique.

**Google Maps et Chrome** peuvent être ouverts par commande écrite ou vocale. Exemples : « Ouvre Google Maps à Lyon », « Prépare un itinéraire vers Lyon », « Cherche sur Google musée Lyon », « Ouvre Chrome » ou « Ouvre dans Chrome https://example.com ». Nexus privilégie l’application nommée puis laisse Android proposer une application compatible si elle est indisponible. Une absence d’application est signalée sans arrêter l’application sur une erreur.

« Itinéraire vers chez moi » et « Ouvre Google Maps pour mon travail » peuvent exploiter une note personnelle explicite. Utiliser un titre **Adresse domicile** ou **Adresse travail**, avec une seule adresse dans le contenu, ou un champ complet tel que **Adresse domicile : 12 rue Exemple, Lyon**. Nexus annonce la destination et demande confirmation avant de transmettre ce champ à Maps. Plusieurs adresses différentes demandent une précision ; une adresse supprimée, modifiée ou expirée invalide la proposition. Les articles importés ne définissent pas le domicile ou le travail.

L’interrupteur **Accès en ligne** s’applique à ces ouvertures. La répétition d’une réponse n’ouvre rien de nouveau. Le micro et le mouvement virtuel sont arrêtés lors de la transmission à une autre application ; un résultat devenu périmé après un passage en arrière-plan n’est pas exécuté. Les itinéraires ouvrent une préparation de trajet sans lancer la navigation. Nexus ne demande ni permission GPS ni accès à l’historique ou aux comptes Google. La lecture optionnelle des Aperçus IA dans Chrome est décrite ci-dessous ; aucune page n’est importée automatiquement dans la mémoire. L’apprentissage par recherche Wikipédia et enregistrement confirmé reste disponible séparément.

## Relances et correction des notes conservées depuis 0.4.18

Après une réponse, « répète ta réponse » relit le texte sans relancer une recherche, une sauvegarde ni un mouvement. Une proposition en attente garde sa date d’expiration initiale. « Et mon prénom ? », « Et pour mon métier ? » et quelques autres questions personnelles courtes utilisent le même interpréteur que les questions complètes ; les déclarations ne sont pas transformées en questions.

Quand la dernière réponse cite une seule note, « lis cette note » en lit le contenu complet et « modifie cette note » ouvre son éditeur. Sans référence unique, Nexus demande le sujet. Le contexte est temporaire : cinq minutes, renouvelées après la fin effective d’une lecture de réponse sans confirmation en attente. Une lecture d’acquittement du micro ne le renouvelle pas. Une note modifiée, supprimée ou expirée ne peut pas être répétée comme une information actuelle. Les dialogues privés et l’arrière-plan effacent ce contexte.

Dans **Mes notes**, ouvrir une note puis **Modifier** permet de relire son titre et tout son contenu avant **Enregistrer**. Une correction crée une nouvelle version et conserve les anciennes dans le même stockage chiffré. Seule la version actuelle alimente les réponses et la recherche. **Versions précédentes** permet de relire un ancien contenu et de le restaurer après une seconde confirmation ; la restauration conserve aussi la version qu’elle remplace. Une modification concurrente empêche d’écraser un contenu devenu plus récent. **Supprimer** annonce puis efface toute la chaîne de versions après confirmation.

La source et l’expiration d’origine sont conservées. Une note corrigée est marquée comme modifiée et non vérifiée, y compris si elle provenait initialement de Wikipédia. Le schéma JSON 1 reste lu ; une correction avec historique utilise le schéma 2. Le format GZIP/AES-GCM, le fichier et la clé Keystore ne changent pas. La bibliothèque accepte au plus **20 000 notes actuelles et 40 000 enregistrements au total**, anciennes versions comprises, toujours dans les **32 Mio avant compression**. Une limite atteinte refuse l’écriture sans supprimer l’original.

## Mémoire, lecture vocale et capacités conservées

« Nexus, lis ma note sur le robot » sélectionne une note sur le sujet et lit son texte complet. Cette lecture est explicite, hors ligne et réservée aux notes utilisables par la conversation ; elle ne remplace pas la relecture manuelle des informations sensibles. Sans sujet correspondant, Nexus demande une précision au lieu de prendre une note récente au hasard. Le rappel ordinaire reste un aperçu court.

Les longues réponses sont réparties sans perte entre des appels compatibles avec la limite du moteur Android. Les séparations privilégient les phrases et espaces et préservent les paires UTF-16. Le micro demeure fermé jusqu’au dernier morceau ; la fenêtre de conversation commence ensuite. Une erreur, l’arrêt, la désactivation de la voix, une interruption ou l’arrière-plan annulent les morceaux restants. La lecture garde son focus audio entre les morceaux. Chaque morceau et la lecture entière disposent d’un délai de récupération.

La recherche utilise un index compact d’empreintes de mots en mémoire et un cache de huit classements. Chaque correspondance est vérifiée contre le texte original : les collisions d’empreintes ne constituent jamais une preuve. Les classements sont réutilisés uniquement pour le snapshot authentifié courant ; l’expiration est recalculée à chaque appel. Une écriture, une suppression ou une modification externe invalide cet index. Rien de cet index ni des requêtes n’est ajouté au stockage.

Le titre d’une note ne peut plus transformer une phrase sans rapport en réponse à une question. Un titre peut toujours qualifier une valeur courte explicite, par exemple « Couleur préférée : bleu ». Les extraits gardent leur provenance et leur statut non vérifié.

| Capacité | 0.4.16 | 0.4.17 |
| --- | --- | --- |
| Notes locales | 5 000 | 20 000 |
| Texte par note / message / transcription | 8 000 caractères | 16 000 caractères |
| Notes candidates par tour | 16 | 32 |
| Résultats de recherche dans les notes | 20 | 100 |
| Requête native, échappement compris | 160 000 caractères | 384 000 caractères |
| Début de réponse sans nouveau mot d’activation | 20 s | 30 s |
| Dictée continue maximale | 118 s | 178 s |
| Proposition en attente de confirmation | 180 s | 300 s |
| Budget du moteur par appel | 120 millions d’instructions / 5 s | 480 millions / 5 s |
| Lecture vocale par réponse | Appel unique limité par Android et 90 s | 20 000 caractères, morceaux ≤ 1 000 caractères et limite moteur ; récupération globale à 20 min |

Le budget total de mémoire reste de **32 Mio de JSON avant compression**, indépendamment du nombre de notes, pour borner les allocations sur téléphone. Les plafonds de nombre et de volume s’appliquent ensemble. Le contexte conserve les premières notes classées qui tiennent dans son budget JSON, sans tronquer les notes stockées. Il est construit sans sérialiser plusieurs fois un contexte trop grand. Ces capacités ne constituent pas une mesure de précision acoustique et ne transforment pas les règles locales en modèle génératif.

`packages/companion-core/src/native-capacity.json` reste la source commune du moteur, des constantes Kotlin, de la limite de saisie et du message d’erreur correspondant. Leurs empreintes sont scellées avec le bundle ; les fichiers désynchronisés sont refusés.

Le correctif de l’importeur 0.4.16 est conservé : APK jusqu’à 512 Mio, cache jusqu’à 1 Gio, dédoublonnage, suppression des versions installées, nettoyage ciblé et erreurs de disque distinctes. La signature, la version croissante, la lecture seule et l’intégrité sont vérifiées. Les permissions, le chiffrement, les limites de mouvement et les arrêts restent actifs.

Depuis 0.4.14/0.4.15 affichant l’erreur de cache, ouvrir l’APK téléchargé dans **Mes fichiers → Téléchargements**, puis confirmer **Mettre à jour**. Conserver Nexus installé pour garder ses notes. Le nouvel écran intégré peut ensuite gérer les mises à jour suivantes.

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

Le script valide la configuration avant de compiler, nettoie les sorties de construction, exécute les tests et Android Lint, vérifie la signature et produit les APK dans `apk-dist/0.4.22-native-alpha/` avec `SHA256SUMS.txt`. Le fichier de sommes référence les APK produits par cette exécution.

Le script optionnel `scripts/build-android-apk-vercel.sh` prépare les outils Linux et appelle le même script. Aucun workflow GitHub Actions n'est nécessaire. Ce bootstrap optionnel n'a pas été validé dans l'environnement Vercel dans cette livraison.

Pour installer une mise à jour, l’application doit garder `fr.nexuscompanion.android`, utiliser le même certificat et augmenter `versionCode` (28 pour cette version). L’installation demande la confirmation Android. Installe l’APK signé par-dessus l’application principale existante pour conserver les notes et la clé Keystore associée.

Distribuer uniquement `nexus-companion-v0.4.22-native-alpha-release.apk`, signé avec la clé permanente du paquet principal.

### Français hors ligne

Gradle récupère `vosk-model-small-fr-0.22`, vérifie sa taille et son SHA-256, puis l’inclut dans l’APK. Nexus le prépare dans l’espace privé lors du premier démarrage du micro. Cette préparation est locale et peut durer quelques secondes. La lecture des réponses utilise toujours la voix française installée sur Android.

### Installation depuis l’application

Après l'installation de 0.4.0, les prochains APK peuvent être sélectionnés dans **Mises à jour → Choisir un APK**. L'écran affiche la version vérifiée ; **Installer cette version** ouvre les réglages d'autorisation si nécessaire. Au retour, un nouveau tap est obligatoire : aucune installation automatique. Android vérifie ensuite le paquet et demande sa confirmation. La validation locale ne remplace pas la vérification cryptographique finale effectuée par Android.

La permission spéciale `REQUEST_INSTALL_PACKAGES` permet ce passage à l'installateur ; elle ne donne pas le droit de modifier les notes ni de procéder à une installation silencieuse. `INTERNET`, les autorisations générales de stockage et la désinstallation ne sont pas utilisés par ce parcours. L'APK doit être un paquet autonome, pas une archive XAPK ou un ensemble de splits. La rotation de clé est refusée par la comparaison stricte des signataires ; elle nécessiterait une migration distincte.

Le cache privé ne contient que des copies APK, limitées à 512 Mio par fichier et 1 Gio au total. Leur SHA-256 est recalculé après une recréation de l'écran et avant la transmission. Les fichiers non transmis sont supprimés à la fermeture ; ceux transmis à Android restent lisibles pour l'installation, puis sont supprimés à l'import suivant une fois âgés de 24 heures. Le fournisseur non exporté expose uniquement `cache/updates/` et interdit l'écriture, sans accès aux notes.

## Limites et prochaine validation

Le premier pont vers les règles TypeScript est intégré. Le cerveau cognitif complet persistant, l'identité native, l'avatar web complet, l'export/import Android chiffré et la recherche en ligne de nouvelles versions restent à intégrer.

Voir les rapports de validation [0.4.22](../../docs/releases/ANDROID-0.4.22-2026-11-12.md), [0.4.21](../../docs/releases/ANDROID-0.4.21-2026-11-12.md), [0.4.20](../../docs/releases/ANDROID-0.4.20-2026-11-12.md), [0.4.19](../../docs/releases/ANDROID-0.4.19-2026-11-12.md), [0.4.18](../../docs/releases/ANDROID-0.4.18-2026-11-12.md), [0.4.17](../../docs/releases/ANDROID-0.4.17-2026-11-12.md), [0.4.16](../../docs/releases/ANDROID-0.4.16-2026-11-12.md), [0.4.14](../../docs/releases/ANDROID-0.4.14-2026-11-11.md), [0.4.13](../../docs/releases/ANDROID-0.4.13-2026-11-11.md), [0.4.12](../../docs/releases/ANDROID-0.4.12-2026-11-08.md), [0.4.11](../../docs/releases/ANDROID-0.4.11-2026-11-08.md), [0.4.10](../../docs/releases/ANDROID-0.4.10-2026-11-08.md), [0.4.9](../../docs/releases/ANDROID-0.4.9-2026-11-08.md), [0.4.8](../../docs/releases/ANDROID-0.4.8-2026-11-08.md), [0.4.7](../../docs/releases/ANDROID-0.4.7-2026-11-07.md), [0.4.6](../../docs/releases/ANDROID-0.4.6-2026-11-07.md), [0.4.5](../../docs/releases/ANDROID-0.4.5-2026-11-07.md) et [0.4.4](../../docs/releases/ANDROID-0.4.4-2026-11-07.md). Les tests exécutent le bundle partagé dans Rhino, l’interface et le câblage vocal dans Robolectric, et la persistance avec AES-GCM de JVM. Les tests Android simulent la source micro. Cette livraison ne mesure pas la précision du modèle sur des enregistrements acoustiques réels. Le microphone, le Bluetooth et l’autonomie restent à vérifier sur téléphone.

## Développement du moteur partagé

Les assets JavaScript et leur manifeste SHA-256 sont inclus. Gradle vérifie qu'ils correspondent aux sources du dépôt complet avant de compiler. Après une modification du moteur TypeScript ou de son compilateur, exécuter depuis la racine :

```bash
npm ci
npm run build:native-brain
npm run check:native-brain
```

Voir la [décision d'architecture](../../docs/architecture/ADR-0002-android-shared-brain.md). Une conversation ne sauvegarde pas automatiquement des informations : les propositions courtes et non sensibles se confirment à la voix, et le bouton « Retenir cette information » garde la relecture manuelle. Les propositions sensibles nécessitent cette relecture manuelle. Le moteur assemble des phrases à partir des extraits pertinents ; ce n’est pas un modèle génératif généraliste. Dans « Mes notes », saisir d'abord un sujet dans le champ texte pour rechercher ; sans sujet, toutes les notes sont accessibles par pages de cinquante avec les boutons Suivantes et Précédentes.

Le contrat de corps roulant et les choix de mémoire/voix sont décrits dans [ADR-0003](../../docs/architecture/ADR-0003-wheeled-body-and-native-memory.md).
