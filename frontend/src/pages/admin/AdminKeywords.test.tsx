// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminLexiconApi, type AdminLexiconEntry } from '../../services/adminLexiconApi';
import { AdminContentPage } from './AdminPages';

vi.mock('../../services/adminLexiconApi', () => ({
  adminLexiconApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    setActive: vi.fn(),
    test: vi.fn(),
  },
}));

function lexiconEntry(id: number): AdminLexiconEntry {
  return {
    id,
    keyword: `Từ khóa ${id}`,
    group: 'CONTACT',
    level: 'BLOCK',
    matchMode: null,
    exceptions: [],
    scope: 'POST',
    sortOrder: id,
    active: true,
  };
}

const entries = Array.from({ length: 21 }, (_, index) => lexiconEntry(index + 1));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('Từ khóa kiểm duyệt', () => {
  beforeEach(() => {
    vi.mocked(adminLexiconApi.list).mockResolvedValue(entries);
    vi.mocked(adminLexiconApi.setActive).mockImplementation(async (id, active) => ({
      ...entries.find((entry) => entry.id === id)!,
      active,
    }));
    vi.mocked(adminLexiconApi.update).mockImplementation(async (id, input) => ({
      ...entries.find((entry) => entry.id === id)!,
      ...input,
    }));
  });

  it('hiện 20 dòng mỗi trang và giữ trang hiện tại sau khi tạm ngưng hoặc sửa', async () => {
    const user = userEvent.setup();
    render(<AdminContentPage kind="keywords" />);

    const firstPageTable = (await screen.findByText('Từ khóa 1')).closest('table')!;
    expect(within(firstPageTable).getAllByRole('row')).toHaveLength(21);
    expect(screen.queryByText('Từ khóa 21')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));
    expect(await screen.findByText('Từ khóa 21')).toBeTruthy();
    expect(screen.getByText('21 kết quả, trang 2/2')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Tạm ngưng' }));
    expect(await screen.findByText('Đang ẩn')).toBeTruthy();
    expect(screen.getByText('21 kết quả, trang 2/2')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Sửa' }));
    const keywordInput = screen.getByLabelText('Từ khóa');
    await user.clear(keywordInput);
    await user.type(keywordInput, 'Từ khóa đã sửa');
    await user.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() => expect(adminLexiconApi.update).toHaveBeenCalled());
    expect(await screen.findByText('Từ khóa đã sửa')).toBeTruthy();
    expect(screen.getByText('21 kết quả, trang 2/2')).toBeTruthy();
  });
});
