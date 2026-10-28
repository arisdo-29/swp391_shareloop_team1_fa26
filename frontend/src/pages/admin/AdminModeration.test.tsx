// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  adminModerationApi,
  type ReviewItemDetail,
  type ReviewQueueItem,
} from '../../services/adminModerationApi';
import { AdminModerationDetail } from './AdminModerationDetail';
import { AdminModerationList } from './AdminModerationList';

vi.mock('../../services/adminModerationApi', () => ({
  adminModerationApi: {
    pendingReviews: vi.fn(),
    detail: vi.fn(),
    rejectReasons: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
  },
}));

const checklist = [
  { id: 11, key: 'real_photos', statement: 'Ảnh là ảnh thật của đúng món đồ' },
  { id: 12, key: 'category_match', statement: 'Danh mục đúng với món đồ' },
];

const queueItems: ReviewQueueItem[] = [
  {
    id: 42,
    donorId: 9,
    donorName: 'Nguyễn Văn A',
    title: 'Áo khoác gió nam',
    aiRisk: 'LOW',
    moderationFlag: 'REVIEW',
    flagReason: 'Có từ khoá cần xem xét: zalo',
    flagReasonCode: 'LEXICON_REVIEW',
    matchedTerms: ['zalo'],
    donorTrustStars: 3,
    createdAt: '2026-10-14T10:00:00Z',
  },
  {
    id: 43,
    donorId: 10,
    donorName: 'Trần Thị B',
    title: 'Bàn học gỗ',
    aiRisk: 'UNCHECKED',
    moderationFlag: 'NONE',
    donorTrustStars: null,
    createdAt: '2026-10-15T10:00:00Z',
  },
];

function detail(overrides: Partial<ReviewItemDetail> = {}): ReviewItemDetail {
  return {
    id: 42,
    title: 'Áo khoác gió nam',
    description: 'Áo còn mới 90 phần trăm',
    desiredItem: 'Giày thể thao size 42',
    offerType: 'SWAP',
    condition: 'GOOD',
    defectNote: null,
    brand: 'Uniqlo',
    status: 'PENDING_REVIEW',
    isActive: true,
    operatingStatus: null,
    images: ['/api/v1/media/101'],
    defectImages: [],
    categoryId: 3,
    categoryName: 'Quần áo',
    areaId: 7,
    areaName: 'Quận 1',
    donorId: 9,
    donorName: 'Nguyễn Văn A',
    donorTrustStars: 3,
    attributes: [{ attributeId: 5, name: 'Kích cỡ', value: 'L' }],
    aiRisk: 'LOW',
    moderationFlag: 'REVIEW',
    flagReasonCode: 'LEXICON_REVIEW',
    flagReason: 'Có từ khoá cần xem xét: zalo',
    matchedTerms: ['zalo'],
    authStatus: 'NONE',
    checklist,
    createdAt: '2026-10-14T10:00:00Z',
    updatedAt: '2026-10-14T10:05:00Z',
    ...overrides,
  };
}

function ListLanding() {
  const notice = (useLocation().state as { notice?: string } | null)?.notice;
  return <p>Danh sách duyệt bài {notice}</p>;
}

