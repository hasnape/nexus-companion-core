import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CompanionBackupInspectionPanel } from './CompanionBackupInspectionPanel';

describe('CompanionBackupInspectionPanel', () => {
  it('disables inspection until a file and a strong passphrase are supplied', () => {
    render(<CompanionBackupInspectionPanel />);
    expect(screen.getByRole('button', { name: 'Vérifier sans restaurer' }).hasAttribute('disabled')).toBe(true);
    expect(screen.getByText(/Aucun souvenir n’est importé ni modifié/)).toBeTruthy();
  });
  it('does not invoke backup inspection during initial render', async () => {
    const inspect = vi.fn();
    render(<CompanionBackupInspectionPanel />);
    expect(inspect).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Fichier de sauvegarde')).toBeTruthy();
  });
});
