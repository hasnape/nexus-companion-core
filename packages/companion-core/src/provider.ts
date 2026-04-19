import { createDefaultNexusPersonalityProfile } from './personality';
import { createResponsePlan, renderDeterministicNexusReply } from './response';
import type { CompanionAiProvider, CompanionContext, CompanionDecision } from './types';

const BLOCKING_CONFIRMATION_FLAGS = new Set([
  'explicit_authorization_required',
  'creator_approval_required',
  'environment_monitoring_consent_required',
  'explicit_environment_scope_and_consent_required',
  'self_code_modification_approval_required',
  'creator_code_change_approval_required'
]);

const hasBlockingConfirmation = (decision: CompanionDecision): boolean => (
  decision.requiredConfirmations.some((flag) => BLOCKING_CONFIRMATION_FLAGS.has(flag))
);

export class LocalDeterministicAiProvider implements CompanionAiProvider {
  async generateCompanionReply(context: CompanionContext, decision: CompanionDecision): Promise<string> {
    if (hasBlockingConfirmation(decision)) {
      const plan = createResponsePlan({ context, decision, personality: createDefaultNexusPersonalityProfile() });
      return renderDeterministicNexusReply({
        ...plan,
        responseMode: 'safety',
        pendingConfirmations: Array.from(new Set([...plan.pendingConfirmations, ...decision.requiredConfirmations]))
      });
    }

    const plan = createResponsePlan({
      context,
      decision,
      personality: {
        ...createDefaultNexusPersonalityProfile(),
        creatorIdentity: context.profile.creatorIdentity.creatorId
      }
    });

    return renderDeterministicNexusReply(plan);
  }
}
