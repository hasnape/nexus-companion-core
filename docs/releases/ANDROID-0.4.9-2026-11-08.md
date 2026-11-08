# Nexus Companion Android 0.4.9 native alpha

## Mémoire locale

- Les notes sont enregistrées après confirmation. Si une note identique existe déjà, Nexus la conserve et indique qu’elle est déjà enregistrée.
- Une demande comme « Mes notes sur l’IA » cible le sujet demandé. La recherche privilégie le nombre de mots retrouvés, puis leur présence dans le titre et la date de la note.
- Les écritures sont atomiques. Nexus compresse le fichier avec GZIP quand sa taille diminue, puis le chiffre avec la clé Android Keystore existante. Les anciennes notes chiffrées restent lisibles. La prochaine modification de la bibliothèque peut les compresser.

## Validation

- Les 170 tests passent en debug et en release. Android Lint ne relève aucun problème. Les tests couvrent le rappel ciblé, l’ouverture d’un ancien fichier chiffré, les écritures concurrentes et la limite de décompression.
- Le paquet principal `fr.nexuscompanion.android` passe du code de version 14 à 15. Son certificat correspond à celui de l’APK 0.4.8 et sa taille de 62 346 523 octets passe sous la limite de 64 Mio de l’installateur intégré.
- Les scénarios de mémoire ont été exécutés en simulation Android avec AES-GCM sur JVM. L’installation et la lecture des notes depuis le Keystore du téléphone restent à vérifier sur l’appareil.

```text
0310814157305003d7968a5d3db28c446b89051837249ee8eb8c0d8fe1c1710f  nexus-companion-v0.4.9-native-alpha-release.apk
```

Installer l’APK par-dessus Nexus Companion depuis **Réglages → Mises à jour → Choisir un APK** pour conserver les données locales.
