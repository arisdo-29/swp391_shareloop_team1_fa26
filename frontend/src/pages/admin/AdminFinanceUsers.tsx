import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Button, PageHeader, SearchField, SkeletonRows } from '../../components/ui';
import {
  CreditHistorySection,
  CreditTransactionDetail,
  HistoryDateFilter,
  HistoryPagination,
} from '../../components/CreditHistoryView';
import { Pagination } from '../../components/Pagination';
import {
  adminFinanceApi,
  type AdminFinanceUserSummary,
} from '../../services/adminFinanceApi';
import type { PageResponse } from '../../services/itemApi';
import type { CreditHistory } from '../../types/domain';
import {
  HISTORY_PAGE_SIZE,
  adminHistoryTabs,
  endOfDayIso,
  startOfDayIso,
  toCreditHistory,
} from '../../utils/creditHistory';
import { useDebouncedValue } from '../../utils/useDebouncedValue';
import { adminErrorMessage } from './adminErrors';

const USER_PAGE_SIZE = 20;
// Admin không có danh sách bài của người dùng nên lịch sử không hiện tên món.
const noItemTitles = new Map<string, string>();

/** Danh sách người dùng kèm số dư Credit: mở trang là thấy ngay, gõ để tìm theo tên, email hoặc số điện thoại. */
export function AdminFinanceUsers() {
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebouncedValue(keyword.trim());
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<AdminFinanceUserSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminFinanceApi
      .users({ keyword: debouncedKeyword || undefined, page, size: USER_PAGE_SIZE })
      .then((response) => {
        if (active) setResult(response);
      })
      .catch((caught) => {
        if (!active) return;
        setResult(null);
        setError(adminErrorMessage(caught));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [debouncedKeyword, page, reloadKey]);

  return (
    <>
      <PageHeader
        title="Tra cứu người dùng"
        description="Xem số dư Credit và lịch sử giao dịch của từng người dùng."
      />
      <div className="mb-5 rounded-lg bg-white p-3 ring-1 ring-border/80">
        <SearchField
          aria-label="Tìm người dùng"
          placeholder="Tìm theo tên, email hoặc số điện thoại..."
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setPage(0);
          }}
        />
      </div>
      {error ? (
        <div className="mb-4 space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử lại
          </Button>
        </div>
      ) : null}
      {loading && !result ? (
        <SkeletonRows label="Đang tải danh sách" />
      ) : result ? (
        <>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Người dùng</th>
                  <th>Số điện thoại</th>
                  <th className="text-right">Khả dụng</th>
                  <th className="text-right">Đang giữ</th>
                  <th className="text-right">Tổng Credit</th>
                  <th className="text-right">Tổng đã nạp</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((user) => (
                  <tr key={user.userId}>
                    <td>
                      <p className="font-semibold text-text-primary">{user.fullName}</p>
                      <p className="text-xs text-text-muted">{user.email}</p>
                    </td>
                    <td>{user.phone || '—'}</td>
                    <td className="text-right tabular-nums">{user.availableCredit}</td>
                    <td className="text-right tabular-nums">{user.heldCredit}</td>
                    <td className="text-right font-semibold tabular-nums">{user.totalCredit}</td>
                    <td className="text-right tabular-nums">{user.totalToppedUpCredit}</td>
                    <td className="text-right">
                      <Link to={`/admin/finance/users/${user.userId}`}>
                        <Button variant="outline">
                          Xem chi tiết
                        </Button>
                      </Link>
                    </td>
                  </tr>
                ))}
                {result.items.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="text-center text-text-muted">
                      {debouncedKeyword ? 'Không tìm thấy người dùng phù hợp.' : 'Chưa có người dùng nào.'}
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
          <Pagination
            page={result.page}
            size={result.size}
            totalElements={result.totalElements}
            disabled={loading}
            onPageChange={setPage}
          />
        </>
      ) : null}
    </>
  );
}

function BalanceCard({ label, value, hint }: { label: string; value: number; hint?: string }) {
  return (
    <div className="rounded-lg bg-white p-4 ring-1 ring-border/80">
      <p className="text-xs font-medium text-text-muted">{label}</p>
      <p className="mt-1 text-2xl font-bold tabular-nums text-text-primary">{value}</p>
      {hint ? <p className="mt-1 text-xs text-text-muted">{hint}</p> : null}
    </div>
  );
}

