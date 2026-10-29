import { useState } from 'react';
import { Alert, Button, EmptyState, PageHeader, SkeletonRows } from '../../components/ui';
import type { AdminConfigResponse } from '../../services/adminConfigApi';
import { ConfigEditDialog } from './ConfigEditDialog';
import { formatConfigValue } from './configDisplay';
import {
  DIAMOND_MIN_KEY,
  GOLD_MIN_KEY,
  SILVER_MIN_KEY,
  rankOrderError,
} from './rankThresholds';
import { useAdminConfigs } from './useAdminConfigs';

const POST_DISPLAY_DAYS_KEY = 'post_display_days';
const POST_DISPLAY_DAYS_GOLD_KEY = 'post_display_days_gold';

interface RankRow {
  name: string;
  minPointsKey: string | null;
  regularFeeLimitKey: string;
  displayDaysKey: string | null;
}

const RANKS: RankRow[] = [
  { name: 'Đồng', minPointsKey: null, regularFeeLimitKey: 'max_active_items_bronze', displayDaysKey: null },
  { name: 'Bạc', minPointsKey: SILVER_MIN_KEY, regularFeeLimitKey: 'max_active_items_silver', displayDaysKey: null },
  {
    name: 'Vàng',
    minPointsKey: GOLD_MIN_KEY,
    regularFeeLimitKey: 'max_active_items_gold',
    displayDaysKey: POST_DISPLAY_DAYS_GOLD_KEY,
  },
  {
    name: 'Kim cương',
    minPointsKey: DIAMOND_MIN_KEY,
    regularFeeLimitKey: 'max_active_items_diamond',
    displayDaysKey: null,
  },
];

// Các key đã nằm trong bảng hạng nên không lặp lại ở bảng quy tắc cộng điểm.
const RANK_TABLE_KEYS = new Set(
  RANKS.flatMap((rank) => [rank.minPointsKey, rank.regularFeeLimitKey, rank.displayDaysKey]).filter(
    (key): key is string => key !== null,
  ),
);

function EditableValue({
  config,
  onEdit,
}: {
  config: AdminConfigResponse | undefined;
  onEdit: (config: AdminConfigResponse) => void;
}) {
  if (!config) return <span className="text-text-muted">—</span>;
  return (
    <span className="inline-flex items-center gap-2">
      <span className="font-semibold tabular-nums">{formatConfigValue(config)}</span>
      <Button
        variant="ghost"
        className="!min-h-7 !px-2"
        aria-label={`Sửa ${config.label}`}
        onClick={() => onEdit(config)}
      >
        Sửa
      </Button>
    </span>
  );
}

/** Uy tín và Hạng: cấu hình hạng thành viên và quy tắc cộng điểm (dùng API cấu hình có sẵn). */
export function AdminReputation() {
  const { configs, loading, error, reload, saving, saveError, setSaveError, clearSaveError, update } =
    useAdminConfigs();
  const [editing, setEditing] = useState<AdminConfigResponse | null>(null);
  const [notice, setNotice] = useState('');
  const byKey = (key: string) => configs.find((entry) => entry.key === key);
  const rules = configs.filter((entry) => entry.group === 'REPUTATION' && !RANK_TABLE_KEYS.has(entry.key));

  const openEditor = (config: AdminConfigResponse) => {
    clearSaveError();
    setNotice('');
    setEditing(config);
  };

  const save = async (value: string, reason: string) => {
    if (!editing) return;
    // Báo lỗi thứ tự điểm các hạng ngay trên trang, Backend vẫn kiểm tra lại khi lưu.
    const orderError = rankOrderError(configs, editing.key, value);
    if (orderError) {
      setSaveError(orderError);
      return;
    }
    const updated = await update(editing.key, value, reason);
    if (!updated) return;
    setEditing(null);
    setNotice(`Đã cập nhật "${updated.label}".`);
  };

  return (
    <div className="space-y-8">
      <PageHeader
        title="Uy tín & Hạng"
        description="Cấu hình hạng thành viên, quy tắc cộng điểm uy tín và hỗ trợ khôi phục uy tín cho tài khoản đủ điều kiện."
      />

      {notice ? (
        <div role="status">
          <Alert tone="success">{notice}</Alert>
        </div>
      ) : null}

      {loading ? (
        <SkeletonRows label="Đang tải cấu hình" />
      ) : error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="outline" onClick={reload}>
            Thử lại
          </Button>
        </div>
      ) : (
        <>
          <section>
            <h2 className="mb-1 section-title">Hạng thành viên</h2>
            <p className="mb-3 text-sm text-text-muted">
              Điểm tối thiểu phải tăng dần: Bạc nhỏ hơn Vàng, Vàng nhỏ hơn Kim cương. Bài Trao đổi vượt ngưỡng phí thường vẫn được đăng với phí cao hơn.
            </p>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Hạng</th>
                    <th>Điểm tối thiểu</th>
                    <th>Số bài Trao đổi phí thường</th>
                    <th>Số ngày hiển thị bài</th>
                  </tr>
                </thead>
                <tbody>
                  {RANKS.map((rank) => {
                    const defaultDays = byKey(POST_DISPLAY_DAYS_KEY);
                    return (
                      <tr key={rank.name}>
                        <td className="font-semibold text-text-primary">{rank.name}</td>
                        <td>
                          {rank.minPointsKey ? (
                            <EditableValue config={byKey(rank.minPointsKey)} onEdit={openEditor} />
                          ) : (
                            <span className="font-semibold tabular-nums">0</span>
                          )}
                        </td>
                        <td>
                          <EditableValue config={byKey(rank.regularFeeLimitKey)} onEdit={openEditor} />
                        </td>
                        <td>
                          {rank.displayDaysKey ? (
                            <EditableValue config={byKey(rank.displayDaysKey)} onEdit={openEditor} />
                          ) : defaultDays ? (
                            <span className="tabular-nums text-text-secondary">{defaultDays.value} ngày</span>
                          ) : (
                            '—'
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>

          <section>
            <h2 className="mb-1 section-title">Quy tắc cộng điểm</h2>
            <p className="mb-3 text-sm text-text-muted">Điểm uy tín quyết định hạng của người dùng.</p>
            {rules.length ? (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Quy tắc</th>
                      <th>Giá trị</th>
                      <th>Mặc định</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rules.map((rule) => (
                      <tr key={rule.key}>
                        <td className="max-w-md">
                          <p className="font-semibold text-text-primary">{rule.label}</p>
                          <p className="mt-0.5 text-xs leading-5 text-text-muted">{rule.description}</p>
                        </td>
                        <td>
                          <EditableValue config={rule} onEdit={openEditor} />
                        </td>
                        <td className="tabular-nums text-text-muted">{rule.defaultValue}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <EmptyState title="Chưa có quy tắc" text="Hiện chưa có quy tắc cộng điểm nào." />
            )}
          </section>
        </>
      )}

      {editing ? (
        <ConfigEditDialog
          config={editing}
          saving={saving}
          error={saveError}
          onClose={() => setEditing(null)}
          onSave={(value, reason) => void save(value, reason)}
        />
      ) : null}
    </div>
  );
}
