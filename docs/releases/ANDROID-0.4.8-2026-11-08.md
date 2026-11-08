# Nexus Companion Android 0.4.8 native alpha

## Accueil Android

Le portrait de Nexus utilise un dessin vectoriel natif. Ses yeux clignent ensemble et sa bouche s’anime pendant la lecture. Le libellé sous le portrait indique « Disponible », « À l’écoute », « Je réfléchis » ou « Nexus parle ». L’écoute s’affiche après le signal de disponibilité du micro. Les animations suivent le réglage système et s’arrêtent lorsque le portrait n’est plus visible.

Les commandes vocales, la conversation, la mémoire et les réglages sont regroupés dans des sections identifiables. Le bouton **Parler maintenant** et l’interrupteur **Réponses vocales** se trouvent dans la section **Voix**.

## Validation

- Le rendu natif a été contrôlé en simulation aux formats 393 × 852 dp et 320 × 640 dp. Le format compact utilise un texte à 150 %. Les contrôles principaux mesurent au moins 48 dp de haut.
- Les 165 tests passent en debug et en release. Android Lint ne relève aucun problème. Les tests couvrent notamment l’indicateur d’écoute et la disposition sur écran compact. Le rendu sur téléphone physique reste à confirmer.
- L’APK principal porte le code de version 14 et le certificat permanent. Ses 62 340 567 octets passent sous la limite de 64 Mio de l’installateur intégré.

```text
c27db2b3dbc15088146009484286744f6c2387c4a7e098256bd666236c2e523e  nexus-companion-v0.4.8-native-alpha-release.apk
```

Installer l’APK par-dessus Nexus Companion depuis **Mises à jour → Choisir un APK**. Sur le nouvel accueil, **Mises à jour** se trouve dans la section **Réglages**.