/** Chi tiết tài chính một người dùng: số dư rồi lịch sử Credit, dùng lại khối lịch sử của trang Credit. */
export function AdminFinanceUserDetail() {
  const { userId: pathUserId } = useParams();
  const userId = Number(pathUserId);
  const validUserId = Number.isSafeInteger(userId) && userId > 0;
  const [summary, setSummary] = useState<AdminFinanceUserSummary | null>(null);
  const [summaryError, setSummaryError] = useState('');
  const [history, setHistory] = useState<CreditHistory[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState('');
  const [tab, setTab] = useState('all');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [selected, setSelected] = useState<CreditHistory | null>(null);
  const dateRangeError =
    fromDate && toDate && fromDate > toDate ? '"Từ ngày" không được sau "Đến ngày".' : '';

  useEffect(() => {
    if (!validUserId) return;
    let active = true;
    setSummary(null);
    setSummaryError('');
    adminFinanceApi
      .userWallet(userId)
      .then((response) => {
        if (active) setSummary(response);
      })
      .catch((caught) => {
        if (active) setSummaryError(adminErrorMessage(caught));
      });
    return () => {
      active = false;
    };
  }, [userId, validUserId, reloadKey]);

  useEffect(() => {
    if (!validUserId || dateRangeError) return;
    let active = true;
    setHistoryLoading(true);
    setHistoryError('');
    adminFinanceApi
      .userHistory(userId, {
        from: fromDate ? startOfDayIso(fromDate) : undefined,
        to: toDate ? endOfDayIso(toDate) : undefined,
        page,
        size: HISTORY_PAGE_SIZE,
      })
      .then((response) => {
        if (!active) return;
        setHistory(response.items.map((entry) => toCreditHistory(entry, String(userId))));
        setHistoryTotal(response.totalElements);
      })
      .catch((caught) => {
        if (!active) return;
        setHistory([]);
        setHistoryError(adminErrorMessage(caught, 'Không tải được lịch sử Credit.'));
      })
      .finally(() => {
        if (active) setHistoryLoading(false);
      });
    return () => {
      active = false;
    };
  }, [userId, validUserId, fromDate, toDate, page, dateRangeError, reloadKey]);

  // Đổi bộ lọc ngày thì quay về trang đầu, giống trang Credit của người dùng.
  const changeDateRange = (nextFrom: string, nextTo: string) => {
    setFromDate(nextFrom);
    setToDate(nextTo);
    setPage(0);
  };

  if (!validUserId) {
    return <Alert tone="error">Đường dẫn người dùng không hợp lệ.</Alert>;
  }

  return (
    <>
      <PageHeader
        title={summary ? summary.fullName : 'Chi tiết Credit người dùng'}
        description={summary ? [summary.email, summary.phone].filter(Boolean).join(' · ') : undefined}
        action={
          <Link to="/admin/finance/users">
            <Button variant="outline" icon="arrow">
              Về danh sách
            </Button>
          </Link>
        }
      />
      {summaryError ? (
        <div className="mb-4 space-y-3">
          <Alert tone="error">{summaryError}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử lại
          </Button>
        </div>
      ) : null}
      {summary ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <BalanceCard label="Khả dụng" value={summary.availableCredit} />
          <BalanceCard label="Đang giữ" value={summary.heldCredit} hint="Phí các bài chưa được duyệt" />
          <BalanceCard label="Tổng Credit" value={summary.totalCredit} />
          <BalanceCard label="Tổng đã nạp" value={summary.totalToppedUpCredit} />
        </div>
      ) : null}
      {historyError ? (
        <div className="mt-6 space-y-3">
          <Alert tone="error">{historyError}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử tải lại
          </Button>
        </div>
      ) : null}
      {historyLoading ? (
        <div className="mt-6">
          <Alert>Đang tải lịch sử Credit...</Alert>
        </div>
      ) : null}
      <CreditHistorySection
        itemTitles={noItemTitles}
        allHistory={history}
        tabs={adminHistoryTabs}
        activeTab={tab}
        onTabChange={setTab}
        onSelect={setSelected}
        dateFilter={
          <HistoryDateFilter
            fromDate={fromDate}
            toDate={toDate}
            error={dateRangeError}
            onChange={changeDateRange}
          />
        }
        pagination={<HistoryPagination page={page} total={historyTotal} onPageChange={setPage} />}
      />
      {selected ? (
        <CreditTransactionDetail entry={selected} onClose={() => setSelected(null)} />
      ) : null}
    </>
  );
}
