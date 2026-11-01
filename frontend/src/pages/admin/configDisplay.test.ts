import { describe, expect, it } from 'vitest';
import type { AdminConfigResponse } from '../../services/adminConfigApi';
import { formatConfigValue } from './configDisplay';

function config(key: string, value: string): AdminConfigResponse {
  return {
    key,
    value,
    defaultValue: value,
    label: key,
    description: '',
    group: 'MODERATION',
    groupLabel: 'Kiểm duyệt và báo cáo',
  };
}

describe('formatConfigValue', () => {
  it('L13: lý do vi phạm nghiêm trọng hiện bằng nhãn tiếng Việt, mã lạ giữ nguyên', () => {
    expect(
      formatConfigValue(config('report_serious_reasons', 'PROHIBITED_ITEM, COUNTERFEIT')),
    ).toBe('Hàng cấm, Hàng giả');
    expect(formatConfigValue(config('report_serious_reasons', 'HARASSMENT,UNKNOWN'))).toBe(
      'Quấy rối, UNKNOWN',
    );
  });

  it('hạn mức -1 hiện là không giới hạn, khoá khác giữ nguyên giá trị', () => {
    expect(formatConfigValue(config('max_active_items_diamond', '-1'))).toBe('Không giới hạn');
    expect(formatConfigValue(config('report_hide_threshold', '3'))).toBe('3');
  });
});
