# Nexus Companion Android 0.4.23

Cette version corrige l’utilisation des notes et ajoute une consolidation locale automatique, un budget adapté au téléphone et un accès personnel par code utilisateur. C’est l’application principale, avec son certificat permanent, son modèle vocal, ses modes plein visage/corps et ses notes existantes.

## Résultat pour le propriétaire

- « Comment je m’appelle ? » rappelle uniquement le prénom ; « Où est-ce que j’habite ? » rappelle uniquement la ville. Une note contenant un nom complet ou une adresse n’est pas lue intégralement à cette occasion.
- Le métier, les préférences, la famille, le nom complet, l’adresse et la gestion des notes demandent le code utilisateur. Le code est à créer dans **Réglages → Code utilisateur et accès personnel** ; aucun code par défaut n’est fourni et le code créateur reste distinct.
- Une ouverture valide reprend la question initiale. L’accès se ferme après cinq minutes ou dès que Nexus passe en arrière-plan. Le texte personnel, sa source et sa lecture vocale sont alors fermés. Le bouton **Verrouiller les informations personnelles** permet de fermer l’accès immédiatement.
- « Qui est le créateur ? » recherche une identité explicite et rappelle le prénom, au lieu de répondre avec une préférence récente contenant le mot « créateur ».
- « Que répondre à bonjour ? » produit une réponse pratique, même lorsqu’une note encyclopédique sur les salutations existe.
- Un titre ambigu comme « la note solution » déclenche une demande de précision avec des suggestions utilisables. Nexus ne transforme pas silencieusement « solution » en « salutation ».
- « Qu’est-ce que ta mémoire interne ? » explique la bibliothèque locale, le contexte temporaire et le type d’apprentissage réellement présent.

La récupération ou le remplacement d’un code existant utilise la vérification du verrouillage Android. Une panne de lecture de la protection ferme l’accès et conserve les notes. La saisie d’un code dans le dialogue vocal, une note ou le navigateur ne déverrouille rien.

## Apprentissage et matériel

La consolidation est activée par défaut et désactivable dans **Matériel et apprentissage**. Elle traite les notes déjà enregistrées et confirmées, construit des associations de recherche et vérifie des exercices de rappel. Les associations servent à retrouver le contexte ; elles ne peuvent pas remplacer une correspondance directe par une masse de notes vaguement liées. Une suppression, une modification ou une expiration invalide les associations concernées avant utilisation.

Le cache dérivé est compressé et chiffré. Il ne réécrit pas les originaux, ne marque pas les sources comme vérifiées et ne réécrit pas un index déjà complet à chaque passage au repos. Un cache endommagé reste reconstructible sans effacer la mémoire primaire. Une réussite aux exercices mesure le rappel de la source et ne garantit pas la vérité de celle-ci.

Nexus mesure CPU logiques, RAM totale/disponible, tas autorisé, stockage libre, batterie, charge, économie d’énergie et niveau thermique Android. Il choisit des budgets de contexte de **12, 48 ou 128 notes**, jusqu’à **512 000 caractères** et **8 secondes par appel du moteur partagé** sur le profil étendu. La pression sur les ressources réduit le budget et met la consolidation en pause. Aucun identifiant unique d’appareil n’est enregistré.

Au premier plan, les petits lots sont limités à trois notes après cinq secondes puis toutes les trente secondes, hors réponse, parole et dialogue. Android peut aussi exécuter des lots périodiques en charge, avec batterie et stockage suffisants. Aucun accès réseau ni enregistrement audio/vidéo n’est ajouté par ce travail automatique. Les nouvelles sources Internet demandent toujours une confirmation avant enregistrement.

Il s’agit d’apprentissage par consolidation et recherche locale, sans réentraînement de poids de modèle de langage. Aucun calcul GPU ou NPU n’est implémenté ou annoncé. L’écran matériel affiche les capacités effectivement détectées sur l’appareil où cette version est exécutée.

