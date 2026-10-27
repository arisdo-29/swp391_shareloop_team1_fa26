// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { aiApi } from '../../services/aiApi';
import { AI } from './AI';

vi.mock('../../app/hooks', () => ({
  useAppSelector: vi.fn(() => ({ id: '7' })),
}));

vi.mock('../../app/store', () => ({
  selectCurrentUser: vi.fn(),
}));

vi.mock('../../app/useWallet', () => ({
  useWallet: vi.fn(() => ({ refresh: vi.fn() })),
}));

vi.mock('../../services/aiApi', () => ({
  aiApi: { quota: vi.fn(), search: vi.fn() },
}));

vi.mock('../../services/catalogApi', () => ({
  catalogApi: {
    categories: vi.fn(() => Promise.resolve([])),
    areas: vi.fn(() => Promise.resolve([])),
  },
}));

beforeEach(() => {
  vi.mocked(aiApi.quota)
    .mockResolvedValueOnce({ freeUsesRemaining: 5, feeCredits: 2, hasToppedUp: true })
    .mockResolvedValueOnce({ freeUsesRemaining: 4, feeCredits: 2, hasToppedUp: true });
  vi.mocked(aiApi.search).mockResolvedValue({
    filters: { keywords: 'sách', categoryId: null, areaId: null, condition: null, offerType: null },
    results: { items: [], page: 0, size: 20, totalElements: 0 },
    quota: { freeUsesRemaining: 5, feeCredits: 2, hasToppedUp: true },
    chargedCredits: 0,
    fallback: false,
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AI search quota', () => {
  it('tải lại quota từ Backend sau khi tìm kiếm thành công', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AI />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Còn 5 lượt miễn phí')).toBeTruthy();
    await user.type(screen.getByRole('textbox', { name: 'Mô tả món đồ cần tìm' }), 'Tìm sách cũ');
    await user.click(screen.getByRole('button', { name: 'Tìm' }));

    await waitFor(() => expect(aiApi.search).toHaveBeenCalledWith('Tìm sách cũ', 0, 20));
    await waitFor(() => expect(aiApi.quota).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Còn 4 lượt miễn phí')).toBeTruthy();
  });

  it('giữ nguyên quota khi Backend fallback sang tìm kiếm thường', async () => {
    const user = userEvent.setup();
    vi.mocked(aiApi.quota).mockReset().mockResolvedValue({
      freeUsesRemaining: 5,
      feeCredits: 2,
      hasToppedUp: true,
    });
    vi.mocked(aiApi.search).mockResolvedValue({
      filters: { keywords: 'sách', categoryId: null, areaId: null, condition: null, offerType: null },
      results: { items: [], page: 0, size: 20, totalElements: 0 },
      quota: { freeUsesRemaining: 5, feeCredits: 2, hasToppedUp: true },
      chargedCredits: 0,
      fallback: true,
    });
    render(
      <MemoryRouter>
        <AI />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Còn 5 lượt miễn phí')).toBeTruthy();
    await user.type(screen.getByRole('textbox', { name: 'Mô tả món đồ cần tìm' }), 'Tìm sách cũ');
    await user.click(screen.getByRole('button', { name: 'Tìm' }));

    await waitFor(() => expect(aiApi.quota).toHaveBeenCalledTimes(2));
    expect(screen.getByText('Còn 5 lượt miễn phí')).toBeTruthy();
    expect(screen.queryByText('AI hiện không phản hồi. Đây là kết quả tìm kiếm thường; lượt miễn phí và Credit của bạn không bị trừ.')).toBeNull();
    expect(screen.getByText('Từ khóa tìm kiếm thường')).toBeTruthy();
  });
});
