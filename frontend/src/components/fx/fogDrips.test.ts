import { describe, expect, it } from 'vitest';
import { MAX_DRIPS_DESKTOP, MAX_DRIPS_LIGHT, type ClearTrail, createDrip, maxDripsFor, updateDrips } from './fogDrips';
import { createMask, maskValueAt, wipeStroke } from './fogEngine';

// Số "ngẫu nhiên" cố định 0.5: giọt không bao giờ khựng lại (0.5 > seconds * 1.1), kết quả ổn định.
const steadyRandom = () => 0.5;

function simulate(seconds: number, update: (frameSeconds: number) => void) {
  const frameSeconds = 1 / 60;
  for (let elapsed = 0; elapsed < seconds; elapsed += frameSeconds) update(frameSeconds);
}

describe('giọt nước chảy', () => {
  it('maxDripsFor_narrowScreenOrWeakDevice_usesLightLimit', () => {
    // then
    expect(maxDripsFor(1280, 8)).toBe(MAX_DRIPS_DESKTOP);
    expect(maxDripsFor(1280, undefined)).toBe(MAX_DRIPS_DESKTOP);
    expect(maxDripsFor(375, 8)).toBe(MAX_DRIPS_LIGHT);
    expect(maxDripsFor(1280, 4)).toBe(MAX_DRIPS_LIGHT);
  });

  it('updateDrips_whileWaiting_keepsDripInPlace', () => {
    // given
    const drip = createDrip(100, 50, 3, 1, steadyRandom);

    // when
    const remaining = updateDrips([drip], 0.5, 600, steadyRandom, () => undefined);

    // then
    expect(remaining).toHaveLength(1);
    expect(drip.y).toBe(50);
  });

  it('updateDrips_afterWait_fallsAndLeavesClearTrail', () => {
    // given
    const mask = createMask(400, 600);
    const clearTrail: ClearTrail = (from, to, radius, strength) => wipeStroke(mask, from, to, radius, strength);
    let drips = [createDrip(200, 40, 3, 0, steadyRandom)];
    const path: Array<{ x: number; y: number }> = [];

    // when: chảy 1 giây, ghi lại đường giọt đã đi (đường hơi lượn sang hai bên)
    simulate(1, (frameSeconds) => {
      drips = updateDrips(drips, frameSeconds, 600, steadyRandom, clearTrail);
      path.push({ x: drips[0].x, y: drips[0].y });
    });

    // then: giọt đã xuống thấp, mọi điểm trên đường đi đều sạch, chỗ xa đường đi vẫn còn sương
    expect(drips[0].y).toBeGreaterThan(120);
    path.forEach((point) => expect(maskValueAt(mask, point.x, point.y)).toBeGreaterThan(0.5));
    expect(maskValueAt(mask, 120, 80)).toBe(0);
  });

  it('updateDrips_belowBottomEdge_removesDrip', () => {
    // given
    let drips = [createDrip(50, 90, 3, 0, steadyRandom)];

    // when
    simulate(3, (frameSeconds) => {
      drips = updateDrips(drips, frameSeconds, 100, steadyRandom, () => undefined);
    });

    // then
    expect(drips).toHaveLength(0);
  });
});
