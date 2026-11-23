// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DEFAULT_PUBLIC_CONFIG, publicConfigApi } from '../../services/publicConfigApi';
import { About } from './About';

vi.mock('../../app/hooks', () => ({ useAppSelector: vi.fn(() => null) }));
vi.mock('../../app/store', () => ({ selectCurrentUser: vi.fn() }));
vi.mock('../../services/itemApi', () => ({ itemApi: { getListingFees: vi.fn() } }));
vi.mock('../../services/publicConfigApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/publicConfigApi')>()),
  publicConfigApi: { load: vi.fn() },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('Trang Giới thiệu đọc số từ cấu hình', () => {
  it('đổi cấu hình thì câu chữ đổi theo', async () => {
    // given: Admin đã đổi thời gian chờ yêu cầu thành 7 ngày
    vi.mocked(publicConfigApi.load).mockResolvedValue({ ...DEFAULT_PUBLIC_CONFIG, request_pending_days: 7 });

    // when
    render(
      <MemoryRouter>
        <About />
      </MemoryRouter>,
    );

    // then
    expect(await screen.findByText(/Yêu cầu chờ quá 7 ngày thì tự huỷ/)).toBeTruthy();
  });

  it('gọi lỗi thì dùng giá trị dự phòng', async () => {
    // given
    vi.mocked(publicConfigApi.load).mockResolvedValue(DEFAULT_PUBLIC_CONFIG);

    // when
    render(
      <MemoryRouter>
        <About />
      </MemoryRouter>,
    );

    // then
    expect(await screen.findByText(/Yêu cầu chờ quá 3 ngày thì tự huỷ/)).toBeTruthy();
  });
});
