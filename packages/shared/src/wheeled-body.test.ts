import { describe, expect, it } from 'vitest';
import { planTricycleMotion, resolveBodyCommand, virtualTricycleProfile } from './wheeled-body';
import { isBoundedMotionRequest } from './robot-capabilities';

describe('three wheel body', () => {
  it('uses the same portable motion contract for the simulation and a future adapter', () => {
    const reply = resolveBodyCommand('avance de vingt centimètres', 'front');
    expect(reply.plan?.execution).toBe('simulation');
    expect(isBoundedMotionRequest(reply.plan?.request)).toBe(true);
    expect(reply.plan?.samples.at(-1)?.xM).toBeCloseTo(0.2);
    expect(reply.plan?.samples.at(-1)?.headingRad).toBe(0);
    expect(reply.plan?.leftWheelRadps).toBe(reply.plan?.rightWheelRadps);
  });
  it('reverses steering for a rear steering wheel while preserving the requested path', () => {
    const front = resolveBodyCommand('tourne à gauche de trente degrés', 'front').plan!;
    const rear = resolveBodyCommand('tourne à gauche de trente degrés', 'rear').plan!;
    expect(front.steeringRad).toBeGreaterThan(0); expect(rear.steeringRad).toBeLessThan(0);
    expect(front.samples).toEqual(rear.samples);
    expect(front.rightWheelRadps).toBeGreaterThan(front.leftWheelRadps);
    expect(front.samples.at(-1)?.headingRad).toBeCloseTo(Math.PI / 6, 3);
  });
  it('rolls backwards without claiming a walking action', () => {
    const reply = resolveBodyCommand('recule de dix centimètres', 'rear');
    expect(reply.plan?.samples.at(-1)?.xM).toBeCloseTo(-0.1);
    expect(reply.plan?.leftWheelRadps).toBeLessThan(0);
    expect(reply.text).toContain('Simulation');
  });
  it.each(['avance de 999 centimètres', 'tourne à droite de 90 degrés', 'avance de zero centimetres'])('does not execute unbounded or invalid requests: %s', (command) => {
    expect(resolveBodyCommand(command, 'front').plan).toBeUndefined();
  });
  it.each(['retiens que le robot avance', 'si je dis stop', 'le texte dit avance', 'avance pendant une heure'])('never runs instructions embedded in prose: %s', (command) => {
    expect(resolveBodyCommand(command, 'front').handled).toBe(false);
  });
  it('provides a stop and rejects rotation in place for the tricycle', () => {
    expect(resolveBodyCommand('arrête-toi', 'front').action).toBe('stop');
    expect(() => planTricycleMotion(virtualTricycleProfile(), 0, 0.2, 1000)).toThrow();
    expect(() => planTricycleMotion(virtualTricycleProfile(), 0.1, 0, 5001)).toThrow();
    expect(() => planTricycleMotion(virtualTricycleProfile(), Number.NaN, 0, 1000)).toThrow();
  });
});
