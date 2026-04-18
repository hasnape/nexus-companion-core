import { describe, expect, it } from 'vitest';
import { companionVisualStateLabel, deriveCompanionVisualState } from './companionVisualState';

describe('companionVisualState', () => {
  it('returns waiting_for_wake_phrase and French label while waiting wake phrase', () => {
    const state = deriveCompanionVisualState({
      isOnline: true,
      wakeState: 'waiting_for_wake_phrase',
      isListening: true,
      listenerError: null,
      companionMode: 'idle'
    });

    expect(state).toBe('waiting_for_wake_phrase');
    expect(companionVisualStateLabel(state, null)).toBe('En attente de “Nexus”');
  });

  it('returns listening_for_command with French label when companion is actively listening', () => {
    const state = deriveCompanionVisualState({
      isOnline: true,
      wakeState: 'awake_listening_for_command',
      isListening: true,
      listenerError: null,
      companionMode: 'listening'
    });

    expect(state).toBe('listening_for_command');
    expect(companionVisualStateLabel(state, null)).toBe('Je vous écoute');
  });

  it('returns thinking when processing command', () => {
    const state = deriveCompanionVisualState({
      isOnline: true,
      wakeState: 'processing_command',
      isListening: true,
      listenerError: null,
      companionMode: 'idle'
    });

    expect(state).toBe('thinking');
    expect(companionVisualStateLabel(state, null)).toBe('Je réfléchis');
  });

  it('returns offline label when disconnected', () => {
    const state = deriveCompanionVisualState({
      isOnline: false,
      wakeState: 'inactive',
      isListening: false,
      listenerError: null,
      companionMode: 'idle'
    });

    expect(state).toBe('offline');
    expect(companionVisualStateLabel(state, null)).toBe('Mode hors ligne');
  });

  it('returns micro indisponible for permission errors', () => {
    const state = deriveCompanionVisualState({
      isOnline: true,
      wakeState: 'error',
      isListening: false,
      listenerError: 'Autorisation micro refusée.',
      companionMode: 'idle'
    });

    expect(state).toBe('error');
    expect(companionVisualStateLabel(state, 'Autorisation micro refusée.')).toBe('Micro indisponible');
  });
});
