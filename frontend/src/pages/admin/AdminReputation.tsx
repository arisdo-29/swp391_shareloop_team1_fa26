import { useState } from 'react';
import { Dialog } from '../../components/Dialog';
import {
  Alert,
  Button,
  EmptyState,
  Field,
  PageHeader,
  SkeletonRows,
  TextArea,
} from '../../components/ui';
import type { AdminConfigResponse } from '../../services/adminConfigApi';
import { ConfigEditDialog } from './ConfigEditDialog';
import { formatConfigValue } from './configDisplay';
import { DIAMOND_MIN_KEY, GOLD_MIN_KEY, SILVER_MIN_KEY, rankOrderError } from './rankThresholds';
import { useAdminConfigs } from './useAdminConfigs';

const POST_DISPLAY_DAYS_KEY = 'post_display_days';
const POST_DISPLAY_DAYS_GOLD_KEY = 'post_display_days_gold';

interface RankRow {
  name: string;
  minPointsKey: string | null;
  regularFeeLimitKey: string;
  displayDaysKey: string;
}

const RANKS: RankRow[] = [
  {
    name: 'Đồng',
    minPointsKey: null,
    regularFeeLimitKey: 'max_active_items_bronze',
    displayDaysKey: POST_DISPLAY_DAYS_KEY,
  },
  {
    name: 'Bạc',
    minPointsKey: SILVER_MIN_KEY,
    regularFeeLimitKey: 'max_active_items_silver',
    displayDaysKey: POST_DISPLAY_DAYS_KEY,
  },
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
    displayDaysKey: POST_DISPLAY_DAYS_GOLD_KEY,
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

function ConfigValue({ config }: { config: AdminConfigResponse | undefined }) {
  if (!config) return <span className="text-text-muted">—</span>;
  return <span className="font-semibold tabular-nums">{formatConfigValue(config)}</span>;
}

interface RankEditValues {
  minPoints: string;
  regularFeeLimit: string;
  displayDays: string;
}

function integerError(value: string, minimum: number, label: string) {
  if (!/^-?\d+$/.test(value.trim()) || Number(value) < minimum) {
    return `${label} phải là số nguyên ${minimum === 1 ? 'lớn hơn 0' : `từ ${minimum} trở lên`}.`;
  }
  return '';
}

function RankEditDialog({
  rank,
  configs,
  saving,
  error,
  onClose,
  onSave,
}: {
  rank: RankRow;
  configs: AdminConfigResponse[];
  saving: boolean;
  error: string;
  onClose: () => void;
  onSave: (values: RankEditValues, reason: string) => void;
}) {
  const byKey = (key: string) => configs.find((entry) => entry.key === key);
  const initialValues: RankEditValues = {
    minPoints: rank.minPointsKey ? (byKey(rank.minPointsKey)?.value ?? '') : '0',
    regularFeeLimit: byKey(rank.regularFeeLimitKey)?.value ?? '',
    displayDays: byKey(rank.displayDaysKey)?.value ?? '',
  };
  const [values, setValues] = useState(initialValues);
  const [reason, setReason] = useState('');
  const minPointsError = rank.minPointsKey
    ? integerError(values.minPoints, 0, 'Điểm tối thiểu') ||
      rankOrderError(configs, rank.minPointsKey, values.minPoints)
    : '';
  const regularFeeLimitError = integerError(
    values.regularFeeLimit,
    -1,
    'Số bài Trao đổi phí thường',
  );
  const displayDaysError = integerError(values.displayDays, 1, 'Số ngày hiển thị bài');
  const changed =
    values.minPoints.trim() !== initialValues.minPoints ||
    values.regularFeeLimit.trim() !== initialValues.regularFeeLimit ||
    values.displayDays.trim() !== initialValues.displayDays;
  const canSave =
    changed &&
    reason.trim().length > 0 &&
    !minPointsError &&
    !regularFeeLimitError &&
    !displayDaysError;

  const setValue = (key: keyof RankEditValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
  };

  return (
    <Dialog title={`Sửa nội dung hạng ${rank.name}`} busy={saving} onClose={onClose}>
      <div className="space-y-4">
        <Field
          label="Điểm tối thiểu"
          type="number"
          min={0}
          value={values.minPoints}
          disabled={saving || !rank.minPointsKey}
          hint={!rank.minPointsKey ? 'Hạng Đồng luôn bắt đầu từ 0 điểm.' : undefined}
          error={minPointsError || undefined}
          onChange={(event) => setValue('minPoints', event.target.value)}
        />
        <Field
          label="Số bài Trao đổi phí thường"
          type="number"
          min={-1}
          value={values.regularFeeLimit}
          disabled={saving}
          hint="Nhập -1 nếu không giới hạn."
          error={regularFeeLimitError || undefined}
          onChange={(event) => setValue('regularFeeLimit', event.target.value)}
        />
        <Field
          label="Số ngày hiển thị bài"
          type="number"
          min={1}
          value={values.displayDays}
          disabled={saving}
          hint={
            rank.displayDaysKey === POST_DISPLAY_DAYS_KEY
              ? 'Áp dụng chung cho hạng Đồng và Bạc.'
              : 'Áp dụng chung cho hạng Vàng và Kim cương.'
          }
          error={displayDaysError || undefined}
          onChange={(event) => setValue('displayDays', event.target.value)}
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
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" disabled={saving} onClick={onClose}>
          Hủy
        </Button>
        <Button disabled={saving || !canSave} onClick={() => onSave(values, reason.trim())}>
          {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
        </Button>
      </div>
    </Dialog>
  );
}

/** Uy tín và Hạng: cấu hình hạng thành viên và quy tắc cộng điểm (dùng API cấu hình có sẵn). */
export function AdminReputation() {
  const {
    configs,
    loading,
    error,
    reload,
    saving,
    saveError,
    setSaveError,
    clearSaveError,
    update,
  } = useAdminConfigs();
  const [editing, setEditing] = useState<AdminConfigResponse | null>(null);
  const [editingRank, setEditingRank] = useState<RankRow | null>(null);
  const [rankSaving, setRankSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const byKey = (key: string) => configs.find((entry) => entry.key === key);
  const rules = configs.filter(
    (entry) => entry.group === 'REPUTATION' && !RANK_TABLE_KEYS.has(entry.key),
  );

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

  const saveRank = async (values: RankEditValues, reason: string) => {
    if (!editingRank || rankSaving) return;
    if (editingRank.minPointsKey) {
      const orderError = rankOrderError(configs, editingRank.minPointsKey, values.minPoints);
      if (orderError) {
        setSaveError(orderError);
        return;
      }
    }

    const changes = [
      ...(editingRank.minPointsKey
        ? [{ key: editingRank.minPointsKey, value: values.minPoints.trim() }]
        : []),
      { key: editingRank.regularFeeLimitKey, value: values.regularFeeLimit.trim() },
      { key: editingRank.displayDaysKey, value: values.displayDays.trim() },
    ].filter(({ key, value }) => byKey(key)?.value !== value);

    setRankSaving(true);
    setNotice('');
    try {
      for (const change of changes) {
        const updated = await update(change.key, change.value, reason);
        if (!updated) return;
      }
      setEditingRank(null);
      setNotice(`Đã cập nhật hạng "${editingRank.name}".`);
    } finally {
      setRankSaving(false);
    }
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
              Điểm tối thiểu phải tăng dần: Bạc nhỏ hơn Vàng, Vàng nhỏ hơn Kim cương. Bài Trao đổi
              vượt ngưỡng phí thường vẫn được đăng với phí cao hơn.
            </p>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Hạng</th>
                    <th>Điểm tối thiểu</th>
                    <th>Số bài Trao đổi phí thường</th>
                    <th>Số ngày hiển thị bài</th>
                    <th className="whitespace-nowrap">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {RANKS.map((rank) => {
                    return (
                      <tr key={rank.name}>
                        <td className="font-semibold text-text-primary">{rank.name}</td>
                        <td>
                          {rank.minPointsKey ? (
                            <ConfigValue config={byKey(rank.minPointsKey)} />
                          ) : (
                            <span className="font-semibold tabular-nums">0</span>
                          )}
                        </td>
                        <td>
                          <ConfigValue config={byKey(rank.regularFeeLimitKey)} />
                        </td>
                        <td>
                          <ConfigValue config={byKey(rank.displayDaysKey)} />
                        </td>
                        <td>
                          <Button
                            variant="outline"
                            className="!px-2"
                            disabled={saving || rankSaving}
                            aria-label={`Sửa nội dung hạng ${rank.name}`}
                            onClick={() => {
                              clearSaveError();
                              setNotice('');
                              setEditingRank(rank);
                            }}
                          >
                            Sửa nội dung
                          </Button>
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
            <p className="mb-3 text-sm text-text-muted">
              Điểm uy tín quyết định hạng của người dùng.
            </p>
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
                          <p className="mt-0.5 text-xs leading-5 text-text-muted">
                            {rule.description}
                          </p>
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

      {editingRank ? (
        <RankEditDialog
          rank={editingRank}
          configs={configs}
          saving={rankSaving}
          error={saveError}
          onClose={() => setEditingRank(null)}
          onSave={(values, reason) => void saveRank(values, reason)}
        />
      ) : null}
    </div>
  );
}
