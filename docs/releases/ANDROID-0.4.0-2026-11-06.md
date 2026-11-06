# Nexus Companion Android 0.4.0 — mises à jour locales

## Résultat

Le bouton « Mises à jour » ouvre un écran Android privé où l'utilisateur choisit un APK local, voit la version contrôlée et demande explicitement son installation. Ce parcours prépare la réception des prochaines versions depuis Nexus ; il ne constitue pas une recherche ou un téléchargement automatiques. La première installation de 0.4.0 se fait par-dessus la version actuelle, avec le même certificat.

Le cœur, les notes chiffrées, leur schéma et la gestion de la voix de 0.3.1 sont conservés. L'ouverture du nouvel écran met l'activité principale en pause et arrête son micro. Aucun code d'installation ne lit la bibliothèque ni ne demande une désinstallation.

## Contrôles et frontières

- Sélection avec `ACTION_OPEN_DOCUMENT`, type APK, URI `content:` uniquement. Ni chemin externe arbitraire ni permission de stockage globale.
- Copie privée indépendante du document source : 64 Mio par fichier, 128 Mio de cache total, flux fermé et fichier incomplet supprimé en cas d'erreur. Vérification interrompable hors du thread UI.
- Application identique, versionCode `Long` strictement supérieur, signataires courants non vides et exactement identiques, niveau Android compatible. Les certificats sont comparés par SHA-256. Une rotation de clé demande une migration distincte et est refusée ici.
- Empreinte SHA-256 des octets recalculée à la restauration de l'écran et avant remise à Android. Fichiers en lecture seule, noms aléatoires internes et chemins canoniques ; les alias du dossier Android sont acceptés, les liens de fichiers sortant de la copie attendue sont refusés.
- `FileProvider` non exporté, racine limitée à `cache/updates/`, refus de toute ouverture en écriture. L'installateur reçoit uniquement une URI vers l'APK contrôlé et une autorisation temporaire de lecture. Les notes restent hors de cette racine.
- Permission spéciale `REQUEST_INSTALL_PACKAGES` ; retour des réglages sans installation automatique. Nouveau tap requis, remise à Android seulement au premier plan, puis confirmation et validation cryptographique finales dans Android.
- Aucune annonce de succès fondée sur le seul lancement de l'installateur. L'utilisateur peut vérifier la version dans « À propos » après installation. Refus, annulation, rotation et retour depuis les réglages n'effacent pas les notes.
- Fichier non transmis supprimé à la fermeture, import abandonné nettoyé, copie déjà transmise conservée pour la lecture de l'installateur. Les copies de plus de 24 h sont purgées lors d'un nouvel import ; Android peut aussi évincer ce cache.

Les métadonnées lues par PackageManager servent au filtrage préalable. La vérification cryptographique complète de l'APK et son acceptation finale restent celles de l'installateur Android. Le moteur de mise à jour n'accepte ni URLs distantes, ni clé de confiance fournie par le fichier choisi.

## Validation exécutée

| Contrôle | Résultat |
| --- | --- |
| Tests Android debug | 122 réussis, aucun échec |
| Tests Android release | 122 réussis, aucun échec |
| Android Lint debug et release | Aucun problème signalé |
| Tests TypeScript et web | 318 réussis, 44 fichiers |
| Lint, typecheck et construction des workspaces | Réussis |
| `check:native-brain` | Bundle partagé inchangé et cohérent avec ses sources |
| Script de distribution avec clé permanente | Réussi ; APK debug et release produits |
| Signature de diffusion | Vérifiée, même certificat permanent que 0.3.1 |
| Métadonnées APK | Code 6, Android API 31 minimum, cible 35, aucune permission INTERNET |

Les 32 cas Android supplémentaires comprennent 8 tests de politique de paquet, 12 de copie/restauration/nettoyage, 8 parcours du nouvel écran, 3 du fournisseur de fichiers et 1 ouverture depuis l'écran principal. Ils couvrent notamment la taille, le quota, les lectures interrompues, les erreurs de fermeture, les chemins et liens, les alias de dossiers Android, la modification après aperçu, les signataires multiples, les versions longues, la rotation d'écran, l'autorisation sans déclenchement automatique et la transmission d'octets en lecture seule.

