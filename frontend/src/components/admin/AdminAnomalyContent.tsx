import { useEffect, useState } from 'react';
import { Alert, Button, EmptyState, PageHeader } from '../ui';
import {
  adminAnomalyApi,
  type AnomalyReportResponse,
} from '../../services/adminAnomalyApi';
import type { ApiError } from '../../services/http';

function anomalyErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.status === 400) return apiError.message || 'Yêu cầu tải cảnh báo không hợp lệ.';
  if (apiError.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (apiError.status === 403) return 'Tài khoản hiện tại không có quyền xem cảnh báo bất thường.';
  if (apiError.status === 404) return 'Không tìm thấy dữ liệu cảnh báo bất thường.';
  if (apiError.status === 0) return 'Không thể kết nối máy chủ. Vui lòng thử lại.';
  return apiError.message || 'Không tải được cảnh báo bất thường. Vui lòng thử lại.';
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Không xác định';
  return date.toLocaleString('vi-VN');
}

export function AdminAnomalyContent() {
  const [report, setReport] = useState<AnomalyReportResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminAnomalyApi
      .getReport()
      .then((response) => {
        if (active) setReport(response);
      })
      .catch((caught) => {
        if (!active) return;
        setReport(null);
        setError(anomalyErrorMessage(caught));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [reloadKey]);

  return (
    <section>
      <PageHeader
        title="Cảnh báo bất thường"
        description="Tín hiệu cần kiểm tra từ hoạt động giao dịch và Credit."
      />

      {loading ? (
        <EmptyState title="Đang tải cảnh báo" text="Vui lòng chờ trong giây lát." />
      ) : error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button size="sm" variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử tải lại
          </Button>
        </div>
      ) : report?.repeatedPairs.length ? (
        <>
          <p className="mb-4 text-sm text-text-secondary">
            Cảnh báo khi một cặp có hơn {report.pairThreshold} giao dịch hoàn tất trong {report.pairWindowDays}{' '}
            ngày. Cập nhật lúc {formatDateTime(report.generatedAt)}.
          </p>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Người dùng thứ nhất</th>
                  <th>Người dùng thứ hai</th>
                  <th>Giao dịch hoàn tất</th>
                  <th>Lần gần nhất</th>
                </tr>
              </thead>
              <tbody>
                {report.repeatedPairs.map((pair) => (
                  <tr key={`${pair.userA.id}-${pair.userB.id}`}>
                    <td>
                      <p className="font-semibold text-text-primary">{pair.userA.fullName}</p>
                      <p className="mt-0.5 text-xs text-text-muted">{pair.userA.email ?? 'Tài khoản đã xóa'}</p>
                    </td>
                    <td>
                      <p className="font-semibold text-text-primary">{pair.userB.fullName}</p>
                      <p className="mt-0.5 text-xs text-text-muted">{pair.userB.email ?? 'Tài khoản đã xóa'}</p>
                    </td>
                    <td className="font-semibold tabular-nums">{pair.completedCount}</td>
                    <td className="whitespace-nowrap text-xs">{formatDateTime(pair.lastCompletedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      ) : (
        <EmptyState
          icon="shield"
          title="Không có cảnh báo bất thường"
          text="Hiện chưa phát hiện cặp người dùng có giao dịch lặp vượt ngưỡng."
        />
      )}
    </section>
  );
}
