# Nexus Companion Android 0.4.3 native alpha

## Lecture des réponses

La 0.4.2 liait la lecture des réponses à l’état de la reconnaissance vocale. Si Android arrêtait la dictée, une réponse saisie au clavier pouvait rester muette. La 0.4.3 permet de lire cette réponse en mode vocal même si le service de reconnaissance est indisponible.

Le contrôleur attend jusqu’à cinq secondes que la voix locale soit prête. Le microphone reste en pause pendant cette attente et pendant la lecture. Nexus affiche les erreurs de synthèse séparément de l’état du microphone, puis reprend l’écoute si elle était active.

Le bouton **Tester la voix locale** dans **Voix et diagnostic** lit une phrase de test. La synthèse sélectionne une voix française installée sur l’appareil.

## Validation

| Édition | Tests debug | Tests release | Android Lint |
| --- | --- | --- | --- |
| Nexus Companion | 152 réussis | 152 réussis | Aucun problème |
| Nexus Companion Test | 152 réussis | 152 réussis | Aucun problème |

Huit nouveaux cas vérifient la lecture sans service de reconnaissance, l’attente et l’expiration de la voix, l’arrêt en arrière-plan, le signalement des erreurs et l’action dans le panneau de diagnostic. Les tests simulent les services Android. L’écoute réelle de la 0.4.3 et la dictée en mode avion restent à vérifier sur téléphone.

Les deux éditions utilisent le code de version 9 et le certificat de signature permanent.

| Édition | Identifiant Android | Fichier | Taille en octets |
| --- | --- | --- | ---: |
| Principale | `fr.nexuscompanion.android` | `nexus-companion-v0.4.3-native-alpha-release.apk` | 6589498 |
| Test | `fr.nexuscompanion.android.preview` | `nexus-companion-v0.4.3-native-alpha-preview-release.apk` | 6589606 |

```text
5aa0b193e61881e363e98accd5c1f61719b37b986acef6b978fe23aee21ddf1e  nexus-companion-v0.4.3-native-alpha-release.apk
e941b8af4f18803e930864075a62fb90f4d3b4cab1d03f6fbaaa66c7f73d3118  nexus-companion-v0.4.3-native-alpha-preview-release.apk
```

L’empreinte SHA-256 du certificat public commun est `01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1`.

## Essai sur téléphone

1. Ouvrir **Voix et diagnostic** et choisir **Tester la voix locale**. Vérifier que la phrase est audible.
2. Revenir à l’accueil. Appuyer sur **Parler maintenant**, attendre **Micro actif** et dire « Bonjour, comment vas-tu ? ». Vérifier **Entendu**, la réponse et la reprise du micro.
3. Répéter en mode avion avec le Wi-Fi désactivé pour valider l’aller-retour vocal hors ligne.
