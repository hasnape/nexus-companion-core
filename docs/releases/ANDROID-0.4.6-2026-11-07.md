# Nexus Companion Android 0.4.6 native alpha

## Écoute après un silence ou une phrase ambiante

Dans la v0.4.5, l’application attendait 500 ms avant de préparer la capture suivante. Une fois le micro libéré, le moteur embarqué prépare désormais la capture suivante au prochain passage de la boucle Android quand aucune réponse n’est en cours. L’écran affiche « Micro actif » après réception des premiers échantillons. L’arrêt manuel annule la reprise planifiée.

## Validation

- Le test à horloge simulée observe la reprise après un silence, une absence de résultat ou une phrase ambiante sans avancer le temps. Le cas échoue avec le code de la v0.4.5. Les 52 tests de session vocale passent.
- Les 160 tests passent en debug et en release. Android Lint ne relève aucun problème.
- L’APK principal a le code de version 12 et le certificat permanent. Ses 62 315 778 octets passent sous la limite de 64 Mio de l’installateur intégré.

```text
034369d30a5c0f4bd72a2c93691a5a8287cd80c59342e31f086f8c6e11e1fe1e  nexus-companion-v0.4.6-native-alpha-release.apk
```

Installer l’APK par-dessus Nexus Companion depuis **Mises à jour → Choisir un APK**. Le délai réel de réouverture du microphone reste à mesurer sur téléphone.
