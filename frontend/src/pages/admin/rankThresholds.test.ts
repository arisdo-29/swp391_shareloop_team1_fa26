import { describe, expect, it } from 'vitest';
import type { AdminConfigResponse } from '../../services/adminConfigApi';
import { RANK_ORDER_MESSAGE, rankOrderError } from './rankThresholds';

function config(key: string, value: string): AdminConfigResponse {
  return {
    key,
    value,
    defaultValue: value,
    label: key,
    description: '',
    group: 'REPUTATION',
    groupLabel: 'Uy tín và hạng',
  };
}

const configs = [
  config('reputation_silver_min', '50'),
  config('reputation_gold_min', '200'),
  config('reputation_diamond_min', '500'),
  config('daily_points_cap', '30'),
];

describe('rankOrderError', () => {
  it('cho phép giá trị giữ nguyên thứ tự Bạc < Vàng < Kim cương', () => {
    expect(rankOrderError(configs, 'reputation_gold_min', '300')).toBe('');
  });

  it('báo lỗi khi điểm Bạc vượt hoặc bằng điểm Vàng', () => {
    expect(rankOrderError(configs, 'reputation_silver_min', '200')).toBe(RANK_ORDER_MESSAGE);
    expect(rankOrderError(configs, 'reputation_silver_min', '250')).toBe(RANK_ORDER_MESSAGE);
  });

  it('báo lỗi khi điểm Kim cương không lớn hơn điểm Vàng', () => {
    expect(rankOrderError(configs, 'reputation_diamond_min', '200')).toBe(RANK_ORDER_MESSAGE);
  });

  it('bỏ qua các cấu hình không phải điểm tối thiểu của hạng', () => {
    expect(rankOrderError(configs, 'daily_points_cap', '1')).toBe('');
  });

  it('bỏ qua giá trị chưa phải số để Backend báo lỗi kiểu dữ liệu', () => {
    expect(rankOrderError(configs, 'reputation_gold_min', 'abc')).toBe('');
  });
});
