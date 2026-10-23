import { useRef, useState } from 'react';
import { Dialog } from '../../components/Dialog';
import { Alert, Button, TextArea } from '../../components/ui';
import { adminReputationApi, type GraceStarResponse } from '../../services/adminReputationApi';
import type { AdminUserSummary } from '../../services/adminUserApi';
import type { ApiError } from '../../services/http';
import { AdminUserPicker } from './AdminUserPicker';

const REASON_MAX_LENGTH = 500;

function graceStarErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.code === 'REPUTATION_GRACE_STAR_ALREADY_USED') {
    return 'Người dùng đã được cấp sao ân hạn trước đó. Mỗi tài khoản chỉ được cấp một lần.';
  }
  if (apiError.code === 'REPUTATION_GRACE_STAR_NOT_ELIGIBLE') {
    return 'Chỉ có thể cấp sao ân hạn khi người dùng đang có 0 sao.';
  }
  if (apiError.status === 404) return 'Không tìm thấy người dùng.';
  if (apiError.status === 403) return 'Bạn không có quyền cấp sao ân hạn.';
  if (apiError.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (apiError.status === 400) return 'Thông tin cấp sao chưa hợp lệ. Vui lòng kiểm tra lại.';
  if (apiError.status === 0) return 'Không thể kết nối đến hệ thống. Vui lòng thử lại.';
  return 'Không thể cấp sao ân hạn. Vui lòng thử lại sau.';
}

/** Cấp một sao ân hạn cho người dùng đang có 0 sao. Mỗi tài khoản chỉ được cấp một lần. */
export function GraceStarSection() {
  const [user, setUser] = useState<AdminUserSummary | null>(null);
  const [reason, setReason] = useState('');
  const [confirmationOpen, setConfirmationOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<{ name: string; response: GraceStarResponse } | null>(null);
  const inFlight = useRef(false);
  const trimmedReason = reason.trim();
  const ready = user !== null && trimmedReason.length > 0 && trimmedReason.length <= REASON_MAX_LENGTH;

  const confirmGrant = async () => {
    if (!user || !ready || inFlight.current) return;
    inFlight.current = true;
    setSubmitting(true);
    try {
      const response = await adminReputationApi.grantGraceStar(user.id, trimmedReason);
      setResult({ name: user.fullName, response });
      setReason('');
      setUser(null);
      setConfirmationOpen(false);
    } catch (caught) {
      setError(graceStarErrorMessage(caught));
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  const closeConfirmation = () => {
    setConfirmationOpen(false);
    setError('');
  };

  return (
    <section>
      <h2 className="mb-1 section-title">Cấp sao ân hạn</h2>
      <p className="mb-4 text-sm text-text-muted">
        Hỗ trợ khôi phục uy tín cho tài khoản đủ điều kiện.
      </p>
      <Alert tone="warning">Sao ân hạn chỉ được cấp một lần cho tài khoản đang có 0 sao uy tín.</Alert>

      <form
        className="mt-5 max-w-2xl space-y-4 rounded-xl border border-border/80 bg-white p-5 shadow-sm sm:p-6"
        onSubmit={(event) => {
          event.preventDefault();
          setError('');
          setResult(null);
          if (ready) setConfirmationOpen(true);
        }}
      >
        <div>
          <span className="label">Người dùng</span>
          <AdminUserPicker
            role="USER"
            selected={user}
            disabled={submitting}
            onSelect={(next) => {
              setUser(next);
              setResult(null);
            }}
          />
        </div>
        <TextArea
          label="Lý do cấp sao"
          required
          maxLength={REASON_MAX_LENGTH}
          value={reason}
          disabled={submitting}
          onChange={(event) => {
            setReason(event.target.value);
            setResult(null);
          }}
          placeholder="Ví dụ: Người dùng đã giải trình và cam kết không tái phạm"
        />
        <p className="text-right text-xs text-text-muted">
          {reason.length}/{REASON_MAX_LENGTH}
        </p>
        {result ? (
          <div role="status">
            <Alert tone="success">
              Đã cấp sao ân hạn cho {result.name}. Số sao hiện tại: {result.response.trustStars}.
            </Alert>
          </div>
        ) : null}
        <Button type="submit" icon="star" disabled={submitting || !ready}>
          Cấp 1 sao ân hạn
        </Button>
      </form>

      {confirmationOpen && user ? (
        <Dialog title="Xác nhận cấp sao ân hạn" busy={submitting} onClose={closeConfirmation}>
          <p className="text-sm leading-6 text-text-secondary">
            Vui lòng kiểm tra thông tin trước khi xác nhận.
          </p>
          <dl className="mt-4 divide-y divide-border rounded-lg bg-surface-low px-4">
            <div className="grid gap-1 py-3 sm:grid-cols-[7rem_1fr]">
              <dt className="text-sm text-text-muted">Người dùng</dt>
              <dd className="font-semibold text-text-primary">
                {user.fullName}
                <span className="block text-xs font-normal text-text-muted">{user.email}</span>
              </dd>
            </div>
            <div className="grid gap-1 py-3 sm:grid-cols-[7rem_1fr]">
              <dt className="text-sm text-text-muted">Lý do</dt>
              <dd className="break-words text-sm leading-6 text-text-primary">{trimmedReason}</dd>
            </div>
          </dl>
          <div className="mt-4">
            <Alert tone="warning">
              Thao tác này chỉ áp dụng một lần cho tài khoản đủ điều kiện và không thể cấp lại.
            </Alert>
          </div>
          {error ? (
            <div className="mt-4" role="alert">
              <Alert tone="error">{error}</Alert>
            </div>
          ) : null}
          <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" disabled={submitting} onClick={closeConfirmation}>
              Hủy
            </Button>
            <Button type="button" icon="star" disabled={submitting} onClick={() => void confirmGrant()}>
              {submitting ? 'Đang cấp sao…' : 'Xác nhận cấp 1 sao'}
            </Button>
          </div>
        </Dialog>
      ) : null}
    </section>
  );
}
