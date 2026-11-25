import { lazy, Suspense, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, EmptyState, Field, PageHeader, Stat } from '../../components/ui';
import { adminFinanceApi, type FinanceSummary } from '../../services/adminFinanceApi';
import { adminAuditApi, type ActivityLogResponse } from '../../services/adminAuditApi';
import type { ApiError } from '../../services/http';
import { formatVnd } from '../../utils/formatting';
import { walletTransactionLabels } from '../../utils/creditHistory';
import { AdminTodoCards } from './AdminTodoCards';
import { AdminSystemStats } from './AdminSystemStats';
import { DashboardGrid, DashboardSection, DashboardStatCard, type DashboardAccent } from './AdminDashboardCards';
import type { IconName } from '../../components/ui';
import { activityLogDetail, activityLogLabel } from './activityLogDisplay';

const RECENT_ACTIVITY_SIZE = 5;

// Khung thẻ tài chính lúc đang tải, giữ đúng vị trí để trang không nhảy khi có số liệu.
const FINANCE_PLACEHOLDERS: Array<{ to: string; icon: IconName; label: string; accent: DashboardAccent }> = [
  { to: '/admin/finance/topup-orders?status=SUCCESS', icon: 'money', label: 'Tổng tiền nạp thành công', accent: 'cash' },
  { to: '/admin/finance/ledger', icon: 'creditIn', label: 'Tổng Credit đã cộng', accent: 'credit-in' },
  { to: '/admin/finance/ledger', icon: 'creditOut', label: 'Tổng Credit đã trừ', accent: 'credit-out' },
];

const CashByDayChart = lazy(() =>
  import('./FinanceCharts').then((module) => ({ default: module.CashByDayChart })),
);
const CreditByTypeChart = lazy(() =>
  import('./FinanceCharts').then((module) => ({ default: module.CreditByTypeChart })),
);

const transactionLabels: Record<string, string> = walletTransactionLabels;
const INVALID_DATE_RANGE =
  'Ngày bắt đầu đang sau ngày kết thúc. Hãy chọn Từ ngày sớm hơn hoặc trùng với Đến ngày.';

