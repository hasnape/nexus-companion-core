# Dialogues français locaux — intégration progressive

## Source et périmètre
- `packages/companion-core/src/dialogues-fr.ts` est la bibliothèque exécutable partagée (règles courtes, non personnelles).
- `apps/face-web/src/data/dialogues.fr.json` est un jeu de données initial non encore branché. Il ne faut pas le considérer comme source unique ni promettre que toutes ses entrées sont actives.
- `renderLocalSmallTalk` dans `packages/companion-core/src/response.ts` conserve les règles historiques prioritaires, puis utilise la bibliothèque en repli.
- Les expressions ne sont reconnues que comme énoncés complets ; une question métier, une demande de mémorisation ou un sujet sensible doit rester traité par le moteur actuel.

## Android natif
L'application Android utilise un bundle JS partagé scellé et un interpréteur natif. Une modification TypeScript **n'est pas une mise à jour Android installable** tant que le bundle n'est pas reconstruit et que les tests Gradle ne passent pas.

Depuis la racine du dépôt :
```bash
npm ci
npm run build:native-brain
npm run check:native-brain
npm run lint
npm run typecheck
npm run test
npm run build
```
Puis dans `apps/companion-android` (JDK 17, SDK 35) :
```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```
Vérifier ensuite la priorité du pont JS dans les réponses natives et le fonctionnement sans réseau, avant de préparer une version signée avec le keystore existant. Ne jamais committer le keystore ni ses secrets.

## Critères de réception
1. Les phrases de politesse simples répondent hors ligne en texte et à l'oral.
2. « Bonjour, comment réparer mon PC ? » n'est pas absorbé par une salutation.
3. Les demandes de mémoire et les règles de sécurité restent prioritaires.
4. Les souvenirs et l'identité proviennent du profil actif, jamais d'une phrase statique.
5. Le moteur natif Android utilise effectivement les nouvelles réponses ; un test du bundle JS seul ne suffit pas.
6. Mise à jour APK : package, certificat et versionCode cohérents ; sauvegarde des données préservée.

## État
Code TypeScript et tests ajoutés sur une branche isolée. Compilation, vérification du bundle scellé, tests Android réels et APK signée non effectués dans cette session.
