import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { CompanionBackupInspectionPanel } from './CompanionBackupInspectionPanel';

describe('CompanionBackupInspectionPanel', () => {
  it('shows read-only privacy explanation and disabled verification on first render', () => {
    const html = renderToStaticMarkup(React.createElement(CompanionBackupInspectionPanel));
    expect(html).toContain('Vérifier une sauvegarde chiffrée');
    expect(html).toContain('Aucun souvenir n’est importé ni modifié');
    expect(html).toContain('disabled=""');
    expect(html).toContain('Vérifier sans restaurer');
    expect(html).toContain('type="password"');
    expect(html).toContain('type="file"');
  });
});
