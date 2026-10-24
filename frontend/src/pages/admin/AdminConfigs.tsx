import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, EmptyState, PageHeader } from '../../components/ui';
import type { AdminConfigResponse } from '../../services/adminConfigApi';
import { ConfigEditDialog } from './ConfigEditDialog';
import { formatConfigValue } from './configDisplay';
import { useAdminConfigs } from './useAdminConfigs';

interface ConfigSection {
  group: string;
  label: string;
  configs: AdminConfigResponse[];
}

/** Gom cấu hình theo nhóm, giữ thứ tự nhóm như Backend trả về. Nhóm uy tín nằm ở trang Uy tín và Hạng nên bỏ qua. */
function groupConfigs(configs: AdminConfigResponse[]): ConfigSection[] {
  const sections: ConfigSection[] = [];
  for (const config of configs) {
    if (config.group === 'REPUTATION') continue;
    let section = sections.find((entry) => entry.group === config.group);
    if (!section) {
      section = { group: config.group, label: config.groupLabel, configs: [] };
      sections.push(section);
    }
    section.configs.push(config);
  }
  return sections;
}

/** Cấu hình hệ thống: bảng theo nhóm, mỗi dòng có tên và mô tả tiếng Việt, sửa kèm lý do. */
export function AdminConfigs() {
  const { configs, loading, error, reload, saving, saveError, clearSaveError, update, updateByBody } = useAdminConfigs();
  const [editing, setEditing] = useState<AdminConfigResponse | null>(null);
  const [notice, setNotice] = useState('');
  const sections = groupConfigs(configs);

  const save = async (value: string, reason: string) => {
    if (!editing) return;
    const updated = editing.key === 'chat_ai_threshold'
      ? await updateByBody(editing.key, value, reason)
      : await update(editing.key, value, reason);
    if (!updated) return;
    setEditing(null);
    setNotice(`Đã cập nhật "${updated.label}".`);
  };

  return (
    <>
      <PageHeader
        title="Cấu hình hệ thống"
        description="Xem và cập nhật các thiết lập vận hành. Mỗi thay đổi cần nhập lý do và được lưu vào nhật ký."
      />
      <p className="mb-5 rounded-md bg-primary-faint p-4 text-sm leading-6 text-text-secondary">
        Hạng thành viên và quy tắc điểm uy tín được cấu hình ở trang{' '}
        <Link className="font-semibold text-primary underline" to="/admin/reputation">
          Uy tín & Hạng
        </Link>
        .
      </p>

      {notice ? (
        <div className="mb-4" role="status">
          <Alert tone="success">{notice}</Alert>
        </div>
      ) : null}

      {loading ? (
        <EmptyState title="Đang tải cấu hình" text="Vui lòng chờ trong giây lát." />
      ) : error ? (
        <div className="space-y-3">
          <Alert tone="error">{error}</Alert>
          <Button size="sm" variant="outline" onClick={reload}>
            Thử lại
          </Button>
        </div>
      ) : sections.length ? (
        <div className="space-y-6">
          {sections.map((section) => (
            <section key={section.group}>
              <h2 className="mb-3 section-title">{section.label}</h2>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Tên cấu hình</th>
                      <th>Giá trị hiện tại</th>
                      <th>Mặc định</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {section.configs.map((config) => (
                      <tr key={config.key}>
                        <td className="max-w-md">
                          <p className="font-semibold text-text-primary">{config.label}</p>
                          <p className="mt-0.5 text-xs leading-5 text-text-muted">{config.description}</p>
                        </td>
                        <td className="font-semibold tabular-nums">{formatConfigValue(config)}</td>
                        <td className="tabular-nums text-text-muted">
                          {formatConfigValue(config, config.defaultValue)}
                        </td>
                        <td className="text-right">
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => {
                              clearSaveError();
                              setNotice('');
                              setEditing(config);
                            }}
                          >
                            Chỉnh sửa
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          ))}
        </div>
      ) : (
        <EmptyState title="Chưa có cấu hình" text="Hiện chưa có cấu hình hệ thống nào." />
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
    </>
  );
}
