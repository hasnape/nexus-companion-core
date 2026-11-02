import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { KawaiiCompanionFace } from './KawaiiCompanionFace';

describe('layered kawaii companion faces', () => {
  it.each(['feminine', 'masculine'] as const)('renders a complete %s avatar with synchronized eyes', (variant) => {
    const html = renderToStaticMarkup(<KawaiiCompanionFace variant={variant} />);
    expect(html).toContain('kawaii-eye-pair');
    expect(html.match(/<ellipse cx="(112|209)" cy="166"/g)).toHaveLength(2);
    expect(html).toContain('kawaii-mouth-open');
    expect(html).toContain('kawaii-brows');
    expect(html).toContain('kawaii-mouth-smile');
    expect(html).not.toContain('kawaii-cheeks');
    expect(html).not.toContain('kawaii-hair-back');
    expect(html).not.toContain('kawaii-hair-top');
    expect(html).not.toContain('nexus-avatar-' + variant + '-live-hair');
    expect(html).toContain(`kawaii-avatar--${variant}`);
    expect(html).toContain('aria-hidden="true"');
  });

  it('maintains visual state changes and keeps animation optional for static preview', () => {
    const html = renderToStaticMarkup(
      <KawaiiCompanionFace variant="masculine" expression="happy" visualState="speaking" />
    );
    expect(html).toContain('kawaii-avatar--speaking');
    expect(html).toContain('kawaii-avatar--happy');
    expect(renderToStaticMarkup(<KawaiiCompanionFace variant="feminine" preview />)).toContain('kawaii-avatar--preview');
  });
});
