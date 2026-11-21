import { Alert } from '../../components/ui';
import type { ConfigRuleMessage } from '../../services/adminConfigApi';

/**
 * Lỗi (Alert đỏ, khoá nút lưu ở nơi gọi) và cảnh báo (Alert vàng, phải tích "Tôi đã hiểu cảnh báo" mới lưu được)
 * của bộ luật cấu hình. Câu chữ do Backend trả về đã là tiếng Việt và không lộ khoá kỹ thuật.
 */
export function ConfigRuleAlerts({
  errors,
  warnings,
  acknowledged,
  disabled,
  onAcknowledgedChange,
}: {
  errors: ConfigRuleMessage[];
  warnings: ConfigRuleMessage[];
  acknowledged: boolean;
  disabled: boolean;
  onAcknowledgedChange: (acknowledged: boolean) => void;
}) {
  return (
    <>
      {errors.length > 0 ? (
        <div className="mt-4 space-y-2" role="alert">
          {errors.map((item) => (
            <Alert key={item.message} tone="error">
              {item.message}
            </Alert>
          ))}
        </div>
      ) : null}
      {warnings.length > 0 ? (
        <div className="mt-4 space-y-2">
          {warnings.map((item) => (
            <Alert key={item.message} tone="warning">
              {item.message}
            </Alert>
          ))}
          <label className="flex items-center gap-2 text-sm font-medium text-text-primary">
            <input
              type="checkbox"
              checked={acknowledged}
              disabled={disabled}
              onChange={(event) => onAcknowledgedChange(event.target.checked)}
            />
            Tôi đã hiểu cảnh báo
          </label>
        </div>
      ) : null}
    </>
  );
}
