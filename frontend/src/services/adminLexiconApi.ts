import { http } from './http';

export interface AdminLexiconEntry {
  id: number;
  keyword: string;
  group: string;
  level: string;
  matchMode?: string | null;
  exceptions: string[];
  scope: string;
  sortOrder: number;
  active: boolean;
}

export interface AdminLexiconRequest {
  keyword: string;
  group: string;
  level: string;
  matchMode?: string;
  exceptions?: string[];
  scope: string;
}

export interface LexiconMatch {
  keyword: string;
  level: string;
  group: string;
  matchedAs: 'EXACT' | 'NORMALIZED';
}

export const adminLexiconApi = {
  async list() {
    const { data } = await http.get<AdminLexiconEntry[]>('/api/v1/admin/lexicon');
    return data;
  },
  async create(input: AdminLexiconRequest) {
    const { data } = await http.post<AdminLexiconEntry>('/api/v1/admin/lexicon', input);
    return data;
  },
  async update(id: number, input: AdminLexiconRequest) {
    const { data } = await http.put<AdminLexiconEntry>(`/api/v1/admin/lexicon/${id}`, input);
    return data;
  },
  async setActive(id: number, active: boolean) {
    const { data } = await http.patch<AdminLexiconEntry>(`/api/v1/admin/lexicon/${id}/active`, { active });
    return data;
  },
  async test(text: string) {
    const { data } = await http.post<LexiconMatch[]>('/api/v1/admin/lexicon/test', { text });
    return data;
  },
};
