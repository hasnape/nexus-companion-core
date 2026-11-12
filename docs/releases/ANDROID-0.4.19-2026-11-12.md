# Nexus Companion Android 0.4.19

Cette version concentre le développement sur la compréhension des données mémorisées et leur utilisation explicite avec Google Maps et Chrome. Le moteur reste local et extractif. Le stockage, l’historique chiffré, les limites de capacité, le modèle français et les modes visage/corps sont conservés.

## Compréhension et utilisation des notes

Sur 0.4.18, une question demandant le prénom et le métier ne réunissait pas les preuves présentes dans deux notes distinctes. Des informations attribuées à un proche pouvaient aussi être utilisées pour répondre à une question sur le propriétaire. Les nouveaux tests reproduisent ces limites sur le code précédent : cinq des six scénarios initiaux échouent avant le correctif.

Nexus décompose désormais les conjonctions explicites de deux à quatre attributs personnels pris en charge : prénom, nom, métier/profession, âge, voiture, couleur et boisson préférées. « Quel est mon prénom et mon métier ? » rassemble les réponses fondées sur les notes pertinentes. Chaque partie garde sa provenance et ses identifiants de preuve. Une information manquante est signalée ; un conflit entre notes conserve la question de clarification. La composition ne crée pas de nouvelle note et n’autorise aucune recherche externe. Une déclaration de plusieurs faits conserve son traitement de proposition à confirmer.

Un prénom explicitement attribué à la fille de l’utilisateur, par exemple, n’est plus utilisé pour répondre au prénom du propriétaire. Le contrôle couvre les marqueurs de proches pris en charge dans les phrases et les titres. Le filtre des notes sensibles reconnaît également les expressions françaises « mot de passe », « code PIN », « code de validation », « clé API » et « jeton d’accès ». Une note contenant ces secrets reste accessible à la consultation manuelle et ne devient pas une lecture conversationnelle automatique.

## Google Maps et Chrome

Le routeur Android accepte uniquement des commandes explicites de l’utilisateur. Les notes et articles ne lui sont jamais présentés comme des commandes. Exemples :

| Demande | Comportement |
| --- | --- |
| Ouvre Chrome | Ouvrir Google dans Chrome en priorité |
| Cherche sur Google musée Lyon | Ouvrir la recherche de ce texte dans Chrome en priorité |
| Ouvre dans Chrome https://example.com | Ouvrir cette page HTTPS |
| Ouvre Google Maps à Lyon | Afficher la recherche du lieu |
| Prépare un itinéraire vers Lyon | Afficher la préparation du trajet |
| Itinéraire vers chez moi | Retrouver une adresse personnelle explicite, l’annoncer puis demander confirmation |
| Ouvre Google Maps pour mon travail | Même contrôle pour l’adresse professionnelle |

Les commandes se prononcent aussi après le mot d’activation ou pendant la fenêtre de conversation. Les formes polies prises en charge acceptent l’infinitif « Peux-tu ouvrir… ». Si l’application nommée manque, Android peut utiliser une application compatible. Si aucun gestionnaire n’est disponible ou autorisé, Nexus affiche une erreur sans planter. L’interrupteur **Accès en ligne** bloque les ouvertures lorsqu’il est désactivé.

Les destinations saisies dans la demande sont les seules données transmises pour les commandes directes. Les liens Maps sont construits vers l’hôte fixe de Google avec encodage des paramètres, `api=1` et une limite de 2 048 caractères. Les itinéraires ne fournissent pas de position d’origine ni d’instruction de démarrage automatique de navigation. Les pages Chrome acceptent HTTPS sans identifiants intégrés. Les schémas `intent:`, `javascript:` et `file:` ne peuvent pas devenir des actions. Les cibles vides, trop longues, contenant des contrôles ou des libellés de secrets sont refusées.

### Adresses issues de la mémoire

Le résolveur utilise uniquement les versions actives et non expirées de notes d’origine `user:manual`. Il accepte une adresse dans un champ explicite tel que « Adresse domicile : … », ou une note intitulée « Adresse domicile » / « Adresse travail » dont le contenu est une seule adresse. Il ne déduit pas le domicile d’un article ni d’une phrase générale sur une ville.

