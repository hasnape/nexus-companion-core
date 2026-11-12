# Nexus Companion Android 0.4.21

Cette mise à jour de l’application principale corrige les demandes vocales de recherche et la pertinence des extraits Wikipédia. Elle conserve le certificat permanent, les données locales, les fonctions vocales et les modes plein visage et corps roulant.

## Recherches et compréhension

| Demande | Comportement |
| --- | --- |
| « Cherche sur Internet les réponses de salutations » | Recherche Web ouverte dans Chrome ou un navigateur compatible, avec le sujet entendu. |
| « Cherche sur Wikipédia les réponses de salutations » | Recherche intégrée avec sélection d’un extrait pertinent et lien vers l’article. |
| « Cher sur interdit que répondre bonjour » | Proposition à confirmer ; aucune recherche n’est exécutée avant un « oui » explicite. |
| « Cherche sur Internet », sans sujet | Reprise de la dernière question publique de la session, sinon demande du sujet. |
| « Que répondre à bonjour ? » | Exemples pratiques locaux, sans lecture d’un article encyclopédique. |

Les variantes « recherche sur le Web », « cherche en ligne » et les demandes polies prises en charge suivent la même distinction. Internet n’est plus présenté comme un autre nom de Wikipédia. Les pages Web ouvertes dans le navigateur ne sont pas lues ni importées automatiquement par Nexus. La recherche intégrée et l’apprentissage par enregistrement confirmé restent disponibles avec Wikipédia.

Le sujet manquant peut être donné à l’échange suivant. Le contexte public provient seulement d’une question de l’utilisateur ou d’un sujet de recherche explicite, jamais du contenu des notes ou d’une réponse externe. Il reste transitoire, expire et disparaît en arrière-plan. Les contrôles de coordonnées, d’adresses, de secrets et de formulations personnelles restent appliqués. Le refus, le changement de sujet, la désactivation d’Internet et l’expiration empêchent une ancienne proposition de déclencher une recherche. Une répétition n’exécute pas à nouveau l’ouverture du navigateur.

Quelques formulations pratiques de salutations et de remerciements reçoivent une réponse locale courte. L’analyse commune reconnaît aussi les questions sans ponctuation commençant par « que/quoi faire, dire, répondre, choisir ou utiliser ». Une demande explicite de recherche conserve son effet. Les règles existantes de rappel et de composition des faits mémorisés restent prioritaires et inchangées.

## Parole avec une courte pause

Après un résultat final qui contient seulement un début de recherche reconnu, par exemple « Nexus cherche sur Internet », la capture attend jusqu’à **1,5 seconde pour que la suite commence**. Les segments sont ensuite réunis sans remplacer les mots reconnus. Une nouvelle activation, un arrêt et un refus restent séparés ; un « oui » ne devient pas une approbation autonome. Sans continuation, le préfixe est transmis pour clarification ou reprise du sujet public. Une continuation sans texte exploitable provoque le traitement d’un silence au lieu de rechercher un ancien sujet.

L’arrêt de l’écoute annule aussi ce préfixe en attente. Le micro et le décodeur sont toujours libérés avant le résultat final. Le modèle Vosk, le décodage principal, les seuils de confirmation et le délai maximal de dictée restent identiques. Il s’agit d’une amélioration de segmentation et de compréhension de commandes, sans mesure d’un gain de précision phonétique.

## Réponses Wikipédia

La requête retire quelques mots de formulation et normalise des termes français, puis examine jusqu’à cinq résultats. Les pages d’homonymie, les extraits vides et les résultats trop éloignés du sujet sont écartés. Une description de film, de roman ou d’autre média ne suffit plus à répondre à une demande générale sur les salutations. Les mentions factuelles comme les voitures « de série » ne sont pas rejetées comme des séries télévisées.

La réponse choisit une ou deux phrases d’origine, au plus **650 caractères**, en favorisant une phrase chiffrée pour une question « combien ». Elle n’invente pas les faits absents de l’extrait. Le classement reste lexical et ne garantit pas la résolution de toute question. Un résultat insuffisant appelle une précision et propose explicitement d’élargir au Web. L’extrait d’origine reste la preuve proposée à l’enregistrement avec son article source, uniquement après confirmation.

