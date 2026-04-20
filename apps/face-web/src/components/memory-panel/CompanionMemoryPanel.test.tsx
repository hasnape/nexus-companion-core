import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { CompanionMemoryPanel } from './CompanionMemoryPanel';

const textOf = (node: React.ReactNode): string => {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (!node) return '';
  if (Array.isArray(node)) return node.map(textOf).join('');
  if (!React.isValidElement(node)) return '';
  if (typeof node.type === 'function') {
    return textOf((node.type as (props: Record<string, unknown>) => React.ReactNode)(node.props));
  }
  return textOf(node.props.children);
};

const findButton = (node: React.ReactNode): React.ReactElement | undefined => {
  if (!node) return undefined;
  if (Array.isArray(node)) {
    for (const child of node) {
      const found = findButton(child);
      if (found) return found;
    }
    return undefined;
  }
  if (!React.isValidElement(node)) return undefined;
  if (node.type === 'button') return node;

  if (typeof node.type === 'function') {
    return findButton((node.type as (props: Record<string, unknown>) => React.ReactNode)(node.props));
  }

  return findButton(node.props.children);
};

describe('CompanionMemoryPanel', () => {
  it('shows revised French-first privacy-safe copy', () => {
    const ui = CompanionMemoryPanel({
      memories: [],
      memoryCandidates: [],
      onClearMemory: async () => {}
    });

    expect(textOf(ui)).toContain('Mémoire de Nexus');
    expect(textOf(ui)).toContain('Nexus apprend progressivement à partir de vos échanges');
    expect(textOf(ui)).toContain('les informations sensibles nécessitent votre accord');
  });

  it('shows grouped summary headings', () => {
    const ui = CompanionMemoryPanel({
      memories: [{
        id: 'm1',
        type: 'user_preference',
        layer: 'preference',
        content: 'Je préfère un mode offline.',
        source: 'user_message',
        confidence: 0.8,
        importance: 0.8,
        createdAt: Date.now(),
        updatedAt: Date.now()
      }],
      memoryCandidates: [],
      onClearMemory: async () => {}
    });

    expect(textOf(ui)).toContain('Préférences');
    expect(textOf(ui)).toContain('Projets');
    expect(textOf(ui)).toContain('Contexte');
    expect(textOf(ui)).toContain('Souvenirs récents');
  });

  it('hides raw app_state JSON memories and still renders clean user memories', () => {
    const ui = CompanionMemoryPanel({
      memories: [
        {
          id: 'raw-1',
          type: 'conversation_summary',
          layer: 'environment_context',
          content: '{"id":"signal-1","type":"app_mode","value":"thinking","source":"app_state","capturedAt":123,"storagePreference":"local","consentRequired":false}',
          source: 'environment_signal',
          confidence: 0.6,
          importance: 0.5,
          createdAt: Date.now(),
          updatedAt: Date.now()
        },
        {
          id: 'proj-1',
          type: 'project_context',
          layer: 'project_context',
          content: 'Souviens-toi que mon projet actuel est Nexus Companion.',
          source: 'user_message',
          confidence: 0.9,
          importance: 0.9,
          createdAt: Date.now(),
          updatedAt: Date.now()
        }
      ],
      memoryCandidates: [{
        id: 'raw-candidate',
        type: 'conversation_summary',
        layer: 'environment_context',
        content: '{"id":"signal-mode","type":"app_mode","source":"app_state","capturedAt":1}',
        source: 'environment_signal',
        confidence: 0.6,
        importance: 0.4,
        createdAt: Date.now(),
        updatedAt: Date.now()
      }],
      onClearMemory: async () => {}
    });

    const text = textOf(ui);
    expect(text).toContain('Souvenirs enregistrés : 1');
    expect(text).toContain('Le projet actuel est Nexus Companion.');
    expect(text).toContain('Projets');
    expect(text).not.toContain('"source":"app_state"');
    expect(text).not.toContain('capturedAt');
    expect(text).not.toContain('Suggestions de mémoire');
  });

  it('renders user-authored memories that mention app_state in prose or config text', () => {
    const ui = CompanionMemoryPanel({
      memories: [
        {
          id: 'prose-app-state',
          type: 'conversation_summary',
          layer: 'episodic',
          content: 'Souviens-toi que mon exemple JSON contient "source":"app_state".',
          source: 'user_message',
          confidence: 0.8,
          importance: 0.8,
          createdAt: Date.now(),
          updatedAt: Date.now()
        },
        {
          id: 'config-app-state',
          type: 'project_context',
          layer: 'project_context',
          content: 'Voici une config: {"source":"app_state","enabled":true}',
          source: 'user_message',
          confidence: 0.8,
          importance: 0.8,
          createdAt: Date.now(),
          updatedAt: Date.now()
        }
      ],
      memoryCandidates: [],
      onClearMemory: async () => {}
    });

    const text = textOf(ui);
    expect(text).toContain('Souvenirs enregistrés : 2');
    expect(text).toContain('mon exemple JSON contient "source":"app_state".');
    expect(text).toContain('Voici une config: {"source":"app_state","enabled":true}');
  });

  it('keeps sensitive location memories in pending confirmations instead of confirmed souvenirs', () => {
    const ui = CompanionMemoryPanel({
      memories: [
        {
          id: 'loc-1',
          type: 'user_profile',
          layer: 'episodic',
          content: 'Souviens-toi que j’habite à Lyon',
          source: 'user_message',
          confidence: 0.8,
          importance: 0.8,
          sensitive: true,
          requiresConfirmation: true,
          lifecycleState: 'pending_confirmation',
          createdAt: Date.now(),
          updatedAt: Date.now()
        }
      ],
      memoryCandidates: [],
      onClearMemory: async () => {}
    });

    const text = textOf(ui);
    expect(text).toContain('Souvenirs enregistrés : 0');
    expect(text).toContain('Retenir que vous habitez à Lyon ?');
  });

  it('normalizes orphan Companion wrappers and deduplicates project memories in display', () => {
    const now = Date.now();
    const ui = CompanionMemoryPanel({
      memories: [
        {
          id: 'proj-a',
          type: 'project_context',
          layer: 'project_context',
          content: 'Nexus Companion est mon projet.',
          source: 'user_message',
          confidence: 0.8,
          importance: 0.8,
          createdAt: now,
          updatedAt: now
        },
        {
          id: 'proj-b',
          type: 'project_context',
          layer: 'project_context',
          content: 'Companion souviens-toi que mon projet actuel est Nexus Companion',
          source: 'user_message',
          confidence: 0.9,
          importance: 0.9,
          createdAt: now + 1,
          updatedAt: now + 1
        }
      ],
      memoryCandidates: [],
      onClearMemory: async () => {}
    });

    const text = textOf(ui);
    expect(text).toContain('Souvenirs enregistrés : 1');
    expect(text).toContain('Le projet actuel est Nexus Companion.');
    expect(text).not.toContain('Companion souviens-toi');
  });

  it('clear memory button still calls onClearMemory', () => {
    const onClearMemory = vi.fn(async () => {});
    const ui = CompanionMemoryPanel({
      memories: [],
      memoryCandidates: [],
      onClearMemory
    });

    const button = findButton(ui);
    expect(button).toBeDefined();
    button?.props.onClick();

    expect(onClearMemory).toHaveBeenCalledTimes(1);
  });
});
