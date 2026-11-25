import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { StarRating } from '../../components/StarRating';
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
      <p className="cell-truncate text-xs text-text-muted" title={user.email ?? undefined}>
        {user.email ?? 'Không có email'}
      </p>
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

  const sharedIpGroups = report?.sharedIpGroups ?? [];
  const repeatedCancelers = report?.repeatedCancelers ?? [];

  return (
    <section className="space-y-6">
      <PageHeader title="Cảnh báo bất thường" />

      {error ? <Alert tone="error">{error}</Alert> : null}

      {loading && !report ? (
        <SkeletonRows label="Đang tải cảnh báo" />
      ) : report ? (
        <div className="space-y-8">
          {/* Khối 1: Cặp giao dịch lặp */}
          <div className="space-y-3">
            <h2 className="text-base font-bold text-text-primary">Cặp giao dịch lặp lại</h2>
            {report.repeatedPairs.length === 0 ? (
              <EmptyState
                title="Chưa có cảnh báo nào"
                text="Hiện không có cặp người dùng nào vượt ngưỡng giao dịch lặp."
              />
            ) : (
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
            )}
          </div>

          {/* Khối huỷ nhiều lần sau khi thấy liên hệ (GN-06), chỉ để Admin xem xét, không tự phạt */}
          <div className="space-y-3">
            <div>
              <h2 className="text-base font-bold text-text-primary">Huỷ nhiều lần sau khi thấy liên hệ</h2>
              <p className="mt-1 text-sm text-text-secondary">
                Người đề nghị huỷ nhiều giao dịch sau khi đã thấy thông tin liên hệ của bên kia. Hệ thống chỉ báo để
                Admin xem xét, không tự phạt.
              </p>
              {report.cancelThreshold !== undefined && report.cancelWindowDays !== undefined ? (
                <p className="mt-1 text-xs text-text-muted">
                  Đang cảnh báo từ {report.cancelThreshold} lần trong {report.cancelWindowDays} ngày
                </p>
              ) : null}
            </div>
            {repeatedCancelers.length === 0 ? (
              <div className="rounded-lg border border-dashed border-border bg-surface-low p-6 text-center text-sm text-text-muted">
                Không có cảnh báo
              </div>
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Người dùng</th>
                      <th>Số lần huỷ</th>
                      <th>Lần gần nhất</th>
                    </tr>
                  </thead>
                  <tbody>
                    {repeatedCancelers.map((item) => (
                      <tr key={item.user.id}>
                        <td>
                          <UserCell user={item.user} />
                        </td>
                        <td className="font-semibold">{item.cancelCount}</td>
                        <td>{formatDateTime(item.lastCancelRequestedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Khối 2: Nhiều tài khoản cùng địa chỉ IP */}
          <div className="space-y-3">
            <div>
              <h2 className="text-base font-bold text-text-primary">Nhiều tài khoản cùng địa chỉ IP</h2>
              <p className="mt-1 text-sm text-text-secondary">
                Có thể là người dùng chung mạng (chung cư, ký túc xá, trường học). Chỉ xử lý khi có thêm dấu hiệu khác như cặp lặp hoặc bị báo cáo.
              </p>
              <div className="mt-1 flex flex-wrap items-baseline gap-x-2 text-xs">
                {report.ipMinAccounts !== undefined && report.ipWindowDays !== undefined && (
                  <p className="text-text-muted">
                    Đang cảnh báo từ {report.ipMinAccounts} tài khoản cùng IP trong {report.ipWindowDays} ngày
                  </p>
                )}
                <Link to="/admin/reputation" className="font-semibold text-link hover:underline">
                  Cấu hình cảnh báo IP
                </Link>
              </div>
            </div>

            {sharedIpGroups.length === 0 ? (
              <div className="rounded-lg border border-dashed border-border bg-surface-low p-6 text-center text-sm text-text-muted">
                Không có cảnh báo
              </div>
            ) : (
              <div className="space-y-4">
                {sharedIpGroups.map((group) => (
                  <div key={group.ipAddress} className="rounded-xl border border-border bg-white p-4 shadow-2xs space-y-3">
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-2">
                      <div className="flex min-w-0 flex-wrap items-center gap-2">
                        <span className="min-w-0 break-all font-mono text-sm font-bold text-text-primary">IP: {group.ipAddress}</span>
                        <span className="shrink-0 rounded-full bg-warning-soft px-2.5 py-0.5 text-xs font-semibold text-warning">
                          {group.accountCount} tài khoản
                        </span>
                      </div>
                    </div>
                    <div className="table-wrap">
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
                              <td className="text-xs text-text-muted">
                                <span className="cell-truncate" title={acc.email || undefined}>
                                  {acc.email || '—'}
                                </span>
                              </td>
                              <td className="whitespace-nowrap text-xs">{formatDateTime(acc.registeredAt)}</td>
                              <td className="whitespace-nowrap text-xs font-medium"><StarRating value={acc.trustStars} /></td>
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
