# Nexus Companion Android 0.4.22

Cette version ajoute la lecture d’une réponse IA affichée dans Chrome et son enregistrement local confirmé. L’application principale, son certificat, ses notes, sa voix et ses modes plein visage/corps sont conservés.

## Utilisation

1. Dans **Réglages**, active **Lire les réponses IA après une recherche Web**, puis **Configurer l’accès Chrome**. Android doit autoriser **Nexus — lecture Chrome à la demande**. L’option est désactivée par défaut ; Nexus ne s’accorde pas cet accès.
2. Avec cet accès actif, **« Cherche sur Internet les volcans »** ouvre Chrome et attend un aperçu IA pour cette recherche. **« Lis la réponse IA de Chrome »** et le bouton équivalent relisent l’onglet affiché sans lancer une nouvelle requête.
3. Lorsque le texte est accessible et stable, Nexus revient le lire dans l’application. Il annonce une **partie accessible non vérifiée**, puis propose de la retenir. Un « oui » explicite ou une confirmation par bouton enregistre le texte avec sa provenance ; aucune mémorisation n’est automatique.

Les réponses vocales doivent être activées pour entendre la lecture. L’accès en ligne reste requis pour ouvrir Chrome. Le micro et le corps virtuel sont arrêtés avant cette ouverture.

Si Android refuse le retour automatique, le résultat reste disponible pour un retour manuel dans Nexus pendant une minute. Si la réponse n’est pas accessible, copie **son texte**, reviens dans Nexus et utilise **Lire un texte copié → Lire à voix haute**. Cette solution présente le texte avant lecture et fonctionne avec l’accès en ligne désactivé. Un lien seul n’est pas téléchargé. La provenance d’un texte copié reste **origine non vérifiée**.

## Portée et traitement

La détection concerne les Aperçus IA identifiés dans Google Search, sur `google.com` ou `google.fr`, dans Chrome stable. Elle exige une adresse de recherche issue de la barre d’adresse native de Chrome. Pour une recherche lancée par Nexus, le sujet doit correspondre à celui demandé, en conservant ses caractères significatifs : `C++` et `C#` ne sont pas confondus. Un onglet « Mode IA » seul ne constitue pas une réponse. Les résultats Web ordinaires, les autres origines et les zones masquées ne sont pas repris comme un aperçu IA.

Le service est lié à Android par `BIND_ACCESSIBILITY_SERVICE` et limité au paquet `com.android.chrome`. La visibilité du paquet Chrome est déclarée pour pouvoir rouvrir son onglet courant. Il ne parcourt aucune fenêtre sans demande active. Les événements d’accessibilité reçus au repos ne lisent rien ; seule la demande explicite lance une boucle finie de 25 secondes. Aucun geste, clic, défilement ou accès au clavier n’est réalisé.

Une capture est bornée à 800 nœuds, 40 niveaux, 400 blocs et 48 000 caractères avant sélection. Les sous-arbres de saisie et de mot de passe sont exclus. Le texte conservé pour lecture ne dépasse pas la capacité actuelle d’une note, soit 16 000 caractères. Les captures restent sur un worker ; un résultat annulé, expiré ou remplacé ne peut pas être livré. Demandes et résultats sont uniquement en mémoire, consommés une fois, sans restauration après arrêt du processus.

Le texte externe entre directement dans le circuit de lecture, sans passer par les analyseurs de commandes, de recherche ou de déplacement. Même s’il contient « ouvre Google Maps » ou « avance », ces phrases sont lues comme des données. Une note importée conserve `verified=false` et sa source ; elle ne devient pas une déclaration personnelle du propriétaire.

La confirmation vocale garde son échéance de cinq minutes à partir de l’import dans Nexus. Répéter la réponse ne renouvelle pas cet accord. Le bouton de mémorisation ouvre sa propre confirmation. Refuser, changer de sujet, passer en arrière-plan ou rencontrer un échec de lecture invalide l’ancienne proposition. Une lecture refusée parce que l’accès en ligne est désactivé, ou parce que le presse-papiers est vide, ne permet plus à un « oui » ultérieur de confirmer une ancienne note.

## Validation exécutée

