# Nexus Companion Android 0.4.15 native alpha

Cette version améliore la mémoire locale, le traitement vocal et l’usage du corps virtuel comme base de commande du futur robot roulant.

Les six cas défaillants relevés sur 0.4.14 sont corrigés : question polie sans ponctuation prise pour une note, explication dirigée vers un modèle de gestion de projet, lieu et raison absents remplacés par un fait voisin, possessif familial incorrect et proposition de mémorisation sur une question privée. Les 11 scénarios ciblés passent. Le rappel distingue aussi le membre de la famille et l’attribut demandé. Une demande explicite peut désormais proposer une connaissance générale avec « Retiens que le Soleil est une étoile ».

Le stockage conserve le format JSON schema 1 / GZIP / AES-GCM et la clé existante. La compression compare les niveaux standard et fort sur les grandes bibliothèques et garde le plus petit résultat. Le cache évite le déchiffrement et l’analyse répétés des octets inchangés tout en détectant les écritures externes et les altérations. Les doublons cosmétiques gardent leur texte, leur identité, leur source et leur date d’origine. « État de ta mémoire » expose les compteurs utiles.

Sur le jeu synthétique de 12 notes répétitives, 15316 octets de JSON occupent 684 octets chiffrés. L’économie de compression avant chiffrement est de 95 %. Ce chiffre décrit le jeu d’essai, pas un gain garanti par rapport à 0.4.14 : dans cet autre test, les deux codecs GZIP produisent 1296 et 1296 octets. Le nouveau choix n’agrandit pas le résultat de l’ancien codec et reste sans perte.

Les blocs d’entrée audio passent à 100 ms. La fin d’une capture récupère le résultat final même sans résultat partiel préalable. Le décodage de consensus Vosk et le modèle français existant sont conservés. Les confiances par mot sont lues ; une confirmation courte incertaine pendant l’échange est redemandée. Les scores de vraisemblance du format alternatif restent séparés des probabilités, et la récupération d’une séparation de mots ne peut pas changer la commande.

Le mode corps utilise un tricycle virtuel à deux roues latérales et une roue centrale directrice configurable avant/arrière. Les commandes « avance de vingt centimètres », « recule de dix centimètres » et « tourne à gauche de trente degrés » produisent un plan borné en unités physiques. Android affiche ses roues, sa distance parcourue et son cap. Le bouton Arrêter invalide aussi les plans en attente. La sortie du mode, l’arrière-plan, la perte de session et l’échéance arrêtent la simulation. Le mode plein visage reste disponible.

Le contrat partagé et ses consignes sont réutilisables par un futur adaptateur de moteurs. L’exécution actuelle est explicitement une simulation, sans connexion à un moteur. La validation de la mécanique, de l’électronique, des capteurs, des communications et de l’arrêt physique reste à faire. Le micro reste en pause pendant la synthèse ; la commande vocale d’arrêt attend donc une capture disponible, tandis que le bouton est indépendant de la lecture.

Validation : 208 tests Android passent dans chacune des variantes debug et release ; Android Lint ne relève aucune anomalie. Les 368 tests TypeScript, le contrôle de types, ESLint, la compilation des workspaces et la vérification du bundle embarqué passent. Les rendus du robot en portrait et paysage ont été inspectés. Les essais couvrent la mémoire ancienne, la déduplication, le cache et ses altérations, les données échappées, les confirmations incertaines, les deux braquages, les trajectoires, l’arrêt et la restauration sans reprise de mouvement.

Le paquet principal `fr.nexuscompanion.android` passe du code de version 20 à 21. Sa signature correspond à celle de 0.4.14 ; les permissions restent identiques. L’APK contient 62 433 715 octets, sous la limite de 64 Mio. Signature v2, alignement 16 Kio, intégrité ZIP, quatre ABI et empreintes du cerveau et du modèle sont vérifiés.

Aucun téléphone ni châssis physique n’était connecté. L’installation, la précision acoustique en situation réelle, la sortie sonore, le Bluetooth, l’autonomie et les moteurs restent à vérifier sur le matériel. Le moteur de dialogue reste local, déterministe et extractif.

```text
62c95f2b5529025b5fd58140110e3fa7ffa26e432b93b8a00613e3000f8a9596  nexus-companion-v0.4.15-native-alpha-release.apk
```

Installer l’APK principal par-dessus la version actuelle via **Réglages → Mises à jour → Choisir un APK** pour conserver les notes. Voir [ADR-0003](../architecture/ADR-0003-wheeled-body-and-native-memory.md) pour les contrats et la frontière avec le matériel.