function renderDetail() {
  return render(
    <MemoryRouter initialEntries={['/admin/moderation/42']}>
      <Routes>
        <Route path="/admin/moderation" element={<ListLanding />} />
        <Route path="/admin/moderation/:itemId" element={<AdminModerationDetail />} />
      </Routes>
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminModerationList', () => {
  beforeEach(() => {
    vi.mocked(adminModerationApi.pendingReviews).mockResolvedValue({
      items: queueItems,
      checklist,
      page: 0,
      size: 100,
    });
  });

  it('mỗi bài một hàng gọn với tên người đăng, cờ dịch sang tiếng Việt và không có mã thô hay id', async () => {
    render(
      <MemoryRouter>
        <AdminModerationList />
      </MemoryRouter>,
    );

    await screen.findByText('Áo khoác gió nam');
    expect(screen.getByText('Nguyễn Văn A')).toBeTruthy();
    expect(screen.getByText('Cần xem kỹ')).toBeTruthy();
    expect(screen.getByText('Có từ khoá cần xem xét: zalo')).toBeTruthy();
    expect(screen.getByText('3/5 sao')).toBeTruthy();
    const page = document.body.textContent ?? '';
    for (const hidden of ['NONE', 'REVIEW', '#', 'Người dùng #', 'Tải chi tiết và hình ảnh']) {
      expect(page).not.toContain(hidden);
    }
  });

  it('SRS v11: không còn cột rủi ro AI và không hiện cờ do AI gắn, cờ từ khoá tầng 1 vẫn hiện', async () => {
    vi.mocked(adminModerationApi.pendingReviews).mockResolvedValue({
      items: [
        ...queueItems,
        {
          id: 44,
          donorId: 11,
          donorName: 'Lê Văn C',
          title: 'Điện thoại cũ',
          aiRisk: 'HIGH',
          moderationFlag: 'REVIEW',
          flagReason: 'AI nghi có số điện thoại trong ảnh',
          flagReasonCode: 'AI nghi có số điện thoại trong ảnh',
          matchedTerms: [],
          donorTrustStars: 5,
          createdAt: '2026-10-16T10:00:00Z',
        },
      ],
      checklist,
      page: 0,
      size: 100,
    });
    render(
      <MemoryRouter>
        <AdminModerationList />
      </MemoryRouter>,
    );

    await screen.findByText('Điện thoại cũ');
    expect(screen.queryByRole('columnheader', { name: 'Rủi ro' })).toBeNull();
    expect(screen.queryByText('AI nghi có số điện thoại trong ảnh')).toBeNull();
    expect(screen.getAllByText('Cần xem kỹ')).toHaveLength(1);
    expect(screen.getByText('Có từ khoá cần xem xét: zalo')).toBeTruthy();
  });

  it('nút Xem chi tiết & duyệt dẫn sang trang riêng của bài, không mở popup', async () => {
    render(
      <MemoryRouter>
        <AdminModerationList />
      </MemoryRouter>,
    );

    await screen.findByText('Áo khoác gió nam');
    const links = screen.getAllByRole('link', { name: 'Xem chi tiết & duyệt' });
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/admin/moderation/42',
      '/admin/moderation/43',
    ]);
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('tìm theo tên người đăng', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AdminModerationList />
      </MemoryRouter>,
    );
    await screen.findByText('Áo khoác gió nam');

    await user.type(screen.getByRole('textbox', { name: 'Tìm bài chờ duyệt' }), 'trần thị');

    expect(screen.queryByText('Áo khoác gió nam')).toBeNull();
    expect(screen.getByText('Bàn học gỗ')).toBeTruthy();
  });

  it('hiện thông báo kết quả duyệt gửi từ trang chi tiết', async () => {
    render(
      <MemoryRouter initialEntries={[{ pathname: '/admin/moderation', state: { notice: 'Đã duyệt bài "Áo".' } }]}>
        <AdminModerationList />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Đã duyệt bài "Áo".')).toBeTruthy();
  });
});

