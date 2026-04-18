import type { TrainingConfig } from '@nexus/shared';

export function TrainingPanel({ config, onChange }: { config: TrainingConfig; onChange: (next: TrainingConfig) => void }) {
  const set = (key: keyof TrainingConfig, value: number) => onChange({ ...config, [key]: value });
  return (
    <section className="panel">
      <h3>Training Panel</h3>
      {([
        ['proactivity', 0, 1, 0.1],
        ['silenceTolerance', 0, 1, 0.1],
        ['greetingFrequency', 0, 1, 0.1],
        ['emotionalIntensity', 0, 1, 0.1],
        ['chatterCooldownMs', 5000, 60000, 1000]
      ] as Array<[keyof TrainingConfig, number, number, number]>).map(([key, min, max, step]) => (
        <label key={key}>{key}
          <input type="range" min={min} max={max} step={step} value={config[key]} onChange={(e) => set(key, Number(e.target.value))} />
          <span>{config[key]}</span>
        </label>
      ))}
    </section>
  );
}
