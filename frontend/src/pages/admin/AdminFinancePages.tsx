import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Button, EmptyState, Field, Icon, PageHeader, Stat, TextArea } from '../../components/ui';
import { adminFinanceApi, type FinanceSummary } from '../../services/adminFinanceApi';
import { adminAuditApi, type ActivityLogResponse } from '../../services/adminAuditApi';
import type { ApiError } from '../../services/http';
import type { PageResponse } from '../../services/itemApi';
import type { WalletTransactionResponse } from '../../services/walletApi';
import { formatVnd } from '../../utils/formatting';
import { AdminTopUpOrders } from './AdminTopUpOrders';

const transactionLabels: Record<string, string> = {
  TOP_UP: 'Nạp Credit', POST_FEE: 'Phí đăng bài', EDIT_FEE: 'Phí sửa bài',
  RENEW_FEE: 'Phí gia hạn', BOOST_FEE: 'Phí đẩy bài', AI_SEARCH_FEE: 'Phí tìm bằng AI', ADMIN_ADJUST: 'Điều chỉnh',
};

function errorMessage(error: unknown) {
  const apiError = error as ApiError;
  if (apiError.status === 403) return 'Bạn không có quyền quản trị để thực hiện thao tác này.';
  return apiError.message || 'Không tải được dữ liệu. Vui lòng thử lại.';
}

const dashboardActions = [
  { to: '/admin/moderation', label: 'Duyệt bài', icon: 'article' },
  { to: '/admin/finance', label: 'Báo cáo tài chính', icon: 'payments' },
  { to: '/admin/users', label: 'Quản lý tài khoản', icon: 'group' },
  { to: '/admin/checklist', label: 'Checklist duyệt bài', icon: 'check' },
  { to: '/admin/audit-logs', label: 'Nhật ký hoạt động', icon: 'history' },
] as const;

function activityTone(type: string) {
  const normalizedType = type.toUpperCase();
  if (['REJECT', 'LOCK', 'DELETE', 'REMOVE', 'FAIL'].some((keyword) => normalizedType.includes(keyword))) {
    return 'bg-red-50 text-error';
  }
  if (['APPROVE', 'CREATE', 'ADD', 'UNLOCK', 'ACTIVATE'].some((keyword) => normalizedType.includes(keyword))) {
    return 'bg-emerald-50 text-success';
  }
  return 'bg-blue-50 text-info';
}

