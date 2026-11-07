# Nexus Companion Android 0.4.4 native alpha

## Dictée française

La 0.4.3 pouvait afficher « Micro actif » sans recevoir de transcription finale du service vocal Android. La version principale utilise désormais `AudioRecord` et le modèle Vosk français embarqué. Le statut passe à « Micro actif » après les premiers échantillons. Le microphone et le décodeur sont libérés avant de transmettre le texte final, puis la session reprend l’écoute après la réponse vocale.

Le modèle est vérifié pendant la compilation et extrait dans l’espace privé au premier démarrage du micro. Il fonctionne sans réseau et les échantillons audio restent en mémoire pendant leur traitement. La synthèse utilise la voix française locale d’Android.

## Validation

| Vérification | Résultat |
| --- | --- |
| Tests Android debug et release | 158 réussis par configuration |
| Android Lint debug et release | Aucun problème |
| Français sur enregistrements réels | « bonjour » et « comment vas-tu » |
| Architecture et alignement natifs | ARM et x86, 32 et 64 bits. Segments 64 bits alignés sur 16 Kio |
| APK | Signature permanente, code 10, paquet `fr.nexuscompanion.android` |
| Taille de l’APK | 62 315 058 octets, sous la limite de 64 Mio de l’installateur intégré |

Les tests Android simulent le microphone. Les deux enregistrements réels sont décodés sur ordinateur avec le même modèle acoustique. Les sources audio sont [« bonjour » par Vion Nicolas, CC BY 2.0 FR](https://commons.wikimedia.org/wiki/File:Fr-bonjour.ogg) et [« comment vas-tu » par Dekel E, CC BY-SA 3.0](https://commons.wikimedia.org/wiki/File:Fr-Comment_vas-tu.oga).

Le déclenchement attend une transcription finale du mot « Nexus ». Une voix synthétique française a produit « l’accès » dans le décodeur général. Une grammaire réduite a également confondu « next » avec « Nexus », donc la version livrée garde la règle d’activation stricte. Le microphone et le déclenchement restent à valider sur le téléphone.

```text
48140f2e16124ec5d448699ece8522979e81407376519f3fb217028d99a4413d  nexus-companion-v0.4.4-native-alpha-release.apk
```

L’empreinte SHA-256 du certificat public est `01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1`.

## Vérification sur téléphone

1. Ouvrir **Mises à jour → Choisir un APK** et sélectionner l’APK principal. Installer par-dessus Nexus Companion pour conserver les notes.
2. Ouvrir Nexus en mode avion avec le Wi-Fi coupé. Choisir **Parler maintenant**, attendre **Micro actif**, puis dire « Bonjour ». Vérifier **Entendu**, la réponse à voix haute et la reprise de l’écoute.
3. Depuis l’écoute automatique, dire « Nexus » pour vérifier séparément le mot d’activation.
