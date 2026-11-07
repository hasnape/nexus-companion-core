# Nexus Companion Android 0.4.5 native alpha

## Reprise après une réponse vocale

La préparation du micro démarre 200 ms après le signal de fin de lecture, contre 700 ms dans la version 0.4.4. Le microphone reste fermé pendant la réponse. Les 200 ms laissent une marge après la lecture et la fenêtre de commande commence quand le micro livre ses premiers échantillons. La reprise après une erreur ou une expiration de la lecture intervient après 700 ms.

## Validation

- Le test à horloge simulée constate une reprise à 200 ms avec la v0.4.5. Le même test échoue avec le code de la v0.4.4. Les 50 tests de la session vocale passent avec la v0.4.5.
- Les 158 tests passent en debug et en release. Android Lint ne relève aucune erreur. En debug, il signale une version plus récente d’AndroidX Core et suggère les extensions Kotlin pour cette dépendance.
- L’APK principal a le code de version 11 et le certificat permanent. Il pèse 62 315 070 octets et passe sous la limite de 64 Mio de l’installateur intégré.

```text
3c080ad8721170aa474087f43f000498219d38821042e42bfde657e9eb115158  nexus-companion-v0.4.5-native-alpha-release.apk
```

Installer par-dessus Nexus Companion depuis **Mises à jour → Choisir un APK**. Après une réponse parlée, observer le retour de **Micro actif**. Le délai de réouverture du micro et le comportement acoustique restent à mesurer sur téléphone.
