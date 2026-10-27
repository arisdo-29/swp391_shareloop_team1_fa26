// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useAppSelector } from '../../app/hooks';
import { itemApi } from '../../services/itemApi';
import { About } from './About';

vi.mock('../../app/hooks', () => ({
  useAppSelector: vi.fn(() => null),
}));

vi.mock('../../app/store', () => ({
  selectCurrentUser: vi.fn(),
}));

vi.mock('../../services/itemApi', () => ({
  itemApi: { getListingFees: vi.fn() },
}));

function renderAbout() {
  return render(
    <MemoryRouter>
      <About />
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('trang Giới thiệu', () => {
  it('khách xem được hướng dẫn mà không gọi API phí', () => {
    renderAbout();

    expect(screen.getByRole('heading', { name: 'Cách dùng trong 4 bước' })).toBeTruthy();
    expect(screen.getByText('Đăng nhập để xem mức phí hiện tại.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Tạo tài khoản' }).getAttribute('href')).toBe('/register');
    expect(itemApi.getListingFees).not.toHaveBeenCalled();
  });

  it('người đã đăng nhập thấy mức phí lấy từ backend', async () => {
    vi.mocked(useAppSelector).mockReturnValue({ id: 'api_7' });
    vi.mocked(itemApi.getListingFees).mockResolvedValue({
      postFeeSwap: 7,
      postFeeGive: 0,
      editFee: 4,
      renewFee: 6,
      renewExpiredWindowDays: 7,
      boostFee: 9,
      boostDays: 3,
      boostMaxDays: 14,
      postDisplayDays: 30,
      postDisplayDaysGold: 45,
    });

    renderAbout();

    expect(await screen.findByText('Đăng bài Trao đổi: 7 Credit')).toBeTruthy();
    expect(screen.getByText('Đẩy bài lên đầu 3 ngày: 9 Credit')).toBeTruthy();
  });
});
