# Nexus Companion Android 0.4.18

Cette version améliore les relances et permet de corriger les notes avec un historique local chiffré. Elle conserve l’application principale, son certificat et ses données. Le moteur reste local, déterministe et extractif ; la reconnaissance acoustique et le modèle français ne changent pas dans cette livraison.

## Conversation

Sur 0.4.17, demander « répète ta réponse » après une proposition de recherche supprimait sa confirmation en attente. Le test ajouté échoue sur le code précédent et passe avec cette version. La répétition reprend le texte, sans exécuter de nouveau la recherche, la sauvegarde ou le plan de déplacement. Elle ne prolonge pas le délai d’une approbation.

Les relances exactes « Et mon prénom ? », « Et pour mon métier ? » et les variantes prévues pour le nom, la profession, l’âge, la voiture et certaines préférences sont interprétées comme des questions complètes. Une déclaration telle que « Et mon métier est technicien informatique » reste une déclaration. Les informations absentes ne sont pas inventées.

Après une réponse fondée sur une seule note, « lis cette note » ou « lis la note complète » reprend ce contenu complet. « Modifie cette note » ou « corrige cette note » ouvre l’éditeur correspondant, sans enregistrer à la place de l’utilisateur. Si plusieurs notes ont servi, Nexus demande une précision. Il ne prend pas une note récente sans rapport.

Le contexte reste en mémoire vive, pour cinq minutes. Une fin effective de lecture de réponse peut renouveler ce contexte de lecture, mais ne renouvelle ni ne réactive une confirmation. Les simples acquittements du micro ne le renouvellent pas. Arrière-plan, dialogue privé et fermeture l’effacent. Avant une relance, les notes utilisées sont comparées à leur état actuel, contenu et expiration compris : suppression, correction ou modification externe empêchent de rejouer une ancienne réponse comme actuelle.

## Notes et historique

**Mes notes → ouvrir une note → Modifier** présente le titre et le texte complet. Seul **Enregistrer** crée une nouvelle version. Annuler et enregistrer sans changement conservent le fichier original. Un formulaire devenu obsolète à cause d’une autre modification est refusé plutôt que d’écraser la nouvelle version.

Les anciennes versions conservent leur texte, leur titre, leur date, leur source et leur expiration dans le même stockage privé chiffré. Elles sont exclues des notes actives, de la recherche et des réponses. **Anciennes versions** permet de les consulter ; **Restaurer cette version** demande une confirmation distincte et crée une nouvelle version, sans effacer les corrections intermédiaires. La suppression permanente annonce explicitement puis retire toute la chaîne de cette note, en laissant les autres notes intactes.

Une correction garde la provenance et l’expiration d’origine et porte le statut « modifiée par toi », non vérifié. Une origine Wikipédia ne transforme donc pas la correction manuelle en citation de Wikipédia. Les réponses et la lecture complète indiquent cette distinction.

## Stockage et capacités

Le schéma JSON 1 existant reste pris en charge. Les chaînes de versions et les notes marquées modifiées utilisent explicitement le schéma 2. Le lecteur valide les identifiants uniques et les liens : ni cycle, ni cible absente, ni branchement ambigu ne sont acceptés. La validation, la consultation et la suppression des chaînes sont itératives pour supporter un historique long. Un fichier invalide ou une erreur de chiffrement n’entraîne pas son remplacement.

La capacité reste de **20 000 notes actives** et passe à **40 000 enregistrements au total**, historiques compris. Les **16 000 caractères par note** et les **32 Mio de JSON avant compression** s’appliquent conjointement avec ces plafonds. À saturation, une modification est refusée sans perdre le texte actuel. Le mécanisme GZIP, AES-GCM, AtomicFile, le nom de fichier et la clé Keystore sont conservés. Aucun historique de conversation ni audio n’est ajouté au stockage.

Les capacités de 0.4.17 restent disponibles : 32 notes candidates, 100 résultats de recherche, fenêtre vocale de 30 secondes, dictée continue bornée à 178 secondes, propositions de 300 secondes et lecture découpée jusqu’à 20 000 caractères. L’importeur conserve ses plafonds de 512 Mio par APK et 1 Gio de cache. Le paquet distribué reste sous 64 Mio pour les anciens importeurs.

## Validation

- **266 tests Android dans chacune des variantes debug et release**, sans échec, erreur ni test ignoré ; **zéro anomalie Android Lint**.
- **381 tests TypeScript**, contrôle de types, ESLint, compilation des workspaces et vérification de concordance du bundle réussis.
- Les tests couvrent la conservation des confirmations après répétition, leur expiration, l’absence de répétition des mouvements et recherches, les références ambiguës, la lecture complète et le refus de preuves supprimées, expirées ou modifiées extérieurement.
- Les tests de stockage et d’interface vérifient l’édition après enregistrement explicite, l’annulation, l’écriture concurrente, la restauration avec relecture, la suppression de l’historique, l’échec de chiffrement et les chaînes invalides. Une chaîne valide de **40 000 versions** est chargée et supprimée sans récursion ; une correction à saturation échoue en conservant le fichier existant.
- Les scénarios existants de voix indépendante du micro, pauses, lecture découpée, pupilles, plein visage, corps roulant, arrêts et mise à jour restent passants.

Les tests s’exécutent dans Rhino et Robolectric avec source micro simulée et chiffrement AES-GCM de JVM. Aucun téléphone ni châssis physique n’est connecté : précision acoustique, son réel, Bluetooth, autonomie et mouvements matériels ne sont pas mesurés.

## APK principal

Paquet `fr.nexuscompanion.android`, version `0.4.18-native-alpha`, code **24**, Android minimum 31, cible 35. L’APK fait **62 483 399 octets**, sous 64 Mio. Signature v2 valide et certificat permanent identique à 0.4.17 ; ZIP intègre et alignement 16 Kio vérifié. Les permissions, les quatre ABI, les huit bibliothèques natives, le modèle Vosk et les notices sont identiques à 0.4.17. Les profils d’optimisation DEX sont régénérés avec le code.

Arbre source compilé : `e413a088ba86bc0c5f0f242bac404618fb3a3264`. Seuls les deux README et ce compte rendu sont actualisés après cette compilation. Le bundle embarqué a pour SHA-256 `348378178b6c718713316c16dbecd650812fbff9869c54659cacdf52f4b3cf31`.

```text
000159be65dd904d77a7f382a0369876d61ef2caee666c31b106e3d3a1b03438  nexus-companion-v0.4.18-native-alpha-release.apk
```

Installer cet APK par-dessus l’application principale, **sans désinstallation**, afin de conserver les notes et la clé Keystore. Depuis un ancien écran affichant une erreur de cache, ouvrir l’APK dans **Mes fichiers → Téléchargements**, puis confirmer **Mettre à jour**. Le format avec historique est lu par 0.4.18 ; un retour à un ancien APK ne prend pas en charge le schéma 2.
