// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Select, TextArea } from './ui';

afterEach(cleanup);

describe('Select', () => {
  it('mở listbox tùy biến và chọn option nhưng vẫn phát change từ select gốc', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <Select label="Khu vực" defaultValue="all" name="areaId" required onChange={onChange}>
        <option value="all">Tất cả khu vực</option>
        <option value="1">Quận 1</option>
        <option value="2">Quận 2</option>
      </Select>,
    );

    const select = screen.getByRole('combobox', { name: 'Khu vực' });
    await user.click(select);
    const listbox = screen.getByRole('listbox', { name: 'Khu vực' });
    await user.click(within(listbox).getByRole('option', { name: 'Quận 2' }));

    expect((select as HTMLSelectElement).value).toBe('2');
    expect((select as HTMLSelectElement).name).toBe('areaId');
    expect((select as HTMLSelectElement).required).toBe(true);
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('hỗ trợ bàn phím và bỏ qua option bị vô hiệu hóa', async () => {
    const user = userEvent.setup();
    render(
      <Select aria-label="Tình trạng" defaultValue="new">
        <option value="new">Mới</option>
        <option value="loading" disabled>
          Đang tải...
        </option>
        <option value="used">Đã sử dụng</option>
      </Select>,
    );

    const select = screen.getByRole('combobox', { name: 'Tình trạng' });
    select.focus();
    await user.keyboard('{ArrowDown}{ArrowDown}{Enter}');

    expect((select as HTMLSelectElement).value).toBe('used');
    expect(screen.queryByRole('listbox')).toBeNull();
  });

  it('tách class bố cục của wrapper khỏi class của ô nhập', () => {
    render(
      <>
        <Select label="Danh mục" className="mt-5" inputClassName="min-h-10">
          <option value="book">Sách</option>
        </Select>
        <TextArea label="Mô tả" className="mt-4" inputClassName="min-h-32" />
      </>,
    );

    const select = screen.getByRole('combobox', { name: 'Danh mục' });
    const textarea = screen.getByRole('textbox', { name: 'Mô tả' });
    expect(select.closest('label')?.classList.contains('mt-5')).toBe(true);
    expect(select.classList.contains('min-h-10')).toBe(true);
    expect(select.classList.contains('mt-5')).toBe(false);
    expect(textarea.closest('label')?.classList.contains('mt-4')).toBe(true);
    expect(textarea.classList.contains('min-h-32')).toBe(true);
    expect(textarea.classList.contains('mt-4')).toBe(false);
  });
});
