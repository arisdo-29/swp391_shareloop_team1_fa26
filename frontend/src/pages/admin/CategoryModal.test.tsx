// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AdminCategoryResponse } from '../../services/adminCategoryApi';
import { ApiCategoryModal } from './AdminPages';

const existingCategory: AdminCategoryResponse = {
  id: 7,
  name: 'Đồ điện tử',
  parentId: null,
  restricted: false,
  boostable: true,
  sortOrder: 3,
  active: true,
};

afterEach(cleanup);

function renderModal(category: AdminCategoryResponse | null = null) {
  const onSave = vi.fn();
  render(
    <ApiCategoryModal
      category={category}
      categories={category ? [category] : []}
      saving={false}
      onClose={() => undefined}
      onSave={onSave}
    />,
  );
  return { onSave };
}

describe('ApiCategoryModal', () => {
  it('không còn ô nhập thứ tự vì Backend tự xếp danh mục', () => {
    renderModal();

    expect(screen.queryByRole('spinbutton', { name: 'Thứ tự' })).toBeNull();
    expect(screen.getByText(/tự xếp cuối danh sách/)).toBeTruthy();
  });

  it('chưa nhập tên thì chưa cho lưu', () => {
    renderModal();

    expect((screen.getByRole('button', { name: 'Lưu' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('thêm danh mục mới không gửi sortOrder và dùng nhãn đẩy tin mới', async () => {
    const user = userEvent.setup();
    const { onSave } = renderModal();

    await user.type(screen.getByRole('textbox', { name: 'Tên danh mục' }), 'Nội thất');
    await user.click(screen.getByRole('checkbox', { name: 'Cho phép đẩy tin' }));
    await user.click(screen.getByRole('button', { name: 'Lưu' }));

    expect(screen.queryByText('Cho phép boost')).toBeNull();
    expect(onSave).toHaveBeenCalledWith({
      name: 'Nội thất',
      parentId: null,
      restricted: false,
      boostable: true,
    });
  });

  it('sửa danh mục giữ nguyên thông tin cũ và không gửi sortOrder', async () => {
    const user = userEvent.setup();
    const { onSave } = renderModal(existingCategory);

    expect(screen.getByRole('dialog', { name: 'Sửa danh mục' })).toBeTruthy();
    expect(screen.queryByText(/tự xếp cuối danh sách/)).toBeNull();
    const nameInput = screen.getByRole('textbox', { name: 'Tên danh mục' }) as HTMLInputElement;
    expect(nameInput.value).toBe('Đồ điện tử');
    await user.clear(nameInput);
    await user.type(nameInput, 'Điện tử gia dụng');
    await user.click(screen.getByRole('button', { name: 'Lưu' }));

    expect(onSave).toHaveBeenCalledWith({
      name: 'Điện tử gia dụng',
      parentId: null,
      restricted: false,
      boostable: true,
    });
  });
});
