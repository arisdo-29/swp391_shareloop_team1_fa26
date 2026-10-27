import { lazy, Suspense, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, EmptyState, Field, PageHeader, Stat } from '../../components/ui';
import { adminFinanceApi, type FinanceSummary } from '../../services/adminFinanceApi';
import { adminAuditApi, type ActivityLogResponse } from '../../services/adminAuditApi';
import type { ApiError } from '../../services/http';
import { formatVnd } from '../../utils/formatting';
import { walletTransactionLabels } from '../../utils/creditHistory';

const CashByDayChart = lazy(() =>
  import('./FinanceCharts').then((module) => ({ default: module.CashByDayChart })),
);
const CreditByTypeChart = lazy(() =>
  import('./FinanceCharts').then((module) => ({ default: module.CreditByTypeChart })),
);

const transactionLabels: Record<string, string> = walletTransactionLabels;

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
const dashboardActions = [
  { to: '/admin/moderation', label: 'Duyệt bài' },
  { to: '/admin/finance', label: 'Báo cáo tài chính' },
  { to: '/admin/users', label: 'Quản lý tài khoản' },
  { to: '/admin/checklist', label: 'Checklist duyệt bài' },
  { to: '/admin/audit-logs', label: 'Nhật ký hoạt động' },
] as const;

function activityTone(type: string) {
  const normalizedType = type.toUpperCase();
  if (['REJECT', 'LOCK', 'DELETE', 'REMOVE', 'FAIL'].some((keyword) => normalizedType.includes(keyword))) {
    return 'bg-error-soft text-error';
  }
  if (['APPROVE', 'CREATE', 'ADD', 'UNLOCK', 'ACTIVATE'].some((keyword) => normalizedType.includes(keyword))) {
    return 'bg-success-soft text-success';
  }
  return 'bg-info-soft text-info';
}

