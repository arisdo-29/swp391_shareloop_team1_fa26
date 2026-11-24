// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminItemApi, type AdminItemFullDetail } from '../../services/adminItemApi';
import { AdminItemDetail } from './AdminItemDetail';

vi.mock('../../services/adminItemApi', () => ({
  adminItemApi: { detail: vi.fn(), hide: vi.fn(), unhide: vi.fn() },
}));

function detail(overrides: Partial<AdminItemFullDetail> = {}, summaryOverrides = {}): AdminItemFullDetail {
  return {
    item: {
      item: {
        id: 7,
        donorId: 11,
        categoryId: 5,
        areaId: 2,
        offerType: 'GIVE',
        title: 'Xe đạp cũ',
        description: 'Xe còn chạy tốt',
        desiredItem: null,
        condition: 'GOOD',
        defectNote: null,
        brand: 'Thống Nhất',
        images: [],
        defectImages: [],
        operatingStatus: null,
        status: 'APPROVED',
        createdAt: '2026-11-01T03:00:00Z',
        updatedAt: '2026-11-01T03:00:00Z',
        expireAt: null,
        boostedUntil: null,
      },
      active: true,
      attributes: [{ attributeId: 1, name: 'Màu sắc', value: 'Xanh' }],
    },
    summary: {
      id: 7,
      title: 'Xe đạp cũ',
      imageUrl: null,
      ownerId: 11,
      ownerName: 'Lan An',
      ownerEmail: 'an@example.com',
      categoryName: 'Đồ thể thao',
      offerType: 'GIVE',
      status: 'APPROVED',
      active: true,
      hiddenSource: null,
      postFeePaid: 5,
      pendingRequestCount: 1,
      createdAt: '2026-11-01T03:00:00Z',
      approvedAt: '2026-11-02T03:00:00Z',
      expireAt: '2026-12-02T03:00:00Z',
      boostedUntil: null,
      boosting: false,
      ...summaryOverrides,
    },
    feeLines: [{ id: 1, type: 'POST_FEE', amount: -5, balanceAfter: 15, itemId: 7, paymentOrderId: null, note: null, createdAt: '2026-11-02T03:00:00Z' }],
    requests: [
      { id: 31, receiverName: 'Bình Trần', type: 'GIVE', status: 'PENDING', offeredItemTitle: null, running: false, createdAt: '2026-11-03T03:00:00Z' },
    ],
    hasRunningTransaction: false,
    runningRequestId: null,
    reports: [{ id: 9, reason: 'COUNTERFEIT', status: 'OPEN', createdAt: '2026-11-04T03:00:00Z' }],
    hiddenReason: null,
    visibilityHistory: [],
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/items/7']}>
      <Routes>
        <Route path="/admin/items/:itemId" element={<AdminItemDetail />} />
      </Routes>
    </MemoryRouter>,
  );
}

function LedgerReturnProbe() {
  const location = useLocation();
  return <p data-testid="restored-ledger-state">{JSON.stringify(location.state)}</p>;
}

