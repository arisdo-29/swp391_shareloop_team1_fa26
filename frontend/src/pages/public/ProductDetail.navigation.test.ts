import { describe, expect, it } from 'vitest';
import { productDetailBackLink } from '../../utils/productDetailNavigation';

describe('productDetailBackLink', () => {
  it('quay lại tin nhắn khi nguồn được whitelist', () => {
    expect(productDetailBackLink('?from=messages')).toEqual({
      target: '/messages',
      label: 'Quay lại tin nhắn',
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
