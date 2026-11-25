import { useEffect, useState } from 'react';
import type { IconName } from '../../components/ui';
import { adminUserApi } from '../../services/adminUserApi';
import { adminItemApi } from '../../services/adminItemApi';
import { adminRequestApi } from '../../services/adminRequestApi';
import type { ItemRequestStatus } from '../../types/domain';
import {
  DashboardGrid,
  DashboardSection,
  DashboardStatCard,
  type DashboardAccent,
} from './AdminDashboardCards';
import { formatDashboardCount, type DashboardCount } from './dashboardCount';

// Giao dịch đã chọn người và chưa kết thúc, cùng nhóm trạng thái backend coi là đang chạy (RequestService).
const RUNNING_REQUEST_STATUSES: ItemRequestStatus[] = [
  'RESERVED',
  'AWAITING_LOGISTICS',
  'LOGISTICS_CONFIRMED',
  'AWAITING_HANDOVER',
];

interface SystemCounts {
  users: DashboardCount;
  visibleItems: DashboardCount;
  runningRequests: DashboardCount;
  completedRequests: DashboardCount;
}

const countRequests = (status: ItemRequestStatus) =>
  adminRequestApi.list({ status, page: 0, size: 1 }).then((page) => page.totalElements);

/**
 * Nhóm "Thống kê hệ thống". Chỉ cần tổng số nên mỗi API xin một dòng và đọc totalElements.
 */
export function AdminSystemStats() {
  const [counts, setCounts] = useState<SystemCounts>({
    users: null,
    visibleItems: null,
    runningRequests: null,
    completedRequests: null,
  });

  useEffect(() => {
    let active = true;
    const load = (key: keyof SystemCounts, request: Promise<number>) => {
      request
        .then((value) => {
          if (active) setCounts((current) => ({ ...current, [key]: value }));
        })
        .catch(() => {
          if (active) setCounts((current) => ({ ...current, [key]: 'error' }));
        });
    };
    load(
      'users',
      adminUserApi.list({ page: 0, size: 1 }).then((page) => page.totalElements),
    );
    // Bài đang hiện cho mọi người: đã duyệt và chưa bị ẩn.
    load(
      'visibleItems',
      adminItemApi
        .list({ status: 'APPROVED', active: true, page: 0, size: 1 })
        .then((page) => page.totalElements),
    );
    load(
      'runningRequests',
      Promise.all(RUNNING_REQUEST_STATUSES.map(countRequests)).then((totals) =>
        totals.reduce((sum, value) => sum + value, 0),
      ),
    );
    load('completedRequests', countRequests('COMPLETED'));
    return () => {
      active = false;
    };
  }, []);

  const cards: Array<{
    to: string;
    icon: IconName;
    label: string;
    count: DashboardCount;
    note: string;
    accent: DashboardAccent;
  }> = [
    {
      to: '/admin/users',
      icon: 'users',
      label: 'Tổng tài khoản',
      count: counts.users,
      note: 'Tài khoản người dùng và quản trị.',
      accent: 'green',
    },
    {
      to: '/admin/items',
      icon: 'manageItems',
      label: 'Bài đang hiển thị',
      count: counts.visibleItems,
      note: 'Bài đã duyệt và chưa bị ẩn.',
      accent: 'green',
    },
    {
      to: '/admin/transactions',
      icon: 'transactions',
      label: 'Giao dịch đang diễn ra',
      count: counts.runningRequests,
      note: 'Đã chọn người nhận, chưa kết thúc.',
      accent: 'blue',
    },
    {
      to: '/admin/transactions?status=COMPLETED',
      icon: 'completed',
      label: 'Giao dịch hoàn tất',
      count: counts.completedRequests,
      note: 'Hai bên đã xác nhận giao nhận.',
      accent: 'green',
    },
  ];

  return (
    <DashboardSection
      id="admin-system-stats"
      title="Thống kê hệ thống"
      description="Quy mô tài khoản, bài đăng và giao dịch hiện tại."
    >
      <DashboardGrid>
        {cards.map((card) => (
          <DashboardStatCard
            key={card.label}
            to={card.to}
            icon={card.icon}
            accent={card.accent}
            label={card.label}
            value={formatDashboardCount(card.count)}
            loading={card.count === null}
            variant="system"
            note={card.count === 'error' ? 'Chưa tải được số liệu.' : card.note}
          />
        ))}
      </DashboardGrid>
    </DashboardSection>
  );
}
