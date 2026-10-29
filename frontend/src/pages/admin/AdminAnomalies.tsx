import { useEffect, useState } from 'react';
import { Alert, EmptyState, PageHeader, SkeletonRows } from '../../components/ui';
import { adminAnomalyApi, type AnomalyReportResponse, type AnomalyUser } from '../../services/adminAnomalyApi';
import type { ApiError } from '../../services/http';

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN');
}

function getErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.status === 403) return 'Bạn không có quyền xem cảnh báo bất thường.';
  return apiError.message || 'Không tải được cảnh báo bất thường. Vui lòng thử lại.';
}

function UserCell({ user }: { user: AnomalyUser }) {
  return (
    <div>
      <p className="font-semibold">{user.fullName}</p>
      <p className="text-xs text-text-muted">{user.email ?? 'Không có email'}</p>
    </div>
  );
}

/** Cảnh báo bất thường cho Admin: các cặp người dùng giao dịch lặp (FR-113, BR-U08). */
export function AdminAnomalies() {
  const [report, setReport] = useState<AnomalyReportResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminAnomalyApi
      .report()
      .then((response) => {
        if (active) setReport(response);
      })
      .catch((caught) => {
        if (!active) return;
        setReport(null);
        setError(getErrorMessage(caught));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <section className="space-y-5">
      <PageHeader title="Cảnh báo bất thường" />

      {error ? <Alert tone="error">{error}</Alert> : null}

      {loading && !report ? (
        <SkeletonRows label="Đang tải cảnh báo" />
      ) : report && report.repeatedPairs.length === 0 ? (
        <EmptyState
          title="Chưa có cảnh báo nào"
          text="Hiện không có cặp người dùng nào vượt ngưỡng giao dịch lặp."
        />
      ) : report ? (
        <div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Người thứ nhất</th>
                  <th>Người thứ hai</th>
                  <th>Số giao dịch hoàn tất</th>
                  <th>Lần gần nhất</th>
                </tr>
              </thead>
              <tbody>
                {report.repeatedPairs.map((pair) => (
                  <tr key={`${pair.userA.id}-${pair.userB.id}`}>
                    <td>
                      <UserCell user={pair.userA} />
                    </td>
                    <td>
                      <UserCell user={pair.userB} />
                    </td>
                    <td className="font-semibold">{pair.completedCount}</td>
                    <td>{formatDateTime(pair.lastCompletedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="mt-2 text-xs text-text-muted">Tính lúc {formatDateTime(report.generatedAt)}.</p>
        </div>
      ) : null}
    </section>
  );
}
