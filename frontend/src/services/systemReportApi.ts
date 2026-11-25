import { http } from './http';

export type SystemReportStatus = 'OPEN' | 'RESOLVED';

export interface SystemReport {
  id: number;
  title: string;
  description: string;
  status: SystemReportStatus;
  createdAt: string;
  resolution: string | null;
  resolvedAt: string | null;
  reporterName: string | null;
}

interface SystemReportPage {
  items: SystemReport[];
  page: number;
  totalPages: number;
  totalElements: number;
}

const mine = '/api/v1/me/system-reports';
const admin = '/api/v1/admin/system-reports';

export const systemReportApi = {
  async submit(title: string, description: string) {
    return (await http.post<SystemReport>(mine, { title, description })).data;
  },
  async listMine(page: number, size: number) {
    return (await http.get<SystemReportPage>(mine, { params: { page, size } })).data;
  },
  async detailMine(id: string) {
    return (await http.get<SystemReport>(`${mine}/${id}`)).data;
  },
  async listAdmin(page: number, size: number, status: SystemReportStatus | '') {
    return (
      await http.get<SystemReportPage>(admin, {
        params: { page, size, status: status || undefined },
      })
    ).data;
  },
  async detailAdmin(id: string) {
    return (await http.get<SystemReport>(`${admin}/${id}`)).data;
  },
  async resolve(id: number, resolution: string) {
    return (await http.post<SystemReport>(`${admin}/${id}/resolve`, { resolution })).data;
  },
};
