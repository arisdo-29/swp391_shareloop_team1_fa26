import { useEffect, useState } from 'react';
import { Dialog } from '../../components/Dialog';
import { Alert, Button, Field, TextArea } from '../../components/ui';
import type {
  AdminConfigResponse,
  BatchUpdateAdminConfigRequest,
  ConfigChangeItem,
  ConfigChangePreview,
  ConfigPreviewResponse,
  ConfigRuleMessage,
} from '../../services/adminConfigApi';
import { ConfigRuleAlerts } from './ConfigRuleAlerts';
import { formatConfigValue } from './configDisplay';
import type { BatchSaveResult, PreviewOutcome } from './useAdminConfigs';

const PREVIEW_DELAY_MS = 400;
const INTEGER_PATTERN = /^-?\d+$/;

type PreviewState =
  | { status: 'idle' }
  | { status: 'checking' }
  | { status: 'ready'; preview: ConfigPreviewResponse }
  | { status: 'error'; message: string };

/** Lỗi hoặc cảnh báo Backend trả về sau khi bấm lưu (ví dụ khi tắt đồng bộ), thay cho kết quả xem trước. */
interface SaveFeedback {
  errors: ConfigRuleMessage[];
  warnings: ConfigRuleMessage[];
}

function syncedChanges(preview: ConfigPreviewResponse | null): ConfigChangePreview[] {
  return preview ? preview.changes.filter((change) => change.causedByKey !== null) : [];
}

/**
 * Hộp thoại sửa một cấu hình: hiện tên và mô tả tiếng Việt, bắt buộc nhập lý do để lưu vào nhật ký. Với khoá số, tự xem
 * trước các cấu hình sẽ đổi kèm, lỗi và cảnh báo của Backend rồi lưu tất cả trong một lần.
 */