function errorMessage(error: unknown) {
  const apiError = error as ApiError;
  if (apiError.status === 403) return 'Bạn không có quyền quản trị để thực hiện thao tác này.';
  return apiError.message || 'Không tải được dữ liệu. Vui lòng thử lại.';
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

type ActivityTone = 'success' | 'error' | 'warning' | 'info';

const activityToneClasses: Record<ActivityTone, string> = {
  success: 'bg-success-soft text-success',
  error: 'bg-error-soft text-error',
  warning: 'bg-warning-soft text-warning',
  info: 'bg-info-soft text-info',
};

// Màu badge theo loại hành động (backend audit.ActivityType): xanh lá cho duyệt, gia hạn, mở khoá,
// đỏ cho bác bỏ, từ chối, khoá, vàng cho cảnh báo, xanh dương cho cập nhật.
const activityTypeTones: Record<string, ActivityTone> = {
  REVIEW_APPROVE: 'success',
  USER_UNLOCK: 'success',
  ITEM_DISPLAY_COMPENSATED: 'success',
  ADMIN_ITEM_UNHIDDEN: 'success',
  AUTH_GRANT: 'success',
  GRACE_STAR: 'success',
  STAR_RESTORED: 'success',
  POINTS: 'success',
  ADMIN_TOPUP_RECONCILED: 'success',
  REVIEW_REJECT: 'error',
  REPORT_DISMISSED: 'error',
  USER_LOCK: 'error',
  USER_BAN: 'error',
  AUTH_REJECT: 'error',
  AUTH_REVOKE: 'error',
  REQUEST_CANCEL: 'error',
  ADMIN_ITEM_HIDDEN: 'error',
  POINTS_REVOKED: 'error',
  STARS: 'error',
  REPORT_UPHELD: 'warning',
  REPORT_RESPONSE_REQUESTED: 'warning',
  ADMIN_VIEWED_CHAT: 'warning',
};

function activityTone(type: string) {
  const normalizedType = type.trim().toUpperCase();
  const known = activityTypeTones[normalizedType];
  if (known) return activityToneClasses[known];
  // Loại mới chưa có trong bảng: đoán theo từ khoá. UNLOCK phải xét trước LOCK.
  const has = (keywords: string[]) => keywords.some((keyword) => normalizedType.includes(keyword));
  if (has(['UNLOCK', 'UNHIDDEN', 'APPROVE', 'RESTORE', 'GRANT', 'EXTEND', 'COMPENSATED'])) return activityToneClasses.success;
  if (has(['REJECT', 'DISMISS', 'LOCK', 'BAN', 'REVOKE', 'CANCEL', 'HIDDEN', 'DELETE', 'FAIL'])) return activityToneClasses.error;
  if (has(['WARN', 'ALERT', 'ANOMAL', 'REPORT'])) return activityToneClasses.warning;
  return activityToneClasses.info;
}

const activityTargetLabels: Record<string, string> = {
  ITEM: 'Bài đăng',
  USER: 'Người dùng',
  REQUEST: 'Giao dịch',
  REPORT: 'Báo cáo',
  CONFIG: 'Cấu hình',
  LEXICON: 'Từ khóa',
  CHECKLIST: 'Câu hỏi kiểm duyệt',
  CATEGORY: 'Danh mục',
  CATEGORY_ATTRIBUTE: 'Thuộc tính danh mục',
  AREA: 'Khu vực',
};

// Trang chi tiết của đối tượng trong nhật ký. Loại không có trang chi tiết riêng thì mở trang quản lý của loại đó.
function activityTargetLink(log: ActivityLogResponse) {
  const type = log.targetType?.trim().toUpperCase() ?? '';
  const id = log.targetId;
  if (type === 'ITEM' && id) return `/admin/items/${id}`;
  if (type === 'USER' && id) return `/admin/users/${id}/profile`;
  if (type === 'REQUEST' && id) return `/admin/transactions/${id}`;
  if (type === 'REPORT') return '/admin/complaints';
  if (type === 'CONFIG') return '/admin/settings/fees';
  if (type === 'LEXICON') return '/admin/keywords';
  if (type === 'CHECKLIST') return '/admin/checklist';
  if (type === 'CATEGORY' || type === 'CATEGORY_ATTRIBUTE') return '/admin/categories';
  if (type === 'AREA') return '/admin/districts';
  return null;
}

function activityTarget(log: ActivityLogResponse) {
  const type = activityTargetLabels[log.targetType?.trim().toUpperCase() ?? ''] ?? null;
  return { type, title: log.targetTitle?.trim() || null };
}

function formatActivityTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Không xác định';
  // Dạng HH:mm dd/MM/yyyy theo giờ Việt Nam.
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Ho_Chi_Minh',
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hourCycle: 'h23',
    }).formatToParts(date).map((part) => [part.type, part.value]),
  );
  return `${parts.hour}:${parts.minute} ${parts.day}/${parts.month}/${parts.year}`;
}

function RetryAlert({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <Alert tone="error">
      {message}{' '}
      <button type="button" className="font-semibold underline" onClick={onRetry}>Thử lại</button>
    </Alert>
  );
}

