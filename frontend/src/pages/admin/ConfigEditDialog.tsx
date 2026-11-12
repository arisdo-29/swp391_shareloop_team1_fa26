import './admin-settings.css';
import { ConfigUnit } from './AdminSettingsParts';
import { useState } from 'react';
import { Dialog } from '../../components/Dialog';
import { Alert, Button, Field, TextArea } from '../../components/ui';
import type { AdminConfigResponse } from '../../services/adminConfigApi';
import { formatConfigValue } from './configDisplay';

/** Hộp thoại sửa một cấu hình: hiện tên và mô tả tiếng Việt, bắt buộc nhập lý do để lưu vào nhật ký. */
export function ConfigEditDialog({
  config,
  saving,
  error,
  onClose,
  onSave,
}: {
  config: AdminConfigResponse;
  saving: boolean;
  error: string;
  onClose: () => void;
  onSave: (value: string, reason: string) => void;
}) {
  const [value, setValue] = useState(config.value);
  const [reason, setReason] = useState('');
  const canSave = value.trim().length > 0 && value.trim() !== config.value && reason.trim().length > 0;
  const unlimitedHint = config.key.startsWith('max_active_items') ? 'Nhập -1 nếu không giới hạn.' : undefined;

  return (
    <Dialog title={config.label} busy={saving} onClose={onClose}>
      <div className="settings-config-context"><p className="text-sm leading-6 text-text-secondary">{config.description}</p><dl><div><dt>Giá trị hiện tại</dt><dd><span>{formatConfigValue(config)}</span><ConfigUnit configKey={config.key} /></dd></div></dl></div>
      <p className="mt-2 text-xs text-text-muted">
        Giá trị mặc định: {formatConfigValue(config, config.defaultValue)} <ConfigUnit configKey={config.key} />
      </p>
      <div className="settings-dialog-content">
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
      {error ? (
        <div className="mt-4" role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : null}
      <div className="settings-form-actions">
        <Button variant="outline" disabled={saving} onClick={onClose}>
          Hủy
        </Button>
        <Button disabled={!canSave || saving} onClick={() => onSave(value.trim(), reason.trim())}>
          {saving ? 'Đang lưu...' : 'Xác nhận cập nhật'}
        </Button>
      </div>
    </Dialog>
  );
}
