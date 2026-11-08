# Nexus Companion Android 0.4.10 native alpha

## Rappel de notes

« Qu’est-ce que j’ai noté sur la batterie ? » et « Tu te souviens de ma batterie ? » cherchent des notes sur la batterie. Si le sujet est absent de la mémoire locale, Nexus l’indique. « Mes notes » et « Qu’ai-je noté ? » affichent des extraits de notes récentes.

Le moteur répond à partir des notes sélectionnées selon le sujet demandé. Les extraits sensibles et les notes expirées sont écartés du rappel conversationnel. Toute nouvelle note demande une confirmation.

## Validation

- Les 171 tests Android passent en debug et en release. Android Lint passe pour les deux variantes. Les tests exécutent le moteur embarqué sur des demandes ciblées avec et sans résultat et sur un rappel général.
- Les 320 tests TypeScript passent, ainsi que le contrôle de types, ESLint et la vérification du moteur embarqué.
- Le paquet principal `fr.nexuscompanion.android` passe du code de version 15 à 16. Son certificat correspond à celui de l’APK 0.4.9. Ses 62 349 791 octets passent sous la limite de 64 Mio de l’installateur intégré.
- Le rappel et la compatibilité du paquet ont été vérifiés dans l’environnement de développement. L’installation et le rappel vocal sur téléphone restent à vérifier.

```text
ff8e713cfb8224cad69dc357ab18664f1938795e45f1b54aa45fcfaa2f6050bb  nexus-companion-v0.4.10-native-alpha-release.apk
```

Installer l’APK par-dessus Nexus Companion via **Réglages → Mises à jour → Choisir un APK** pour conserver les notes.
