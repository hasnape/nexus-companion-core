# Nexus Companion Android 0.4.13 native alpha

Le mode plein visage adapte le portrait à la place disponible en portrait et en paysage. Le bouton de sortie et l’indicateur d’état gardent leur place. Le halo couvre le fond du visage.

Dans **Réglages**, **Accès en ligne** est activé au départ et mémorise le choix. « Cherche sur Internet la tour Eiffel » consulte Wikipédia en français et affiche un lien vers l’article source. Les demandes de rappel de notes et de mémorisation sont traitées localement.

Le dépannage local couvre la connexion Wi-Fi, l’impression, Outlook, les lenteurs Windows et le VPN. Il utilise les mots de la demande pour classer les sujets et demande une précision quand plusieurs problèmes se ressemblent.

Les 180 tests Android passent en debug et en release, ainsi qu’Android Lint. Les 327 tests TypeScript, le contrôle de types, ESLint et la vérification du moteur embarqué passent. Les essais couvrent le plein visage, les pupilles, le choix de l’accès en ligne, les demandes privées et la continuité du dialogue après une recherche indisponible. Une requête réelle a retrouvé l’article « Tour Eiffel ».

Le paquet principal `fr.nexuscompanion.android` passe du code de version 18 à 19. Son certificat correspond à celui de l’APK 0.4.12. Ses 62 375 543 octets passent sous la limite de 64 Mio de l’installateur intégré.

Le rendu a été vérifié en simulation Android. L’installation, le microphone et la lecture sur téléphone restent à vérifier.

```text
a8c4f37ac4509a771275af6c9829d19716de66f14eb61e0fa11ce53f407c79fd  nexus-companion-v0.4.13-native-alpha-release.apk
```

Installer l’APK par-dessus Nexus Companion via **Réglages → Mises à jour → Choisir un APK** pour conserver les notes.
