import React from 'react';
import { describe, expect, it } from 'vitest';
import { CompanionFaceScreen } from './CompanionFaceScreen';
import type { CompanionAction, InternalState } from '@nexus/shared';

const baseState: InternalState = {
  mode: 'idle',
  mood: 'neutral',
  energy: 0.5,
  socialDrive: 0.5,
  attentionTarget: 'user',
  lastInteractionAt: 0,
  lastUserSeenAt: 0
};

const baseAction: CompanionAction = {
  name: 'idle_happy',
  category: 'idle',
  intensity: 0.5,
  durationMs: 1200,
  priority: 2,
  interruptible: true,
  mappingHint: 'idle-happy'
};

const toText = (node: React.ReactNode): string => {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (!node) return '';
  if (Array.isArray(node)) return node.map(toText).join('');
  if (React.isValidElement(node)) return toText(node.props.children);
  return '';
};

const findElements = (node: React.ReactNode, predicate: (element: React.ReactElement) => boolean): React.ReactElement[] => {
  const matches: React.ReactElement[] = [];
  const visit = (current: React.ReactNode) => {
    if (!current) return;
    if (Array.isArray(current)) {
      current.forEach(visit);
      return;
    }
    if (!React.isValidElement(current)) return;
    if (predicate(current)) matches.push(current);
    visit(current.props.children);
  };
  visit(node);
  return matches;
};

const renderFace = (overrides?: {
  state?: InternalState;
  action?: CompanionAction;
  isListening?: boolean;
  transcript?: string;
  subtitle?: string;
}) => CompanionFaceScreen({
  state: overrides?.state ?? baseState,
  action: overrides?.action ?? baseAction,
  subtitle: overrides?.subtitle,
  isListening: overrides?.isListening ?? false,
  transcript: overrides?.transcript
});

describe('CompanionFaceScreen', () => {
  it('renders safely with minimal required props', () => {
    const ui = renderFace();
    expect(toText(ui)).toContain('idle • idle_happy');
  });

  it('renders safely when listening with an empty transcript', () => {
    const ui = renderFace({ isListening: true, transcript: '' });
    expect(toText(ui)).toContain('Listening');
    expect(toText(ui)).not.toContain('“”');
  });

  it('shows transcript feedback when listening and transcript exists', () => {
    const ui = renderFace({ isListening: true, transcript: 'Testing transcript feedback' });
    expect(toText(ui)).toContain('Mic live');
    expect(toText(ui)).toContain('Testing transcript feedback');
  });

  it('handles long transcript text without crashing', () => {
    const longTranscript = 'This is a very long transcript '.repeat(80);
    const ui = renderFace({ isListening: true, transcript: longTranscript });
    expect(toText(ui)).toContain('This is a very long transcript');
  });

  it('handles unknown or undefined mood/mode values defensively', () => {
    const weirdState = {
      ...baseState,
      mood: undefined,
      mode: 'mystery-mode'
    } as unknown as InternalState;

    const ui = renderFace({ state: weirdState });
    expect(toText(ui)).toContain('mystery-mode');
  });

  it('activates speaking visual indicator when mode is speaking', () => {
    const ui = renderFace({ state: { ...baseState, mode: 'speaking' } });
    const mouth = findElements(ui, (element) => element.props.className?.includes('mouth'))[0];
    expect(mouth.props.className).toContain('is-speaking');
    expect(toText(ui)).toContain('Speaking');
  });

  it('activates listening indicator when isListening is true', () => {
    const ui = renderFace({ isListening: true });
    expect(toText(ui)).toContain('Listening');
    expect(toText(ui)).toContain('Mic live');
  });

  it('does not show false listening or speaking feedback while idle', () => {
    const ui = renderFace({ state: { ...baseState, mode: 'idle' }, isListening: false });
    const mouth = findElements(ui, (element) => element.props.className?.includes('mouth'))[0];
    expect(toText(ui)).toContain('idle');
    expect(toText(ui)).not.toContain('Mic live');
    expect(toText(ui)).not.toContain('Listening');
    expect(mouth.props.className).not.toContain('is-speaking');
  });
});
