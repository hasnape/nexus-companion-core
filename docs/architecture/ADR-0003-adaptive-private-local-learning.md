# ADR-0003 — Apprentissage local adaptatif et accès personnel

Date : 2026-11-13. Application Android principale 0.4.23 (code 29).

## Décision

Le cerveau demeure local et déterministe. La bibliothèque chiffrée est la source durable ; un index dérivé, également compressé et chiffré, apprend des associations de recherche et vérifie des exercices de rappel sur ces sources. Aucun poids de modèle de langage n’est entraîné, aucun code externe n’est exécuté et aucune capacité GPU/NPU n’est annoncée.

La règle du propriétaire autorise le rappel du prénom et de la ville sans code. Les informations personnelles complètes et la gestion des notes demandent un code utilisateur distinct de l’identité et du code créateur. Aucun code par défaut n’est livré.

## Accès aux données

| Action | Sans code | Avec session utilisateur |
| --- | --- | --- |
| « Comment je m’appelle ? » | Premier prénom explicitement enregistré | Même réponse minimale |
| « Où est-ce que j’habite ? » | Ville explicitement enregistrée, jamais rue/numéro | Même réponse minimale |
| Nom complet, métier, préférences, famille, adresse | Demande du code, aucune valeur ou source affichée | Réponse fondée sur les notes disponibles |
| Liste, édition, historique des notes | Écran sécurisé | Gestion des notes |
| Lecture d’une note générale utilisable | Possible | Possible |
| Texte copié | Écran sécurisé avant aperçu | Aperçu puis lecture explicite |
| Réponse Chrome contenant du texte personnel | Demande du code avant lecture | Lecture explicite, source conservée |
| Mot de passe, code ou identifiant secret | Pas de lecture conversationnelle | Pas de lecture conversationnelle |

La projection publique utilise uniquement des faits explicites issus de notes manuelles. Une phrase à la première personne importée d’Internet ne devient pas l’identité du propriétaire. Les valeurs manuelles non classées sont privées par défaut ; seules des définitions générales reconnues sont utilisables publiquement. Les contradictions sont signalées ; une note expirée est exclue. Le code est un contrôle de consultation, indépendant du chiffrement AES-GCM de la bibliothèque.

Le code de 6 à 12 chiffres est saisi dans une activité non exportée, sans microphone et protégée contre les captures. Seuls un sel aléatoire et un dérivé PBKDF2-HMAC-SHA256 (600 000 itérations) sont persistés, dans une enveloppe chiffrée par Android Keystore. La comparaison est constante en temps. Après cinq erreurs, un délai persistant d’une minute s’applique ; après dix erreurs, le verrouillage Android est requis pour remplacer le code. Le remplacement passe par la confirmation du verrouillage Android ; en l’absence de verrouillage configuré, un code existant n’est pas remplacé. La création initiale est explicite et utilise aussi le verrouillage Android lorsqu’il existe.

L’autorisation reste en mémoire pendant cinq minutes au maximum et n’est jamais renouvelée par la répétition d’une réponse. Quitter Nexus la révoque. Le texte privé, les sources, les dialogues et la lecture vocale sont alors effacés ou arrêtés. Les résultats asynchrones sont contrôlés avant affichage et lecture ; la question initiale ne reprend qu’après un retour sécurisé valide. Une panne ou une corruption des identifiants ferme l’accès sans effacer les notes.

## Adaptation aux ressources réelles

Chaque échange et lot mesure CPU logiques, RAM totale/disponible, limite de tas Java, stockage libre, batterie/charge, économie d’énergie et niveau thermique Android. Aucun identifiant unique d’appareil n’est enregistré.

| Profil | Notes dans le contexte | Budget JSON | Temps JS par appel | Lot de consolidation |
| --- | ---: | ---: | ---: | ---: |
| Économe / pression | 12 | 96 000 caractères | 4 secondes | En pause |
| Équilibré | 48 | 256 000 caractères | 5 secondes | 12 notes |
| Étendu | 128 | 512 000 caractères | 8 secondes | 32 notes |

Le profil étendu requiert au moins six CPU logiques, 6 Gio de RAM totale, 2 Gio disponibles et 384 Mio de tas autorisé. La pression mémoire, moins de 512 Mio disponibles, moins de 192 Mio de tas, moins de 256 Mio de stockage, la chauffe modérée ou supérieure, l’économie d’énergie et une batterie à 15 % ou moins hors charge réduisent le budget et mettent la consolidation en pause. Les limites de l’OS restent respectées.

Au premier plan, les lots sont plafonnés à trois notes toutes les trente secondes après un délai initial de cinq secondes, hors réponse, parole et dialogue. Les commandes utilisateur ont priorité sur les nouveaux lots. En arrière-plan, JobScheduler propose une maintenance périodique d’au moins quinze minutes, uniquement en charge, avec batterie et stockage suffisants. Android décide du moment effectif. La maintenance ne demande aucun réseau ni enregistrement audio/vidéo et peut être désactivée.

## Consolidation, provenance et compression

Chaque entrée de l’index référence l’ID et une empreinte SHA-256 du titre, texte, source, date, expiration et état d’édition de sa note. La consolidation part de la bibliothèque existante ; les nouveaux textes externes demandent toujours une confirmation avant d’y entrer. Les associations sont réellement utilisées pour sélectionner le contexte, puis les sources actives sont relues avant toute réponse.

Les questions de rappel sont vérifiées par le même moteur partagé que les réponses. Un exercice réussi prouve seulement la récupération d’une source ; il ne prouve pas que la source est vraie. Ni la consolidation ni la confirmation utilisateur ne marquent une source comme vérifiée. Les contradictions ne sont pas résolues automatiquement par la date la plus récente.

L’index est dérivé et reconstructible. Une modification, suppression ou expiration invalide ses associations avant utilisation. Les versions historiques ne réentrent pas dans le contexte. Le cache utilise la compression existante puis AES-GCM et AtomicFile ; un cache corrompu est ignoré et reconstruit, sans réinitialiser la bibliothèque primaire. Les index complets ne sont pas réécrits à chaque passage au repos. Les originaux, l’historique, le modèle vocal embarqué et le format de bibliothèque sont conservés.

## Validation requise

Les scénarios de l’audit précédent deviennent un test de régression de 35 cas, dans une session explicitement autorisée. Des tests séparés couvrent la consultation sans code, le code saisi comme texte (inefficace), les vues et répétitions privées, les confirmations, les limites temporelles, les erreurs persistantes, la récupération, l’invalidation de l’index, la pression matérielle, l’absence de réécriture inutile et les contraintes du travail automatique. Le moteur Rhino réel vérifie aussi la 128e source.

La validation automatisée ne remplace pas une validation acoustique ou une mesure matérielle sur le téléphone du propriétaire. Les lectures Chrome restent limitées à un contenu accessible et explicitement demandé, sans navigation autonome ajoutée.

## Références de conception

- Android MemoryInfo : https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo
- Android PowerManager : https://developer.android.com/reference/android/os/PowerManager
- Android JobInfo : https://developer.android.com/reference/android/app/job/JobInfo
- OWASP, Password Storage : https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
