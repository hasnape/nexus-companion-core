import type { ResponseContext, ResponseProvider } from '@nexus/shared';

export class LocalTemplateResponseProvider implements ResponseProvider {
  generate(context: ResponseContext): string {
    const warmPrefix = context.personality.warmth > 0.6 ? 'Avec plaisir.' : 'Compris.';
    const followUp = context.personality.curiosity > 0.6 ? 'Tu veux que je creuse un peu plus ?' : 'Je reste bref pour le moment.';
    const preference = context.knownPreferences[0] ? `Je garde en tête: ${context.knownPreferences[0]}.` : '';
    return `${warmPrefix} ${preference} J’ai noté: "${context.userMessage}". ${followUp}`.trim();
  }
}
