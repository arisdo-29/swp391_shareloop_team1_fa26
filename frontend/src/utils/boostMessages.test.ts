import { describe, expect, it } from 'vitest';
import { boostStatusLines, formatDateTime, formatRemaining } from './boostMessages';

const NOW = Date.parse('2026-11-06T08:00:00Z');
const HOUR = 60 * 60 * 1000;

describe('boostStatusLines', () => {
  it('đang đẩy: đếm ngược, mốc kết thúc và dòng mua thêm tới mốc cuối đợt', () => {
    // given: còn 2 ngày 5 giờ, đợt còn chỗ mua thêm
    const boostedUntil = new Date(NOW + 53 * HOUR).toISOString();
    const chainEndsAt = new Date(NOW + 5 * 24 * HOUR).toISOString();

    // when
    const status = boostStatusLines({ boostedUntil, boostChainEndsAt: chainEndsAt }, NOW);

    // then
    expect(status).toEqual({
      main: `Đang đẩy · còn 2 ngày 5 giờ · kết thúc ${formatDateTime(boostedUntil)}`,
      extra: `Mua thêm được tới ${formatDateTime(chainEndsAt)}`,
    });
  });

  it('đang đẩy tới đúng mốc cuối đợt thì ẩn dòng mua thêm', () => {
    // given
    const boostedUntil = new Date(NOW + 10 * HOUR).toISOString();

    // when
    const status = boostStatusLines({ boostedUntil, boostChainEndsAt: boostedUntil }, NOW);

    // then
    expect(status?.extra).toBeUndefined();
  });

  it('đã hết đẩy và đang nghỉ: hiện mốc được đẩy lại', () => {
    // given
    const cooldownUntil = new Date(NOW + 30 * HOUR).toISOString();

    // when
    const status = boostStatusLines(
      { boostedUntil: new Date(NOW - HOUR).toISOString(), boostCooldownUntil: cooldownUntil },
      NOW,
    );

    // then
    expect(status).toEqual({ main: `Được đẩy lại từ ${formatDateTime(cooldownUntil)}` });
  });

  it('không đẩy, không nghỉ thì không hiện gì', () => {
    expect(boostStatusLines({ boostCooldownUntil: new Date(NOW - HOUR).toISOString() }, NOW)).toBeNull();
    expect(boostStatusLines({}, NOW)).toBeNull();
  });
});

describe('formatRemaining', () => {
  it('dưới 1 ngày chỉ ghi giờ, dưới 1 giờ ghi "dưới 1 giờ"', () => {
    expect(formatRemaining(new Date(NOW + 5 * HOUR).toISOString(), NOW)).toBe('5 giờ');
    expect(formatRemaining(new Date(NOW + 10 * 60 * 1000).toISOString(), NOW)).toBe('dưới 1 giờ');
  });
});