Le champ exact est annoncé avant un « oui » explicite. Plusieurs notes donnant la même adresse partagent leurs preuves ; plusieurs adresses différentes demandent une précision. À la confirmation, les enregistrements sont relus et comparés entièrement, expiration comprise. Une correction, suppression, erreur de lecture, expiration, annulation, désactivation d’Internet ou effacement du contexte empêche la transmission. La répétition ne prolonge pas la confirmation et ne relance pas une application. La bibliothèque n’est pas modifiée par l’ouverture.

Le résultat du worker ne peut ouvrir une application que si l’écran est toujours actif et que sa génération n’a pas changé. Le passage en arrière-plan et le retour à l’écran ne réactivent pas une ancienne demande. La transmission arrête le mouvement virtuel et le micro. Le contexte est effacé ; la reprise vocale au retour suit la préférence existante.

Il s’agit d’ouvertures explicites dans d’autres applications. Nexus ne lit pas les comptes, l’historique, les favoris ou les résultats de Chrome et ne les importe pas automatiquement. L’apprentissage local à partir d’un extrait Wikipédia approuvé reste distinct. Aucune permission GPS, contact, stockage ou inventaire général des applications n’est ajoutée. Aucune clé d’API Google n’est nécessaire.

## Références techniques

- [Google Maps URLs](https://developers.google.com/maps/documentation/urls/get-started) : actions, encodage et paramètres.
- [Android — Sending the user to another app](https://developer.android.com/training/basics/intents/sending) : `ACTION_VIEW` et gestion des applications compatibles.

## Validation et paquet

- **284 tests Android dans chacune des variantes debug et release**, sans échec, erreur ni test ignoré ; **zéro anomalie Android Lint**.
- **392 tests TypeScript**, contrôle de types, ESLint, compilation de tous les workspaces et concordance du bundle réussis.
- Le moteur Rhino embarqué compose les réponses depuis plusieurs notes et garde les preuves distinctes. Les tests vérifient les attributs manquants, les contradictions, la provenance modifiée, les notes expirées et les libellés français de secrets.
- Les tests vérifient les URL et paramètres encodés, les schémas refusés, la préférence d’application et les replis en cas d’indisponibilité ou de refus Android. L’interface est testée pour l’arrêt du corps, l’absence d’ouverture avant confirmation, l’interrupteur hors ligne et l’annulation après un cycle arrière-plan/reprise.
- Les adresses mémorisées sont testées pour la confirmation, la répétition sans nouvelle action, l’expiration sans renouvellement, l’annulation, l’effacement de contexte, la correction, la suppression et l’ambiguïté. Un texte de note contenant une commande n’entraîne aucune ouverture.
- Les scénarios existants de lecture vocale découpée, pauses du micro, notes et restauration, pupilles, plein visage, corps roulant, chiffrement, compression et mises à jour restent passants.

Les contrôles Android utilisent Robolectric, Rhino et des sources audio simulées. Aucun téléphone ni châssis physique n’est connecté : le son réel, la précision acoustique, les applications Google effectivement installées, le GPS, le Bluetooth et l’autonomie ne sont pas mesurés sur matériel.

APK principal : `fr.nexuscompanion.android`, version `0.4.19-native-alpha`, code **25**, Android minimum 31, cible 35. **62 510 311 octets**, sous 64 Mio. Signature v2 valide et certificat permanent identique à 0.4.18 ; ZIP intègre et alignement 16 Kio vérifié. Les permissions, quatre ABI, huit bibliothèques natives, le modèle français et les notices sont identiques à 0.4.18.

Arbre source compilé : `33cc3cac00e8333181163157b4a394e4cdba3c84`. Seuls les deux README et ce compte rendu sont actualisés après cette compilation. Le bundle embarqué a pour SHA-256 `c41c8991cf139d697e7994b1f11c8ac6a0eec8a150c6aa71c9543b3de25f992a`.

```text
4748968056784aca9d7335bb2f5ab57ec0c04a04b519ba0efdba36d35eff6c06  nexus-companion-v0.4.19-native-alpha-release.apk
```

Installer par-dessus l’application principale, **sans désinstallation**, afin de conserver les notes et la clé Keystore. Si un ancien importeur affiche une erreur de cache, ouvrir l’APK dans **Mes fichiers → Téléchargements**, puis confirmer **Mettre à jour**.
