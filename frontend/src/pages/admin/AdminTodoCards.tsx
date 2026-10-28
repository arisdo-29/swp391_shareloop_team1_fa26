import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminModerationApi } from '../../services/adminModerationApi';
import { reportApi } from '../../services/reportApi';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import { adminAnomalyApi } from '../../services/adminAnomalyApi';

// Hàng đợi duyệt bài không trả tổng số nên đọc một trang lớn; vượt ngưỡng thì hiện "100+".
const REVIEW_QUEUE_PEEK = 100;

type Count = number | null | 'error';

interface TodoCounts {
  reviews: Count;
  reports: Count;
  topUps: Count;
  anomalies: Count;
}

/**
 * Bốn ô "Việc cần làm" ở trang Tổng quan. Mỗi ô tự tải bằng API đọc sẵn có,
 * ô nào lỗi thì chỉ ô đó hiện "--", các ô khác vẫn dùng được.
 */
export function AdminTodoCards() {
  const [counts, setCounts] = useState<TodoCounts>({ reviews: null, reports: null, topUps: null, anomalies: null });

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
    load('reviews', adminModerationApi.pendingReviews({ page: 0, size: REVIEW_QUEUE_PEEK }).then((page) => page.items.length));
    load(
      'reports',
      Promise.all([
        reportApi.list({ page: 0, size: 1, status: 'OPEN' }),
        reportApi.list({ page: 0, size: 1, status: 'AWAITING_RESPONSE' }),
      ]).then(([open, awaiting]) => open.totalElements + awaiting.totalElements),
    );
    load(
      'topUps',
      adminFinanceApi.topUpOrders({ page: 0, size: 1, status: 'PENDING', submittedOnly: true }).then((page) => page.totalElements),
    );
    load('anomalies', adminAnomalyApi.report().then((report) => report.repeatedPairs.length));
    return () => {
      active = false;
    };
  }, []);

  const cards: Array<{ to: string; label: string; count: Count; note: string; capped?: boolean }> = [
    { to: '/admin/moderation', label: 'Bài chờ duyệt', count: counts.reviews, note: 'Duyệt sớm để bài hiện cho mọi người.', capped: true },
    { to: '/admin/complaints', label: 'Khiếu nại và báo cáo đang mở', count: counts.reports, note: 'Gồm cả việc đang chờ giải trình.' },
    { to: '/admin/finance/topup-orders', label: 'Đơn nạp chờ xác nhận', count: counts.topUps, note: 'Người dùng đã báo chuyển khoản.' },
    { to: '/admin/alerts', label: 'Cảnh báo giao dịch lặp', count: counts.anomalies, note: 'Cặp người dùng giao dịch lặp nhiều lần.' },
  ];

  return (
    <section aria-labelledby="admin-todo" className="space-y-3">
      <h2 id="admin-todo" className="section-title">Việc cần làm</h2>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => {
          const urgent = typeof card.count === 'number' && card.count > 0;
          const value =
            card.count === null
              ? '...'
              : card.count === 'error'
                ? '--'
                : card.capped && card.count >= REVIEW_QUEUE_PEEK
                  ? `${REVIEW_QUEUE_PEEK}+`
                  : String(card.count);
          return (
            <Link
              key={card.to}
              to={card.to}
              className={`flex flex-col gap-1.5 rounded-lg border bg-white p-4 transition hover:bg-surface-low ${urgent ? 'border-error/40' : 'border-border'}`}
            >
              <span className="text-sm font-semibold text-text-secondary">{card.label}</span>
              <strong className="text-3xl tabular-nums">{value}</strong>
              <span className={`text-xs ${urgent ? 'font-semibold text-error' : 'text-text-muted'}`}>
                {card.count === 'error' ? 'Chưa tải được số liệu.' : urgent ? card.note : 'Không có việc chờ.'}
              </span>
            </Link>
          );
        })}
      </div>
    </section>
  );
}
