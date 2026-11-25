import { http } from './http';

// Contract: docs/api/setting.md. label, description, group và groupLabel lấy từ enum ConfigKey của Backend.
export type AdminConfigGroup =
  | 'FEES'
  | 'POSTING'
  | 'AI'
  | 'MODERATION'
  | 'ACCOUNT'
  | 'TRANSACTION'
  | 'REPUTATION';

export interface AdminConfigResponse {
  key: string;
  value: string;
  defaultValue: string;
  label: string;
  description: string;
  group: AdminConfigGroup;
  groupLabel: string;
}

export interface UpdateAdminConfigRequest {
  value: string;
  reason: string;
}

export interface BulkUpdateAdminConfigRequest extends UpdateAdminConfigRequest {
  key: string;
}

/** Một cấu hình sẽ đổi. Cấu hình đồng bộ có causedByKey, causedByLabel và note, cấu hình Admin tự gửi thì null. */
export interface ConfigChangePreview {
  key: string;
  label: string;
  oldValue: string;
  newValue: string;
  causedByKey: string | null;
  causedByLabel: string | null;
  note: string | null;
}

/** Lỗi hoặc cảnh báo của bộ luật cấu hình. message đã là tiếng Việt, keys là các khoá liên quan, errorCode null với cảnh báo. */
export interface ConfigRuleMessage {
  message: string;
  keys: string[];
  errorCode: string | null;
}

export interface ConfigPreviewResponse {
  changes: ConfigChangePreview[];
  errors: ConfigRuleMessage[];
  warnings: ConfigRuleMessage[];
}

export interface ConfigChangeItem {
  key: string;
  value: string;
}

export interface BatchUpdateAdminConfigRequest {
  changes: ConfigChangeItem[];
  reason: string;
  acknowledgedWarnings: boolean;
}

export interface BatchUpdateAdminConfigResponse {
  configs: AdminConfigResponse[];
}

export const adminConfigApi = {
  async list() {
    const { data } = await http.get<AdminConfigResponse[]>('/api/v1/admin/configs');
    return data;
  },
  async update(key: string, input: UpdateAdminConfigRequest) {
    const { data } = await http.put<AdminConfigResponse>(
      `/api/v1/admin/configs/${encodeURIComponent(key)}`,
      input,
    );
    return data;
  },
  async updateByBody(input: BulkUpdateAdminConfigRequest) {
    const { data } = await http.put<AdminConfigResponse>('/api/v1/admin/configs', input);
    return data;
  },
  /** Xem trước một thay đổi, không ghi gì. signal để huỷ lần gọi cũ khi Admin gõ tiếp. */
  async preview(key: string, value: string, signal?: AbortSignal) {
    const { data } = await http.post<ConfigPreviewResponse>(
      '/api/v1/admin/configs/preview',
      { key, value },
      { signal },
    );
    return data;
  },
  /** Lưu cả lô hoặc không lưu gì, kèm các cấu hình đồng bộ. */
  async updateBatch(input: BatchUpdateAdminConfigRequest) {
    const { data } = await http.put<BatchUpdateAdminConfigResponse>(
      '/api/v1/admin/configs/batch',
      input,
    );
    return data;
  },
};
