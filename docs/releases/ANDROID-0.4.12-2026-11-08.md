# Nexus Companion Android 0.4.12 native alpha

## Pupilles du portrait

Les yeux de Nexus ont des pupilles sombres et un petit reflet sur l’accueil et en mode plein visage. Les deux pupilles regardent dans la même direction pendant la réflexion et se masquent au clignement.

## Validation

- Les 176 tests passent en debug et en release. Android Lint passe pour les deux variantes. Les tests de rendu contrôlent les pixels des deux pupilles dans les quatre états du portrait, aux deux tailles et pendant le clignement.
- Le paquet principal `fr.nexuscompanion.android` passe du code de version 17 à 18. Son certificat correspond à celui de l’APK 0.4.11. Ses 62 356 759 octets passent sous la limite de 64 Mio de l’installateur intégré.
- Le rendu a été vérifié en simulation Android. L’installation sur téléphone reste à vérifier.

```text
c18d6cc4d71714c5f27aaeda1514a7f191c5fcc7068d5325b11d248248315769  nexus-companion-v0.4.12-native-alpha-release.apk
```

Installer l’APK par-dessus Nexus Companion via **Réglages → Mises à jour → Choisir un APK** pour conserver les notes.