export function AdminOperationsDashboard() {
  const [report, setReport] = useState<FinanceSummary | null>(null);
  const [logs, setLogs] = useState<ActivityLogResponse[]>([]);
  const [reportLoading, setReportLoading] = useState(true);
  const [logsLoading, setLogsLoading] = useState(true);
  const [reportError, setReportError] = useState('');
  const [logsError, setLogsError] = useState('');
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setReport(null);
    setLogs([]);
    setReportLoading(true);
    setLogsLoading(true);
    setReportError('');
    setLogsError('');

    void adminFinanceApi.report({})
      .then((summary) => { if (active) setReport(summary); })
      .catch((caught) => { if (active) setReportError(errorMessage(caught)); })
      .finally(() => { if (active) setReportLoading(false); });

    void adminAuditApi.list({ page: 0, size: 5 })
      .then((recent) => { if (active) setLogs(recent.content); })
      .catch((caught) => { if (active) setLogsError(errorMessage(caught)); })
      .finally(() => { if (active) setLogsLoading(false); });

    return () => { active = false; };
  }, [reload]);
  const metrics = report ? [
    { label: 'Tiền thu', value: formatVnd(report.cashAmountVnd), tone: 'border-primary' },
    { label: 'Credit cộng', value: report.creditInTotal.toLocaleString('vi-VN'), tone: 'border-success' },
    { label: 'Credit trừ', value: report.creditOutTotal.toLocaleString('vi-VN'), tone: 'border-secondary' },
  ] : [];

  return <>
    <PageHeader
      title="Tổng quan vận hành"
      description="Theo dõi nhanh số liệu tài chính và những hoạt động mới nhất trên ShareLoop."
    />
    <div className="grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1fr)_18rem] xl:items-start">
      <div className="min-w-0 space-y-6">
        {reportError ? (
          <Alert tone="error">
            {reportError}{' '}
            <button type="button" className="font-semibold underline" onClick={() => setReload((value) => value + 1)}>Thử lại</button>
          </Alert>
        ) : report ? (
          <div className="grid gap-4 sm:grid-cols-3">
            {metrics.map((metric) => (
              <section key={metric.label} className={`rounded-xl border border-l-4 border-border/80 bg-white p-5 shadow-sm ${metric.tone}`}>
                <p className="text-sm font-medium text-text-muted">{metric.label}</p>
                <p className="mt-1 break-words text-2xl font-bold tabular-nums text-text-primary">{metric.value}</p>
              </section>
            ))}
          </div>
        ) : (
          reportLoading ? <EmptyState title="Đang tải tổng quan" text="Vui lòng chờ trong giây lát." /> : null
        )}

        <section className="overflow-hidden rounded-xl border border-border/80 bg-white shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/70 px-5 py-4 sm:px-6">
            <div>
              <h2 className="section-title">Hoạt động gần đây</h2>
              <p className="mt-1 text-sm text-text-muted">Các thay đổi mới nhất được ghi nhận trong hệ thống.</p>
            </div>
            <Link className="text-sm font-semibold text-primary hover:text-primary-hover" to="/admin/audit-logs">
              Xem tất cả
            </Link>
          </div>
          {logsError ? (
            <div className="px-6 py-6">
              <Alert tone="error">
                {logsError}{' '}
                <button type="button" className="font-semibold underline" onClick={() => setReload((value) => value + 1)}>Thử lại</button>
              </Alert>
            </div>
          ) : logsLoading ? (
            <div className="px-6 py-10 text-center text-sm text-text-muted">Đang tải hoạt động gần đây...</div>
          ) : logs.length ? (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] border-collapse text-left text-sm">
                <thead className="bg-surface-low text-xs font-semibold text-text-muted">
                  <tr>
                    <th className="whitespace-nowrap px-5 py-3">Thời gian</th>
                    <th className="whitespace-nowrap px-5 py-3">Người thực hiện</th>
                    <th className="whitespace-nowrap px-5 py-3">Hành động</th>
                    <th className="px-5 py-3">Chi tiết</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => (
                    <tr key={log.id} className="border-t border-border/70 transition hover:bg-primary-faint">
                      <td className="whitespace-nowrap px-5 py-4 text-xs text-text-muted">{new Date(log.createdAt).toLocaleString('vi-VN')}</td>
                      <td className="max-w-48 px-5 py-4">
                        <p className="truncate font-semibold text-text-primary">{log.actorName || log.actorEmail || (log.actorId ? 'Tài khoản đã xóa' : 'Hệ thống')}</p>
                        {log.actorName && log.actorEmail ? <p className="mt-0.5 truncate text-xs text-text-muted">{log.actorEmail}</p> : null}
                      </td>
                      <td className="px-5 py-4">
                        <span className={`inline-flex rounded-md px-2.5 py-1 text-xs font-semibold ${activityTone(log.type)}`}>
                          {log.typeLabel || log.type}
                        </span>
                      </td>
                      <td className="max-w-sm px-5 py-4 leading-6 text-text-secondary">{log.detail || log.reason || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="px-6 py-10 text-center text-sm text-text-muted">Chưa có hoạt động nào được ghi nhận.</div>
          )}
        </section>
      </div>

      <aside className="rounded-xl border border-border/80 bg-white p-4 shadow-sm sm:p-5 xl:sticky xl:top-6">
        <h2 className="text-base font-bold text-text-primary">Thao tác nhanh</h2>
        <p className="mt-1 text-sm leading-6 text-text-muted">Truy cập các công cụ quản trị thường dùng.</p>
        <nav aria-label="Thao tác nhanh" className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-1">
          {dashboardActions.map((action) => (
            <Link
              key={action.to}
              to={action.to}
              className="flex min-h-11 min-w-0 items-center rounded-lg border border-border/70 px-3.5 py-3 text-sm font-semibold text-text-secondary transition hover:border-primary/30 hover:bg-primary-faint hover:text-primary"
            >
              {action.label}
            </Link>
          ))}
        </nav>
      </aside>
    </div>
  </>;
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
    if (from && to && from > to) { setRangeError('Từ ngày phải trước hoặc bằng đến ngày.'); return; }
    setRangeError('');
    setFilters({ from, to });
  };

  const exportCsv = async () => {
    if (exporting) return;
    setExporting(true);
    setExportError('');
    setExportNotice('');
    try {
      const download = await adminFinanceApi.reportCsv({
        from: filters.from || undefined,
        to: filters.to || undefined,
      });
      downloadBlob(download.blob, download.fileName);
      setExportNotice('Đã tải báo cáo CSV theo bộ lọc hiện tại.');
    } catch (caught) {
      setExportError(errorMessage(caught));
    } finally {
      setExporting(false);
    }
  };

  return <>
    <PageHeader title="Báo cáo tài chính" description="Tiền thu, Credit cộng và Credit trừ theo sổ hệ thống." action={<div className="flex flex-wrap gap-2"><Button variant="outline" disabled={exporting} onClick={() => void exportCsv()}>{exporting ? 'Đang xuất...' : 'Xuất báo cáo CSV'}</Button><Link to="/admin/finance/users"><Button variant="outline">Tra cứu người dùng</Button></Link></div>} />
    <form className="mb-5 flex flex-wrap items-end gap-3" onSubmit={submit}>
      <Field label="Từ ngày" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
      <Field label="Đến ngày" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
      <Button type="submit" disabled={loading}>Lọc</Button>
    </form>
    {rangeError ? <div className="mb-4" role="alert"><Alert tone="error">{rangeError}</Alert></div> : null}
    {exportError ? <div className="mb-4" role="alert"><Alert tone="error">Không xuất được báo cáo CSV. {exportError}</Alert></div> : null}
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
      </section>
      <section className="rounded-lg bg-white p-5 ring-1 ring-border">
        <h2 className="mb-1 section-title">Tiền thu theo ngày</h2>
        <p className="mb-4 text-sm text-text-muted">Tổng tiền các đơn nạp đã thanh toán trong khoảng ngày đã chọn.</p>
        <Suspense fallback={null}>
          <CashByDayChart days={report.cashByDay} />
        </Suspense>
      </section>
      <div className="grid gap-5 lg:grid-cols-2">
        {([
          { title: 'Credit cộng theo loại', rows: report.creditInByType, tone: 'credit' },
          { title: 'Credit trừ theo loại', rows: report.creditOutByType, tone: 'debit' },
        ] as const).map((group) => <section key={group.title} className="min-w-0 rounded-lg bg-white p-5 ring-1 ring-border">
          <h2 className="mb-4 section-title">{group.title}</h2>
          <Suspense fallback={null}>
            <CreditByTypeChart
              title={group.title}
              tone={group.tone}
              rows={group.rows.map((row) => ({
                label: transactionLabels[row.type] ?? 'Khác',
                value: row.amount,
                detail: `${row.entryCount} bút toán`,
              }))}
            />
          </Suspense>
        </section>)}
      </div>
    </div> : null}
  </>;
}
