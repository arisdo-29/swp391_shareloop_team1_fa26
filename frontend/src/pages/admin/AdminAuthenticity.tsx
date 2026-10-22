import { useRef, useState } from 'react';
import { Alert, Button, Field, Icon, PageHeader, TextArea } from '../../components/ui';
import {
  adminAuthenticityApi,
  authenticityErrorMessage,
  beginAuthenticitySubmission,
  validateAuthenticityReason,
  type AuthenticityAction,
} from '../../services/adminAuthenticityApi';

const STATUS_WARNING =
  'Trạng thái xác minh chưa được cung cấp bởi Backend. Vui lòng kiểm tra bài đăng trước khi thao tác. Backend sẽ xác thực điều kiện thực hiện.';

const actionContent: Record<
  AuthenticityAction,
  { button: string; confirm: string; title: string; description: string; success: string }
> = {
  grant: {
    button: 'Cấp nhãn chính hãng',
    confirm: 'Xác nhận cấp nhãn',
    title: 'Xác nhận cấp nhãn chính hãng',
    description: 'Backend chỉ chấp nhận thao tác khi bài đăng đang ở trạng thái PENDING.',
    success: 'Đã cấp nhãn chính hãng thành công.',
  },
  reject: {
    button: 'Từ chối nhãn',
    confirm: 'Xác nhận từ chối',
    title: 'Từ chối nhãn chính hãng',
    description: 'Backend chỉ chấp nhận thao tác khi bài đăng đang ở trạng thái PENDING.',
    success: 'Đã từ chối nhãn chính hãng thành công.',
  },
  revoke: {
    button: 'Thu hồi nhãn',
    confirm: 'Xác nhận thu hồi',
    title: 'Thu hồi nhãn chính hãng',
    description: 'Backend chỉ chấp nhận thao tác khi bài đăng đang ở trạng thái VERIFIED.',
    success: 'Đã thu hồi nhãn chính hãng thành công.',
  },
};

function parseItemId(value: string): number | null {
  if (!/^\d+$/.test(value.trim())) return null;
  const itemId = Number(value);
  return Number.isSafeInteger(itemId) && itemId > 0 ? itemId : null;
}

export function AdminAuthenticity() {
  const [itemIdInput, setItemIdInput] = useState('');
  const [itemIdError, setItemIdError] = useState('');
  const [selectedAction, setSelectedAction] = useState<AuthenticityAction | null>(null);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);
  const submissionLock = useRef(false);

  const itemId = parseItemId(itemIdInput);

  const openConfirmation = (action: AuthenticityAction) => {
    if (itemId === null) {
      setItemIdError('Vui lòng nhập ID bài đăng là số nguyên dương.');
      return;
    }
    setItemIdError('');
    setReason('');
    setReasonError('');
    setNotice(null);
    setSelectedAction(action);
  };

  const closeConfirmation = () => {
    if (submitting) return;
    setSelectedAction(null);
    setReason('');
    setReasonError('');
  };

  const submitAction = async () => {
    if (
      selectedAction === null ||
      itemId === null ||
      !beginAuthenticitySubmission(submissionLock, true)
    ) {
      return;
    }

    const validationError = validateAuthenticityReason(selectedAction, reason);
    if (validationError) {
      submissionLock.current = false;
      setReasonError(validationError);
      return;
    }

    const action = selectedAction;
    setReasonError('');
    setNotice(null);
    setSubmitting(true);
    try {
      if (action === 'grant') await adminAuthenticityApi.grant(itemId);
      else if (action === 'reject') await adminAuthenticityApi.reject(itemId, reason.trim());
      else await adminAuthenticityApi.revoke(itemId, reason.trim());

      setNotice({ tone: 'success', message: actionContent[action].success });
      setSelectedAction(null);
      setReason('');
    } catch (error) {
      setNotice({ tone: 'error', message: authenticityErrorMessage(error) });
    } finally {
      submissionLock.current = false;
      setSubmitting(false);
    }
  };

  return (
    <section className="space-y-5">
      <PageHeader
        title="Nhãn chính hãng"
        description="Quản lý nhãn chính hãng thủ công theo ID bài đăng."
      />

      <Alert tone="warning">{STATUS_WARNING}</Alert>

      {notice ? (
        <div role={notice.tone === 'error' ? 'alert' : 'status'}>
          <Alert tone={notice.tone}>{notice.message}</Alert>
        </div>
      ) : null}

      <div className="rounded-lg border border-border bg-white p-5 shadow-sm sm:p-6">
        <div className="max-w-xl">
          <Field
            label="ID bài đăng"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            placeholder="Nhập ID bài đăng"
            value={itemIdInput}
            error={itemIdError}
            disabled={submitting}
            onChange={(event) => {
              setItemIdInput(event.target.value);
              if (itemIdError) setItemIdError('');
            }}
            hint="Sử dụng ID của bài đăng có thật. Hệ thống không tự suy đoán trạng thái xác minh."
          />
        </div>

        <div className="mt-6 grid gap-3 md:grid-cols-3">
          <Button
            type="button"
            icon="shield"
            disabled={submitting}
            onClick={() => openConfirmation('grant')}
          >
            {actionContent.grant.button}
          </Button>
          <Button
            type="button"
            variant="secondary"
            disabled={submitting}
            onClick={() => openConfirmation('reject')}
          >
            {actionContent.reject.button}
          </Button>
          <Button
            type="button"
            variant="danger"
            disabled={submitting}
            onClick={() => openConfirmation('revoke')}
          >
            {actionContent.revoke.button}
          </Button>
        </div>
      </div>

      {selectedAction && itemId !== null ? (
        <div
          className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4"
          role="presentation"
        >
          <div
            className="w-full max-w-lg rounded-lg border border-border bg-white p-5 shadow-xl sm:p-6"
            role="dialog"
            aria-modal="true"
            aria-labelledby="authenticity-dialog-title"
          >
            <div className="flex items-start gap-3">
              <div className="grid size-10 shrink-0 place-items-center rounded-full bg-primary-faint text-primary">
                <Icon name="shield" className="size-5" weight="bold" />
              </div>
              <div>
                <h2 id="authenticity-dialog-title" className="text-lg font-bold text-text-primary">
                  {actionContent[selectedAction].title}
                </h2>
                <p className="mt-1 text-sm leading-6 text-text-secondary">
                  Bài đăng #{itemId}. {actionContent[selectedAction].description}
                </p>
              </div>
            </div>

            <div className="mt-4">
              <Alert tone="warning">{STATUS_WARNING}</Alert>
            </div>

            {notice?.tone === 'error' ? (
              <div className="mt-4" role="alert">
                <Alert tone="error">{notice.message}</Alert>
              </div>
            ) : null}

            {selectedAction !== 'grant' ? (
              <div className="mt-4">
                <TextArea
                  label="Lý do"
                  required
                  autoFocus
                  placeholder="Nhập lý do xử lý nhãn chính hãng"
                  value={reason}
                  error={reasonError}
                  disabled={submitting}
                  onChange={(event) => {
                    setReason(event.target.value);
                    if (reasonError) setReasonError('');
                  }}
                />
              </div>
            ) : null}

            <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button
                type="button"
                variant="outline"
                disabled={submitting}
                onClick={closeConfirmation}
              >
                Hủy
              </Button>
              <Button
                type="button"
                variant={
                  selectedAction === 'revoke'
                    ? 'danger'
                    : selectedAction === 'reject'
                      ? 'secondary'
                      : 'primary'
                }
                disabled={submitting}
                onClick={submitAction}
              >
                {submitting ? 'Đang xử lý…' : actionContent[selectedAction].confirm}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}