beforeEach(() => {
  vi.mocked(adminItemApi.detail).mockResolvedValue(detail());
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminItemDetail', () => {
  it('có nút về lịch sử Credit và trả lại nguyên trạng thái bộ lọc khi được mở từ sổ Credit', async () => {
    const user = userEvent.setup();
    const ledgerReturn = {
      type: 'POST_FEE' as const,
      user: null,
      keywordInput: 'xe đạp',
      fromDate: '2026-11-01',
      toDate: '2026-11-20',
      page: 3,
    };
    render(
      <MemoryRouter initialEntries={[{ pathname: '/admin/items/7', state: { ledgerReturn } }]}>
        <Routes>
          <Route path="/admin/items/:itemId" element={<AdminItemDetail />} />
          <Route path="/admin/finance/ledger" element={<LedgerReturnProbe />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('Nội dung bài');
    await user.click(screen.getByRole('link', { name: 'Về lịch sử giao dịch Credit' }));

    expect(screen.getByTestId('restored-ledger-state').textContent).toContain('"restoreLedger"');
    expect(screen.getByTestId('restored-ledger-state').textContent).toContain('"page":3');
    expect(screen.getByTestId('restored-ledger-state').textContent).toContain('"keywordInput":"xe đạp"');
  });

  it('bài không còn tồn tại vẫn cho quay về lịch sử giao dịch Credit', async () => {
    vi.mocked(adminItemApi.detail).mockRejectedValue({ status: 404, message: 'Không tìm thấy bài đăng này.' });
    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: '/admin/items/7',
            state: {
              ledgerReturn: { type: '', user: null, keywordInput: '', fromDate: '', toDate: '', page: 1 },
            },
          },
        ]}
      >
        <Routes>
          <Route path="/admin/items/:itemId" element={<AdminItemDetail />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('Không tìm thấy dữ liệu yêu cầu.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Về lịch sử giao dịch Credit' })).toBeTruthy();
  });
  it('hiện nội dung bài, phí, yêu cầu và báo cáo bằng chữ tiếng Việt', async () => {
    renderPage();

    await screen.findByText('Nội dung bài');
    expect(adminItemApi.detail).toHaveBeenCalledWith(7);
    expect(screen.getByText('Phí đăng bài lúc', { exact: false })).toBeTruthy();
    expect(screen.getByText('Bình Trần')).toBeTruthy();
    expect(screen.getByText('Chờ phản hồi')).toBeTruthy();
    expect(screen.getByText('Hàng giả', { exact: false })).toBeTruthy();
    expect(screen.getByText('Đang mở')).toBeTruthy();
    const text = document.body.textContent ?? '';
    for (const hidden of ['POST_FEE', 'COUNTERFEIT', 'PENDING', 'APPROVED']) {
      expect(text).not.toContain(hidden);
    }
  });

  it('bài có giao dịch đang chạy thì nút Ẩn bài bị khoá kèm giải thích và liên kết sang giao dịch', async () => {
    vi.mocked(adminItemApi.detail).mockResolvedValue(
      detail({
        hasRunningTransaction: true,
        runningRequestId: 31,
        requests: [
          { id: 31, receiverName: 'Bình Trần', type: 'GIVE', status: 'AWAITING_LOGISTICS', offeredItemTitle: null, running: true, createdAt: '2026-11-03T03:00:00Z' },
        ],
      }),
    );
    renderPage();

    await screen.findByText('Nội dung bài');
    expect((screen.getByRole('button', { name: 'Ẩn bài' }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText(/chưa ẩn được/)).toBeTruthy();
    const links = screen.getAllByRole('link', { name: /Mở giao dịch/ });
    expect(links.some((link) => link.getAttribute('href') === '/admin/transactions/31')).toBe(true);
  });

  it('ẩn bài có lý do rồi cập nhật trang theo kết quả của máy chủ', async () => {
    const user = userEvent.setup();
    vi.mocked(adminItemApi.hide).mockResolvedValue(
      detail({ hiddenReason: 'Hàng giả theo báo cáo' }, { active: false, hiddenSource: 'ADMIN' }),
    );
    renderPage();
    await screen.findByText('Nội dung bài');

    await user.click(screen.getByRole('button', { name: 'Ẩn bài' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Lý do ẩn (bắt buộc)'), 'Hàng giả theo báo cáo');
    await user.click(within(dialog).getByRole('button', { name: 'Ẩn bài' }));

    await waitFor(() => expect(adminItemApi.hide).toHaveBeenCalledWith(7, 'Hàng giả theo báo cáo'));
    expect(await screen.findByText('Lý do Admin đã ẩn: Hàng giả theo báo cáo')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Hiện lại bài' })).toBeTruthy();
  });

  it('bài đang ẩn có nút Hiện lại và vẫn đọc được lịch sử ẩn hiện từ API cũ', async () => {
    vi.mocked(adminItemApi.detail).mockResolvedValue(
      detail(
        {
          hiddenReason: 'Vi phạm nội quy',
          visibilityHistory: [
            { id: 1, type: 'ADMIN_ITEM_HIDDEN', typeLabel: 'Admin ẩn bài đăng', actorName: 'Quản trị', reason: 'Vi phạm nội quy', createdAt: '2026-11-05T03:00:00Z' },
          ],
        },
        { active: false, hiddenSource: 'ADMIN' },
      ),
    );
    renderPage();

    await screen.findByText('Nội dung bài');
    expect(screen.getByRole('button', { name: 'Hiện lại bài' })).toBeTruthy();
    expect(screen.getByText('Admin ẩn bài đăng bởi Quản trị')).toBeTruthy();
    expect(screen.getByText('Lý do: Vi phạm nội quy')).toBeTruthy();
  });

  it('hiện lịch sử của cả quản trị viên và chủ bài, kèm lý do từ chối', async () => {
    vi.mocked(adminItemApi.detail).mockResolvedValue(
      detail({
        postHistory: [
          { key: 'log-12', action: 'Từ chối bài', actorRole: 'ADMIN', actorName: 'Quản trị', reason: 'Ảnh chưa rõ.', createdAt: '2026-11-05T04:00:00Z' },
          { key: 'log-11', action: 'Gửi bản sửa để duyệt', actorRole: 'OWNER', actorName: 'Lan An', reason: null, createdAt: '2026-11-05T03:00:00Z' },
        ],
      }, { status: 'REJECTED' }),
    );
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Lịch sử bài đăng' })).toBeTruthy();
    const rows = within(screen.getByRole('list', { name: 'Lịch sử bài đăng' })).getAllByRole('listitem');
    expect(within(rows[0]).getByText('Từ chối bài bởi Quản trị')).toBeTruthy();
    expect(within(rows[0]).getByText('Lý do: Ảnh chưa rõ.')).toBeTruthy();
    expect(within(rows[1]).getByText('Gửi bản sửa để duyệt bởi Lan An')).toBeTruthy();
  });

  it('báo lỗi khi không tải được và cho thử lại', async () => {
    const user = userEvent.setup();
    vi.mocked(adminItemApi.detail)
      .mockRejectedValueOnce({ status: 404, message: 'Bài không tồn tại.' })
      .mockResolvedValueOnce(detail());
    renderPage();
    expect(await screen.findByText('Không tìm thấy dữ liệu yêu cầu.')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(await screen.findByText('Nội dung bài')).toBeTruthy();
  });
});
