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
    expect(textOf(ui)).toContain('Nexus retient le minimum utile pour mieux vous accompagner.');
    expect(textOf(ui)).toContain('Les informations sensibles nécessitent votre accord');
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
