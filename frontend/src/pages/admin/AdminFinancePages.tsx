import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Button, EmptyState, Field, PageHeader, Stat, TextArea } from '../../components/ui';
import { adminFinanceApi, type FinanceSummary } from '../../services/adminFinanceApi';
import { adminAuditApi, type ActivityLogResponse } from '../../services/adminAuditApi';
import type { ApiError } from '../../services/http';
import type { PageResponse } from '../../services/itemApi';
import type { WalletTransactionResponse } from '../../services/walletApi';
import { formatVnd } from '../../utils/formatting';

const transactionLabels: Record<string, string> = {
  TOP_UP: 'Nạp Credit', POST_FEE: 'Phí đăng bài', EDIT_FEE: 'Phí sửa bài',
  RENEW_FEE: 'Phí gia hạn', BOOST_FEE: 'Phí đẩy bài', AI_SEARCH_FEE: 'Phí tìm bằng AI', ADMIN_ADJUST: 'Điều chỉnh',
};

function errorMessage(error: unknown) {
  const apiError = error as ApiError;
  if (apiError.status === 403) return 'Bạn không có quyền quản trị để thực hiện thao tác này.';
  return apiError.message || 'Không tải được dữ liệu. Vui lòng thử lại.';
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
  return <>
    <PageHeader title="Tổng quan vận hành" description="Số liệu tài chính và hoạt động gần đây của hệ thống." />
    <div className="mb-6 flex flex-wrap gap-3">
      <Link to="/admin/moderation"><Button>Duyệt bài</Button></Link>
      <Link to="/admin/finance"><Button variant="outline">Báo cáo tài chính</Button></Link>
      <Link to="/admin/users"><Button variant="outline">Quản lý tài khoản</Button></Link>
    </div>
    {error ? <Alert tone="error">{error} <button type="button" className="underline" onClick={() => setReload((value) => value + 1)}>Thử lại</button></Alert> : report ? <>
      <div className="grid gap-4 sm:grid-cols-3">
        <Stat label="Tiền thu" value={formatVnd(report.cashAmountVnd)} />
        <Stat label="Credit cộng" value={report.creditInTotal} />
        <Stat label="Credit trừ" value={report.creditOutTotal} />
      </div>
      <section className="mt-6 rounded-lg bg-white p-5 ring-1 ring-border">
        <h2 className="mb-3 section-title">Hoạt động gần đây</h2>
        {logs.length ? logs.map((log) => <div key={log.id} className="border-b border-border py-3 text-sm"><p>{log.actorName ?? 'Hệ thống'} · {log.typeLabel ?? log.type}</p><p>{log.detail || log.reason || '—'}</p><p className="text-xs text-text-muted">{new Date(log.createdAt).toLocaleString('vi-VN')}</p></div>) : <p>Chưa có nhật ký.</p>}
        <Link className="mt-3 inline-block text-sm text-primary" to="/admin/audit-logs">Xem toàn bộ nhật ký</Link>
      </section>
    </> : <EmptyState title="Đang tải tổng quan" text="Vui lòng chờ trong giây lát." />}
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
      <section className="rounded-lg bg-white p-5 ring-1 ring-border">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="section-title">Đối chiếu tiền thu và Credit nạp</h2>
          <span className={`rounded-sm px-2 py-1 text-xs font-semibold ${report.reconciliation.matched ? 'bg-emerald-50 text-success' : 'bg-amber-50 text-warning'}`}>
            {report.reconciliation.matched ? 'Khớp' : 'Có chênh lệch'}
          </span>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <Stat label="Credit quy đổi từ tiền thu" value={report.reconciliation.cashCredits} />
          <Stat label="Credit đã nạp" value={report.reconciliation.topUpCredits} />
          <Stat label="Chênh lệch" value={report.reconciliation.difference} />
        </div>
      </section>
      <div className="grid gap-5 lg:grid-cols-2">
        {[{ title: 'Credit cộng theo loại', rows: report.creditInByType }, { title: 'Credit trừ theo loại', rows: report.creditOutByType }].map((group) => <section key={group.title}>
          <h2 className="mb-3 section-title">{group.title}</h2>
          {/* !min-w-0: bảng chỉ 3 cột trong nửa khung, không cần min-w-[720px] mặc định nên không còn thanh cuộn ngang. */}
          <div className="table-wrap"><table className="data-table !min-w-0"><thead><tr><th>Loại</th><th>Credit</th><th>Số bút toán</th></tr></thead><tbody>
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
