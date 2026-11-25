import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Dialog } from '../../components/Dialog';
import { Pagination } from '../../components/Pagination';
import {
  Alert,
  Avatar,
  Button,
  PageHeader,
  SearchField,
  Select,
  TextArea,
  SkeletonRows,
} from '../../components/ui';
import { adminFinanceApi, type AdminFinanceUserSummary } from '../../services/adminFinanceApi';
import { adminItemApi } from '../../services/adminItemApi';
import {
  adminUserApi,
  type AdminAccountRole,
  type AdminAccountState,
  type AdminUserSummary,
} from '../../services/adminUserApi';
import type { PageResponse } from '../../services/itemApi';
import { userApi, type PublicProfileResponse } from '../../services/userApi';
import { useDebouncedValue } from '../../utils/useDebouncedValue';
import { adminErrorMessage } from './adminErrors';
import './admin-accounts.css';

const PAGE_SIZE = 20;
const REASON_MAX_LENGTH = 500;

interface AccountStats {
  total: number;
  active: number;
  locked: number;
}

interface AccountEnrichment {
  profile?: PublicProfileResponse;
  wallet?: AdminFinanceUserSummary;
}

function avatarInitials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

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
  const [target, setTarget] = useState<{
    account: AdminUserSummary;
    action: 'lock' | 'unlock';
  } | null>(null);
  const [selectedAccount, setSelectedAccount] = useState<AdminUserSummary | null>(null);
  const [stats, setStats] = useState<AccountStats | null>(null);
  const [enrichment, setEnrichment] = useState<Record<number, AccountEnrichment>>({});

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

  useEffect(() => {
    let active = true;
    Promise.all([
      adminUserApi.list({ page: 0, size: 1 }),
      adminUserApi.list({ status: 'ACTIVE', page: 0, size: 1 }),
      adminUserApi.list({ status: 'LOCKED', page: 0, size: 1 }),
    ])
      .then(([allAccounts, activeAccounts, lockedAccounts]) => {
        if (!active) return;
        setStats({
          total: allAccounts.totalElements,
          active: activeAccounts.totalElements,
          locked: lockedAccounts.totalElements,
        });
      })
      .catch(() => {
        if (active) setStats(null);
      });
    return () => {
      active = false;
    };
  }, [reloadKey]);

  useEffect(() => {
    if (!result) return;
    let active = true;
    setEnrichment({});
    Promise.all(
      result.items.map(async (account) => {
        const [profileResult, walletResult] = await Promise.allSettled([
          userApi.getPublicProfile(account.id),
          account.role === 'USER'
            ? adminFinanceApi.userWallet(account.id)
            : Promise.resolve(undefined),
        ]);
        return {
          id: account.id,
          profile: profileResult.status === 'fulfilled' ? profileResult.value : undefined,
          wallet: walletResult.status === 'fulfilled' ? walletResult.value : undefined,
        };
      }),
    ).then((rows) => {
      if (!active) return;
      setEnrichment(Object.fromEntries(rows.map(({ id, ...data }) => [id, data])));
    });
    return () => {
      active = false;
    };
  }, [result]);

  const closeDialog = useCallback(() => setTarget(null), []);

  const onDone = (message: string) => {
    setTarget(null);
    setNotice(message);
    setReloadKey((key) => key + 1);
  };

  return (
    <section className="admin-accounts-page">
      <div className="admin-accounts-header">
        <PageHeader
          title="Quản lý tài khoản"
          description="Theo dõi tài khoản trong hệ thống, khóa hoặc mở khóa kèm lý do lưu vào nhật ký."
        />
      </div>

      <div className="admin-accounts-stats">
        <AccountStat label="Tổng tài khoản" value={stats?.total} modifier="total" />
        <AccountStat label="Đang hoạt động" value={stats?.active} modifier="active" />
        <AccountStat label="Đã khóa" value={stats?.locked} modifier="locked" />
      </div>

      <div className="admin-accounts-filters">
        <div className="admin-accounts-filter-search">
          <span className="label">Tìm kiếm tài khoản</span>
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
          className="admin-accounts-filter-state"
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
          className="admin-accounts-filter-role"
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
        <div role="status">
          <Alert tone="success">{notice}</Alert>
        </div>
      ) : null}
      {error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={() => setReloadKey((key) => key + 1)}>
            Thử lại
          </Button>
        </div>
      ) : null}

      {loading && !result ? (
        <SkeletonRows label="Đang tải danh sách tài khoản" />
      ) : result ? (
        <div className="admin-accounts-table-section">
          <div className="table-wrap admin-accounts-table-wrap">
            <table className="data-table admin-accounts-table">
              <thead>
                <tr>
                  <th>Người dùng</th>
                  <th>Số điện thoại</th>
                  <th>Vai trò</th>
                  <th className="text-right">Số dư Credit</th>
                  <th className="text-center">Uy tín</th>
                  <th>Trạng thái</th>
                  <th>Ngày đăng ký</th>
                  <th className="text-center">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((account) => {
                  const action = availableAction(account);
                  const data = enrichment[account.id];
                  return (
                    <tr key={account.id} className="admin-accounts-row">
                      <td>
                        <div className="admin-accounts-user">
                          <Avatar
                            user={{
                              name: account.fullName,
                              avatarInitials:
                                data?.profile?.avatarInitials || avatarInitials(account.fullName),
                              avatarUrl: data?.profile?.avatarUrl ?? undefined,
                            }}
                            size="md"
                          />
                          <div className="min-w-0">
                            <p className="admin-accounts-user-name">{account.fullName}</p>
                            <p className="admin-accounts-user-email">{account.email}</p>
                          </div>
                        </div>
                      </td>
                      <td>{account.phone || '—'}</td>
                      <td>{roleLabels[account.role]}</td>
                      <td className="admin-accounts-credit">
                        {data?.wallet
                          ? `${data.wallet.totalCredit.toLocaleString('vi-VN')} Credit`
                          : '—'}
                      </td>
                      <td className="admin-accounts-reputation">
                        {data?.profile
                          ? `${data.profile.trustStars.toLocaleString('vi-VN')} sao`
                          : '—'}
                      </td>
                      <td>
                        <span className={`admin-accounts-state ${stateBadgeClass(account.status)}`}>
                          {stateLabels[account.status]}
                        </span>
                      </td>
                      <td className="admin-accounts-date">
                        {new Date(account.createdAt).toLocaleDateString('vi-VN')}
                      </td>
                      <td>
                        <div className="admin-accounts-actions">
                          <button
                            type="button"
                            className="admin-accounts-action"
                            onClick={() => setSelectedAccount(account)}
                          >
                            Xem chi tiết
                          </button>
                          {action ? (
                            <Button
                              size="sm"
                              className="admin-accounts-action-size !min-h-8 !px-2 !py-1"
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
                              className="admin-accounts-action"
                              to={`/admin/finance/users/${account.id}`}
                            >
                              Xem Credit
                            </Link>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {result.items.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="text-center text-text-muted">
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
        </div>
      ) : null}

      {selectedAccount ? (
        <AccountDetailDialog account={selectedAccount} onClose={() => setSelectedAccount(null)} />
      ) : null}

      {target ? (
        <LockDialog
          account={target.account}
          action={target.action}
          onClose={closeDialog}
          onDone={onDone}
        />
      ) : null}
    </section>
  );
}

function AccountStat({
  label,
  value,
  modifier,
}: {
  label: string;
  value: number | undefined;
  modifier: 'total' | 'active' | 'locked';
}) {
  return (
    <div className={`admin-accounts-stat admin-accounts-stat--${modifier}`}>
      <p>{label}</p>
      <strong>{value === undefined ? '—' : value.toLocaleString('vi-VN')}</strong>
      <span>Toàn hệ thống</span>
    </div>
  );
}

function AccountDetailDialog({
  account,
  onClose,
}: {
  account: AdminUserSummary;
  onClose: () => void;
}) {
  const [profile, setProfile] = useState<PublicProfileResponse | null>(null);
  const [wallet, setWallet] = useState<AdminFinanceUserSummary | null>(null);
  const [totalItems, setTotalItems] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.allSettled([
      userApi.getPublicProfile(account.id),
      account.role === 'USER' ? adminFinanceApi.userWallet(account.id) : Promise.resolve(null),
      adminItemApi.list({ ownerId: account.id, page: 0, size: 1 }),
    ]).then(([profileResult, walletResult, itemResult]) => {
      if (!active) return;
      setProfile(profileResult.status === 'fulfilled' ? profileResult.value : null);
      setWallet(walletResult.status === 'fulfilled' ? walletResult.value : null);
      setTotalItems(itemResult.status === 'fulfilled' ? itemResult.value.totalElements : null);
      setLoading(false);
    });
    return () => {
      active = false;
    };
  }, [account.id, account.role]);

  return (
    <Dialog title="Chi tiết tài khoản" wide onClose={onClose}>
      <div className="admin-account-detail-profile">
        <Avatar
          user={{
            name: account.fullName,
            avatarInitials: profile?.avatarInitials || avatarInitials(account.fullName),
            avatarUrl: profile?.avatarUrl ?? undefined,
          }}
          size="lg"
        />
        <div>
          <p className="font-semibold text-text-primary">{account.fullName}</p>
          <p className="break-all text-xs text-text-muted">{account.email}</p>
        </div>
      </div>

      <dl className="admin-account-detail-grid">
        <AccountDetailItem label="Mã tài khoản">#{account.id}</AccountDetailItem>
        <AccountDetailItem label="Họ tên">{account.fullName}</AccountDetailItem>
        <AccountDetailItem label="Email">{account.email}</AccountDetailItem>
        <AccountDetailItem label="Số điện thoại">{account.phone || '—'}</AccountDetailItem>
        <AccountDetailItem label="Vai trò">{roleLabels[account.role]}</AccountDetailItem>
        <AccountDetailItem label="Trạng thái">
          <span className={`admin-accounts-state ${stateBadgeClass(account.status)}`}>
            {stateLabels[account.status]}
          </span>
        </AccountDetailItem>
        <AccountDetailItem label="Ngày đăng ký">
          {new Date(account.createdAt).toLocaleString('vi-VN')}
        </AccountDetailItem>
        {wallet ? (
          <AccountDetailItem label="Số dư Credit">
            {wallet.totalCredit.toLocaleString('vi-VN')} Credit
          </AccountDetailItem>
        ) : null}
        {profile ? (
          <AccountDetailItem label="Uy tín">
            {profile.trustStars.toLocaleString('vi-VN')} sao
          </AccountDetailItem>
        ) : null}
        {totalItems !== null ? (
          <AccountDetailItem label="Tổng bài đăng">
            {totalItems.toLocaleString('vi-VN')}
          </AccountDetailItem>
        ) : null}
        {profile ? (
          <AccountDetailItem label="Giao dịch hoàn tất">
            {profile.completedTransactions.toLocaleString('vi-VN')}
          </AccountDetailItem>
        ) : null}
      </dl>

      {loading ? (
        <p className="mt-3 text-xs text-text-muted">Đang tải thông tin bổ sung...</p>
      ) : null}

      <div className="admin-account-detail-links">
 fix/admin-user-profile
        <Link to={`/admin/users/${account.id}/profile`}>Xem hồ sơ người dùng</Link>

        <Link to={`/users/${account.id}`}>Xem hồ sơ người dùng</Link>
 develop
        {account.role === 'USER' ? (
          <Link to={`/admin/finance/users/${account.id}`}>Xem lịch sử Credit</Link>
        ) : null}
      </div>
    </Dialog>
  );
}

function AccountDetailItem({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="admin-account-detail-item">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
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
        {locking ? 'Khóa' : 'Mở khóa'} tài khoản <strong>{account.fullName}</strong> (
        {account.email}). Lý do sẽ được lưu vào nhật ký hành động.
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
