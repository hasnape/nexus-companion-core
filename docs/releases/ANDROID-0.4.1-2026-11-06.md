# Nexus Companion Android 0.4.1 — écoute et diagnostic d'installation

## Constat

Le téléphone affiche « Application non installée » pour 0.4.0. Cet écran ne donne pas le code d'erreur et ne prouve pas un conflit de signature. Les copies conservées des clés de diffusion sont identiques et les APK 0.2.1, 0.3.0 et 0.4.0 contrôlés portent le même certificat permanent. Le certificat de la version effectivement installée sur le téléphone reste à observer ; aucune désinstallation n'est demandée.

## Écoute

- Erreurs Android de service occupé et de limitation des demandes traitées séparément, au lieu d'une succession rapide de tentatives. Repos d'au moins 1,5 seconde pour un service occupé et 15 secondes pour une limitation. Le budget de pannes et l'arrêt manuel restent bornés et les callbacks périmés restent ignorés.
- Témoin indicatif du signal RMS Android, limité à 0–100 pour l'affichage, sans calibration acoustique revendiquée. Aucune commande ne provient de ce niveau. Remise à zéro à la fin, en pause, pendant la parole de Nexus et à l'arrêt. Aucun enregistrement de l'audio ou des niveaux.
- Panneau privé « Voix et diagnostic », avec arrêt du micro de l'accueil par le cycle de vie de l'activité. Vérification API 33+ du français `fr-FR`/français générique, selon les listes installées/en attente/proposées par Android. L'absence de résultat, une erreur et une expiration ne deviennent jamais « prêt ».
- Modèle demandé au service Android uniquement sur tap explicite, après qu'il a indiqué le français disponible au téléchargement. Pas de téléchargement automatique au démarrage et pas de permission INTERNET dans Nexus. Cette préparation optionnelle peut utiliser le réseau du service Android ; la reconnaissance reste sur l'appareil.
- Accès API 33 isolés derrière une méthode annotée et un contrôle d'API. Sur API 31–32, la vérification détaillée reste inconnue ou signale l'absence de service ; elle ne provoque pas d'appel à une API manquante.

Ces changements améliorent la préparation et la reprise du service ; ils ne constituent ni un entraînement de modèle, ni une mesure d'amélioration du taux d'erreurs avec la voix du propriétaire.

## Installation et canal Test

Version de diffusion : `0.4.1-native-alpha`, code `7`, identifiant `fr.nexuscompanion.android`. Le mode de compilation explicite `nexusPreview=true` produit `0.4.1-native-alpha-preview` sous `fr.nexuscompanion.android.preview`, avec le nom « Nexus Companion Test » et un bandeau rappelant ses notes séparées.

Test peut être installé à côté de la version existante ; il ne remplace, ne lit et ne migre pas ses notes. Les données et les clés Keystore sont propres à chaque identifiant. La clé de diffusion permanente reste la même ; les mises à jour vérifiées restent strictement limitées à l'identifiant du canal choisi.

Le diagnostic interroge uniquement le package courant et `fr.nexuscompanion.android`, déclaré explicitement dans les queries. Il compare les signataires courants et peut copier versions, niveau Android, identifiants des deux applications et SHA-256 des certificats publics. Aucun accès aux autres applications, notes, conversations, fichiers externes ou identifiants matériels n'est ajouté. L'écran de diagnostic et le fournisseur de mises à jour ne sont pas exportés.

## Validation exécutée

| Variante | Tests Android debug | Tests Android release | Android Lint |
| --- | --- | --- | --- |
| Diffusion | 139 réussis | 139 réussis | Aucun problème |
| Test/preview | 139 réussis | 139 réussis | Aucun problème |

Les 17 nouveaux cas vérifient les reprises occupé/limité, l'arrêt pendant une reprise différée, le témoin micro et sa pause, les états du modèle français, le téléchargement explicite, l'expiration sans faux succès, les callbacks périmés, le fonctionnement dégradé API 31, la comparaison de certificats, l'absence de lecture des notes et le panneau privé. Les services vocaux et les métadonnées Android sont simulés sous Robolectric ; aucun taux d'erreur de reconnaissance réel n'est mesuré.

Les deux exécutions du script complet de distribution ont réussi avec la clé permanente. Identifiants, code 7, libellés, absence d'INTERNET, certificats et bundle du cerveau ont été contrôlés dans les APK. `check:native-brain` réussit et le TypeScript partagé reste inchangé. Le script de compilation a aussi été vérifié par `bash -n`.

Une tentative complémentaire d'installation sur Android 16/API 36 virtuel sans accélération matérielle a été préparée. Le système n'a pas terminé son démarrage dans le délai du test ; aucune installation n'a donc été comptée comme réussie. L'émulateur de cette tentative a été arrêté. Le refus vu sur le téléphone et la conservation des notes lors d'une mise à jour physique ne sont pas validés par ces simulations.

## Fichiers de diffusion

| Canal | Fichier | Taille | SHA-256 |
| --- | --- | --- | --- |
| Diffusion | `nexus-companion-v0.4.1-native-alpha-release.apk` | 6 575 494 octets | `ff26c07e995dfbaf6c0d2a829abff4bb82f31025c11b448d824a881db44b8c2f` |
| Test | `nexus-companion-v0.4.1-native-alpha-preview-release.apk` | 6 575 606 octets | `369ef0eaf8185822fdb5f14a1d8813bbdac1dbad525a5f19a6662ee2ca2f0ec9` |

Certificat public SHA-256 commun : `01:44:5C:D0:F0:F5:66:88:FB:A4:6B:AD:32:84:0F:F8:5F:BD:EE:11:9C:DD:BD:2D:3A:19:31:4D:94:C4:72:D1`.

Bundle du cerveau embarqué inchangé : `4474fe5f27a051782d51ea0c33ab787bbcd64f12c7ea9487a839af269c96e3b8`.

## Recette téléphone

1. Garder l'application actuelle et ses notes. Si la mise à jour de diffusion est refusée, essayer le fichier **Test**, qui porte un autre identifiant et un autre nom visible.
2. Autoriser le microphone lors de l'ouverture de Test. Ouvrir « Voix et diagnostic » et vérifier le français hors ligne. Si le service le propose, demander le modèle puis vérifier son état ultérieurement.
3. Revenir à l'accueil, appuyer sur « Parler maintenant », attendre le micro actif et dicter une phrase. Comparer le résultat « Entendu » avec les mots prononcés, puis essayer « Nexus, bonjour ».
4. Vérifier que le témoin micro revient à zéro quand Nexus répond et que l'écoute reprend. Tester silence, arrêt manuel et arrière-plan.
5. Copier le diagnostic pour identifier la version et la signature actuellement installées. Une signature différente explique une incompatibilité de mise à jour directe ; des signatures identiques ne permettent pas d'exclure une règle d'installation propre au téléphone.

## Références

- [Mise à jour : identifiant, version et certificat](https://developer.android.com/google/play/app-updates)
- [SpeechRecognizer : prise en charge et modèle](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [RecognitionSupport : langues locales](https://developer.android.com/reference/android/speech/RecognitionSupport)