La requête respecte la borne documentée de **1 200 caractères demandés par extrait**, avec une réponse HTTP limitée à 48 000 caractères. Aucun service génératif, compte, clé API, accès à l’historique Chrome ni dépendance supplémentaire n’est ajouté.

## Vérifications exécutées

- **413 tests TypeScript**, contrôle de types, ESLint, compilation de tous les workspaces et concordance du bundle partagé réussis.
- **303 tests Android dans chacune des variantes debug et release**, sur 29 suites, sans échec, erreur ni test ignoré ; **zéro anomalie Android Lint** pour les deux variantes.
- Les dix premiers scénarios de compréhension ajoutés échouaient huit fois sur 0.4.20 ; ils passent après correction. Le scénario de commande interrompue par un point final acoustique échoue également sur l’ancienne capture et passe sur la nouvelle.
- Les nouveaux tests vérifient les deux destinations de recherche, l’intent Android et son sujet, la confirmation des transcriptions incertaines, les délais, le contexte public, le mode hors ligne, l’absence de réouverture par répétition, la séparation des confirmations et l’annulation pendant une capture interrompue.
- Les tests de pertinence rejettent le film « Notre salut », trouvent un résultat pertinent après une page d’homonymie, choisissent la phrase chiffrée demandée et conservent les bornes Unicode.
- Une requête réelle à l’API Wikipédia a renvoyé cinq pages sur les salutations. Le code Kotlin compilé sélectionne « Salutation » et donne un score nul à « Notre salut » ; l’extrait sélectionné compte 201 caractères. Ce contrôle concerne la sélection de texte, pas un test audio.
- Les scénarios existants de mémoire et d’historique, chiffrement, compression, voix, pauses du micro, plein visage, pupilles, corps roulant et installation restent passants.

Aucun téléphone ni châssis physique n’est connecté. Les tests Android simulent les échantillons et les résultats du micro. La précision acoustique réelle, la qualité sonore, le Bluetooth, l’autonomie et les applications effectivement installées restent à vérifier sur appareil. Cette livraison ne constitue pas un nouveau modèle de langage généraliste.

## APK principal et installation

Paquet **`fr.nexuscompanion.android`**, version **`0.4.21-native-alpha`**, code **27**, Android minimum 31, cible 35. Taille : **62 529 551 octets**, sous 64 Mio pour les anciens importeurs de mise à jour. Signature v2 valide et certificat permanent identique à 0.4.20 ; ZIP intègre et alignement 16 Kio vérifié.

Les permissions, les quatre ABI, les huit bibliothèques natives et le modèle français sont identiques à 0.4.20. Les **33 305 octets** des notices tierces sont inchangés. Les ressources compilées conservent la propriété du projet par Amine HARBI, l’édition par Rép&Web et la distinction avec les droits des tiers. Le format de stockage, les clés de chiffrement, les capacités de mémoire et la stratégie de compression ne changent pas.

Arbre source compilé : `f39adf167c131edc14ca1b6d08b0dd79ed3eb7b9`. Seuls le numéro de code dans le README Android et ce compte rendu sont actualisés après compilation. Le bundle embarqué compte **118 039 octets**, son SHA-256 est `cc4b81c69a91a6e5d1113dbca13bd6330c6881e100e44f8bb83dbf32b63ceb2d` et les 22 empreintes de son manifeste concordent avec les sources compilées et livrées.

```text
794796a83e6136eecdb2c22d10d3cbadb2a0df878bee467fbeba50cf9c5f0eb4  nexus-companion-v0.4.21-native-alpha-release.apk
```

Installer **par-dessus l’application principale, sans désinstallation**, pour conserver les notes et la clé Keystore. Les versions intermédiaires ne sont pas nécessaires. Si un ancien importeur signale une erreur de cache, ouvrir l’APK depuis **Mes fichiers → Téléchargements**, puis confirmer **Mettre à jour**.
