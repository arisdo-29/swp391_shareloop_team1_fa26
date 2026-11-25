import { useEffect, useState } from 'react';
import type { IconName } from '../../components/ui';
import { adminModerationApi } from '../../services/adminModerationApi';
import { reportApi } from '../../services/reportApi';
import { adminAnomalyApi } from '../../services/adminAnomalyApi';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import {
  DashboardGrid,
  DashboardSection,
  DashboardStatCard,
  type DashboardAccent,
} from './AdminDashboardCards';
import { formatDashboardCount, type DashboardCount } from './dashboardCount';

// Hàng đợi duyệt bài không trả tổng số nên đọc một trang lớn; vượt ngưỡng thì hiện "100+".
const REVIEW_QUEUE_PEEK = 100;

interface TodoCounts {
  reviews: DashboardCount;
  reports: DashboardCount;
  anomalies: DashboardCount;
  reconcile: DashboardCount;
}

/**
 * Nhóm "Công việc cần xử lý" ở trang Tổng quan. Mỗi thẻ tự tải bằng API đọc sẵn có,
 * thẻ nào lỗi thì chỉ thẻ đó hiện "--", các thẻ khác vẫn dùng được.
 */
export function AdminTodoCards() {
  const [counts, setCounts] = useState<TodoCounts>({
    reviews: null,
    reports: null,
    anomalies: null,
    reconcile: null,
  });

  useEffect(() => {
    let active = true;
    const load = (key: keyof TodoCounts, request: Promise<number>) => {
      request
        .then((value) => {
          if (active) setCounts((current) => ({ ...current, [key]: value }));
        })
        .catch(() => {
          if (active) setCounts((current) => ({ ...current, [key]: 'error' }));
        });
    };
    load(
      'reviews',
      adminModerationApi
        .pendingReviews({ page: 0, size: REVIEW_QUEUE_PEEK })
        .then((page) => page.items.length),
    );
    load(
      'reports',
      Promise.all([
        reportApi.list({ page: 0, size: 1, status: 'OPEN' }),
        reportApi.list({ page: 0, size: 1, status: 'AWAITING_RESPONSE' }),
      ]).then(([open, awaiting]) => open.totalElements + awaiting.totalElements),
    );
    load(
      'anomalies',
      adminAnomalyApi.report().then((report) => report.repeatedPairs.length),
    );
    load(
      'reconcile',
      adminFinanceApi.topUpSummary({}).then((summary) => summary.needsReconcileCount),
    );
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
    cap?: number;
  }> = [
    {
      to: '/admin/moderation',
      icon: 'moderation',
      label: 'Bài chờ duyệt',
      count: counts.reviews,
      note: 'Duyệt sớm để bài hiện cho mọi người.',
      accent: 'gold',
      cap: REVIEW_QUEUE_PEEK,
    },
    {
      to: '/admin/complaints',
      icon: 'complaints',
      label: 'Khiếu nại và báo cáo cần xử lý',
      count: counts.reports,
      note: 'Gồm cả hồ sơ đang chờ giải trình.',
      accent: 'blue',
    },
    {
      to: '/admin/alerts',
      icon: 'alert',
      label: 'Cảnh báo giao dịch lặp',
      count: counts.anomalies,
      note: 'Cặp người dùng giao dịch lặp nhiều lần.',
      accent: 'red',
    },
    {
      to: '/admin/finance/topup-orders?needsReconcile=true',
      icon: 'reconcile',
      label: 'Đơn nạp cần đối soát',
      count: counts.reconcile,
      note: 'Đơn đã đóng nhưng cổng thanh toán vẫn thu tiền.',
      accent: 'red',
    },
  ];

  return (
    <DashboardSection
      id="admin-todo"
      title="Công việc cần xử lý"
      description="Các việc đang chờ Admin xử lý."
    >
      <DashboardGrid>
        {cards.map((card) => {
          const pending = typeof card.count === 'number' && card.count > 0;
          return (
            <DashboardStatCard
              key={card.to}
              to={card.to}
              icon={card.icon}
              label={card.label}
              value={formatDashboardCount(card.count, card.cap)}
              loading={card.count === null}
              variant="task"
              accent={card.accent}
              pending={pending}
              note={
                card.count === 'error'
                  ? 'Chưa tải được số liệu.'
                  : card.count === null
                    ? 'Đang tải số liệu.'
                    : pending
                      ? card.note
                      : 'Không có việc chờ.'
              }
            />
          );
        })}
      </DashboardGrid>
    </DashboardSection>
  );
}
