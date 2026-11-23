import { describe, expect, it } from 'vitest';
import type { RequestResponse } from '../services/requestApi';
import { deadlineLines, nearestDeadlineLine } from './deadlines';

const base: RequestResponse = {
  id: 101,
  itemId: 45,
  receiverId: 12,
  donorId: 9,
  type: 'GIVE',
  status: 'RESERVED',
  itemTitle: 'Máy ảnh',
  createdAt: '2026-11-20T08:00:00Z',
};

describe('deadlineLines', () => {
  it('chưa chốt lịch: tự huỷ nếu chưa chốt lịch trước mốc backend trả (giờ Việt Nam)', () => {
    // 2026-12-02T08:00Z là 15:00 giờ Việt Nam
    const lines = deadlineLines({ ...base, autoCancelAt: '2026-12-02T08:00:00Z' }, true, 'Bình');
    expect(lines.map((line) => line.text)).toEqual(['Tự huỷ nếu chưa chốt lịch trước 15:00 ngày 02/12']);
  });

  it('Gặp trực tiếp: tự hoàn tất nói rõ ai im lặng theo vai người xem', () => {
    const request: RequestResponse = {
      ...base,
      status: 'AWAITING_HANDOVER',
      deliveryMethod: 'IN_PERSON',
      donorHandoverAt: '2026-12-02T08:00:00Z',
      autoConfirmAt: '2026-12-05T08:00:00Z',
    };
    expect(deadlineLines(request, false, 'An')[0].text).toBe('Tự hoàn tất lúc 15:00 ngày 05/12 nếu bạn không phản hồi');
    expect(deadlineLines(request, true, 'Bình')[0].text).toBe(
      'Tự hoàn tất lúc 15:00 ngày 05/12 nếu Bình không phản hồi',
    );
  });

  it('Giao hàng: hạn gửi và mốc tự ghi nhận từng chiều', () => {
    const request: RequestResponse = {
      ...base,
      type: 'SWAP',
      offeredItemId: 77,
      offeredItemTitle: 'Mũ bảo hiểm',
      status: 'AWAITING_HANDOVER',
      deliveryMethod: 'SHIPPING',
      donorShippedAt: '2026-12-01T08:00:00Z',
      sendDeadlineAt: '2026-12-08T08:00:00Z',
      leg1AutoReceiveAt: '2026-12-08T08:00:00Z',
    };
    const texts = deadlineLines(request, false, 'An').map((line) => line.text);
    expect(texts).toContain('Hạn gửi hàng là 15:00 ngày 08/12');
    expect(texts).toContain('Tự ghi nhận bạn đã nhận món Máy ảnh lúc 15:00 ngày 08/12 nếu bạn không phản hồi');
  });

  it('đề nghị huỷ và dời lịch có câu riêng, nearestDeadlineLine chọn mốc sớm nhất', () => {
    const request: RequestResponse = {
      ...base,
      status: 'LOGISTICS_CONFIRMED',
      cancelRequestExpiresAt: '2026-12-02T08:00:00Z',
      rescheduleExpiresAt: '2026-12-03T08:00:00Z',
      autoCancelAt: '2026-12-09T08:00:00Z',
    };
    const lines = deadlineLines(request, true, 'Bình');
    expect(lines.map((line) => line.key)).toEqual(['autoCancel', 'cancelRequest', 'reschedule']);
    expect(nearestDeadlineLine(request, true, 'Bình')?.key).toBe('cancelRequest');
    expect(nearestDeadlineLine(base, true, 'Bình')).toBeNull();
  });
});
