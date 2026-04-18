import React from 'react';
import { describe, expect, it } from 'vitest';
import { CompanionFaceScreen } from './CompanionFaceScreen';
import type { CompanionAction, InternalState } from '@nexus/shared';
import type { CompanionVisualState } from './companionVisualState';

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

const findFirstByClass = (node: React.ReactNode, classFragment: string): React.ReactElement | undefined =>
  findElements(node, (element) => typeof element.props.className === 'string' && element.props.className.includes(classFragment))[0];

const renderFace = (overrides?: {
  state?: InternalState;
  action?: CompanionAction;
  isListening?: boolean;
  transcript?: string;
  subtitle?: string;
  isOnline?: boolean;
  companionVisualState?: CompanionVisualState;
  companionVisualStateLabel?: string;
}) => CompanionFaceScreen({
  state: overrides?.state ?? baseState,
  action: overrides?.action ?? baseAction,
  companionVisualState: overrides?.companionVisualState ?? 'idle',
  companionVisualStateLabel: overrides?.companionVisualStateLabel ?? 'Au repos',
  subtitle: overrides?.subtitle,
  isListening: overrides?.isListening ?? false,
  transcript: overrides?.transcript,
  isOnline: overrides?.isOnline
});

describe('CompanionFaceScreen', () => {
  it('renders safely with minimal required props', () => {
    const ui = renderFace();
    expect(toText(ui)).toContain('Au repos • idle_happy');
  });

  it('renders safely when listening with an empty transcript', () => {
    const ui = renderFace({ isListening: true, transcript: '', companionVisualState: 'listening_for_command', companionVisualStateLabel: 'Je vous écoute' });
    expect(toText(ui)).toContain('Je vous écoute');
    expect(toText(ui)).not.toContain('“”');
  });

  it('shows transcript feedback when listening and transcript exists', () => {
    const ui = renderFace({ isListening: true, transcript: 'Testing transcript feedback' });
    expect(toText(ui)).toContain('Micro actif');
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
    expect(toText(ui)).toContain('Au repos');
  });

  it('activates speaking visual indicator when mode is speaking', () => {
    const ui = renderFace({ state: { ...baseState, mode: 'speaking' }, companionVisualState: 'speaking', companionVisualStateLabel: 'Je réponds' });
    const mouth = findFirstByClass(ui, 'mouth');
    const screen = findFirstByClass(ui, 'face-screen');
    const face = findFirstByClass(ui, 'face ');
    expect(mouth).toBeDefined();
    expect(screen?.props.className).toContain('visual-speaking');
    expect(face?.props.className).toContain('visual-speaking');
    expect(mouth?.props.className).toContain('is-speaking');
    expect(toText(ui)).toContain('Je réponds');
  });

  it('applies waiting visual classes and keeps wake phrase label', () => {
    const ui = renderFace({
      companionVisualState: 'waiting_for_wake_phrase',
      companionVisualStateLabel: 'En attente de “Nexus”'
    });
    const screen = findFirstByClass(ui, 'face-screen');
    const face = findFirstByClass(ui, 'face ');
    expect(screen?.props.className).toContain('visual-waiting_for_wake_phrase');
    expect(screen?.props.className).toContain('state-wake');
    expect(face?.props.className).toContain('visual-waiting_for_wake_phrase');
    expect(face?.props.className).toContain('state-wake');
    expect(toText(ui)).toContain('En attente de “Nexus”');
  });

  it('activates listening indicator when isListening is true', () => {
    const ui = renderFace({ isListening: true, companionVisualState: 'listening_for_command', companionVisualStateLabel: 'Je vous écoute' });
    const screen = findFirstByClass(ui, 'face-screen');
    expect(toText(ui)).toContain('Je vous écoute');
    expect(toText(ui)).toContain('Micro actif');
    expect(screen?.props.className).toContain('visual-listening_for_command');
    expect(screen?.props.className).toContain('state-listening');
  });

  it('does not show false listening or speaking feedback while idle', () => {
    const ui = renderFace({ state: { ...baseState, mode: 'idle' }, isListening: false });
    const mouth = findElements(ui, (element) => element.props.className?.includes('mouth'))[0];
    expect(toText(ui)).toContain('Au repos');
    expect(toText(ui)).not.toContain('Micro actif');
    expect(toText(ui)).not.toContain('Je vous écoute');
    expect(mouth.props.className).not.toContain('is-speaking');
  });

  it('shows offline indicator when connectivity is unavailable', () => {
    const ui = renderFace({ isOnline: false, companionVisualState: 'offline', companionVisualStateLabel: 'Mode hors ligne' });
    const screen = findFirstByClass(ui, 'face-screen');
    const face = findFirstByClass(ui, 'face ');
    expect(screen?.props.className).toContain('visual-offline');
    expect(face?.props.className).toContain('visual-offline');
    expect(toText(ui)).toContain('Mode hors ligne');
    expect(toText(ui)).toContain('Hors ligne');
  });

  it('applies error visual classes and keeps error label', () => {
    const ui = renderFace({ companionVisualState: 'error', companionVisualStateLabel: 'Erreur d’écoute' });
    const screen = findFirstByClass(ui, 'face-screen');
    const face = findFirstByClass(ui, 'face ');
    expect(screen?.props.className).toContain('visual-error');
    expect(face?.props.className).toContain('visual-error');
    expect(toText(ui)).toContain('Erreur d’écoute');
  });

  it('keeps attention target eye offset via css variable', () => {
    const ui = renderFace({ state: { ...baseState, attentionTarget: 'screen' } });
    const eyes = findFirstByClass(ui, 'eyes');
    expect(eyes?.props.style?.['--eye-offset-x']).toBe('-7px');
  });
});
