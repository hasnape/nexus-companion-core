# Source native récupérée de la version 0.4.25

Les changements TypeScript et Kotlin non publiés des versions 0.4.24/0.4.25 et la première intégration LLM ont été perdus lors de la réinitialisation du poste de développement. L’APK 0.4.25 signé a été conservé.

`nexus-brain.js` est la source JavaScript exécutable extraite **sans modification** de cet APK. Son SHA-256 est `48da6489e14dd595c48706c0d65f9d3e3c72b83d3d062b22636968bf4deaf779`. Le manifeste d’origine est conservé à côté comme provenance, avec les empreintes des sources TypeScript disparues. Il ne constitue pas une restitution de ces fichiers TypeScript.

Le constructeur Android utilise ce snapshot par défaut et vérifie son empreinte. Le manifeste produit référence les fichiers effectivement disponibles. Les sources typées du Web restent celles de la base publiée 0.4.23 ; leurs 438 tests ne sont pas présentés comme les 472 tests perdus de 0.4.25. `--from-typescript` permet un travail explicite sur cette ancienne base, mais il ne faut pas l’utiliser pour remplacer silencieusement le moteur Android récupéré.

Le routage contextuel Kotlin, les sources affichées, la confiance vocale par mot et la recherche par passages sont reconstitués à partir de la comparaison des deux APK. Ils sont couverts par les tests Android actuels ; cette reconstruction n’est pas une restauration octet par octet du Kotlin d’origine.