Voir [ADR-0003](../architecture/ADR-0003-adaptive-private-local-learning.md) pour les budgets, le code utilisateur et la provenance.

## Validation exécutée

| Contrôle | Résultat |
| --- | --- |
| TypeScript / Web | 438 tests, 51 suites ; types, ESLint et builds des workspaces réussis |
| Android debug | 349 tests ; aucun échec, erreur ou test ignoré |
| Android release | 349 tests ; aucun échec, erreur ou test ignoré |
| Régression de l’audit précédent | 35/35 scénarios dans chaque variante, session personnelle autorisée explicitement |
| Tests distincts d’accès fermé | Prénom/ville seuls, code manquant ou saisi comme texte, nom complet, métier, adresses, répétition, lecture privée, source externe |
| Interface sécurisée | Création/confirmation, mauvais code, session éphémère, retour reprenant la question, arrière-plan et verrouillage manuel |
| Protection persistante | Dérivé salé/chiffré, délai après cinq erreurs, récupération Android après dix erreurs, corruption fermant l’accès |
| Apprentissage | Index réellement utilisé, persistance, sources intactes, invalidation, absence de réécriture inutile, priorité des correspondances directes |
| Ressources | RAM/tas/CPU, batterie, chauffe, économie, stockage, contraintes de maintenance et absence de réseau requis |
| Capacité réelle du moteur | La 128e note peut fournir la réponse dans l’interpréteur Rhino embarqué |
| Android Lint | Aucune annotation dans les deux rapports de cette construction |
| Bundle partagé | 147 835 octets ; 24 empreintes de sources vérifiées |
| Paquet et signature | Application principale, code 29, certificat identique à 0.4.22, signature v2 validée |
| Compatibilité | ZIP intègre, alignement 16 Kio, taille inférieure à 64 Mio |

Les scénarios existants de voix, de confirmations, de Chrome, de notes et d’historique, chiffrement, compression, pupilles, plein visage, corps roulant et mises à jour restent passants. Les bibliothèques natives et le modèle vocal ne changent pas.

Les notes des tests sont des fixtures reconstituées, pas une copie du téléphone. Aucun téléphone n’est connecté : la reconnaissance acoustique, la restitution sonore et la consommation réelle restent à vérifier sur appareil. La détection Chrome conserve les limites documentées en 0.4.22.

Les preuves machine et les réponses aux 35 scénarios sont dans [validation 0.4.23](../validation/0.4.23/release-proof.json), [debug](../validation/0.4.23/understanding-debug.json) et [release](../validation/0.4.23/understanding-release.json).

## APK principal

- Paquet : `fr.nexuscompanion.android` ; version `0.4.23-native-alpha` ; code **29** ; Android minimum **31**, cible **35**.
- Taille : **62 662 983 octets**, sous l’ancienne limite de 64 Mio.
- Certificat SHA-256 : `01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1`, identique à 0.4.22.
- Quatre ABI, huit bibliothèques natives, modèle français et 33 305 octets de notices tierces identiques à 0.4.22. La propriété d’Amine HARBI et l’édition Rép&Web restent présentes dans les ressources compilées.
- Cerveau SHA-256 : `24baf77d88ed99e323d1c80ca40036db02810d72f765f785b9b7cc2806329d2b`.
- Arbre source compilé : `7206f72e20926f334567ca4e23ea7d031268ac57`. Seuls ce compte rendu et les preuves JSON sont ajoutés après compilation.

```text
178a6dc87633bde0c90bf70f9762f362f62bbc4d230dbcabf9ed0ad2f7622f34  nexus-companion-v0.4.23-native-alpha-release.apk
```

Installer par-dessus la version principale, sans désinstallation. Les versions intermédiaires ne sont pas nécessaires. Si un ancien importeur refuse son cache, ouvrir directement l’APK depuis **Mes fichiers → Téléchargements** et confirmer **Mettre à jour**. Le format de mémoire primaire et ses clés sont conservés.
