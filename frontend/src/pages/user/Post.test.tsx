// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { catalogApi } from '../../services/catalogApi';
import { itemApi } from '../../services/itemApi';
import { Post } from './Post';

vi.mock('../../app/hooks', () => ({
  useAppSelector: () => ({ id: 'api_10', name: 'Người đăng', role: 'user' }),
}));
vi.mock('../../app/useWallet', () => ({
  useWallet: () => ({ wallet: { available: 100 }, loading: false }),
}));

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it('K10: trạng thái hoạt động bất thường tự chọn Có lỗi và mở mô tả, ảnh lỗi', async () => {
  vi.spyOn(catalogApi, 'categories').mockResolvedValue([
    { id: 7, name: 'Điện tử và phụ kiện', parentId: null, restricted: false,
      boostable: true, sortOrder: 1, activeItemCount: 0 },
  ]);
  vi.spyOn(catalogApi, 'areas').mockResolvedValue([]);
  vi.spyOn(catalogApi, 'categoryAttributes').mockResolvedValue([
    { id: 1, categoryId: 7, name: 'Trạng thái hoạt động', dataType: 'SELECT',
      options: ['NORMAL', 'PARTIAL', 'NOT_WORKING'], required: true, sortOrder: 1 },
  ]);
  vi.spyOn(itemApi, 'postingQuota').mockRejectedValue(new Error('Không có dữ liệu phí trong test'));
  const user = userEvent.setup();
  render(<MemoryRouter><Post /></MemoryRouter>);
  await screen.findByRole('option', { name: 'Điện tử và phụ kiện' });
  await user.selectOptions(screen.getByLabelText('Danh mục'), 'Điện tử và phụ kiện');
  const status = await screen.findByLabelText('Trạng thái hoạt động');

  await user.selectOptions(status, 'PARTIAL');
  expect(screen.getByRole('button', { name: /Có lỗi/ }).getAttribute('aria-pressed')).toBe('true');
  expect(screen.getByLabelText('Lỗi cụ thể')).toBeTruthy();
  expect(screen.getByLabelText('Ảnh chụp rõ chỗ lỗi')).toBeTruthy();
  expect(screen.getByText(/Thiết bị không hoạt động bình thường cần mô tả lỗi/)).toBeTruthy();

  await user.selectOptions(status, 'NORMAL');
  expect(screen.getByRole('button', { name: /Có lỗi/ }).getAttribute('aria-pressed')).toBe('true');
  expect(screen.queryByText(/Thiết bị không hoạt động bình thường cần mô tả lỗi/)).toBeNull();
});
