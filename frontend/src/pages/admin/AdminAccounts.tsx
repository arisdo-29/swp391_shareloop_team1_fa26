import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Dialog } from '../../components/Dialog';
import { Pagination } from '../../components/Pagination';
import {
  Alert,
  Button,
  EmptyState,
  PageHeader,
  SearchField,
  Select,
  TextArea,
} from '../../components/ui';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import {
  adminUserApi,
  type AdminAccountRole,
  type AdminAccountState,
  type AdminUserSummary,
} from '../../services/adminUserApi';
import type { PageResponse } from '../../services/itemApi';
import { useDebouncedValue } from '../../utils/useDebouncedValue';
import { adminErrorMessage } from './adminErrors';

const PAGE_SIZE = 20;
const REASON_MAX_LENGTH = 500;

const stateLabels: Record<AdminAccountState, string> = {
  ACTIVE: 'Đang hoạt động',
  LOCKED: 'Đã khóa',
  PENDING_VERIFICATION: 'Chờ xác minh',
  BANNED: 'Bị cấm',
};

const roleLabels: Record<AdminAccountRole, string> = {
  ADMIN: 'Quản trị viên',
  USER: 'Người dùng',
};

function stateBadgeClass(state: AdminAccountState) {
  if (state === 'ACTIVE') return 'bg-success-soft text-success';
  if (state === 'PENDING_VERIFICATION') return 'bg-warning-soft text-warning';
  return 'bg-error-soft text-error';
}

/** Khóa chỉ dành cho người dùng chưa bị cấm, mở khóa chỉ khi tài khoản đang ở trạng thái "Đã khóa". */
function availableAction(account: AdminUserSummary): 'lock' | 'unlock' | null {
  if (account.role !== 'USER') return null;
  if (account.status === 'LOCKED') return 'unlock';
  if (account.status === 'ACTIVE' || account.status === 'PENDING_VERIFICATION') return 'lock';
  return null;
}

