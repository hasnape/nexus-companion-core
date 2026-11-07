# Nexus Companion Android 0.4.7 native alpha

## Réponses vocales

L’écran principal propose **Réponses vocales** à côté du bouton micro. Avec les réponses vocales activées, Nexus lit aussi les réponses aux messages écrits si le micro est coupé ou si sa permission a été refusée. Couper le micro laisse la lecture en cours se terminer. Couper les réponses vocales interrompt la lecture et permet à l’écoute de reprendre si le micro est actif.

Le choix de lecture est enregistré séparément. À la première ouverture après la mise à jour, l’ancien choix concernant le micro initialise l’interrupteur pour préserver les installations déjà réglées en mode silencieux. La voix française locale utilise le volume multimédia.

## Validation

- Les 164 tests passent en debug et en release. Android Lint ne relève aucun problème.
- Les tests simulent une réponse écrite avec la permission micro refusée, l’arrêt du micro pendant une lecture et la coupure des réponses vocales pendant l’initialisation et la lecture. L’audio audible reste à vérifier sur téléphone.
- L’APK principal porte le code de version 13 et le certificat permanent. Ses 62 318 326 octets passent sous la limite de 64 Mio de l’installateur intégré.

```text
cf0c6acafee15913e0bd2af83b80f3ccf5046b64269b3148a18b4c8f6de57a27  nexus-companion-v0.4.7-native-alpha-release.apk
```

Installer l’APK par-dessus Nexus Companion depuis **Mises à jour → Choisir un APK**. Activer **Réponses vocales** sur l’écran principal et envoyer « Bonjour ». Pour isoler le son Android, ouvrir **Voix et diagnostic → Tester la voix locale** et vérifier le volume multimédia pendant l’essai.