export function AdminOperationsDashboard() {
  const [report, setReport] = useState<FinanceSummary | null>(null);
  const [logs, setLogs] = useState<ActivityLogResponse[]>([]);
  const [reportLoading, setReportLoading] = useState(true);
  const [logsLoading, setLogsLoading] = useState(true);
  const [reportError, setReportError] = useState('');
  const [logsError, setLogsError] = useState('');
  const [reportReload, setReportReload] = useState(0);
  const [logsReload, setLogsReload] = useState(0);

  useEffect(() => {
    let active = true;
    setReport(null);
    setReportLoading(true);
    setReportError('');
    void adminFinanceApi.report({})
      .then((summary) => { if (active) setReport(summary); })
      .catch((caught) => { if (active) setReportError(errorMessage(caught)); })
      .finally(() => { if (active) setReportLoading(false); });
    return () => { active = false; };
  }, [reportReload]);

  useEffect(() => {
    let active = true;
    setLogs([]);
    setLogsLoading(true);
    setLogsError('');
    void adminAuditApi.list({ page: 0, size: RECENT_ACTIVITY_SIZE })
      .then((recent) => { if (active) setLogs(recent.content.slice(0, RECENT_ACTIVITY_SIZE)); })
      .catch((caught) => { if (active) setLogsError(errorMessage(caught)); })
      .finally(() => { if (active) setLogsLoading(false); });
    return () => { active = false; };
  }, [logsReload]);

  const financeCards: Array<{ to: string; icon: IconName; label: string; accent: DashboardAccent; value: string; unit?: string; note: string }> = report ? [
    {
      ...FINANCE_PLACEHOLDERS[0],
      value: formatVnd(report.cashAmountVnd),
      note: `Từ ${report.cashOrderCount.toLocaleString('vi-VN')} đơn nạp thành công.`,
    },
    {
      ...FINANCE_PLACEHOLDERS[1],
      value: report.creditInTotal.toLocaleString('vi-VN'),
      unit: 'Credit',
      note: 'Gồm nạp Credit và điều chỉnh cộng.',
    },
    {
      ...FINANCE_PLACEHOLDERS[2],
      value: report.creditOutTotal.toLocaleString('vi-VN'),
      unit: 'Credit',
      note: 'Gồm các loại phí và điều chỉnh trừ.',
    },
  ] : [];

  return (
    <div className="admin-dashboard">
      <header className="admin-dashboard-hero">
        <h1>Tổng quan</h1>
        <p>Theo dõi việc cần xử lý, số liệu hệ thống, tài chính và hoạt động mới nhất.</p>
      </header>

      <AdminTodoCards />
      <AdminSystemStats />

      <DashboardSection
        id="admin-finance-stats"
        title="Thống kê tài chính"
        description="Tổng cộng từ trước đến nay theo sổ hệ thống."
        panel
        action={<Link className="admin-dash-link" to="/admin/finance">Xem báo cáo tài chính</Link>}
      >
        {reportError ? (
          <RetryAlert message={reportError} onRetry={() => setReportReload((value) => value + 1)} />
        ) : (
          <DashboardGrid columns={3}>
            {reportLoading || !report
              ? FINANCE_PLACEHOLDERS.map((card) => (
                <DashboardStatCard key={card.label} {...card} variant="finance" value="..." note="Đang tải số liệu." loading />
              ))
              : financeCards.map((card) => <DashboardStatCard key={card.label} {...card} variant="finance" />)}
          </DashboardGrid>
        )}
      </DashboardSection>

      <DashboardSection
        id="admin-recent-activity"
        title="Hoạt động gần đây"
        description={`${RECENT_ACTIVITY_SIZE} thay đổi mới nhất được ghi nhận trong hệ thống.`}
        action={<Link className="admin-dash-link" to="/admin/audit-logs">Xem tất cả</Link>}
      >
        <div className="admin-dash-table-wrap">
          {logsError ? (
            <div className="p-5">
              <RetryAlert message={logsError} onRetry={() => setLogsReload((value) => value + 1)} />
            </div>
          ) : logsLoading ? (
            <div className="admin-dash-empty">Đang tải hoạt động gần đây...</div>
          ) : logs.length ? (
            <div className="admin-dash-table-scroll">
              <table className="admin-dash-table">
                <thead>
                  <tr>
                    <th scope="col" className="admin-dash-col-time">Thời gian</th>
                    <th scope="col" className="admin-dash-col-actor">Người thực hiện</th>
                    <th scope="col" className="admin-dash-col-action">Hành động</th>
                    <th scope="col" className="admin-dash-col-target">Đối tượng</th>
                    <th scope="col">Chi tiết</th>
                    <th scope="col" className="admin-dash-col-button">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => {
                    const target = activityTarget(log);
                    const link = activityTargetLink(log);
                    return (
                      <tr key={log.id}>
                        <td className="admin-dash-col-time text-xs tabular-nums text-text-muted">{formatActivityTime(log.createdAt)}</td>
                        <td className="admin-dash-col-actor">
                          <p className="truncate font-semibold text-text-primary">{log.actorName || log.actorEmail || (log.actorId ? 'Tài khoản đã xóa' : 'Hệ thống')}</p>
                          {log.actorName && log.actorEmail ? <p className="mt-0.5 truncate text-xs text-text-muted">{log.actorEmail}</p> : null}
                        </td>
                        <td className="admin-dash-col-action">
                          <span className={`inline-flex whitespace-nowrap rounded-sm px-2 py-1 text-xs font-semibold ${activityTone(log.type)}`}>
                            {activityLogLabel(log) || 'Hoạt động khác'}
                          </span>
                        </td>
                        <td className="admin-dash-col-target">
                          {target.title || target.type ? <>
                            <p className="line-clamp-2 font-medium text-text-primary" title={target.title ?? target.type ?? undefined}>{target.title ?? target.type}</p>
                            {target.title && target.type ? <p className="mt-0.5 text-xs text-text-muted">{target.type}</p> : null}
                          </> : <span className="text-text-muted">—</span>}
                        </td>
                        <td className="leading-6 text-text-secondary">
                          {activityLogDetail(log) || log.reason ? <p className="line-clamp-2">{activityLogDetail(log) || log.reason}</p> : null}
                        </td>
                        <td className="admin-dash-col-button">
                          {link ? (
                            <Link to={link} className="admin-dash-detail-button">Xem chi tiết</Link>
                          ) : null}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="admin-dash-empty">Chưa có hoạt động nào được ghi nhận.</div>
          )}
        </div>
      </DashboardSection>
    </div>
  );
}

export function AdminFinanceReport() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [filters, setFilters] = useState({ from: '', to: '' });
  const [report, setReport] = useState<FinanceSummary | null>(null);
  const [error, setError] = useState('');
  const [rangeError, setRangeError] = useState('');
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState('');
  const [exportNotice, setExportNotice] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminFinanceApi.report({ from: filters.from || undefined, to: filters.to || undefined })
      .then((response) => { if (active) setReport(response); })
      .catch((caught) => { if (active) { setReport(null); setError(errorMessage(caught)); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [filters, reload]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (from && to && from > to) { setRangeError(INVALID_DATE_RANGE); return; }
    setRangeError('');
    setFilters({ from, to });
  };

  const exportExcel = async () => {
    if (exporting) return;
    setExporting(true);
    setExportError('');
    setExportNotice('');
    try {
      const download = await adminFinanceApi.reportExcel({
        from: filters.from || undefined,
        to: filters.to || undefined,
      });
      downloadBlob(download.blob, download.fileName);
      setExportNotice('Đã tải báo cáo Excel theo bộ lọc hiện tại.');
    } catch (caught) {
      setExportError(errorMessage(caught));
    } finally {
      setExporting(false);
    }
  };

  return <>
    <PageHeader title="Báo cáo tài chính" description="Tiền thu, Credit cộng và Credit trừ theo sổ hệ thống." action={<div className="flex flex-wrap gap-2"><Button variant="outline" disabled={exporting} onClick={() => void exportExcel()}>{exporting ? 'Đang xuất...' : 'Xuất báo cáo Excel'}</Button><Link to="/admin/finance/users"><Button variant="outline">Tra cứu người dùng</Button></Link></div>} />
    <form className="mb-5" onSubmit={submit} noValidate>
      <div className="flex flex-wrap items-end gap-3">
        <Field label="Từ ngày" type="date" value={from} max={to || undefined}
          aria-invalid={!!rangeError} aria-describedby={rangeError ? 'finance-range-error' : undefined}
          onChange={(event) => { setFrom(event.target.value); setRangeError(''); }} />
        <Field label="Đến ngày" type="date" value={to} min={from || undefined}
          aria-invalid={!!rangeError} aria-describedby={rangeError ? 'finance-range-error' : undefined}
          onChange={(event) => { setTo(event.target.value); setRangeError(''); }} />
        <Button type="submit" disabled={loading}>Lọc</Button>
      </div>
      {rangeError && <p id="finance-range-error" role="alert" className="mt-2 text-sm text-error">{rangeError}</p>}
    </form>
    {exportError ? <div className="mb-4" role="alert"><Alert tone="error">Không xuất được báo cáo Excel. {exportError}</Alert></div> : null}
    {exportNotice ? <div className="mb-4" role="status"><Alert tone="success">{exportNotice}</Alert></div> : null}
    {error ? <Alert tone="error">{error} <button type="button" className="underline" onClick={() => setReload((value) => value + 1)}>Thử lại</button></Alert> : null}
    {loading ? <EmptyState title="Đang tải báo cáo" text="Vui lòng chờ trong giây lát." /> : report ? <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-3">
        <Stat label="Tiền thu" value={formatVnd(report.cashAmountVnd)} detail={`${report.cashOrderCount} đơn nạp`} />
        <Stat label="Credit cộng" value={report.creditInTotal} />
        <Stat label="Credit trừ" value={report.creditOutTotal} />
      </div>
      <section className="rounded-lg bg-white p-5 ring-1 ring-border">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="section-title">Đối chiếu tiền thu và Credit nạp</h2>
          <span className={`rounded-sm px-2 py-1 text-xs font-semibold ${report.reconciliation.matched ? 'bg-success-soft text-success' : 'bg-warning-soft text-warning'}`}>
            {report.reconciliation.matched ? 'Khớp' : 'Có chênh lệch'}
          </span>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <Stat label="Credit quy đổi từ tiền thu" value={report.reconciliation.cashCredits} />
          <Stat label="Credit đã nạp" value={report.reconciliation.topUpCredits} />
          <Stat label="Chênh lệch" value={report.reconciliation.difference} />
        </div>
        {!!report.reconciliation.unlinkedEntryCount && (
          <div className="mt-3 text-sm text-text-secondary">
            <p>
              Có {report.reconciliation.unlinkedEntryCount} dòng nạp Credit không gắn với đơn thanh toán,
              tổng {(report.reconciliation.unlinkedCredits ?? 0).toLocaleString('vi-VN')} Credit.
            </p>
            <ul className="mt-2 list-disc space-y-1 pl-5">
              {report.reconciliation.unlinkedEntries?.map((entry) => (
                <li key={entry.id}>
                  Dòng #{entry.id} · Người dùng #{entry.userId} · {entry.amount.toLocaleString('vi-VN')} Credit ·{' '}
                  {new Date(entry.createdAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>
      <section className="rounded-lg bg-white p-5 ring-1 ring-border">
        <h2 className="mb-1 section-title">Tiền thu theo ngày</h2>
        <p className="mb-4 text-sm text-text-muted">Chọn một cột để xem các đơn nạp thành công theo ngày thanh toán.</p>
        <Suspense fallback={null}>
          <CashByDayChart days={report.cashByDay} />
        </Suspense>
      </section>
      <section className="min-w-0 rounded-lg bg-white p-5 ring-1 ring-border">
        <h2 className="mb-4 section-title">Credit trừ theo loại</h2>
        <Suspense fallback={null}>
          <CreditByTypeChart
            title="Credit trừ theo loại"
            rows={report.creditOutByType.map((row) => ({
              label: transactionLabels[row.type] ?? 'Khác',
              value: row.amount,
              detail: `${row.entryCount} lượt`,
            }))}
          />
        </Suspense>
      </section>
    </div> : null}
  </>;
}
