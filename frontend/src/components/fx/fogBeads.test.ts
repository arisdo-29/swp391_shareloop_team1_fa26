import { describe, expect, it } from 'vitest';
import { BEAD_AREA_PER_BEAD_PX, MAX_BEADS, generateBeads, seededRandom } from './fogBeads';

describe('hạt sương', () => {
  it('seededRandom_sameSeed_returnsSameSequence', () => {
    // given
    const first = seededRandom(42);
    const second = seededRandom(42);

    // when
    const firstValues = [first(), first(), first()];
    const secondValues = [second(), second(), second()];

    // then: đổi slide dựng lại lớp sương thì hạt vẫn ở nguyên chỗ
    expect(firstValues).toEqual(secondValues);
    firstValues.forEach((value) => {
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThan(1);
    });
  });

  it('generateBeads_desktopHero_scalesWithAreaAndStaysInside', () => {
    // when
    const beads = generateBeads(1280, 700, seededRandom(1));

    // then
    expect(beads.length).toBe(Math.round((1280 * 700) / BEAD_AREA_PER_BEAD_PX));
    beads.forEach((bead) => {
      expect(bead.x).toBeGreaterThanOrEqual(0);
      expect(bead.x).toBeLessThan(1280);
      expect(bead.y).toBeGreaterThanOrEqual(0);
      expect(bead.y).toBeLessThan(700);
      expect(bead.radius).toBeGreaterThan(0);
    });
  });

  it('generateBeads_hugeScreen_capsBeadCount', () => {
    // when
    const beads = generateBeads(5000, 3000, seededRandom(1));

    // then
    expect(beads.length).toBe(MAX_BEADS);
  });
});
