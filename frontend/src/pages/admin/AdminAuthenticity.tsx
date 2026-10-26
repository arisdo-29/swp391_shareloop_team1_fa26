import { useRef, useState } from 'react';
import { Alert, Button, Field, Icon, PageHeader, StatusBadge, TextArea } from '../../components/ui';
import {
  adminAuthenticityApi,
  authenticityErrorMessage,
  beginAuthenticitySubmission,
  validateAuthenticityReason,
  type AuthenticityAction,
} from '../../services/adminAuthenticityApi';
import type { ApiError } from '../../services/http';
import { itemApi, type ItemResponse } from '../../services/itemApi';

const STATUS_WARNING =
  'Trạng thái xác minh chưa được cung cấp bởi Backend. Vui lòng kiểm tra bài đăng trước khi thao tác. Backend sẽ xác thực điều kiện thực hiện.';

const actionContent: Record<
  AuthenticityAction,
  { button: string; confirm: string; title: string; description: string; success: string }
> = {
  grant: {
    button: 'Xác nhận chính hãng',
    confirm: 'Xác nhận chính hãng',
    title: 'Xác nhận hàng chính hãng',
    description: 'Backend chỉ chấp nhận thao tác khi bài đăng đang ở trạng thái PENDING.',
    success: 'Đã xác nhận hàng chính hãng thành công.',
  },
  reject: {
    button: 'Từ chối xác minh',
    confirm: 'Xác nhận từ chối',
    title: 'Từ chối xác minh',
    description: 'Backend chỉ chấp nhận thao tác khi bài đăng đang ở trạng thái PENDING.',
    success: 'Đã từ chối xác minh thành công.',
  },
  revoke: {
    button: 'Thu hồi xác minh',
    confirm: 'Xác nhận thu hồi',
    title: 'Thu hồi xác minh',
    description: 'Backend chỉ chấp nhận thao tác khi bài đăng đang ở trạng thái VERIFIED.',
    success: 'Đã thu hồi xác minh thành công.',
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
  const [item, setItem] = useState<ItemResponse | null>(null);
  const [itemLoading, setItemLoading] = useState(false);
  const [itemError, setItemError] = useState('');
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);
  const submissionLock = useRef(false);

  const itemId = parseItemId(itemIdInput);

  const openConfirmation = (action: AuthenticityAction) => {
    if (itemId === null || item?.id !== itemId) {
      setItemIdError('Vui lòng tải bài đăng hợp lệ trước khi thao tác.');
      return;
    }
    setItemIdError('');
    setReason('');
    setReasonError('');
    setNotice(null);
    setSelectedAction(action);
  };

  const loadItem = async () => {
    if (itemId === null || itemLoading) {
      setItemIdError('Vui lòng nhập ID bài đăng là số nguyên dương.');
      return;
    }
    setItemLoading(true);
    setItemError('');
    setItemIdError('');
    setItem(null);
    setNotice(null);
    try {
      setItem(await itemApi.detail(itemId));
    } catch (caught) {
      const apiError = caught as Partial<ApiError>;
      setItemError(
        apiError.status === 404
          ? 'Không tìm thấy bài đăng.'
          : apiError.message || 'Không tải được bài đăng. Vui lòng thử lại.',
      );
    } finally {
      setItemLoading(false);
    }
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
      setItem(null);
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
        title="Xác minh hàng chính hãng"
        description="Kiểm tra và xác minh tính chính hãng của món đồ."
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
              setItem(null);
              setItemError('');
              setNotice(null);
              if (itemIdError) setItemIdError('');
            }}
            hint="Tải bài đăng từ Backend để kiểm tra ngữ cảnh trước khi thao tác."
          />
          <Button
            className="mt-3"
            type="button"
            variant="outline"
            disabled={itemLoading || itemId === null}
            onClick={() => void loadItem()}
          >
            {itemLoading ? 'Đang tải bài…' : 'Tải thông tin bài'}
          </Button>
        </div>

        {itemError ? (
          <div className="mt-4" role="alert">
            <Alert tone="error">{itemError}</Alert>
          </div>
        ) : null}

        {item ? (
          <div className="mt-5 rounded-lg bg-surface-low p-4" role="status">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-bold text-text-primary">{item.title}</p>
                <p className="mt-1 text-xs text-text-muted">
                  {item.offerType === 'GIVE' ? 'Cho tặng' : 'Trao đổi'}
                </p>
              </div>
              <StatusBadge status={item.status} />
            </div>
            <p className="mt-3 text-xs leading-5 text-text-muted">
              Backend chỉ cung cấp trạng thái bài đăng ở API này, chưa cung cấp trạng thái hoặc
              chứng từ xác minh chính hãng.
            </p>
          </div>
        ) : null}

        <div className="mt-6 grid gap-3 md:grid-cols-3">
          <Button
            type="button"
            icon="shield"
            disabled={submitting || !item}
            onClick={() => openConfirmation('grant')}
          >
            {actionContent.grant.button}
          </Button>
          <Button
            type="button"
            variant="secondary"
            disabled={submitting || !item}
            onClick={() => openConfirmation('reject')}
          >
            {actionContent.reject.button}
          </Button>
          <Button
            type="button"
            variant="danger"
            disabled={submitting || !item}
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
            className="w-full max-w-lg rounded-lg border border-border bg-white p-5 shadow-lg sm:p-6"
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
                  {actionContent[selectedAction].description}
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
                  placeholder="Nhập lý do từ chối hoặc thu hồi xác minh"
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
