import { describe, expect, it } from 'vitest';
import {
  REFOG_PER_SECOND,
  WIPE_RADIUS_MOUSE_PX,
  coverFit,
  createMask,
  decayMask,
  fillMaskAlpha,
  introStrokePoint,
  maskValueAt,
  resizeMask,
  stampMask,
  wipeStroke,
} from './fogEngine';

describe('mặt nạ kính sương', () => {
  it('stampMask_atPoint_raisesValuesOnlyInsideRadius', () => {
    // given
    const mask = createMask(400, 200);

    // when
    stampMask(mask, 100, 100, 40);

    // then
    expect(maskValueAt(mask, 100, 100)).toBeGreaterThanOrEqual(1);
    expect(maskValueAt(mask, 125, 100)).toBeGreaterThan(0);
    expect(maskValueAt(mask, 150, 100)).toBe(0);
    expect(maskValueAt(mask, 300, 100)).toBe(0);
  });

  it('stampMask_nearEdge_doesNotWriteOutsideGrid', () => {
    // given
    const mask = createMask(100, 100);

    // when
    stampMask(mask, 0, 0, 60);

    // then
    expect(maskValueAt(mask, 0, 0)).toBeGreaterThanOrEqual(1);
    expect(mask.values.length).toBe(mask.columns * mask.rows);
  });

  it('decayMask_afterEnoughSeconds_refogsEverythingToZero', () => {
    // given
    const mask = createMask(400, 200);
    stampMask(mask, 200, 100, 50);

    // when: mô phỏng 12 giây, mỗi frame 1/60 giây
    for (let frame = 0; frame < 12 * 60; frame++) decayMask(mask, REFOG_PER_SECOND / 60);

    // then
    expect(Math.max(...mask.values)).toBe(0);
    expect(decayMask(mask, REFOG_PER_SECOND / 60)).toBe(false);
  });

  it('decayMask_afterOneSecond_keepsJustWipedAreaClear', () => {
    // given
    const mask = createMask(400, 200);
    stampMask(mask, 200, 100, 50);

    // when
    const changed = decayMask(mask, REFOG_PER_SECOND);

    // then: kính "còn ướt" vẫn sạch hẳn sau 1 giây
    expect(changed).toBe(true);
    expect(maskValueAt(mask, 200, 100)).toBeGreaterThanOrEqual(1);
  });

  it('wipeStroke_fastMove_leavesNoGapAlongPath', () => {
    // given
    const mask = createMask(600, 200);
    wipeStroke(mask, null, { x: 20, y: 100 }, WIPE_RADIUS_MOUSE_PX);

    // when: một sự kiện con trỏ nhảy 200px
    wipeStroke(mask, { x: 20, y: 100 }, { x: 220, y: 100 }, WIPE_RADIUS_MOUSE_PX);

    // then
    for (let x = 20; x <= 220; x += 5) expect(maskValueAt(mask, x, 100)).toBeGreaterThanOrEqual(1);
  });

  it('wipeStroke_afterLift_doesNotConnectStrokes', () => {
    // given
    const mask = createMask(600, 300);
    wipeStroke(mask, null, { x: 50, y: 50 }, WIPE_RADIUS_MOUSE_PX);

    // when: nhấc nét (from = null) rồi lau ở chỗ khác
    wipeStroke(mask, null, { x: 250, y: 250 }, WIPE_RADIUS_MOUSE_PX);

    // then: điểm giữa hai chỗ vẫn còn sương
    expect(maskValueAt(mask, 150, 150)).toBe(0);
    expect(maskValueAt(mask, 250, 250)).toBeGreaterThanOrEqual(1);
  });

  it('wipeStroke_whenPointerJumpsFar_onlyStampsNewPoint', () => {
    // given
    const mask = createMask(800, 200);

    // when: nhảy 500px, xa hơn ngưỡng 220px
    wipeStroke(mask, { x: 50, y: 100 }, { x: 550, y: 100 }, WIPE_RADIUS_MOUSE_PX);

    // then
    expect(maskValueAt(mask, 300, 100)).toBe(0);
    expect(maskValueAt(mask, 550, 100)).toBeGreaterThanOrEqual(1);
  });

  it('fillMaskAlpha_mapsFogToAlpha', () => {
    // given
    const mask = createMask(20, 10, 10);
    mask.values[1] = 1.35;
    const pixels = new Uint8ClampedArray(mask.columns * mask.rows * 4);

    // when
    fillMaskAlpha(mask, pixels);

    // then: ô chưa lau đục hẳn, ô đã lau trong suốt
    expect(pixels[3]).toBe(255);
    expect(pixels[7]).toBe(0);
  });

  it('resizeMask_whenHeroGrowsTaller_keepsWipedArea', () => {
    // given
    const mask = createMask(400, 200);
    stampMask(mask, 100, 100, 30);

    // when: hero cao thêm vì chip tải xong
    const resized = resizeMask(mask, 400, 260);

    // then
    expect(resized.rows).toBeGreaterThan(mask.rows);
    expect(maskValueAt(resized, 100, 100)).toBe(maskValueAt(mask, 100, 100));
    expect(maskValueAt(resized, 100, 240)).toBe(0);
  });

  it('introStrokePoint_fromStartToEnd_crossesLowerHeroLeftToRight', () => {
    // when
    const points = [0, 0.25, 0.5, 0.75, 1].map((progress) => introStrokePoint(progress, 1280, 730));

    // then: đi từ trái sang phải, luôn nằm trong phần dưới hero
    for (let index = 1; index < points.length; index++) expect(points[index].x).toBeGreaterThan(points[index - 1].x);
    points.forEach((point) => {
      expect(point.x).toBeGreaterThan(0);
      expect(point.x).toBeLessThan(1280);
      expect(point.y).toBeGreaterThan(730 * 0.8);
      expect(point.y).toBeLessThan(730);
    });
  });

  it('coverFit_wideBox_coversAndCentersImage', () => {
    // when
    const rect = coverFit(100, 100, 200, 100, 1.05);

    // then
    expect(rect.width).toBeCloseTo(210);
    expect(rect.height).toBeCloseTo(210);
    expect(rect.x).toBeCloseTo(-5);
    expect(rect.y).toBeCloseTo(-55);
  });
});