Les métadonnées Android et l'installateur sont simulés sous Robolectric API 35 ; les copies, empreintes et lectures via le fournisseur portent sur des fichiers réels. Il n'y a pas de preuve d'installation de cette version sur émulateur ou téléphone, ni de mesure acoustique nouvelle. La conservation des notes au cours d'une installation réelle reste à confirmer sur le téléphone de test.

## APK de diffusion

- Fichier : `nexus-companion-v0.4.0-native-alpha-release.apk`.
- Taille : 6 544 166 octets.
- SHA-256 APK : `3bea91909c112374dd2f85f21623b1d198c9e104a2327e65aa5cc3d5692fd17b`.
- SHA-256 cerveau embarqué : `4474fe5f27a051782d51ea0c33ab787bbcd64f12c7ea9487a839af269c96e3b8`.
- Certificat SHA-256 : `01:44:5C:D0:F0:F5:66:88:FB:A4:6B:AD:32:84:0F:F8:5F:BD:EE:11:9C:DD:BD:2D:3A:19:31:4D:94:C4:72:D1`.

Le manifeste contient RECORD_AUDIO, REQUEST_INSTALL_PACKAGES et la permission interne de signature AndroidX pour ses récepteurs non exportés. Les règles de sauvegarde, l'absence de stockage global et la racine limitée du fournisseur sont conservées. L'augmentation de taille provient de la dépendance AndroidX standard et de ses dépendances conservées.

## Dépendances et compatibilité

Version `0.4.0-native-alpha`, code `6`, application `fr.nexuscompanion.android`, Android 12/API 31 minimum, cible API 35. Même clé de diffusion que 0.3.1 ; aucun changement de schéma ou de chiffrement des notes. Pas de permission INTERNET ni de mise à jour silencieuse.

AndroidX Core 1.15.0 est fixé pour utiliser FileProvider sur la chaîne compileSdk 35 existante. Ses dépendances Maven restent standard, sans exclusions partielles de classes. Les attributions AndroidX, Kotlin, coroutines et Guava, ainsi que la licence Apache 2.0, sont ajoutées aux notices consultables dans « À propos ».

## Recette sur le téléphone de test

1. Installer 0.4.0 par-dessus la version actuelle. Si Android refuse le certificat, conserver l'installation et ses notes ; ne pas désinstaller pour contourner le refus.
2. Vérifier une note existante, le texte, « Nexus, bonjour » et « Parler maintenant ». Ouvrir « Mises à jour » et confirmer l'arrêt du micro ; annuler puis revenir à l'écoute selon la préférence conservée.
3. Choisir l'APK 0.3.1 : il doit être refusé comme ancien. Un fichier quelconque doit être refusé sans ouvrir l'installateur. Annuler le sélecteur ne doit lancer aucune installation.
4. Avec un futur APK autonome signé de la même clé et de code supérieur à 6, vérifier la version proposée, l'autorisation Android si nécessaire, le nouveau tap au retour des réglages, puis confirmer dans Android.
5. Après mise à jour, vérifier la nouvelle version dans « À propos », la présence des notes et le fonctionnement de la voix en mode avion. Aucun essai réel d'installation de cette version n'est revendiqué par les simulations.

## Travaux suivants

Un canal de versions en ligne contrôlé, le téléchargement avec opt-in et un catalogue de versions signé restent à concevoir. Ils pourront réutiliser la politique de paquet et le cache privé sans déplacer le cerveau local vers un service externe. L'export/import Android chiffré et l'ASR embarqué indépendant du téléphone restent également ouverts.

## Sources de l'API

- [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)
- [Autorisation de proposer une installation](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())
- [Sélection de fichiers avec le framework de stockage](https://developer.android.com/training/data-storage/shared/documents-files)
- [Informations de signature](https://developer.android.com/reference/android/content/pm/SigningInfo)
