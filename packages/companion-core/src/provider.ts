import type { CompanionAiProvider, CompanionContext, CompanionDecision } from './types';

export class LocalDeterministicAiProvider implements CompanionAiProvider {
  async generateCompanionReply(context: CompanionContext, decision: CompanionDecision): Promise<string> {
    switch (decision.intent) {
      case 'safety_refusal':
        return 'Je ne peux pas aider pour une demande dangereuse ou illégale. Je peux proposer une alternative sûre.';
      case 'ask_clarification':
        return 'Je veux bien vous aider. Pouvez-vous préciser votre besoin en une phrase ?';
      case 'emotional_support':
        return 'Merci de me le confier. Je suis avec vous, on peut avancer étape par étape.';
      case 'project_help':
        return 'D’accord. Je peux structurer cela en objectifs, étapes concrètes et priorités.';
      case 'action_request':
        return 'Compris. Je peux préparer les prochaines actions et vous proposer une check-list.';
      case 'remember_candidate':
        if (decision.requiredConfirmations.length > 0) {
          return 'Cela peut m’aider pour la suite. Voulez-vous que je retienne cette information sensible ?';
        }
        return 'Bonne idée, je peux retenir cette information utile pour mieux vous accompagner.';
      case 'answer':
      default:
        return context.profile.languagePreference === 'fr-FR'
          ? 'Voici une réponse claire et concise. Si vous voulez, je peux détailler.'
          : 'Here is a clear and concise answer. I can expand if you want.';
    }
  }
}
