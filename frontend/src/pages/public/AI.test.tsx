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
    message: 'Mình chưa tìm thấy món phù hợp.',
    results: { items: [], page: 0, size: 20, totalElements: 0 },
    quota: { freeUsesRemaining: 5, feeCredits: 2, hasToppedUp: true },
    chargedCredits: 0,
    fallback: false,
    outOfScope: false,
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
    await user.click(screen.getByRole('button', { name: 'Tìm giúp tôi' }));

    await waitFor(() => expect(aiApi.search).toHaveBeenCalledWith('Tìm sách cũ'));
    await waitFor(() => expect(aiApi.quota).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Còn 4 lượt miễn phí')).toBeTruthy();
    expect(screen.getByText('Mình chưa tìm thấy món phù hợp.')).toBeTruthy();
  });

  it('giữ nguyên quota khi AI không khả dụng', async () => {
    const user = userEvent.setup();
    vi.mocked(aiApi.quota).mockReset().mockResolvedValue({
      freeUsesRemaining: 5,
      feeCredits: 2,
      hasToppedUp: true,
    });
    vi.mocked(aiApi.search).mockResolvedValue({
      message: 'Mình chưa thể tìm đồ bằng AI lúc này.',
      results: { items: [], page: 0, size: 20, totalElements: 0 },
      quota: { freeUsesRemaining: 5, feeCredits: 2, hasToppedUp: true },
      chargedCredits: 0,
      fallback: true,
      outOfScope: false,
    });
    render(
      <MemoryRouter>
        <AI />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Còn 5 lượt miễn phí')).toBeTruthy();
    await user.type(screen.getByRole('textbox', { name: 'Mô tả món đồ cần tìm' }), 'Tìm sách cũ');
    await user.click(screen.getByRole('button', { name: 'Tìm giúp tôi' }));

    await waitFor(() => expect(aiApi.quota).toHaveBeenCalledTimes(2));
    expect(screen.getByText('Còn 5 lượt miễn phí')).toBeTruthy();
    expect(screen.getByText('Mình chưa thể tìm đồ bằng AI lúc này.')).toBeTruthy();
  });

  it('chỉ hiển thị một lời nhắn cho yêu cầu ngoài phạm vi', async () => {
    const user = userEvent.setup();
    vi.mocked(aiApi.search).mockResolvedValue({
      message: 'Mình chỉ hỗ trợ tìm đồ trên ShareLoop.',
      results: { items: [], page: 0, size: 0, totalElements: 0 },
      quota: { freeUsesRemaining: 0, feeCredits: 2, hasToppedUp: true },
      chargedCredits: 0,
      fallback: false,
      outOfScope: true,
    });
    render(
      <MemoryRouter>
        <AI />
      </MemoryRouter>,
    );

    await user.type(screen.getByRole('textbox', { name: 'Mô tả món đồ cần tìm' }), 'bàn học');
    await user.click(screen.getByRole('button', { name: 'Tìm giúp tôi' }));

    expect(await screen.findByText('Mình chỉ hỗ trợ tìm đồ trên ShareLoop.')).toBeTruthy();
    expect(screen.queryByText('Bộ lọc trợ lý hiểu được')).toBeNull();
  });
});
