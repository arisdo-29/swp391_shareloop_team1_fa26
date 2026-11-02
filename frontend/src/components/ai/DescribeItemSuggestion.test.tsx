// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { aiApi } from '../../services/aiApi';
import { DescribeItemSuggestion } from './DescribeItemSuggestion';

vi.mock('../../services/aiApi', () => ({ aiApi: { describeItem: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('DescribeItemSuggestion', () => {
  it('chỉ dùng tên và chỉ điền mô tả sau khi người đăng chấp nhận', async () => {
    const user = userEvent.setup();
    const onUse = vi.fn();
    vi.mocked(aiApi.describeItem).mockResolvedValue({
      description: 'Loại: Đồng hồ\nKích thước: … x … x … cm',
      attributes: [{ name: 'Kích thước', value: '… x … x … cm', requiresUserInput: true }],
      remainingUses: 4,
      message: 'Bạn kiểm tra bản nháp nhé.',
    });
    const { rerender } = render(<DescribeItemSuggestion title="" onUse={onUse} />);
    expect(screen.getByRole('button', { name: 'Gợi ý mô tả bằng AI' }).hasAttribute('disabled')).toBe(true);

    rerender(<DescribeItemSuggestion title="Đồng hồ" onUse={onUse} />);
    await user.click(screen.getByRole('button', { name: 'Gợi ý mô tả bằng AI' }));
    expect(aiApi.describeItem).toHaveBeenCalledWith('Đồng hồ');
    expect(await screen.findByText(/Kích thước: … x … x … cm/)).toBeTruthy();
    expect(onUse).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Dùng gợi ý' }));
    expect(onUse).toHaveBeenCalledWith('Loại: Đồng hồ\nKích thước: … x … x … cm');
  });

  it('lỗi AI không cản người đăng tự viết mô tả', async () => {
    const user = userEvent.setup();
    vi.mocked(aiApi.describeItem).mockRejectedValue(new Error('timeout'));
    render(<DescribeItemSuggestion title="Đồng hồ" onUse={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Gợi ý mô tả bằng AI' }));
    expect(await screen.findByText('Chưa gợi ý được, bạn tự viết mô tả nhé.')).toBeTruthy();
  });
});
