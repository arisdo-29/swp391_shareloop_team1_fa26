// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminAuthenticityApi } from '../../services/adminAuthenticityApi';
import { itemApi } from '../../services/itemApi';
import { AdminAuthenticity } from './AdminAuthenticity';

vi.mock('../../services/adminAuthenticityApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/adminAuthenticityApi')>();
  return {
    ...actual,
    adminAuthenticityApi: {
      grant: vi.fn(),
      reject: vi.fn(),
      revoke: vi.fn(),
    },
  };
});

vi.mock('../../services/itemApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/itemApi')>();
  return { ...actual, itemApi: { ...actual.itemApi, detail: vi.fn() } };
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminAuthenticity', () => {
  it('chỉ cho thao tác sau khi tải bài thật từ Backend', async () => {
    const user = userEvent.setup();
    vi.mocked(itemApi.detail).mockResolvedValue({
      id: 42,
      donorId: 9,
      categoryId: 1,
      areaId: 1,
      offerType: 'SWAP',
      title: 'Túi xách',
      description: 'Mô tả',
      desiredItem: null,
      condition: 'GOOD',
      defectNote: null,
      brand: 'Brand',
      images: [],
      defectImages: [],
      operatingStatus: 'NORMAL',
      status: 'APPROVED',
      createdAt: '2026-10-01T00:00:00Z',
      updatedAt: '2026-10-01T00:00:00Z',
      expireAt: null,
      boostedUntil: null,
    });
    vi.mocked(adminAuthenticityApi.grant).mockResolvedValue(undefined);
    render(<AdminAuthenticity />);

    const action = screen.getByRole('button', { name: 'Xác nhận chính hãng' });
    expect((action as HTMLButtonElement).disabled).toBe(true);
    await user.type(screen.getByRole('textbox', { name: /^ID bài đăng/ }), '42');
    await user.click(screen.getByRole('button', { name: 'Tải thông tin bài' }));

    expect(await screen.findByText('Túi xách')).toBeTruthy();
    expect(screen.getByText(/Backend chỉ cung cấp trạng thái bài đăng/)).toBeTruthy();
    expect((action as HTMLButtonElement).disabled).toBe(false);
    await user.click(action);
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận chính hãng' }));

    expect(adminAuthenticityApi.grant).toHaveBeenCalledWith(42);
    expect(await screen.findByText('Đã xác nhận hàng chính hãng thành công.')).toBeTruthy();
  });
});
