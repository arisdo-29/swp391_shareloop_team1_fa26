import { describe, expect, it } from 'vitest';
import type { ApiError } from '../services/http';
import { offerChangeErrorMessage } from './offerChange';

describe('offerChangeErrorMessage', () => {
  it('món đề nghị đang tạm ẩn thì báo rõ thay vì câu chung của backend', () => {
    // given: chủ bài đồng ý đổi món trong chat nhưng món đã bị ẩn
    const apiError = { status: 409, code: 'ITEM_HIDDEN', message: 'Bài đang tạm ẩn nên chưa giữ chỗ được.' };

    // when
    const message = offerChangeErrorMessage(apiError as ApiError);

    // then
    expect(message).toBe('Món đề nghị đang tạm ẩn nên chưa giữ chỗ được.');
  });
});
