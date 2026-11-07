# Nexus Companion Android 0.4.2 native alpha

## Reprise de l’écoute

Après un silence ou un résultat final, un événement tardif du service vocal pouvait affecter la capture suivante. Chaque capture terminée ferme désormais son client Android avant la reprise. Le contrôleur ignore les résultats, les aperçus et les erreurs des clients fermés.

Après « Nexus », les 12 secondes pour commencer à parler partent du signal « Micro actif ». La fenêtre reste disponible quand la synthèse vocale ne peut pas dire « Je t’écoute ». Elle s’arrête de compter dès le début de parole.

## Validation locale

| Édition | Tests debug | Tests release | Android Lint |
| --- | --- | --- | --- |
| Nexus Companion | 144 réussis | 144 réussis | Aucun problème |
| Nexus Companion Test | 144 réussis | 144 réussis | Aucun problème |

Quatre scénarios reproduisent les défauts sur la base 0.4.1. Les tests ajoutés vérifient le rejet des événements tardifs après un silence ou une transcription, le démarrage lent du micro avec et sans synthèse, et la fermeture des clients Android au fil de dix reprises simulées.

Les deux exécutions du script `scripts/build-android-apk.sh` produisent un APK signé avec la clé permanente. Le contrôle du paquet valide le nom, le code de version 8, la signature et le contenu du moteur local. Les tests utilisent des services Android simulés. L’installation et la reconnaissance française en mode avion restent à vérifier sur téléphone.

## APK signés

| Édition | Identifiant Android | Fichier | Taille en octets |
| --- | --- | --- | ---: |
| Diffusion | `fr.nexuscompanion.android` | `nexus-companion-v0.4.2-native-alpha-release.apk` | 6575142 |
| Test | `fr.nexuscompanion.android.preview` | `nexus-companion-v0.4.2-native-alpha-preview-release.apk` | 6575258 |

```text
28a4406648b3d82ba253566d77dddbb47addcb0cd9e48e0b9bc8675f78d65189  nexus-companion-v0.4.2-native-alpha-release.apk
c02f11acc4cedf2f91bccd03cafe09117cea7e97a75c9c8a33720430d0f3473a  nexus-companion-v0.4.2-native-alpha-preview-release.apk
```

L’empreinte SHA-256 du certificat public commun est `01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1`.

## Essai sur téléphone

1. Installer **Nexus Companion Test** à côté de l’application actuelle et autoriser le microphone. Test utilise sa propre mémoire.
2. Ouvrir **Voix et diagnostic → Vérifier le français hors ligne**. Passer en mode avion une fois le modèle prêt.
3. Depuis l’accueil, choisir **Parler maintenant**, dicter « Bonjour, comment vas-tu ? » et vérifier la transcription dans **Entendu**.
4. Dire « Nexus », attendre **Micro actif**, puis poser une question. Vérifier que l’écoute se met en pause pendant la réponse et reprend ensuite.
5. En cas de refus de la mise à jour de l’édition principale, utiliser **Copier le diagnostic** depuis Test pour comparer les versions et les signatures.
