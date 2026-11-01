import { useState } from 'react';
import { inspectEncryptedCompanionBackupFile, BACKUP_FILE_MAX_BYTES } from '../../services/backup/browserBackupFiles';

/**
 * Read-only encrypted backup inspection. Does not persist the passphrase,
 * decrypted memories, or selected file, and never imports data automatically.
 */
export function CompanionBackupInspectionPanel() {
  const [file, setFile] = useState<File | null>(null);
  const [passphrase, setPassphrase] = useState('');
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);

  const inspect = async () => {
    if (!file || passphrase.length < 12 || busy) return;
    setBusy(true);
    setStatus('');
    try {
      const result = await inspectEncryptedCompanionBackupFile(file, passphrase);
      setStatus(`Sauvegarde vérifiée pour ${result.identity.displayName}. Aucune donnée n’a été restaurée ni modifiée.`);
    } catch {
      setStatus('Vérification impossible : fichier invalide, altéré ou phrase secrète incorrecte.');
    } finally {
      setPassphrase('');
      setBusy(false);
    }
  };

  return (
    <section className="panel" aria-label="Vérification de sauvegarde Nexus">
      <h3>Vérifier une sauvegarde chiffrée</h3>
      <p>Choisissez un fichier Nexus et sa phrase secrète pour vérifier son intégrité. Aucun souvenir n’est importé ni modifié.</p>
      <label htmlFor="nexus-backup-file">Fichier de sauvegarde</label>
      <input
        id="nexus-backup-file"
        type="file"
        accept=".json,.nexus-backup.json,application/json"
        onChange={(event) => {
          const selected = event.target.files?.[0] ?? null;
          setFile(selected && selected.size <= BACKUP_FILE_MAX_BYTES ? selected : null);
          setStatus(selected && selected.size > BACKUP_FILE_MAX_BYTES ? 'Fichier trop volumineux.' : '');
        }}
      />
      <label htmlFor="nexus-backup-secret">Phrase secrète</label>
      <input
        id="nexus-backup-secret"
        type="password"
        autoComplete="off"
        value={passphrase}
        onChange={(event) => setPassphrase(event.target.value)}
      />
      <button type="button" disabled={!file || passphrase.length < 12 || busy} onClick={() => { void inspect(); }}>
        {busy ? 'Vérification…' : 'Vérifier sans restaurer'}
      </button>
      {status ? <p role="status" aria-live="polite">{status}</p> : null}
    </section>
  );
}
