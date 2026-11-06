# Nexus Companion Android 0.3.1 — reconnaissance française

## Problème et comportement livré

Après « Nexus » et l'accusé vocal, la fenêtre de commande pouvait expirer alors que l'utilisateur parlait. L'écran ne présentait ni les mots reconnus, ni une distinction fiable entre le micro activé et un service réellement prêt : une opération locale pouvait remplacer le statut vocal par « Prêt — fonctionnement local ».

La fenêtre de 12 secondes attend désormais le début de parole, et est suspendue dès le début de capture ou le premier résultat partiel. La capture et l'attente du résultat final ont leurs propres délais bornés. Une panne du service ne peut pas maintenir indéfiniment une fausse écoute active. Les silences normaux restent sans épuisement du budget d'erreurs.

Le service reçoit explicitement le français, la demande de résultats partiels et cinq hypothèses au maximum. Sur API 33+, seules les marques publiques Nexus sont proposées comme indications ; le contexte privé de l'appareil n'est pas demandé. Les préfixes français et « Nexus Companion » sont reconnus au début d'une phrase complète. Une alternative ne peut récupérer le mot d'activation que parmi les trois premiers résultats, avec un score valide >= 0,65 et distant du premier de <= 0,15. Le mode commande conserve le premier résultat. Les résultats vides ou de plus de 2000 caractères ne sont pas exécutés, ni tronqués en commandes.

« Parler maintenant » permet de dicter directement sans mot-clé, y compris pour interrompre une réponse vocale. L'aperçu « En cours » n'exécute aucune commande ; « Entendu » montre le texte final utilisé. Ces textes restent en mémoire, disparaissent après 15 secondes sans mise à jour ou à l'arrêt, et ne remplissent pas le champ clavier. Les dialogues privés et le passage en arrière-plan arrêtent toujours la session. Le statut vocal courant est restauré après une opération locale. Le fond sombre couvre tout l'écran.

## Périmètre et compatibilité

- Version `0.3.1-native-alpha`, `versionCode=5`, application `fr.nexuscompanion.android`.
- Même clé permanente de diffusion ; schéma et chiffrement des notes inchangés.
- Aucune permission INTERNET, aucun son ou historique de transcription enregistré par l'application.
- Aucune modification des règles du cerveau partagé ni des garanties de confirmation des notes.
- Aucun workflow GitHub Actions requis.

## Validation exécutée

| Contrôle | Résultat |
| --- | --- |
| Lint, typecheck et construction TypeScript | Réussis |
| Tests partagés et web | 318 tests réussis, 44 fichiers |
| Cohérence du bundle natif avec les sources | Réussie ; bundle du cerveau inchangé |
| Tests Android debug et release | 90 tests par variante, aucun échec |
| Android Lint debug et release | Aucune erreur ni avertissement |
| Script de distribution et compilation signée | Réussis |
| Manifeste de diffusion | API 31 minimum, cible 35, permission RECORD_AUDIO seule |
| Certificat | Même certificat permanent que 0.3.0 et 0.2.1 |

Les 90 cas par variante comprennent 36 tests de session vocale, 8 de sélection des transcriptions, 8 de paramètres et extraction des résultats (API 31 et 35), 2 du contrôleur vocal avec le service simulé API 34, 10 parcours d'interface, 9 tests du moteur embarqué, 8 de bibliothèque, 7 de politique des notes et 2 du service moteur/bibliothèque. Les 32 cas supplémentaires couvrent notamment les phrases longues, les partiels sans exécution, l'absence de résultat final, les callbacks périmés, les scores incomplets, la dictée directe et le statut après un travail local ou un dialogue privé.

Le service vocal Android et son audio sont simulés dans les tests. Aucun essai physique de cette version, taux d'erreur français, essai Bluetooth, mesure de consommation ou essai de conservation des notes lors d'une mise à jour sur téléphone n'est revendiqué.

## APK de diffusion

- Fichier : `nexus-companion-v0.3.1-native-alpha-release.apk`.
- Taille : 3 554 113 octets.
- SHA-256 APK : `8b13cc8e718014b7fecb81febf86aedccb6c27339422ff3654b24ba89c3ee6c0`.
- SHA-256 cerveau embarqué : `4474fe5f27a051782d51ea0c33ab787bbcd64f12c7ea9487a839af269c96e3b8` ; bytes de l'APK comparés à l'asset validé.
- Certificat SHA-256 : `01:44:5C:D0:F0:F5:66:88:FB:A4:6B:AD:32:84:0F:F8:5F:BD:EE:11:9C:DD:BD:2D:3A:19:31:4D:94:C4:72:D1`.

## Vérification sur téléphone de test

Installer par-dessus la version actuelle lorsque les certificats correspondent. Si Android refuse la mise à jour, conserver l'installation existante et ses notes : ne pas désinstaller pour contourner l'erreur.

1. En mode avion, ouvrir l'application et attendre « Micro actif ». Vérifier « Nexus, bonjour », puis « Hey Nexus, comment ça va ? » ; comparer « Entendu » avec les mots prononcés.
2. Dire « Nexus », attendre « Je t'écoute », puis dicter une phrase de plus de 12 secondes. Vérifier qu'elle arrive entière au cerveau local.
3. Utiliser « Parler maintenant », attendre le micro prêt et dire « bonjour » sans mot-clé. Répéter après une réponse ; vérifier l'absence d'auto-écoute de Nexus.
4. Rester silencieux puis reprendre ; vérifier que la session reprend sans faux état actif. Arrêter le micro puis parler : aucune commande ni aperçu ne doit apparaître.
5. Afficher ou relire une note, puis passer en arrière-plan : le micro et l'aperçu doivent être arrêtés. Au retour, vérifier la préférence d'écoute et la conservation des notes existantes.
6. Répéter en pièce calme et avec bruit modéré, à distance habituelle ; noter les phrases prononcées, le texte « Entendu », les échecs d'activation et le temps de reprise. Aucun taux d'erreur acoustique n'est revendiqué avant cette mesure.

## Références de l'API utilisée

- [RecognizerIntent — paramètres et indications de vocabulaire](https://developer.android.com/reference/android/speech/RecognizerIntent)
- [SpeechRecognizer — service sur l'appareil et cycle de reconnaissance](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [RecognitionListener — événements et résultats partiels](https://developer.android.com/reference/android/speech/RecognitionListener)

Les paramètres supplémentaires restent des demandes au service installé. Cette version fiabilise l'orchestration et le traitement des résultats ; elle n'entraîne pas un moteur acoustique et n'embarque pas de modèle ASR indépendant du téléphone.