export function ConfigEditDialog({
  config,
  saving,
  error,
  onClose,
  onPreview,
  onCancelPreview,
  onSave,
}: {
  config: AdminConfigResponse;
  saving: boolean;
  error: string;
  onClose: () => void;
  onPreview: (key: string, value: string) => Promise<PreviewOutcome>;
  onCancelPreview: () => void;
  onSave: (request: BatchUpdateAdminConfigRequest) => Promise<BatchSaveResult>;
}) {
  const [value, setValue] = useState(config.value);
  const [reason, setReason] = useState('');
  const [syncEnabled, setSyncEnabled] = useState(true);
  const [acknowledged, setAcknowledged] = useState(false);
  const [previewState, setPreviewState] = useState<PreviewState>({ status: 'idle' });
  const [feedback, setFeedback] = useState<SaveFeedback | null>(null);
  const [retryCount, setRetryCount] = useState(0);

  const trimmedValue = value.trim();
  const valueChanged = trimmedValue.length > 0 && trimmedValue !== config.value;
  // Khoá số (mặc định là số nguyên) mới có luật liên khoá; khoá khác (danh sách mã, số thập phân) lưu thẳng.
  const shouldPreview =
    INTEGER_PATTERN.test(config.defaultValue) && INTEGER_PATTERN.test(trimmedValue) && valueChanged;
  const unlimitedHint = config.key.startsWith('max_active_items') ? 'Nhập -1 nếu không giới hạn.' : undefined;

  // Debounce: chờ Admin ngừng gõ rồi mới gọi. Dọn effect thì huỷ cả lần gọi đang chạy, để kết quả của giá trị cũ không
  // hiện lên khi ô nhập đã đổi.
  useEffect(() => {
    setFeedback(null);
    setAcknowledged(false);
    if (!shouldPreview) {
      setPreviewState({ status: 'idle' });
      return;
    }
    setPreviewState({ status: 'checking' });
    const timer = window.setTimeout(() => {
      void onPreview(config.key, trimmedValue).then((outcome) => {
        if (outcome.status === 'stale') return;
        setPreviewState(
          outcome.status === 'ok'
            ? { status: 'ready', preview: outcome.preview }
            : { status: 'error', message: outcome.message },
        );
      });
    }, PREVIEW_DELAY_MS);
    return () => {
      window.clearTimeout(timer);
      onCancelPreview();
    };
  }, [config.key, trimmedValue, shouldPreview, retryCount, onPreview, onCancelPreview]);

  const preview = previewState.status === 'ready' ? previewState.preview : null;
  const synced = syncedChanges(preview);
  // Tắt đồng bộ thì kết quả xem trước (tính khi bật đồng bộ) không còn đúng, nên chỉ hiện lỗi và cảnh báo Backend trả
  // về sau khi bấm lưu.
  const activeErrors = feedback?.errors ?? (syncEnabled && preview ? preview.errors : []);
  const activeWarnings = feedback?.warnings ?? (syncEnabled && preview ? preview.warnings : []);
  const previewPending = shouldPreview && (previewState.status === 'checking' || previewState.status === 'error');
  const canSave =
    valueChanged &&
    reason.trim().length > 0 &&
    !previewPending &&
    activeErrors.length === 0 &&
    (activeWarnings.length === 0 || acknowledged);

  const toggleSync = (enabled: boolean) => {
    setSyncEnabled(enabled);
    setFeedback(null);
    setAcknowledged(false);
  };

  const buildChanges = (): ConfigChangeItem[] => {
    const changes = [{ key: config.key, value: trimmedValue }];
    if (syncEnabled) return changes;
    // Tắt đồng bộ: gửi kèm các khoá liên quan với giá trị hiện tại để Backend không tự ghi đè chúng.
    return [...changes, ...synced.map((change) => ({ key: change.key, value: change.oldValue }))];
  };

  const save = async () => {
    const result = await onSave({
      changes: buildChanges(),
      reason: reason.trim(),
      acknowledgedWarnings: acknowledged,
    });
    if (result.ok) return;
    if (result.errors.length > 0 || result.preview) {
      setFeedback({ errors: result.errors, warnings: result.preview?.warnings ?? [] });
    }
  };

  return (
    <Dialog title={config.label} busy={saving} onClose={onClose}>
      <p className="text-sm leading-6 text-text-secondary">{config.description}</p>
      <p className="mt-2 text-xs text-text-muted">
        Giá trị mặc định: {formatConfigValue(config, config.defaultValue)}
      </p>
      <div className="mt-4 space-y-4">
        <Field
          label="Giá trị mới"
          value={value}
          hint={unlimitedHint}
          disabled={saving}
          onChange={(event) => setValue(event.target.value)}
        />
        <TextArea
          label="Lý do thay đổi"
          value={reason}
          maxLength={500}
          placeholder="Mô tả lý do cần cập nhật cấu hình..."
          disabled={saving}
          onChange={(event) => setReason(event.target.value)}
        />
      </div>
      {previewState.status === 'checking' ? (
        <p className="mt-4 text-sm text-text-muted" role="status">
          Đang kiểm tra...
        </p>
      ) : null}
      {previewState.status === 'error' && previewState.message ? (
        <div className="mt-4 space-y-2" role="alert">
          <Alert tone="error">{previewState.message}</Alert>
          <Button variant="outline" onClick={() => setRetryCount((count) => count + 1)}>
            Kiểm tra lại
          </Button>
        </div>
      ) : null}
      {synced.length > 0 ? (
        <div className="mt-4 rounded-md bg-surface-low p-4">
          <h3 className="text-sm font-semibold text-text-primary">Sẽ cập nhật kèm</h3>
          <ul className="mt-2 space-y-2 text-sm text-text-secondary">
            {synced.map((change) => (
              <li key={change.key}>
                <span className="font-medium text-text-primary">
                  {change.label}: {change.oldValue} → {change.newValue}
                </span>
                {change.note ? <span className="block text-xs text-text-muted">{change.note}</span> : null}
              </li>
            ))}
          </ul>
          <label className="mt-3 flex items-center gap-2 text-sm font-medium text-text-primary">
            <input
              type="checkbox"
              checked={syncEnabled}
              disabled={saving}
              onChange={(event) => toggleSync(event.target.checked)}
            />
            Đồng bộ các cấu hình liên quan
          </label>
        </div>
      ) : null}
      <ConfigRuleAlerts
        errors={activeErrors}
        warnings={activeWarnings}
        acknowledged={acknowledged}
        disabled={saving}
        onAcknowledgedChange={setAcknowledged}
      />
      {error ? (
        <div className="mt-4" role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : null}
      <p className="mt-4 text-xs leading-5 text-text-muted">
        Thay đổi áp dụng cho lượt mua/duyệt mới, không ảnh hưởng đợt đang chạy.
      </p>
      <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variant="outline" disabled={saving} onClick={onClose}>
          Hủy
        </Button>
        <Button disabled={!canSave || saving} onClick={() => void save()}>
          {saving ? 'Đang lưu...' : 'Xác nhận cập nhật'}
        </Button>
      </div>
    </Dialog>
  );
}
