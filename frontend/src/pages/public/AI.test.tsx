// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { aiApi } from '../../services/aiApi';
import { useWallet } from '../../app/useWallet';
import type { ItemResponse } from '../../services/itemApi';
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
  sessionStorage.clear();
  vi.mocked(useWallet).mockReturnValue({
    wallet: { creditBalance: 4, heldCredit: 0, available: 4, hasToppedUp: true },
    loading: false,
    error: '',
    refresh: vi.fn(),
    replaceWallet: vi.fn(),
  });
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
  it('hỏi xác nhận trước lượt tìm có phí', async () => {
    const user = userEvent.setup();
    vi.mocked(aiApi.quota).mockReset().mockResolvedValue({ freeUsesRemaining: 0, feeCredits: 2, hasToppedUp: true });
    render(<MemoryRouter><AI /></MemoryRouter>);

    await screen.findByText(/Lượt tiếp theo/);
    await user.type(screen.getByRole('textbox', { name: 'Mô tả món đồ cần tìm' }), 'Tìm sách cũ');
    await user.click(screen.getByRole('button', { name: 'Tìm giúp tôi' }));

    expect(screen.getByRole('dialog')).toBeTruthy();
    expect(screen.getByText('Lượt này tính 2 Credit. Số dư hiện có 4 Credit. Tiếp tục?')).toBeTruthy();
    expect(aiApi.search).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Tiếp tục' }));
    await waitFor(() => expect(aiApi.search).toHaveBeenCalledWith('Tìm sách cũ', true));
  });

  it('hỏi ngay khi chọn gợi ý có phí và chỉ tìm sau khi xác nhận', async () => {
    const user = userEvent.setup();
    vi.mocked(aiApi.quota).mockReset().mockResolvedValue({ freeUsesRemaining: 0, feeCredits: 2, hasToppedUp: true });
    render(<MemoryRouter><AI /></MemoryRouter>);

    await screen.findByText(/Lượt tiếp theo/);
    const suggestion = screen.getByText('Thử:').parentElement!;
    const example = within(suggestion).getAllByRole('button')[0];
    await user.click(example);

    const dialog = screen.getByRole('dialog', { name: 'Xác nhận lượt tìm có phí' });
    expect(aiApi.search).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: 'Huỷ' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(aiApi.search).not.toHaveBeenCalled();

    await user.click(example);
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Tiếp tục' }));
    await waitFor(() => expect(aiApi.search).toHaveBeenCalledExactlyOnceWith(example.textContent, true));
  });

  it('chỉ điền gợi ý vào ô tìm khi còn lượt miễn phí để người dùng chỉnh sửa', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><AI /></MemoryRouter>);

    await screen.findByText('Còn 5 lượt miễn phí');
    const example = within(screen.getByText('Thử:').parentElement!).getAllByRole('button')[0];
    await user.click(example);

    const input = screen.getByRole<HTMLTextAreaElement>('textbox', { name: 'Mô tả món đồ cần tìm' });
    expect(input.value).toBe(example.textContent);
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(aiApi.search).not.toHaveBeenCalled();
  });

  it('không gọi API khi hết lượt và thiếu Credit', async () => {
    const user = userEvent.setup();
    vi.mocked(useWallet).mockReturnValue({
      wallet: { creditBalance: 1, heldCredit: 0, available: 1, hasToppedUp: true },
      loading: false,
      error: '',
      refresh: vi.fn(),
      replaceWallet: vi.fn(),
    });
    vi.mocked(aiApi.quota).mockReset().mockResolvedValue({ freeUsesRemaining: 0, feeCredits: 2, hasToppedUp: true });
    render(<MemoryRouter><AI /></MemoryRouter>);

    await screen.findByText(/Lượt tiếp theo/);
    await user.type(screen.getByRole('textbox', { name: 'Mô tả món đồ cần tìm' }), 'Tìm sách cũ');
    await user.click(screen.getByRole('button', { name: 'Tìm giúp tôi' }));

    expect(aiApi.search).not.toHaveBeenCalled();
    expect(screen.getByText('Bạn đã hết lượt miễn phí và chưa đủ Credit cho lượt tìm tiếp theo.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Nạp Credit' })).toBeTruthy();
  });

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

    await waitFor(() => expect(aiApi.search).toHaveBeenCalledWith('Tìm sách cũ', false));
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

  it('giữ gợi ý khi xem bài rồi quay lại mà không tìm hoặc trừ lượt lần nữa', async () => {
    const user = userEvent.setup();
    const item: ItemResponse = {
      id: 12,
      donorId: 9,
      categoryId: 2,
      areaId: 3,
      offerType: 'GIVE',
      title: 'Đèn bàn học',
      description: 'Đèn còn dùng tốt',
      desiredItem: null,
      condition: 'GOOD',
      defectNote: null,
      brand: null,
      images: [],
      defectImages: [],
      operatingStatus: null,
      status: 'APPROVED',
      createdAt: '2026-11-02T10:00:00Z',
      updatedAt: '2026-11-02T10:00:00Z',
      expireAt: null,
      boostedUntil: null,
    };
    vi.mocked(aiApi.quota).mockReset().mockResolvedValue({ freeUsesRemaining: 4, feeCredits: 2, hasToppedUp: true });
    vi.mocked(aiApi.search).mockResolvedValue({
      message: 'Mình tìm được món này cho bạn nhé!',
      results: { items: [item], page: 0, size: 1, totalElements: 1 },
      quota: { freeUsesRemaining: 4, feeCredits: 2, hasToppedUp: true },
      chargedCredits: 0,
      fallback: false,
      outOfScope: false,
    });

    render(
      <MemoryRouter initialEntries={['/ai']}>
        <Routes>
          <Route path="/ai" element={<AI />} />
          <Route path="/items/:id" element={<Link to="/ai">Quay lại gợi ý AI</Link>} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('Còn 4 lượt miễn phí');
    await user.type(screen.getByRole('textbox', { name: 'Mô tả món đồ cần tìm' }), 'Cần đèn học');
    await user.click(screen.getByRole('button', { name: 'Tìm giúp tôi' }));
    expect(await screen.findByText('Mình tìm được món này cho bạn nhé!')).toBeTruthy();
    const itemLink = screen.getByRole('link', { name: /Đèn bàn học/ });
    expect(itemLink.getAttribute('href')).toBe('/items/12?from=ai');
    await user.click(itemLink);
    await user.click(screen.getByRole('link', { name: 'Quay lại gợi ý AI' }));

    expect(await screen.findByText('Mình tìm được món này cho bạn nhé!')).toBeTruthy();
    expect(screen.getByText('Đèn bàn học')).toBeTruthy();
    expect(aiApi.search).toHaveBeenCalledTimes(1);
  });
});