describe('AdminModerationDetail', () => {
  beforeEach(() => {
    vi.mocked(adminModerationApi.detail).mockResolvedValue(detail());
    vi.mocked(adminModerationApi.rejectReasons).mockResolvedValue(['Hình ảnh không phù hợp']);
  });

  it('hiện đủ thông tin để duyệt trên cùng một trang, dùng endpoint của admin', async () => {
    renderDetail();

    await screen.findByRole('heading', { name: 'Áo khoác gió nam' });
    expect(adminModerationApi.detail).toHaveBeenCalledWith(42);
    for (const text of [
      'Nguyễn Văn A',
      '3/5 sao',
      'Quần áo',
      'Quận 1',
      'Còn tốt',
      'Trao đổi',
      'Giày thể thao size 42',
      'Kích cỡ',
      'Áo còn mới 90 phần trăm',
      'zalo',
      'Cần xem kỹ',
    ]) {
      expect(screen.getAllByText(text).length).toBeGreaterThan(0);
    }
    expect(screen.getByText('Ảnh là ảnh thật của đúng món đồ')).toBeTruthy();
    expect(screen.queryByText(/Không thể đọc chi tiết/)).toBeNull();
    expect(screen.queryByText('Mức rủi ro')).toBeNull();
  });

  it('SRS v11: bài chỉ có cờ AI thì trang chi tiết ghi "Không có cờ" và không hiện lý do AI', async () => {
    vi.mocked(adminModerationApi.detail).mockResolvedValue(
      detail({
        aiRisk: 'HIGH',
        flagReasonCode: 'AI nghi có số điện thoại trong ảnh',
        flagReason: 'AI nghi có số điện thoại trong ảnh',
        matchedTerms: [],
      }),
    );
    renderDetail();

    await screen.findByRole('heading', { name: 'Áo khoác gió nam' });
    expect(screen.getByText('Không có cờ')).toBeTruthy();
    expect(screen.queryByText('AI nghi có số điện thoại trong ảnh')).toBeNull();
    expect(screen.queryByText('Cần xem kỹ')).toBeNull();
  });

  it('chỉ cho duyệt khi tích đủ checklist rồi quay về danh sách với thông báo', async () => {
    const user = userEvent.setup();
    vi.mocked(adminModerationApi.approve).mockResolvedValue(undefined);
    renderDetail();
    await screen.findByRole('heading', { name: 'Áo khoác gió nam' });

    const approve = screen.getByRole('button', { name: 'Xác nhận duyệt bài' }) as HTMLButtonElement;
    expect(approve.disabled).toBe(true);
    await user.click(screen.getByLabelText('Ảnh là ảnh thật của đúng món đồ'));
    expect(approve.disabled).toBe(true);
    await user.click(screen.getByLabelText('Danh mục đúng với món đồ'));
    await user.click(approve);

    await waitFor(() =>
      expect(adminModerationApi.approve).toHaveBeenCalledWith(42, {
        answers: [
          { id: 11, passed: true },
          { id: 12, passed: true },
        ],
      }),
    );
    expect(await screen.findByText(/Đã duyệt bài "Áo khoác gió nam"/)).toBeTruthy();
  });

  it('từ chối bằng lý do mẫu rồi quay về danh sách', async () => {
    const user = userEvent.setup();
    vi.mocked(adminModerationApi.reject).mockResolvedValue(undefined);
    renderDetail();
    await screen.findByRole('heading', { name: 'Áo khoác gió nam' });

    await user.click(screen.getByRole('button', { name: 'Từ chối bài đăng' }));
    await user.click(screen.getByLabelText('Hình ảnh không phù hợp'));
    await user.click(screen.getByRole('button', { name: 'Xác nhận từ chối' }));

    await waitFor(() =>
      expect(adminModerationApi.reject).toHaveBeenCalledWith(42, { reason: 'Hình ảnh không phù hợp' }),
    );
    expect(await screen.findByText(/Đã từ chối bài "Áo khoác gió nam"/)).toBeTruthy();
  });

  it('checklist đã đổi thì tải lại trang và báo admin tích lại', async () => {
    const user = userEvent.setup();
    vi.mocked(adminModerationApi.approve).mockRejectedValue({ code: 'REVIEW_CHECKLIST_OUTDATED', status: 409 });
    renderDetail();
    await screen.findByRole('heading', { name: 'Áo khoác gió nam' });

    await user.click(screen.getByLabelText('Ảnh là ảnh thật của đúng món đồ'));
    await user.click(screen.getByLabelText('Danh mục đúng với món đồ'));
    await user.click(screen.getByRole('button', { name: 'Xác nhận duyệt bài' }));

    expect(await screen.findByText(/Checklist đã thay đổi/)).toBeTruthy();
    await waitFor(() => expect(adminModerationApi.detail).toHaveBeenCalledTimes(2));
  });

  it('bài không còn chờ duyệt thì không có nút duyệt hoặc từ chối', async () => {
    vi.mocked(adminModerationApi.detail).mockResolvedValue(detail({ status: 'APPROVED' }));
    renderDetail();

    await screen.findByRole('heading', { name: 'Áo khoác gió nam' });
    expect(screen.getByText(/không còn ở trạng thái chờ duyệt/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Xác nhận duyệt bài' })).toBeNull();
  });

  it('báo rõ khi bài không tồn tại', async () => {
    vi.mocked(adminModerationApi.detail).mockRejectedValue({ status: 404 });
    renderDetail();

    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText(/Không tìm thấy bài đăng này/)).toBeTruthy();
  });
});
