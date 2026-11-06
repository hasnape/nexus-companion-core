# Nexus Companion Android 0.2.1 — validation du 6 novembre 2026

Statut : correctif de l'alpha native. Ne constitue pas une déclaration de produit complet ou une validation sur téléphone physique.

## Changements

- Les événements de silence et d'absence de résultat n'arrêtent plus la session après douze reprises. Les véritables pannes gardent une limite et une temporisation.
- Le contrôleur vocal utilise une session testable indépendante des services Android. Les callbacks de reconnaisseurs détruits et de sessions précédentes ne peuvent plus exécuter de commandes.
- Le délai de commande commence après « Je t'écoute ». Les formulations « Nexus, bonjour » et « Nexus », puis « Nexus : bonjour », sont prises en charge.
- La synthèse gère fin, erreur, interruption, remplacement d'une réponse et absence de callback. Le microphone est fermé avant la synthèse ; la bouche suit le démarrage audio réel. La priorité audio est demandée puis libérée.
- Arrêt au passage hors premier plan (`onPause`) ; demande d'autorisation protégée contre les doublons. L'arrêt manuel conserve sa préférence.
- Marges de barres système/clavier Android 15, ressources de texte et icône natives ; exclusions explicites de sauvegarde système et de transfert des données privées.
- Wrapper Gradle Windows/Linux inclus, Gradle et son archive vérifiés, sorties et secrets ignorés par Git. Script local de distribution avec tests, lint, signature et sommes SHA-256. Configuration de signature partielle refusée avant compilation.
- `applicationId` conservé : `fr.nexuscompanion.android` ; `versionCode` 2 → 3 ; nom `0.2.1-native-alpha`.

## Vérifications exécutées

| Contrôle | Résultat |
| --- | --- |
| `npm ci --no-audit --no-fund` | Réussi |
| `npm run lint` | Réussi |
| `npm run typecheck` | Réussi |
| `npm test` | 290 tests, 43 fichiers, tous réussis |
| `npm run build` | Réussi pour tous les workspaces |
| Tests JVM Android debug | 25 tests réussis, dont 20 nouveaux scénarios vocaux |
| Tests JVM Android release | 25 tests réussis |
| Android Lint debug et release | Aucune erreur ni avertissement |
| APK debug et release | Compilés |
| Vérification du certificat | Même certificat que la clé permanente existante |

Les scénarios vocaux couvrent notamment 100 événements de silence/absence de résultat, 40 transcriptions ambiantes sans mot-clé, les commandes en un/deux tours, les événements périmés, le remplacement de réponse, les voix absentes, le moteur qui ne termine pas, les autorisations/langues manquantes, les pannes bornées et l'interruption audio.

Empreinte SHA-256 du certificat public : `01:44:5C:D0:F0:F5:66:88:FB:A4:6B:AD:32:84:0F:F8:5F:BD:EE:11:9C:DD:BD:2D:3A:19:31:4D:94:C4:72:D1`.

SHA-256 de l'APK signé livré : `fdac291119cb8b537924c6e3c46f6cc8f920bccf577f17278197c3891794b242`.

## Essai sur émulateur et limites observées

Un émulateur AOSP Android 15 / API 35 x86_64 a été démarré sans accélération matérielle. L'ancien APK signé, versionCode 2 construit depuis `4e2928a`, a été installé avec succès. L'installation par-dessus de l'APK 0.2.1 signé avec la même clé a ensuite réussi avec `adb install -r`, sans désinstallation ni effacement des données.

La recette de l'interface (note enregistrée, mise à jour avec conservation de cette note, conversation en mode avion et refus du micro) n'a pas abouti : l'environnement a affiché « Process system isn't responding », empêchant l'automatisation d'atteindre le champ de saisie. Ce blocage du système émulé ne valide ni n'invalide ces parcours dans l'application. Aucune conservation de note lors d'une mise à jour, reconnaissance vocale réelle ou autonomie sur téléphone n'est déclarée testée.

Le script de distribution local complet a été exécuté avec succès. Le bootstrap Linux optionnel utilise un JDK et Android CLI 19.0 vérifiés ; son exécution dans Vercel n'a pas été testée. Les fins de ligne des scripts Windows et Unix sont fixées par `.gitattributes`.

## Recette sur téléphone à effectuer

1. Installer l'APK signé par-dessus une version ayant le même certificat, sans désinstaller ni effacer les données.
2. Passer en mode avion ; envoyer « bonjour » par écrit, enregistrer et rechercher une note.
3. Avec le service et les voix françaises hors ligne installés, autoriser le micro. Dire « Nexus », attendre « Je t'écoute », puis « bonjour ». Tester aussi « Nexus, comment ça va ».
4. Vérifier que Nexus ne se répond pas à lui-même, que la bouche reste fermée hors parole, que l'écoute reprend après plusieurs silences et que le bouton d'arrêt tient après réouverture.
5. Mettre l'application en arrière-plan, verrouiller l'écran, tester une interruption audio puis le retour, et contrôler que le micro s'arrête hors premier plan. Vérifier la consommation sur une durée représentative.

## Travaux encore ouverts

Intégration du cerveau portable et de l'identité native, avatar complet, ASR/mot-clé local embarqué, sauvegarde Android chiffrée portable, gestion des mises à jour dans l'application et recette sur appareils physiques. Le fonctionnement vocal dépend encore des services hors ligne installés par Android ; aucun repli réseau n'a été ajouté.

## Références techniques

- [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Android UtteranceProgressListener](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener)
- [Android Auto Backup et exclusions de transfert](https://developer.android.com/identity/data/autobackup)
