import { describe, expect, it } from 'vitest';
import { creatorModeConstants, isCreatorCodeValid, trimTapHistory } from './useCreatorMode';

describe('useCreatorMode helpers', () => {
  it('requires five quick taps within the allowed time window', () => {
    let taps: number[] = [];
    taps = trimTapHistory(taps, 0);
    taps = trimTapHistory(taps, 350);
    taps = trimTapHistory(taps, 700);
    taps = trimTapHistory(taps, 1050);

    expect(taps).toHaveLength(4);

    taps = trimTapHistory(taps, 1400);

    expect(taps).toHaveLength(creatorModeConstants.REQUIRED_TAP_COUNT);
  });

  it('drops old taps outside the two-second window', () => {
    let taps: number[] = [];
    taps = trimTapHistory(taps, 0);
    taps = trimTapHistory(taps, 400);
    taps = trimTapHistory(taps, 800);
    taps = trimTapHistory(taps, 1200);
    taps = trimTapHistory(taps, 2600);

    expect(taps).toEqual([800, 1200, 2600]);
    expect(taps).toHaveLength(3);
  });

  it('only accepts creator code 0410', () => {
    expect(isCreatorCodeValid('0410')).toBe(true);
    expect(isCreatorCodeValid(' 0410 ')).toBe(true);
    expect(isCreatorCodeValid('410')).toBe(false);
    expect(isCreatorCodeValid('0000')).toBe(false);
  });
});
