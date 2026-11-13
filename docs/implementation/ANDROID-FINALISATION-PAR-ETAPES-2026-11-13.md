# Finalisation Android par étapes

Base livrée : 0.4.26, commit 7e0d65ac5770cc37f5d30c26d05807b93cb8baaf. Une fonction présente dans le code n’est pas une mesure de son fonctionnement acoustique ou physique.

| Étape | État | Résultat et preuve |
| --- | --- | --- |
| 1. Notes et LLM | Implémentée en 0.4.27 ; essai sur téléphone à réaliser | Activation volontaire, sélection locale de phrases autorisées, citations exactes et contrôle du code utilisateur. Tests de droits, secrets, changements de note et négations ; trois inférences JNI réelles sur des notes synthétiques. |
| 2a. Sauvegarde des notes | Implémentée en 0.4.27 ; essai sur téléphone à réaliser | Toutes les notes et leurs versions, archive chiffrée par phrase secrète, aperçu, confirmation, remplacement atomique et retour chiffré. Tests de mauvais secret, altération, capacité, graphe de versions, conflits et échecs d’écriture ; parcours d’interface export/import/retour. |
| 2b. Export complet du cerveau | À compléter après définition des profils | Inclure l’identité et les réglages compatibles avec un format versionné et une migration. Le cache d’apprentissage dérivé peut se reconstruire depuis les notes ; les clés et le code d’accès ne doivent pas être transférés comme des données ordinaires. La sauvegarde 2a n’est pas présentée comme cet export complet. |
| 3. Plusieurs compagnons | À implémenter | Identités et espaces de mémoire distincts, changement de profil sans session privée héritée. Vérifier l’isolation des notes, clés, index et contextes, puis la migration du profil existant. Les espaces de noms internes existent mais ne constituent pas un sélecteur de compagnons. |
| 4. Voix prolongée | Écoute au premier plan livrée ; arrière-plan à implémenter | Option explicite, notification et arrêt visible. Vérifier cycle de vie, permissions, verrouillage, consommation et audio sur téléphone. Aucun service d’écoute en arrière-plan n’a été ajouté en 0.4.27. |
| 5. Perception | À implémenter | Caméra et reconnaissance du locuteur avec consentement, sans remplacer le code utilisateur. Vérifier modèles, licences, captures réelles, faux positifs et refus. |
| 6. Corps physique | Matériel et protocole à préciser | Adaptateur propre au robot, limites et arrêt d’urgence. Nécessite une connexion réelle et des essais de mouvement et d’arrêt. |

Les étapes 4 à 6 ne peuvent être déclarées validées sur appareil sans téléphone ou robot connecté. La vision et la reconnaissance de locuteur ne doivent pas être remplacées par un bouton ou une simulation prétendant avoir reconnu quelqu’un. Le corps virtuel déjà livré reste distinct du pilotage matériel.

## Vérification immédiate sur le téléphone

1. Mettre à jour vers 0.4.27 sans désinstaller. Créer une note sans information sensible pour l’essai, par exemple « Je préfère le thé vert. Je ne bois pas de café. ».
2. Dans **Modèle de langage local**, installer le modèle s’il est absent, puis activer le modèle et **Utiliser mes notes avec le modèle local**. Déverrouiller avec le code utilisateur et demander quelle boisson est préférée. Vérifier la citation et le titre de la note utilisée. Verrouiller puis répéter : aucune note privée ne doit être révélée.
3. Dans **Sauvegarde de ma mémoire**, déverrouiller, saisir deux fois une phrase secrète de 12 à 128 caractères et créer un fichier `.nxb`. Conserver cette phrase séparément. Vérifier ensuite l’archive par l’écran d’import ; le simple aperçu ne doit pas remplacer la mémoire. Une restauration explicitement confirmée doit proposer le retour à l’état précédent.
4. Lancer **Test vocal sur ce téléphone** : prononcer les deux phrases affichées, apprécier le volume, observer la pause pendant la réponse et la reprise, puis exporter le rapport. Répéter en mode avion après installation des modèles.
5. Relever la batterie avant et après une durée fixe, en notant le temps d’écoute et le nombre de réponses LLM. Garder volume, luminosité et conditions comparables. Aucun résultat d’autonomie n’est encore disponible. À ce stade, l’écoute doit s’arrêter quand l’application passe en arrière-plan.

[Rapport de livraison 0.4.27](../releases/ANDROID-0.4.27-2026-11-13.md).
