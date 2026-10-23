import type { AdminConfigResponse } from '../../services/adminConfigApi';

export const SILVER_MIN_KEY = 'reputation_silver_min';
export const GOLD_MIN_KEY = 'reputation_gold_min';
export const DIAMOND_MIN_KEY = 'reputation_diamond_min';

export const RANK_ORDER_MESSAGE =
  'Điểm tối thiểu các hạng phải tăng dần: Bạc nhỏ hơn Vàng, Vàng nhỏ hơn Kim cương.';

function pointsOf(configs: AdminConfigResponse[], key: string, overrideKey: string, overrideValue: number) {
  if (key === overrideKey) return overrideValue;
  const found = configs.find((entry) => entry.key === key);
  return found ? Number(found.value) : Number.NaN;
}

/**
 * Kiểm tra trước khi gửi: nếu đặt `newValue` cho `key` thì điểm Bạc < Vàng < Kim cương có còn đúng không.
 * Trả về câu báo lỗi, hoặc chuỗi rỗng khi hợp lệ hoặc khi key không phải điểm tối thiểu của hạng.
 * Backend vẫn kiểm tra lại, đây chỉ để báo lỗi sớm.
 */
export function rankOrderError(configs: AdminConfigResponse[], key: string, newValue: string) {
  if (![SILVER_MIN_KEY, GOLD_MIN_KEY, DIAMOND_MIN_KEY].includes(key)) return '';
  const value = Number(newValue);
  if (!Number.isFinite(value)) return '';
  const silver = pointsOf(configs, SILVER_MIN_KEY, key, value);
  const gold = pointsOf(configs, GOLD_MIN_KEY, key, value);
  const diamond = pointsOf(configs, DIAMOND_MIN_KEY, key, value);
  return silver < gold && gold < diamond ? '' : RANK_ORDER_MESSAGE;
}
