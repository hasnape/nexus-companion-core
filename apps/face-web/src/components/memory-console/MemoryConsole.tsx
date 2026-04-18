import type { MemoryState } from '@nexus/memory';
import { useState } from 'react';

export function MemoryConsole({ memory, onAddPreference, onRemove }: { memory: MemoryState; onAddPreference: (value: string) => Promise<void>; onRemove: (id: string) => Promise<void> }) {
  const [value, setValue] = useState('');
  const records = [...memory.session, ...memory.longTerm, ...memory.behavioral];
  return (
    <section className="panel">
      <h3>Memory Console</h3>
      <div className="row">
        <input value={value} onChange={(e) => setValue(e.target.value)} placeholder="New preference" />
        <button onClick={async () => { if (!value) return; await onAddPreference(value); setValue(''); }}>Add</button>
      </div>
      {records.map((record) => (
        <div key={record.id} className="memory-item">
          <span>{record.type}: {record.content} ({record.confidence.toFixed(2)})</span>
          <button onClick={() => onRemove(record.id)}>Delete</button>
        </div>
      ))}
    </section>
  );
}