export function AdminOperationsDashboard() {
  const [report, setReport] = useState<FinanceSummary | null>(null);
  const [logs, setLogs] = useState<ActivityLogResponse[]>([]);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    setReport(null); setError('');
    Promise.all([adminFinanceApi.report({}), adminAuditApi.list({ page: 0, size: 5 })])
      .then(([summary, recent]) => { if (active) { setReport(summary); setLogs(recent.content); } })
      .catch((caught) => { if (active) setError(errorMessage(caught)); });
    return () => { active = false; };
  }, [reload]);
  const metrics = report ? [
    { label: 'Tiền thu', value: formatVnd(report.cashAmountVnd), icon: 'payments', tone: 'bg-primary-faint text-primary' },
    { label: 'Credit cộng', value: report.creditInTotal.toLocaleString('vi-VN'), icon: 'add', tone: 'bg-emerald-50 text-success' },
    { label: 'Credit trừ', value: report.creditOutTotal.toLocaleString('vi-VN'), icon: 'transaction', tone: 'bg-orange-50 text-secondary' },
  ] : [];

  return <>
    <PageHeader
      title="Tổng quan vận hành"
      description="Theo dõi nhanh số liệu tài chính và những hoạt động mới nhất trên ShareLoop."
    />
    <div className="grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1fr)_18rem] xl:items-start">
      <div className="min-w-0 space-y-6">
        {error ? (
          <Alert tone="error">
            {error}{' '}
            <button type="button" className="font-semibold underline" onClick={() => setReload((value) => value + 1)}>Thử lại</button>
          </Alert>
        ) : report ? (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              {metrics.map((metric) => (
                <section key={metric.label} className="rounded-xl border border-border/80 bg-white p-5 shadow-sm">
                  <div className={`flex size-11 items-center justify-center rounded-lg ${metric.tone}`}>
                    <Icon name={metric.icon} className="size-5" weight="bold" />
                  </div>
                  <p className="mt-4 text-sm font-medium text-text-muted">{metric.label}</p>
                  <p className="mt-1 break-words text-2xl font-bold tabular-nums text-text-primary">{metric.value}</p>
                </section>
              ))}
            </div>

            <section className="overflow-hidden rounded-xl border border-border/80 bg-white shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/70 px-5 py-4 sm:px-6">
                <div>
                  <h2 className="section-title">Hoạt động gần đây</h2>
                  <p className="mt-1 text-sm text-text-muted">Các thay đổi mới nhất được ghi nhận trong hệ thống.</p>
                </div>
                <Link className="inline-flex items-center gap-1.5 text-sm font-semibold text-primary hover:text-primary-hover" to="/admin/audit-logs">
                  Xem tất cả
                  <Icon name="arrow" className="size-4" />
                </Link>
              </div>
              {logs.length ? (
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
                            <p className="truncate font-semibold text-text-primary">{log.actorName || log.actorEmail || (log.actorId ? `#${log.actorId}` : '—')}</p>
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
          </>
        ) : (
          <EmptyState title="Đang tải tổng quan" text="Vui lòng chờ trong giây lát." />
        )}
      </div>

      <aside className="rounded-xl border border-border/80 bg-white p-4 shadow-sm sm:p-5 xl:sticky xl:top-6">
        <h2 className="text-base font-bold text-text-primary">Thao tác nhanh</h2>
        <p className="mt-1 text-sm leading-6 text-text-muted">Truy cập các công cụ quản trị thường dùng.</p>
        <nav aria-label="Thao tác nhanh" className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-1">
          {dashboardActions.map((action) => (
            <Link
              key={action.to}
              to={action.to}
              className="group flex min-w-0 items-center gap-3 rounded-lg border border-border/70 px-3.5 py-3 text-sm font-semibold text-text-secondary transition hover:border-primary/30 hover:bg-primary-faint hover:text-primary"
            >
              <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-surface-low text-primary transition group-hover:bg-white">
                <Icon name={action.icon} className="size-[18px]" weight="bold" />
              </span>
              <span className="min-w-0 flex-1">{action.label}</span>
              <Icon name="arrow" className="size-4 shrink-0 text-text-muted transition group-hover:translate-x-0.5 group-hover:text-primary" />
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
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);

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
    if (from && to && from > to) { setError('Từ ngày phải trước hoặc bằng đến ngày.'); return; }
    setFilters({ from, to });
  };

  return <>
    <PageHeader title="Báo cáo tài chính" description="Tiền thu, Credit cộng và Credit trừ theo sổ hệ thống." action={<Link to="/admin/finance/users"><Button variant="outline">Lịch sử người dùng</Button></Link>} />
    <AdminTopUpOrders onProcessed={() => setReload((value) => value + 1)} />
    <form className="mb-5 flex flex-wrap items-end gap-3" onSubmit={submit}>
      <Field label="Từ ngày" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
      <Field label="Đến ngày" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
      <Button type="submit" disabled={loading}>Lọc</Button>
    </form>
    {error ? <Alert tone="error">{error} <button type="button" className="underline" onClick={() => setReload((value) => value + 1)}>Thử lại</button></Alert> : null}
    {loading ? <EmptyState title="Đang tải báo cáo" text="Vui lòng chờ trong giây lát." /> : report ? <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-3">
        <Stat label="Tiền thu" value={formatVnd(report.cashAmountVnd)} detail={`${report.cashOrderCount} đơn nạp`} />
        <Stat label="Credit cộng" value={report.creditInTotal} />
        <Stat label="Credit trừ" value={report.creditOutTotal} />
      </div>
      <Alert tone={report.reconciliation.matched ? 'success' : 'warning'}>
        Đối chiếu tiền thu và Credit nạp: {report.reconciliation.matched ? 'Khớp' : 'Có chênh lệch'}.
        Credit quy đổi: {report.reconciliation.cashCredits}; Credit nạp: {report.reconciliation.topUpCredits}; chênh lệch: {report.reconciliation.difference}.
      </Alert>
      <div className="grid gap-5 lg:grid-cols-2">
        {[{ title: 'Credit cộng theo loại', rows: report.creditInByType }, { title: 'Credit trừ theo loại', rows: report.creditOutByType }].map((group) => <section key={group.title}>
          <h2 className="mb-3 section-title">{group.title}</h2>
          <div className="table-wrap"><table className="data-table"><thead><tr><th>Loại</th><th>Credit</th><th>Số bút toán</th></tr></thead><tbody>
            {group.rows.length ? group.rows.map((row) => <tr key={row.type}><td>{transactionLabels[row.type] ?? row.type}</td><td>{row.amount}</td><td>{row.entryCount}</td></tr>) : <tr><td colSpan={3}>Chưa có dữ liệu.</td></tr>}
          </tbody></table></div>
        </section>)}
      </div>
    </div> : null}
  </>;
}

export function AdminAccountActions() {
  const [userId, setUserId] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const numericId = Number(userId);
  const validId = Number.isSafeInteger(numericId) && numericId > 0;
  const update = async (locked: boolean) => {
    if (inFlight.current) return;
    setError(''); setNotice('');
    if (!validId || !reason.trim() || reason.trim().length > 500) { setError('Nhập mã người dùng hợp lệ và lý do từ 1 đến 500 ký tự.'); return; }
    if (!window.confirm(`${locked ? 'Khóa' : 'Mở khóa'} tài khoản #${numericId}?`)) return;
    inFlight.current = true; setBusy(true);
    try {
      await adminFinanceApi.setLocked(numericId, locked, reason.trim());
      setNotice(`Đã ${locked ? 'khóa' : 'mở khóa'} tài khoản #${numericId}.`);
      setReason('');
    } catch (caught) { setError(errorMessage(caught)); }
    finally { inFlight.current = false; setBusy(false); }
  };
  return <>
    <PageHeader title="Quản lý tài khoản" description="Khóa hoặc mở khóa tài khoản kèm lý do lưu trong nhật ký." />
    <div className="max-w-xl space-y-4 rounded-lg bg-white p-5 ring-1 ring-border">
      <Field label="Mã người dùng" type="number" min="1" value={userId} disabled={busy} onChange={(event) => { setUserId(event.target.value); setNotice(''); }} />
      <TextArea label="Lý do" maxLength={500} value={reason} disabled={busy} onChange={(event) => setReason(event.target.value)} />
      {error ? <Alert tone="error">{error}</Alert> : null}
      {notice ? <Alert tone="success">{notice}</Alert> : null}
      <div className="flex flex-wrap gap-2">
        <Button variant="danger" disabled={busy || !validId || !reason.trim()} onClick={() => void update(true)}>Khóa tài khoản</Button>
        <Button variant="outline" disabled={busy || !validId || !reason.trim()} onClick={() => void update(false)}>Mở khóa</Button>
        {validId ? <Link to={`/admin/finance/users/${numericId}`}><Button variant="ghost">Xem lịch sử Credit</Button></Link> : null}
      </div>
    </div>
  </>;
}

export function AdminUserCreditHistory() {
  const { userId: pathUserId } = useParams();
  const [userId, setUserId] = useState(pathUserId ?? '');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [filters, setFilters] = useState({ userId: pathUserId ?? '', from: '', to: '', page: 0 });
  const [result, setResult] = useState<PageResponse<WalletTransactionResponse> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setUserId(pathUserId ?? '');
    setFilters({ userId: pathUserId ?? '', from: '', to: '', page: 0 });
  }, [pathUserId]);

  useEffect(() => {
    let active = true;
    setResult(null);
    setLoading(false);
    if (!filters.userId) return;
    const id = Number(filters.userId);
    if (!Number.isSafeInteger(id) || id <= 0) { setError('Mã người dùng không hợp lệ.'); return; }
    setLoading(true); setError('');
    adminFinanceApi.userHistory(id, { from: filters.from || undefined, to: filters.to || undefined, page: filters.page, size: 20 })
      .then((response) => { if (active) setResult(response); })
      .catch((caught) => { if (active) setError(errorMessage(caught)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [filters]);

  return <>
    <PageHeader title="Lịch sử Credit người dùng" description="Tra cứu các bút toán tài chính theo người dùng và khoảng ngày." />
    <form className="mb-5 flex flex-wrap items-end gap-3" onSubmit={(event) => {
      event.preventDefault();
      if (!userId.trim()) { setError('Vui lòng nhập mã người dùng.'); return; }
      if (from && to && from > to) { setError('Từ ngày phải trước hoặc bằng đến ngày.'); return; }
      setFilters({ userId, from, to, page: 0 });
    }}>
      <Field label="Mã người dùng" type="number" min="1" required value={userId} onChange={(event) => setUserId(event.target.value)} />
      <Field label="Từ ngày" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
      <Field label="Đến ngày" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
      <Button type="submit" disabled={loading}>Tra cứu</Button>
    </form>
    {error ? <Alert tone="error">{error}</Alert> : null}
    {loading ? <EmptyState title="Đang tải lịch sử" text="Vui lòng chờ trong giây lát." /> : result ? <>
      <div className="table-wrap"><table className="data-table"><thead><tr><th>Thời gian</th><th>Loại</th><th>Credit</th><th>Số dư sau</th><th>Ghi chú</th></tr></thead><tbody>
        {result.items.length ? result.items.map((entry) => <tr key={entry.id}><td>{new Date(entry.createdAt).toLocaleString('vi-VN')}</td><td>{transactionLabels[entry.type] ?? entry.type}</td><td>{entry.amount}</td><td>{entry.balanceAfter}</td><td>{entry.note || '—'}</td></tr>) : <tr><td colSpan={5}>Chưa có bút toán trong khoảng ngày đã chọn.</td></tr>}
      </tbody></table></div>
      <div className="mt-4 flex items-center justify-center gap-3">
        <Button variant="outline" disabled={result.page === 0} onClick={() => setFilters((current) => ({ ...current, page: current.page - 1 }))}>Trang trước</Button>
        <span className="text-sm">Trang {result.page + 1}/{Math.max(1, Math.ceil(result.totalElements / result.size))}</span>
        <Button variant="outline" disabled={(result.page + 1) * result.size >= result.totalElements} onClick={() => setFilters((current) => ({ ...current, page: current.page + 1 }))}>Trang sau</Button>
      </div>
    </> : null}
  </>;
}
