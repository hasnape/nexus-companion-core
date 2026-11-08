# Nexus Companion Android 0.4.11 native alpha

## Mode plein visage

Le bouton **Mode plein visage**, sous le portrait de l’accueil, agrandit le visage et masque les barres Android. Le portrait et l’indicateur suivent l’écoute, la réflexion et la parole. **Quitter** ou **Retour** réaffiche l’accueil et la saisie en cours. Le mode reste sélectionné quand Android recrée l’écran.

## Validation

- Les 174 tests passent en debug et en release. Android Lint passe pour les deux variantes. Les tests couvrent le retour à l’accueil, la conservation de la saisie et de la session micro, le rendu paysage à 150 % et la touche Retour sous Android 12.
- Le paquet principal `fr.nexuscompanion.android` passe du code de version 16 à 17. Son certificat correspond à celui de l’APK 0.4.10. Ses 62 356 651 octets passent sous la limite de 64 Mio de l’installateur intégré.
- Le parcours a été vérifié en simulation Android. L’installation et le comportement des barres système restent à vérifier sur téléphone.

```text
7b01765dfefc5ff8b57ad8db034a3d6cedafe0a428fb8b426ad5b82e7cbf890c  nexus-companion-v0.4.11-native-alpha-release.apk
```

Installer l’APK par-dessus Nexus Companion via **Réglages → Mises à jour → Choisir un APK** pour conserver les notes.
