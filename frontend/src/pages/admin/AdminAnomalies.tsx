import './admin-settings.css';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, EmptyState, PageHeader, SkeletonRows } from '../../components/ui';
import {
  adminAnomalyApi,
  type AnomalyReportResponse,
  type AnomalyUser,
  type SharedIpAccount,
} from '../../services/adminAnomalyApi';
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

function accountStatusBadge(account: SharedIpAccount) {
  if (account.status === 'BANNED') {
    return <span className="inline-flex rounded-sm bg-error-soft px-2 py-0.5 text-2xs font-semibold text-error">Bị cấm</span>;
  }
  if (account.locked) {
    return <span className="inline-flex rounded-sm bg-error-soft px-2 py-0.5 text-2xs font-semibold text-error">Đã khóa</span>;
  }
  return <span className="inline-flex rounded-sm bg-success-soft px-2 py-0.5 text-2xs font-semibold text-success">Đang hoạt động</span>;
}

/** Cảnh báo bất thường cho Admin: các cặp người dùng giao dịch lặp và nhiều tài khoản cùng IP (AD-04, UT-08). */
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

  const ipWindowDays = report?.ipWindowDays ?? 30;
  const ipMinAccounts = report?.ipMinAccounts ?? 3;
  const sharedIpGroups = report?.sharedIpGroups ?? [];

  return (
    <section className="admin-settings-page admin-alerts-page space-y-6">
      <PageHeader title="Cảnh báo bất thường" description="Các tín hiệu cần kiểm tra từ hệ thống. Trang chỉ đọc, không tự xử lý tài khoản hoặc giao dịch." />

      {error ? <Alert tone="error">{error}</Alert> : null}

      {loading && !report ? (
        <SkeletonRows label="Đang tải cảnh báo" />
      ) : report ? (
        <div className="space-y-8">
          {/* Khối 1: Cặp giao dịch lặp */}
          <div className="settings-alert-section">
            <header className="settings-section-heading"><h2>Cặp giao dịch lặp lại</h2><p>Cảnh báo khi vượt {report.pairThreshold} giao dịch trong {report.pairWindowDays} ngày. Cần kiểm tra trước khi kết luận.</p></header>
            {report.repeatedPairs.length === 0 ? (
              <EmptyState
                title="Chưa có cảnh báo nào"
                text="Hiện không có cặp người dùng nào vượt ngưỡng giao dịch lặp."
              />
            ) : (
              <div className="table-wrap settings-table">
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
            )}
          </div>

          {/* Khối 2: Nhiều tài khoản cùng địa chỉ IP */}
          <div className="settings-alert-section">
            <div className="settings-section-heading">
              <h2>Nhiều tài khoản cùng địa chỉ IP</h2>
              <p className="mt-0.5 text-xs text-text-muted">
                IP có từ {ipMinAccounts} tài khoản đăng ký trong {ipWindowDays} ngày
              </p>
            </div>

            <p className="settings-note">Dùng chung IP là tín hiệu cần kiểm tra, chưa đủ để kết luận gian lận.</p>

            {sharedIpGroups.length === 0 ? (
              <div className="rounded-lg border border-dashed border-border bg-surface-low p-6 text-center text-sm text-text-muted">
                Không có cảnh báo
              </div>
            ) : (
              <div className="space-y-4">
                {sharedIpGroups.map((group) => (
                  <div key={group.ipAddress} className="settings-ip-group">
                    <div className="settings-ip-heading">
                      <div className="flex min-w-0 flex-wrap items-center gap-2">
                        <span className="settings-ip-address">IP: {group.ipAddress}</span>
                        <span className="rounded-full bg-warning-soft px-2.5 py-0.5 text-xs font-semibold text-warning">
                          {group.accountCount} tài khoản
                        </span>
                      </div>
                    </div>
                    <div className="table-wrap settings-table">
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>Họ tên</th>
                            <th>Email</th>
                            <th>Ngày đăng ký</th>
                            <th>Số sao</th>
                            <th>Trạng thái</th>
                          </tr>
                        </thead>
                        <tbody>
                          {group.accounts.map((acc) => (
                            <tr key={acc.id}>
                              <td>
                                <Link
                                  to={`/admin/finance/users/${acc.id}`}
                                  className="font-semibold text-link hover:underline"
                                >
                                  {acc.fullName}
                                </Link>
                              </td>
                              <td className="text-xs text-text-muted">{acc.email || '—'}</td>
                              <td className="whitespace-nowrap text-xs">{formatDateTime(acc.registeredAt)}</td>
                              <td className="whitespace-nowrap text-xs font-medium">{acc.trustStars} ⭐</td>
                              <td>{accountStatusBadge(acc)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <p className="text-xs text-text-muted">Tính lúc {formatDateTime(report.generatedAt)}.</p>
        </div>
      ) : null}
    </section>
  );
}
