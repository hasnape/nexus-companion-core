# Nexus Companion Android 0.4.14 native alpha

Les questions ordinaires exploitent les notes locales pertinentes. Un vocabulaire partagé entre la sélection native et la formulation extrait les faits utiles et adapte les formulations personnelles courantes. Une question sans ponctuation ne devient pas une nouvelle mémoire. Si deux informations personnelles se contredisent, Nexus demande laquelle est à jour.

Lorsqu’une question publique n’a pas de réponse locale, Nexus peut proposer de consulter Wikipédia. La recherche attend un accord explicite, puis la sauvegarde du résultat et de sa source attend un second accord. Les notes et l’audio ne sont pas envoyés. Les informations retenues gardent le statut non vérifié et sont retrouvables hors ligne. La proposition expire après 90 secondes ; un refus, un nouveau sujet, un dialogue privé, l’arrêt du micro ou l’arrière-plan l’annulent. Les informations sensibles restent dans la relecture manuelle.

Une réponse ouvre une fenêtre de conversation de 12 secondes à partir du micro prêt, après la fin confirmée de la lecture. Le mot « Nexus » n’est pas nécessaire pendant cette fenêtre. Le micro reste fermé pendant la synthèse et une lecture échouée n’ouvre pas cette fenêtre.

Le mode robot ajoute un corps turquoise, un visage ivoire, des yeux verts et des pupilles. Le mode plein visage garde son accès, son cadrage et ses contrôles. « Affiche ton corps », « affiche ton visage » et « retour à l’accueil » se commandent aussi à la voix. Les états du regard et de la bouche suivent l’écoute, la réflexion et la lecture réelles.

L’adaptation de Nexus Brain reprend les principes de pertinence minimale, de priorité aux connaissances locales, de traçabilité et de clarification des candidats ambigus. Références lues dans `hasnape/Nexus-Help-Desk-N1` : `docs/NEXUS_BRAIN_DOCTRINE.md` (a2186a2), `src/lib/nexusBrainLocalCore.mjs` (3a430de), `supabase/functions/_shared/ai/companyKnowledgeRetrieval.mjs` (a029e59). Le projet Support Hub n’a pas été modifié. Le moteur du compagnon reste déterministe et extractif ; l’apprentissage ajoute des connaissances à la mémoire locale sans entraîner un modèle génératif.

Les filtres existants continuent d’exclure les notes sensibles du rappel conversationnel. La sélection reste lexicale, avec des rapprochements de vocabulaire ciblés. Les réponses reformulent des extraits pertinents ; elles ne couvrent pas toutes les questions générales.

Les 192 tests Android passent dans chacune des variantes debug et release, ainsi qu’Android Lint sans anomalie. Les 344 tests TypeScript, le contrôle de types, ESLint, la compilation de tous les workspaces et la vérification du bundle embarqué passent. Les essais couvrent la recherche proposée, le double accord, la déduplication, les refus, l’expiration, les changements de sujet, l’arrêt de session, le rappel hors ligne avec source, les anciennes notes chiffrées et compressées, le micro fermé pendant la lecture et les deux modes d’affichage. Une requête HTTPS réelle « un volcan » a retrouvé l’article « Volcan ». Les rendus portrait et paysage ont été inspectés en simulation Android.

Le paquet principal `fr.nexuscompanion.android` passe du code de version 19 à 20. Son certificat correspond à celui de l’APK 0.4.13. Ses 62 393 363 octets restent sous la limite de 64 Mio de l’installateur intégré. Les permissions sont identiques à celles de 0.4.13. Le modèle français, les quatre ABI, le contenu ZIP, l’alignement 16 Kio et la signature v2 sont contrôlés.

Les tests Android simulent le micro et la synthèse. Aucun téléphone n’était connecté à cet environnement. L’installation, la précision de la reconnaissance en situation réelle, le son, le Bluetooth et l’autonomie restent à valider sur téléphone.

```text
0c5213f72cd0a1c8eb5130ff477bd71399ccb1e1e279079e5f98f60d8e51bac1  nexus-companion-v0.4.14-native-alpha-release.apk
```

Installer cet APK principal par-dessus Nexus Companion via **Réglages → Mises à jour → Choisir un APK** pour conserver les notes. Garder **Réponses vocales** activé pour le dialogue parlé.
