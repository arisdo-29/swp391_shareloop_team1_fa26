import { useRef, useState, type FormEvent } from 'react';
import { Alert, Button, Field, PageHeader, TextArea } from '../../components/ui';
import {
  adminReputationApi,
  type GraceStarResponse,
} from '../../services/adminReputationApi';
import type { ApiError } from '../../services/http';

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

export function AdminReputation() {
  const [userId, setUserId] = useState('');
  const [reason, setReason] = useState('');
  const [confirmationOpen, setConfirmationOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<GraceStarResponse | null>(null);
  const inFlight = useRef(false);
  const normalizedUserId = userId.trim();
  const numericUserId = Number(normalizedUserId);
  const validUserId =
    /^[1-9]\d*$/.test(normalizedUserId) &&
    Number.isSafeInteger(numericUserId) &&
    numericUserId > 0;
  const trimmedReason = reason.trim();
  const validReason = trimmedReason.length > 0 && trimmedReason.length <= 500;
  const userIdError = userId && !validUserId ? 'ID người dùng phải là số nguyên dương.' : '';

  const openConfirmation = (event: FormEvent) => {
    event.preventDefault();
    setError('');
    setResult(null);
    if (!validUserId) {
      setError('Vui lòng nhập ID người dùng là số nguyên dương.');
      return;
    }
    if (!validReason) {
      setError('Lý do phải có từ 1 đến 500 ký tự.');
      return;
    }
    setConfirmationOpen(true);
  };

  const confirmGrant = async () => {
    if (!validUserId || !validReason || inFlight.current) return;

    inFlight.current = true;
    setSubmitting(true);
    try {
      const response = await adminReputationApi.grantGraceStar(numericUserId, trimmedReason);
      setResult(response);
      setReason('');
      setConfirmationOpen(false);
    } catch (caught) {
      setError(graceStarErrorMessage(caught));
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <section>
      <PageHeader
        title="Uy tín & Hạng"
        description="Quản lý và hỗ trợ khôi phục uy tín cho tài khoản đủ điều kiện."
      />

      <Alert tone="warning">
        Sao ân hạn chỉ được cấp một lần cho tài khoản đang có 0 sao uy tín.
      </Alert>

      <form
        className="mt-5 max-w-2xl space-y-4 rounded-xl border border-border/80 bg-white p-5 shadow-sm sm:p-6"
        onSubmit={openConfirmation}
      >
        <Field
          label="ID người dùng"
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          required
          value={userId}
          disabled={submitting}
          error={userIdError}
          onChange={(event) => {
            setUserId(event.target.value);
            setError('');
            setResult(null);
          }}
          hint="Hiện hệ thống chỉ hỗ trợ xác định tài khoản theo ID."
        />
        <TextArea
          label="Lý do cấp sao"
          required
          maxLength={500}
          value={reason}
          disabled={submitting}
          onChange={(event) => {
            setReason(event.target.value);
            setError('');
            setResult(null);
          }}
          placeholder="Ví dụ: Người dùng đã giải trình và cam kết không tái phạm"
        />
        <p className="text-right text-xs text-text-muted">{reason.length}/500</p>

        {error && !confirmationOpen ? (
          <div role="alert">
            <Alert tone="error">{error}</Alert>
          </div>
        ) : null}
        {result ? (
          <div role="status">
            <Alert tone="success">
              Đã cấp sao ân hạn cho người dùng #{result.userId}. Số sao hiện tại:{' '}
              {result.trustStars}.
            </Alert>
          </div>
        ) : null}

        <Button type="submit" icon="star" disabled={submitting || !validUserId || !validReason}>
          Cấp 1 sao ân hạn
        </Button>
      </form>

      {confirmationOpen ? (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4">
          <div
            className="w-full max-w-lg rounded-xl border border-border bg-white p-5 shadow-xl sm:p-6"
            role="dialog"
            aria-modal="true"
            aria-labelledby="grace-star-confirm-title"
          >
            <h2 id="grace-star-confirm-title" className="text-lg font-bold text-text-primary">
              Xác nhận cấp sao ân hạn
            </h2>
            <p className="mt-1 text-sm leading-6 text-text-secondary">
              Vui lòng kiểm tra thông tin trước khi xác nhận.
            </p>

            <dl className="mt-5 divide-y divide-border rounded-lg bg-surface-low px-4">
              <div className="grid gap-1 py-3 sm:grid-cols-[9rem_1fr]">
                <dt className="text-sm text-text-muted">ID người dùng</dt>
                <dd className="font-semibold text-text-primary">#{numericUserId}</dd>
              </div>
              <div className="grid gap-1 py-3 sm:grid-cols-[9rem_1fr]">
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

            <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button
                type="button"
                variant="outline"
                disabled={submitting}
                onClick={() => {
                  setConfirmationOpen(false);
                  setError('');
                }}
              >
                Hủy
              </Button>
              <Button type="button" icon="star" disabled={submitting} onClick={() => void confirmGrant()}>
                {submitting ? 'Đang cấp sao…' : 'Xác nhận cấp 1 sao'}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}
