// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminRequestApi, type AdminRequestRow } from '../../services/adminRequestApi';
import { AdminTransactions } from './AdminTransactions';

vi.mock('../../services/adminRequestApi', () => ({
  adminRequestApi: { list: vi.fn(), cancel: vi.fn() },
}));

function row(overrides: Partial<AdminRequestRow>): AdminRequestRow {
  return {
    id: 5,
    itemId: 1,
    itemTitle: 'Xe đạp cũ',
    type: 'GIVE',
    status: 'LOGISTICS_CONFIRMED',
    donorId: 20,
    donorName: 'An Chủ Bài',
    receiverId: 10,
    receiverName: 'Bình Người Nhận',
    meetingTime: '2026-10-25T09:00:00Z',
    createdAt: '2026-10-20T08:00:00Z',
    flagReason: 'Quá giờ hẹn hơn 48 giờ mà chưa bên nào xác nhận bàn giao',
    ...overrides,
  };
}

function page(items: AdminRequestRow[]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

beforeEach(() => {
  vi.mocked(adminRequestApi.list).mockResolvedValue(
    page([row({}), row({ id: 6, itemTitle: 'Sách cũ', status: 'COMPLETED', flagReason: null })]),
  );
  vi.mocked(adminRequestApi.cancel).mockResolvedValue({} as never);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminTransactions', () => {
  it('hiện danh sách kèm lý do kẹt và chỉ có nút huỷ ở giao dịch huỷ được', async () => {
    render(<AdminTransactions />);

    expect(await screen.findByText('Xe đạp cũ')).toBeTruthy();
    expect(screen.getByText(/Bị kẹt: Quá giờ hẹn hơn 48 giờ/)).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'Huỷ giao dịch' })).toHaveLength(1);
  });

  it('bấm Bị kẹt thì gọi API với stuck=true', async () => {
    render(<AdminTransactions />);
    await screen.findByText('Xe đạp cũ');

    await userEvent.click(screen.getByRole('button', { name: 'Bị kẹt' }));

    await waitFor(() =>
      expect(adminRequestApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ stuck: true, page: 0 })),
    );
  });

  it('lọc theo trạng thái gửi status lên API', async () => {
    render(<AdminTransactions />);
    await screen.findByText('Xe đạp cũ');

    await userEvent.selectOptions(screen.getByLabelText('Trạng thái'), 'DISPUTED');

    await waitFor(() =>
      expect(adminRequestApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'DISPUTED' })),
    );
  });

  it('huỷ giao dịch cần lý do tối thiểu 5 ký tự rồi gọi API và tải lại danh sách', async () => {
    render(<AdminTransactions />);
    await screen.findByText('Xe đạp cũ');

    await userEvent.click(screen.getByRole('button', { name: 'Huỷ giao dịch' }));
    const dialog = await screen.findByRole('dialog');
    const submit = within(dialog).getByRole('button', { name: 'Huỷ giao dịch' });
    expect((submit as HTMLButtonElement).disabled).toBe(true);

    await userEvent.type(within(dialog).getByLabelText(/Lý do huỷ/), 'Hai bên không liên lạc được');
    await userEvent.click(submit);

    await waitFor(() => expect(adminRequestApi.cancel).toHaveBeenCalledWith(5, 'Hai bên không liên lạc được'));
    expect(await screen.findByText(/Đã huỷ giao dịch/)).toBeTruthy();
    expect(adminRequestApi.list).toHaveBeenCalledTimes(2);
  });

  it('hiện lỗi tiếng Việt khi backend trả 409', async () => {
    vi.mocked(adminRequestApi.cancel).mockRejectedValue({ status: 409, code: 'REQUEST_INVALID_STATE_TRANSITION' });
    render(<AdminTransactions />);
    await screen.findByText('Xe đạp cũ');

    await userEvent.click(screen.getByRole('button', { name: 'Huỷ giao dịch' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/Lý do huỷ/), 'Lý do hợp lệ');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Huỷ giao dịch' }));

    expect(await within(dialog).findByRole('alert')).toBeTruthy();
    expect(within(dialog).getByRole('alert').textContent).toContain('không còn ở trạng thái huỷ được');
  });
});
