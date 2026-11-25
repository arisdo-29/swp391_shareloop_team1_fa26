// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminRequestApi, type AdminRequestDetail, type AdminRequestRow } from '../../services/adminRequestApi';
import { AdminTransactions } from './AdminTransactions';

vi.mock('../../services/adminRequestApi', () => ({
  adminRequestApi: { list: vi.fn(), detail: vi.fn(), cancel: vi.fn() },
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
    deliveryMethod: 'IN_PERSON',
    ...overrides,
  };
}

function page(items: AdminRequestRow[]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

function detail(rowValue: AdminRequestRow, offeredItem: AdminRequestDetail['offeredItem'] = null): AdminRequestDetail {
  return {
    id: rowValue.id,
    type: rowValue.type,
    status: rowValue.status,
    donor: null,
    receiver: { userId: rowValue.receiverId, name: rowValue.receiverName, email: null, trustStars: null, rank: null, locked: false },
    item: {
      itemId: rowValue.itemId,
      title: rowValue.itemTitle,
      imageUrl: `/media/items/${rowValue.itemId}.jpg`,
      status: 'APPROVED',
      offerType: rowValue.type,
    },
    offeredItem,
    deliveryMethod: rowValue.deliveryMethod,
    meetingPlace: null,
    meetingTime: rowValue.meetingTime,
    timeline: [],
    photos: [],
    reports: [],
    points: [],
    messagesViewable: false,
    cancellable: true,
    createdAt: rowValue.createdAt,
  };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminTransactions />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  const rows = [row({}), row({ id: 6, itemTitle: 'Sách cũ', status: 'COMPLETED', flagReason: null })];
  vi.mocked(adminRequestApi.list).mockResolvedValue(page(rows));
  vi.mocked(adminRequestApi.detail).mockImplementation(async (id) => detail(rows.find((entry) => entry.id === id) ?? rows[0]));
  vi.mocked(adminRequestApi.cancel).mockResolvedValue({} as never);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminTransactions', () => {
  it('hiện danh sách kèm lý do kẹt và chỉ có nút huỷ ở giao dịch huỷ được', async () => {
    renderPage();

    expect(await screen.findByText('Xe đạp cũ')).toBeTruthy();
    expect(screen.getByText(/Bị kẹt: Quá giờ hẹn hơn 48 giờ/)).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'Huỷ giao dịch' })).toHaveLength(1);
  });

  it('bấm Bị kẹt thì gọi API với stuck=true', async () => {
    renderPage();
    await screen.findByText('Xe đạp cũ');

    await userEvent.click(screen.getByRole('button', { name: 'Quá giờ xác nhận bàn giao' }));

    await waitFor(() =>
      expect(adminRequestApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ stuck: true, page: 0 })),
    );
  });

  it('lọc theo trạng thái gửi status lên API', async () => {
    renderPage();
    await screen.findByText('Xe đạp cũ');

    await userEvent.selectOptions(screen.getByLabelText('Trạng thái'), 'DISPUTED');

    await waitFor(() =>
      expect(adminRequestApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'DISPUTED' })),
    );
  });

  it('hiện hình thức giao nhận, không hiện mã kỹ thuật, và món đồ dẫn tới trang chi tiết bài đăng Admin', async () => {
    renderPage();

    const link = await screen.findByRole('link', { name: 'Xe đạp cũ' });
    expect(link.getAttribute('href')).toBe('/admin/items/1');
    expect(screen.getByRole('link', { name: 'Xem chi tiết Xe đạp cũ' }).getAttribute('href')).toBe('/admin/items/1');
    expect(screen.getByRole('columnheader', { name: 'Món đồ chủ bài' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Món đem đổi' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Hình thức' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Người nhận / trao đổi' })).toBeTruthy();
    expect(screen.getAllByText('Cho tặng').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Gặp trực tiếp').length).toBeGreaterThan(0);
    expect(document.body.textContent ?? '').not.toContain('mã 5');
    expect(document.body.textContent ?? '').not.toContain('IN_PERSON');
  });

  it('hiện đúng món đồ trao đổi của từng giao dịch SWAP và liên kết bằng ID API trả về', async () => {
    const first = row({ id: 8, itemId: 18, itemTitle: 'Máy ảnh của chủ bài', type: 'SWAP', flagReason: null });
    const second = row({ id: 9, itemId: 19, itemTitle: 'Đàn của chủ bài', type: 'SWAP', flagReason: null });
    vi.mocked(adminRequestApi.list).mockResolvedValue(page([first, second]));
    vi.mocked(adminRequestApi.detail).mockImplementation(async (id) => {
      const current = id === first.id ? first : second;
      return detail(current, {
        itemId: id === first.id ? 108 : 109,
        title: id === first.id ? 'Xe đạp đề xuất' : 'Loa đề xuất',
        imageUrl: `/media/items/${id === first.id ? 108 : 109}.jpg`,
        status: 'RESERVED',
        offerType: 'SWAP',
      });
    });

    renderPage();

    expect((await screen.findByRole('link', { name: 'Xe đạp đề xuất' })).getAttribute('href')).toBe('/admin/items/108');
    expect(screen.getByRole('link', { name: 'Loa đề xuất' }).getAttribute('href')).toBe('/admin/items/109');
    expect(screen.getByRole('link', { name: 'Xem chi tiết Xe đạp đề xuất' }).getAttribute('href')).toBe('/admin/items/108');
    expect(screen.getAllByText('Trao đổi', { selector: 'span' })).toHaveLength(2);
  });

  it('chỉ hiện món được cho tặng với giao dịch GIVE', async () => {
    renderPage();

    const title = await screen.findByText('Xe đạp cũ');
    const currentRow = title.closest('tr');
    expect(currentRow).not.toBeNull();
    expect(within(currentRow as HTMLTableRowElement).getByText('Cho tặng')).toBeTruthy();
    expect(within(currentRow as HTMLTableRowElement).getAllByRole('img')).toHaveLength(1);
    expect(within(currentRow as HTMLTableRowElement).getAllByText('—').length).toBeGreaterThan(0);
  });

  it('lọc theo loại, hình thức giao nhận và khoảng ngày gửi đúng tham số', async () => {
    renderPage();
    await screen.findByText('Xe đạp cũ');

    await userEvent.selectOptions(screen.getByLabelText('Loại giao dịch'), 'SWAP');
    await userEvent.selectOptions(screen.getByLabelText('Hình thức giao nhận'), 'SHIPPING');
    await userEvent.type(screen.getByLabelText('Từ ngày'), '2026-10-01');

    await waitFor(() =>
      expect(adminRequestApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ type: 'SWAP', deliveryMethod: 'SHIPPING', from: '2026-10-01', page: 0 }),
      ),
    );
  });

  it('khoảng ngày ngược thì báo lỗi và không gọi lại API', async () => {
    renderPage();
    await screen.findByText('Xe đạp cũ');
    vi.mocked(adminRequestApi.list).mockClear();

    await userEvent.type(screen.getByLabelText('Từ ngày'), '2026-10-20');
    await userEvent.type(screen.getByLabelText('Đến ngày'), '2026-10-10');

    expect(await screen.findByText('"Từ ngày" không được sau "Đến ngày".')).toBeTruthy();
    expect(adminRequestApi.list).not.toHaveBeenCalledWith(
      expect.objectContaining({ from: '2026-10-20', to: '2026-10-10' }),
    );
  });

  it('huỷ giao dịch cần lý do tối thiểu 5 ký tự rồi gọi API và tải lại danh sách', async () => {
    renderPage();
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
    renderPage();
    await screen.findByText('Xe đạp cũ');

    await userEvent.click(screen.getByRole('button', { name: 'Huỷ giao dịch' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/Lý do huỷ/), 'Lý do hợp lệ');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Huỷ giao dịch' }));

    expect(await within(dialog).findByRole('alert')).toBeTruthy();
    expect(within(dialog).getByRole('alert').textContent).toContain('không còn ở trạng thái huỷ được');
  });
});

describe('AdminTransactions nhãn và bộ lọc của luật mới', () => {
  it('hiện nhãn đề nghị huỷ đang chờ, chờ gửi hàng và một bên đã nhận', async () => {
    vi.mocked(adminRequestApi.list).mockResolvedValue(
      page([
        row({ id: 7, flagReason: null, labels: ['Đang chờ phản hồi đề nghị huỷ'] }),
        row({
          id: 8,
          flagReason: null,
          deliveryMethod: 'SHIPPING',
          labels: ['Chờ gửi hàng', 'Quá hạn gửi hàng', 'Một bên đã nhận'],
        }),
      ]),
    );
    renderPage();

    expect(await screen.findByText('Đang chờ phản hồi đề nghị huỷ', { selector: 'p' })).toBeTruthy();
    expect(screen.getByText('Chờ gửi hàng')).toBeTruthy();
    expect(screen.getByText('Một bên đã nhận')).toBeTruthy();
  });

  it('bấm hai nút lọc mới thì gọi API với pendingCancel và sendOverdue', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Sách cũ');

    await user.click(screen.getByRole('button', { name: 'Đang chờ phản hồi đề nghị huỷ' }));
    await waitFor(() =>
      expect(adminRequestApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ pendingCancel: true })),
    );
    await user.click(screen.getByRole('button', { name: 'Giao hàng quá hạn gửi' }));
    await waitFor(() =>
      expect(adminRequestApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ pendingCancel: true, sendOverdue: true }),
      ),
    );
  });
});
