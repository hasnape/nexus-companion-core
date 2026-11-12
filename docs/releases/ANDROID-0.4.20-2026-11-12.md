# Nexus Companion Android 0.4.20

Cette version clarifie la propriété du projet et corrige des incohérences de compréhension et d’ouverture des applications. Elle reste une mise à jour de l’application principale, avec le même certificat et sans effacement des notes.

## Propriété et licences dans « À propos »

Le texte affiche **Propriété du projet : Amine HARBI**, **Éditeur : Rép&Web** et **© 2026 Amine HARBI — Rép&Web**. La réserve de droits est explicitement limitée aux éléments originaux propres au projet, sous réserve des droits des tiers. Le document [OWNERSHIP.md](../../OWNERSHIP.md) reprend cette attribution.

Les dépendances sont présentées sous **Composants tiers et licences**. Le titre général « Nexus Companion — logiciels libres » est remplacé par un titre décrivant ces composants ; tous les octets du corps des notices de licence et de leurs crédits restent identiques à 0.4.19. La présentation de l’application et l’attribution du projet sont distinctes des auteurs, droits et licences de Rhino, Vosk et des autres dépendances. Aucune revendication de marque déposée n’est ajoutée.

## Cohérence des faits mémorisés

Sur 0.4.19, « Mon prénom est Amine » et « Je m’appelle Amine » pouvaient déclencher « Laquelle est à jour ? ». Le même problème concernait des formulations explicites de métier et de boisson préférée. Le moteur compare désormais la valeur après quelques prédicats équivalents reconnus. La négation, les qualificatifs et le reste de la valeur ne sont pas supprimés pour forcer une égalité. Les formulations non reconnues conservent leur comparaison littérale.

Deux valeurs différentes d’un même attribut explicite peuvent être signalées dans une seule note, avec un identifiant de preuve unique. Cette note reste accessible à la commande d’édition existante. Les explications complémentaires, par exemple les raisons d’une préférence, et les objectifs d’une note ne sont pas déclarés contradictoires par cette règle. Le choix de réponse conserve sa provenance et son statut modifié éventuel. Aucune fusion ni écriture de notes n’est déclenchée par cette comparaison.

Six des sept scénarios initiaux ajoutés échouent sur le code 0.4.19 et passent après correction. Deux cas complémentaires ont permis de vérifier que les nuances d’explication et d’objectif sont conservées.

## Ouverture de Chrome et Maps

Le retour de l’adaptateur distingue maintenant l’ouverture acceptée par l’application nommée, l’ouverture par une application de repli et l’indisponibilité complète. L’interface annonce le repli au lieu de conserver « J’ouvre Chrome » ou « J’ouvre Google Maps » lorsque cette application n’a pas pu être ouverte. Une erreur laisse la conversation utilisable. Une répétition ne réexécute pas la demande.

Le repli reprend uniquement le même URI vérifié, sans nouvelle requête ni nouvel accès aux notes. Les confirmations des adresses mémorisées, l’interrupteur d’accès en ligne, la suppression des actions périmées après un changement de cycle de vie et les arrêts du micro et du corps sont conservés. Aucun accès aux comptes ou à l’historique de Chrome n’est ajouté.

## Validation et paquet

- **401 tests TypeScript**, contrôle de types, ESLint, compilation de tous les workspaces et concordance du bundle réussis.
- **287 tests Android dans chacune des variantes debug et release**, sans échec, erreur ni test ignoré ; **zéro anomalie Android Lint**.
- Les tests couvrent les formulations équivalentes, les valeurs différentes d’une même note, les négations, les qualificatifs, les explications complémentaires, les objectifs, la provenance et les questions composées.
- Le bundle Rhino embarqué reconnaît les faits équivalents et conserve l’édition d’une note unique contenant deux valeurs à clarifier, sans écrire dans la bibliothèque.
- Les tests de l’adaptateur et de l’interface vérifient les trois résultats d’ouverture, les données de l’intent, l’absence de réouverture par répétition et la poursuite de la conversation après indisponibilité.
- Les scénarios existants de voix, pauses du micro, notes et historique, chiffrement, compression, plein visage, pupilles, corps roulant et mise à jour restent passants.
- La mention de propriété est vérifiée dans les ressources de l’APK compilé. Après la première ligne descriptive, les **33 256 octets** de notices tierces restent identiques à ceux de l’APK 0.4.19.

Les tests Android utilisent Rhino et Robolectric avec des sources audio simulées. Aucun téléphone ni châssis physique n’est connecté : le son réel, la précision acoustique, les applications installées, le Bluetooth, l’autonomie et les mouvements matériels ne sont pas mesurés.

APK principal : `fr.nexuscompanion.android`, version `0.4.20-native-alpha`, code **26**, Android minimum 31, cible 35. **62 512 767 octets**, sous 64 Mio. Signature v2 valide et certificat permanent identique à 0.4.19 ; ZIP intègre et alignement 16 Kio vérifié. Permissions, quatre ABI, huit bibliothèques natives et modèle français identiques à 0.4.19.

Arbre source compilé : `681b9591351e9189d9e33ead6ebad4b952d80200`. Seuls les deux README et ce compte rendu sont actualisés après cette compilation. `OWNERSHIP.md` est déjà inclus dans l’arbre source compilé. Le bundle embarqué a pour SHA-256 `9e6f50fbf9aa922f8f739ba7f2384ece5bcb6b289a3f9e6c7a12b9398468d8d2`.

```text
506a41abbb20a6089c4fc048e4e4eb748098cea03ddda5b28a778209632100c8  nexus-companion-v0.4.20-native-alpha-release.apk
```

Installer par-dessus l’application principale, **sans désinstallation**, afin de conserver les notes et la clé Keystore. Les versions intermédiaires 0.4.18 et 0.4.19 ne sont pas nécessaires. Si un ancien importeur affiche une erreur de cache, ouvrir l’APK dans **Mes fichiers → Téléchargements**, puis confirmer **Mettre à jour**.
