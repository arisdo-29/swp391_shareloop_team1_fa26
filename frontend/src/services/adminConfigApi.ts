import { http } from './http';

export interface AdminConfigResponse {
  key: string;
  value: string;
  defaultValue: string;
}

export const adminConfigApi = {
  async list() {
    const { data } = await http.get<AdminConfigResponse[]>('/api/v1/admin/configs');
    return data;
  },
};
