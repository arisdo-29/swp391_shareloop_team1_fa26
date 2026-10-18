import { http } from './http';

export interface AdminConfigResponse {
  key: string;
  value: string;
  defaultValue: string;
}

export interface UpdateAdminConfigRequest {
  value: string;
  reason: string;
}

export interface BulkUpdateAdminConfigRequest extends UpdateAdminConfigRequest {
  key: string;
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
};
