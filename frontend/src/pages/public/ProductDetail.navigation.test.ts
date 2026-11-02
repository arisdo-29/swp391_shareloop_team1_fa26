import { describe, expect, it } from 'vitest';
import {
  productDetailBackLink,
  productDetailFromRequestPath,
} from '../../utils/productDetailNavigation';

describe('productDetailBackLink', () => {
  it('quay lại tin nhắn khi nguồn được whitelist', () => {
    expect(productDetailBackLink('?from=messages')).toEqual({
      target: '/messages',
      label: 'Quay lại tin nhắn',
    });
  });

  it('quay lại đúng yêu cầu đang xem khi có requestId hợp lệ', () => {
    expect(productDetailBackLink('?from=messages&requestId=101')).toEqual({
      target: '/requests/101',
      label: 'Quay lại tin nhắn',
    });
    expect(productDetailFromRequestPath(77, 101)).toBe(
      '/items/77?from=messages&requestId=101',
    );
  });

  it('giữ modal chọn người khi mở chi tiết từ món đề xuất', () => {
    const detailPath = productDetailFromRequestPath(77, 101, true);
    expect(detailPath).toBe(
      '/items/77?from=messages&requestId=101&selectRequest=101',
    );
    expect(productDetailBackLink(detailPath.split('?')[1] ? `?${detailPath.split('?')[1]}` : ''))
      .toEqual({
        target: '/requests/101?selectRequest=101',
        label: 'Quay lại tin nhắn',
      });
  });

  it.each(['0', '-1', 'abc', '1.5'])('bỏ qua requestId không hợp lệ: %s', (requestId) => {
    expect(productDetailBackLink(`?from=messages&requestId=${requestId}`)).toEqual({
      target: '/messages',
      label: 'Quay lại tin nhắn',
    });
  });

  it('quay lại danh sách gợi ý khi mở bài từ trợ lý AI', () => {
    expect(productDetailBackLink('?from=ai')).toEqual({
      target: '/ai',
      label: 'Quay lại gợi ý AI',
    });
  });

  it.each(['', '?from=browse', '?from=https://example.com', '?from=/messages'])(
    'bỏ qua nguồn không hợp lệ: %s',
    (search) => {
      expect(productDetailBackLink(search)).toEqual({
        target: '/browse',
        label: 'Quay lại tìm đồ',
      });
    },
  );
});
