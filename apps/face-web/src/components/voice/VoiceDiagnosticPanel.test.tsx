import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { VoiceDiagnosticPanel } from './VoiceDiagnosticPanel';

describe('VoiceDiagnosticPanel', () => {
  it('starts disabled and does not expose a log', () => {
    const html = renderToStaticMarkup(React.createElement(VoiceDiagnosticPanel, {
      enabled: false, events: [], onEnabledChange: () => {}, onClear: () => {}
    }));
    expect(html).toContain('Diagnostic vocal');
    expect(html).toContain('Activer le journal vocal');
    expect(html).not.toContain('role="log"');
  });
  it('renders a local event timeline without requiring a remote service', () => {
    const html = renderToStaticMarkup(React.createElement(VoiceDiagnosticPanel, {
      enabled: true,
      events: [{ at: '2026-11-02T08:00:00.000Z', type: 'recognition:onerror', detail: 'network' }],
      onEnabledChange: () => {}, onClear: () => {}
    }));
    expect(html).toContain('recognition:onerror');
    expect(html).toContain('network');
    expect(html).toContain('Copier le journal');
  });
});