- **413 tests TypeScript**, contrôle de types, ESLint, compilation des workspaces et concordance du bundle partagés réussis.
- **324 tests Android dans chacune des variantes debug et release**, sur 31 suites, sans échec, erreur ni test ignoré.
- **Android Lint sans erreur**. Debug ne comporte aucune annotation. Release signale un avertissement `GradleDependency` et une information `KtxExtensionAvailable` concernant `androidx.core:core:1.15.0`, déjà épinglé dans le projet. Cette dépendance et les autres dépendances restent inchangées.
- Les tests couvrent la sélection FR/EN, l’absence de marqueur IA, les autres domaines, un ancien sujet, les interfaces en cours de génération, les bornes, les doublons et les réponses chiffrées courtes.
- Les tests de capture utilisent les nœuds Android simulés : adresse native distincte du document, faux identifiant de barre d’adresse dans une page, zones invisibles, champs de saisie et de mot de passe, interruption et expiration.
- Les scénarios d’interface vérifient le consentement initial, l’annulation du réglage, l’ouverture de l’écran Android ciblé, la réception d’un résultat, la lecture vocale d’un texte copié hors ligne et la conservation de sa provenance après confirmation.
- Le texte contenant des commandes reste sans effet d’application ou de mouvement. Une réponse confirmée reste accessible dans les notes après redémarrage. Le scénario d’ancienne confirmation après échec échoue avant la correction et passe ensuite pour Chrome hors ligne et un presse-papiers vide.
- Les scénarios existants de voix, de mémoire et d’historique, chiffrement, compression, pupilles, plein visage, corps roulant et installation restent passants.

Aucun téléphone n’est connecté. Les tests simulent Chrome, ses nœuds et les composants audio Android ; ils ne constituent pas une validation sur une version réelle de Chrome. Les marqueurs et l’accessibilité des Aperçus IA peuvent varier. Le Mode IA, les interfaces des autres fournisseurs et la lecture intégrale d’une réponse longue ne sont pas garantis. L’activation système, le retour de Chrome, la restitution sonore et l’autonomie restent à vérifier sur appareil. La solution par texte copié est disponible séparément.

## APK principal

Paquet **`fr.nexuscompanion.android`**, version **`0.4.22-native-alpha`**, code **28**, Android minimum 31, cible 35. Taille : **62 577 211 octets**, sous 64 Mio pour les anciens importeurs. Signature v2 et certificat permanent identiques à 0.4.21 ; ZIP intègre et alignement 16 Kio vérifié. Le manifeste compilé contient la visibilité ciblée de Chrome et la protection système du service ; aucune permission générale supplémentaire n’est demandée à l’installation.

Les quatre ABI, les huit bibliothèques natives, le modèle français et les **33 305 octets** des notices tierces sont identiques à 0.4.21. Les ressources compilées conservent la propriété du projet par Amine HARBI et l’édition par Rép&Web. Le format de mémoire, ses clés, la compression et les capacités générales ne changent pas. Le bundle du cerveau est inchangé : **118 039 octets**, SHA-256 `cc4b81c69a91a6e5d1113dbca13bd6330c6881e100e44f8bb83dbf32b63ceb2d`, avec 22 empreintes de sources vérifiées.

Arbre source compilé : `d95e89816b682aa8eaadb1cbb4f9bb446dec0169`. Seul ce compte rendu est ajouté après compilation.

```text
aa609f560ad90449303ed396ea1c51a6f559dda0b746514561c1f2a10f9cbbb4  nexus-companion-v0.4.22-native-alpha-release.apk
```

Installer **par-dessus la version principale, sans désinstallation**. Les versions intermédiaires ne sont pas nécessaires. Si un ancien importeur rencontre son ancienne limite de cache, ouvrir l’APK depuis **Mes fichiers → Téléchargements**, puis confirmer **Mettre à jour**.

Références techniques : [service d’accessibilité Android](https://developer.android.com/guide/topics/ui/accessibility/service), [configuration du service](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo), [réglages du composant Android](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/master/src/com/android/settings/accessibility/AccessibilityDetailsSettingsFragment.java), [Aperçus IA Google](https://support.google.com/websearch/answer/14901683?hl=fr).
