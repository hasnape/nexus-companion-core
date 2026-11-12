import { isBoundedMotionRequest, type NexusMotionRequest } from './robot-capabilities';

export type SteeringAxle = 'front' | 'rear';
export interface TricycleProfile {
  steeringAxle: SteeringAxle;
  wheelbaseM: number; trackM: number; wheelRadiusM: number;
  maxSpeedMps: number; maxSteeringRad: number;
}
/** Dimensions describe the virtual chassis. Real hardware must supply measured geometry. */
export const virtualTricycleProfile = (steeringAxle: SteeringAxle = 'front'): TricycleProfile => ({
  steeringAxle, wheelbaseM: 0.28, trackM: 0.26, wheelRadiusM: 0.05,
  maxSpeedMps: 0.15, maxSteeringRad: 0.7
});
export interface RollingSample { elapsedMs: number; xM: number; yM: number; headingRad: number; distanceM: number }
export interface RollingPlan {
  protocolVersion: 1; execution: 'simulation'; steeringAxle: SteeringAxle;
  request: NexusMotionRequest; linearMps: number; angularRadps: number;
  steeringRad: number; leftWheelRadps: number; rightWheelRadps: number;
  samples: RollingSample[];
}

export const planTricycleMotion = (profile: TricycleProfile, linearMps: number, angularRadps: number, durationMs: number): RollingPlan => {
  if (!['front', 'rear'].includes(profile.steeringAxle)
      || ![profile.wheelbaseM, profile.trackM, profile.wheelRadiusM, profile.maxSpeedMps, profile.maxSteeringRad].every((n) => Number.isFinite(n) && n > 0)
      || profile.maxSpeedMps > 0.3 || profile.maxSteeringRad >= Math.PI / 2
      || !Number.isFinite(linearMps) || !Number.isFinite(angularRadps) || Math.abs(linearMps) > profile.maxSpeedMps
      || Math.abs(angularRadps) > 1 || (linearMps === 0 && angularRadps !== 0)) throw new Error('Invalid tricycle motion');
  const steeringRad = linearMps === 0 ? 0 : Math.atan(angularRadps * profile.wheelbaseM / linearMps) * (profile.steeringAxle === 'front' ? 1 : -1);
  const request: NexusMotionRequest = { motionGroup: 'wheeled-base', action: 'roll', durationMs, normalizedSpeed: Math.abs(linearMps) / profile.maxSpeedMps };
  if (Math.abs(steeringRad) > profile.maxSteeringRad || !isBoundedMotionRequest(request)) throw new Error('Motion exceeds tricycle limits');
  const sample = (elapsedMs: number): RollingSample => {
    const t = elapsedMs / 1000;
    const headingRad = angularRadps * t;
    return { elapsedMs, xM: angularRadps === 0 ? linearMps * t : linearMps / angularRadps * Math.sin(headingRad),
      yM: angularRadps === 0 ? 0 : linearMps / angularRadps * (1 - Math.cos(headingRad)), headingRad, distanceM: linearMps * t };
  };
  const samples: RollingSample[] = [];
  for (let elapsed = 0; elapsed < durationMs; elapsed += 100) samples.push(sample(elapsed));
  samples.push(sample(durationMs));
  return { protocolVersion: 1, execution: 'simulation', steeringAxle: profile.steeringAxle, request, linearMps, angularRadps, steeringRad,
    leftWheelRadps: (linearMps - angularRadps * profile.trackM / 2) / profile.wheelRadiusM,
    rightWheelRadps: (linearMps + angularRadps * profile.trackM / 2) / profile.wheelRadiusM, samples };
};

export interface BodyReply { handled: boolean; text: string; action?: 'stop' | 'drive' | 'status'; plan?: RollingPlan }
const numbers: Record<string, number> = { un: 1, une: 1, deux: 2, trois: 3, quatre: 4, cinq: 5, dix: 10, quinze: 15, vingt: 20, trente: 30, quarante: 40, cinquante: 50, soixante: 60 };
export const resolveBodyCommand = (command: string, axle: SteeringAxle): BodyReply => {
  const text = command.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[’']/g, ' ')
    .replace(/[!?.,;:]/g, ' ').replace(/\s+/g, ' ').trim();
  if (/^(?:stop|arrete|arrete[- ]toi|arret|stoppe|stoppe[- ]toi|arrete le corps|arret du corps)$/.test(text)) return { handled: true, action: 'stop', text: 'Corps virtuel arrêté.' };
  if (/^(?:etat du corps|etat du robot|ou en est ton corps|combien de roues as tu|comment est ton corps|decris ton corps)$/.test(text)) return { handled: true, action: 'status', text: `Mon corps virtuel roule sur trois roues. La roue directrice est à l’${axle === 'front' ? 'avant' : 'arrière'}.` };
  const straight = text.match(/^(avance|avancer|recule|reculer)(?: de)?(?: ([0-9]+|un|une|deux|trois|quatre|cinq|dix|quinze|vingt|trente|quarante|cinquante) (cm|centimetres?|metres?))?$/);
  const turn = text.match(/^tourne(?: a)? (gauche|droite)(?: de ([0-9]+|dix|quinze|vingt|trente|quarante|cinquante|soixante) degres?)?$/);
  if (!straight && !turn) return { handled: false, text: '' };
  const quantity = (value: string | undefined, fallback: number) => value ? numbers[value] ?? Number(value) : fallback;
  if (straight) {
    const cm = quantity(straight[2], 15) * (straight[3]?.startsWith('metre') ? 100 : 1);
    if (cm < 1 || cm > 50) return { handled: true, text: 'Pour le corps virtuel, demande une distance de 1 à 50 centimètres.' };
    const direction = straight[1].startsWith('rec') ? -1 : 1;
    return { handled: true, action: 'drive', text: `Simulation : je ${direction === 1 ? 'roule en avant' : 'recule'} de ${cm} centimètres.`,
      plan: planTricycleMotion(virtualTricycleProfile(axle), direction * 0.1, 0, Math.round(cm * 100)) };
  }
  const degrees = quantity(turn?.[2], 20);
  if (degrees < 1 || degrees > 60) return { handled: true, text: 'Pour le corps virtuel, demande un virage de 1 à 60 degrés.' };
  const left = turn?.[1] === 'gauche';
  return { handled: true, action: 'drive', text: `Simulation : je roule en virant à ${left ? 'gauche' : 'droite'} de ${degrees} degrés.`,
    plan: planTricycleMotion(virtualTricycleProfile(axle), 0.1, left ? 0.25 : -0.25, Math.round(degrees * Math.PI / 180 / 0.25 * 1000)) };
};
