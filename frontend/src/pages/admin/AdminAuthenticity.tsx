import { useRef, useState } from 'react';
import { Alert, Button, Field, PageHeader, Select, TextArea } from '../../components/ui';
import {
  adminAuthenticityApi,
  type AuthenticityAction,
} from '../../services/adminAuthenticityApi';
import type { ApiError } from '../../services/http';

const actionLabels: Record<AuthenticityAction, string> = {
  grant: 'Cấp nhãn chính hãng',
  reject: 'Từ chối cấp nhãn',
  revoke: 'Thu hồi nhãn',
};

function authenticityErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const messages: Record<string, string> = {
    VALIDATION_FAILED: 'Lý do xử lý chưa hợp lệ.',
    AUTHENTICITY_REASON_REQUIRED: 'Vui lòng nhập lý do xử lý.',
    AUTHENTICITY_ITEM_NOT_FOUND: 'Không tìm thấy bài đăng.',
    AUTHENTICITY_INVALID_STATUS: 'Bài đăng không ở trạng thái chờ xét duyệt nhãn.',
    AUTHENTICITY_NOT_VERIFIED: 'Bài đăng chưa được cấp nhãn chính hãng.',
  };
  if (apiError.code && messages[apiError.code]) return messages[apiError.code];
  if (apiError.status === 401) return 'Phiên đăng nhập đã hết hạn.';
  if (apiError.status === 403) return 'Bạn không có quyền quản trị nhãn chính hãng.';
  return apiError.message || 'Không xử lý được nhãn chính hãng. Vui lòng thử lại.';
}

export function AdminAuthenticity() {
  const [itemId, setItemId] = useState('');
  const [action, setAction] = useState<AuthenticityAction>('grant');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const inFlight = useRef(false);
  const numericItemId = Number(itemId);
  const validItemId = Number.isSafeInteger(numericItemId) && numericItemId > 0;
  const needsReason = action !== 'grant';

  const submit = async () => {
    const normalizedReason = reason.trim();
    if (!validItemId || (needsReason && !normalizedReason) || inFlight.current) return;
    if (!window.confirm(`${actionLabels[action]} cho bài #${numericItemId}?`)) return;
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    setNotice('');
    try {
      if (action === 'grant') await adminAuthenticityApi.grant(numericItemId);
      else await adminAuthenticityApi[action](numericItemId, normalizedReason);
      setNotice(`${actionLabels[action]} cho bài #${numericItemId} thành công.`);
      setReason('');
    } catch (caught) {
      setError(authenticityErrorMessage(caught));
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <section>
      <PageHeader
        title="Nhãn chính hãng"
        description="Cấp, từ chối hoặc thu hồi nhãn sau khi đối chiếu chứng từ của bài đăng."
      />
      <div className="max-w-2xl space-y-4 rounded-xl bg-white p-5 ring-1 ring-border/80 sm:p-6">
        <Alert>
          Backend chưa cung cấp danh sách bài đang chờ đối chiếu chứng từ. Hãy nhập mã bài từ hồ sơ kiểm duyệt liên quan.
        </Alert>
        <Field
          label="Mã bài đăng"
          type="number"
          min="1"
          value={itemId}
          disabled={submitting}
          onChange={(event) => {
            setItemId(event.target.value);
            setNotice('');
          }}
        />
        <Select
          label="Thao tác"
          value={action}
          disabled={submitting}
          onChange={(event) => {
            setAction(event.target.value as AuthenticityAction);
            setError('');
            setNotice('');
          }}
        >
          <option value="grant">Cấp nhãn chính hãng</option>
          <option value="reject">Từ chối cấp nhãn</option>
          <option value="revoke">Thu hồi nhãn</option>
        </Select>
        {needsReason ? (
          <TextArea
            label="Lý do"
            value={reason}
            disabled={submitting}
            onChange={(event) => setReason(event.target.value)}
          />
        ) : null}
        {error ? <Alert tone="error">{error}</Alert> : null}
        {notice ? <Alert tone="success">{notice}</Alert> : null}
        <Button
          disabled={submitting || !validItemId || (needsReason && !reason.trim())}
          onClick={() => void submit()}
        >
          {submitting ? 'Đang xử lý...' : actionLabels[action]}
        </Button>
      </div>
    </section>
  );
}