/** Quản lý tài khoản: danh sách có tìm kiếm, lọc, phân trang và khóa hoặc mở khóa ngay trên từng hàng. */
export function AdminAccounts() {
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebouncedValue(keyword.trim());
  const [state, setState] = useState<AdminAccountState | ''>('');
  const [role, setRole] = useState<AdminAccountRole | ''>('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<AdminUserSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [target, setTarget] = useState<{ account: AdminUserSummary; action: 'lock' | 'unlock' } | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminUserApi
      .list({
        keyword: debouncedKeyword || undefined,
        status: state || undefined,
        role: role || undefined,
        page,
        size: PAGE_SIZE,
      })
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
  }, [debouncedKeyword, state, role, page, reloadKey]);

  const closeDialog = useCallback(() => setTarget(null), []);

  const onDone = (message: string) => {
    setTarget(null);
    setNotice(message);
    setReloadKey((key) => key + 1);
  };

  return (
    <>
      <PageHeader
        title="Quản lý tài khoản"
        description="Theo dõi tài khoản trong hệ thống, khóa hoặc mở khóa kèm lý do lưu vào nhật ký."
      />
      <div className="mb-5 flex flex-col gap-3 rounded-lg bg-white p-3 ring-1 ring-border/80 lg:flex-row lg:items-end">
        <div className="min-w-0 flex-1">
          <SearchField
            aria-label="Tìm tài khoản"
            placeholder="Tìm theo tên, email hoặc số điện thoại..."
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value);
              setPage(0);
            }}
          />
        </div>
        <Select
          label="Trạng thái"
          value={state}
          onChange={(event) => {
            setState(event.target.value as AdminAccountState | '');
            setPage(0);
          }}
        >
          <option value="">Tất cả</option>
          {(Object.keys(stateLabels) as AdminAccountState[]).map((value) => (
            <option key={value} value={value}>
              {stateLabels[value]}
            </option>
          ))}
        </Select>
        <Select
          label="Vai trò"
          value={role}
          onChange={(event) => {
            setRole(event.target.value as AdminAccountRole | '');
            setPage(0);
          }}
        >
          <option value="">Tất cả</option>
          {(Object.keys(roleLabels) as AdminAccountRole[]).map((value) => (
            <option key={value} value={value}>
              {roleLabels[value]}
            </option>
          ))}
        </Select>
      </div>

      {notice ? (
        <div className="mb-4" role="status">
          <Alert tone="success">{notice}</Alert>
        </div>
      ) : null}
      {error ? (
        <div className="mb-4 space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử lại
          </Button>
        </div>
      ) : null}

      {loading && !result ? (
        <EmptyState title="Đang tải danh sách tài khoản" text="Vui lòng chờ trong giây lát." />
      ) : result ? (
        <>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Họ tên</th>
                  <th>Email</th>
                  <th>Số điện thoại</th>
                  <th>Vai trò</th>
                  <th>Trạng thái</th>
                  <th>Ngày tạo</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((account) => {
                  const action = availableAction(account);
                  return (
                    <tr key={account.id}>
                      <td className="font-semibold text-text-primary">{account.fullName}</td>
                      <td className="break-all">{account.email}</td>
                      <td>{account.phone || '—'}</td>
                      <td>{roleLabels[account.role]}</td>
                      <td>
                        <span
                          className={`inline-flex whitespace-nowrap rounded-sm px-2 py-1 text-2xs font-semibold ${stateBadgeClass(account.status)}`}
                        >
                          {stateLabels[account.status]}
                        </span>
                      </td>
                      <td className="whitespace-nowrap text-xs">
                        {new Date(account.createdAt).toLocaleDateString('vi-VN')}
                      </td>
                      <td>
                        <div className="flex flex-wrap items-center gap-2">
                          {action ? (
                            <Button
                              variant={action === 'lock' ? 'danger' : 'outline'}
                              onClick={() => {
                                setNotice('');
                                setTarget({ account, action });
                              }}
                            >
                              {action === 'lock' ? 'Khóa' : 'Mở khóa'}
                            </Button>
                          ) : null}
                          {account.role === 'USER' ? (
                            <Link
                              className="text-xs font-semibold text-primary hover:underline"
                              to={`/admin/finance/users/${account.id}`}
                            >
                              Xem Credit
                            </Link>
                          ) : (
                            <span className="text-xs text-text-muted">—</span>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {result.items.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="text-center text-text-muted">
                      Không có tài khoản phù hợp bộ lọc hiện tại.
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

      {target ? (
        <LockDialog
          account={target.account}
          action={target.action}
          onClose={closeDialog}
          onDone={onDone}
        />
      ) : null}
    </>
  );
}

function LockDialog({
  account,
  action,
  onClose,
  onDone,
}: {
  account: AdminUserSummary;
  action: 'lock' | 'unlock';
  onClose: () => void;
  onDone: (message: string) => void;
}) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);
  const locking = action === 'lock';
  const trimmedReason = reason.trim();

  const submit = async () => {
    if (inFlight.current || !trimmedReason) return;
    inFlight.current = true;
    setBusy(true);
    setError('');
    try {
      await adminFinanceApi.setLocked(account.id, locking, trimmedReason);
      onDone(`Đã ${locking ? 'khóa' : 'mở khóa'} tài khoản của ${account.fullName}.`);
    } catch (caught) {
      setError(adminErrorMessage(caught, 'Không cập nhật được tài khoản. Vui lòng thử lại.'));
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  return (
    <Dialog title={locking ? 'Khóa tài khoản' : 'Mở khóa tài khoản'} busy={busy} onClose={onClose}>
      <p className="text-sm leading-6 text-text-secondary">
        {locking ? 'Khóa' : 'Mở khóa'} tài khoản <strong>{account.fullName}</strong> ({account.email}).
        Lý do sẽ được lưu vào nhật ký hành động.
      </p>
      <div className="mt-4">
        <TextArea
          label="Lý do (bắt buộc)"
          maxLength={REASON_MAX_LENGTH}
          value={reason}
          disabled={busy}
          onChange={(event) => setReason(event.target.value)}
        />
        <p className="mt-1 text-right text-xs text-text-muted">
          {reason.length}/{REASON_MAX_LENGTH}
        </p>
      </div>
      {error ? (
        <div className="mt-3" role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : null}
      <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variant="outline" disabled={busy} onClick={onClose}>
          Hủy
        </Button>
        <Button
          variant={locking ? 'danger' : 'primary'}
          disabled={busy || !trimmedReason}
          onClick={() => void submit()}
        >
          {busy ? 'Đang xử lý...' : locking ? 'Xác nhận khóa' : 'Xác nhận mở khóa'}
        </Button>
      </div>
    </Dialog>
  );
}
