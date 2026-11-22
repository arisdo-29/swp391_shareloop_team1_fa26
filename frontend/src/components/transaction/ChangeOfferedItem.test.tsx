// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import { requestApi } from '../../services/requestApi';
import { ChangeOfferedItem } from './ChangeOfferedItem';

vi.mock('../../services/itemApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/itemApi')>()),
  itemApi: { mine: vi.fn() },
}));
vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: { changeOfferedItem: vi.fn() },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('ChangeOfferedItem', () => {
  it('món mới đang tạm ẩn thì hiện câu lỗi rõ cho món đề nghị', async () => {
    // given
    const userEvents = userEvent.setup();
    const newItem = { id: 61, title: 'Đèn bàn', status: 'APPROVED', images: [] } as unknown as ItemResponse;
    vi.mocked(itemApi.mine).mockResolvedValue([newItem]);
    vi.mocked(requestApi.changeOfferedItem).mockRejectedValue({
      status: 409,
      code: 'ITEM_HIDDEN',
      message: 'Bài đang tạm ẩn nên chưa giữ chỗ được.',
    });
    render(<ChangeOfferedItem requestId={101} currentUserId={12} currentItemId={60} onUpdated={vi.fn()} />);

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Đổi món đề nghị' }));
    await userEvents.click(await screen.findByRole('radio'));
    await userEvents.click(screen.getByRole('button', { name: 'Xác nhận đổi món' }));

    // then
    expect(await screen.findByText('Món đề nghị đang tạm ẩn nên chưa giữ chỗ được.')).toBeTruthy();
  });
});
