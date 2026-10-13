import { http } from './http';

export interface MediaResponse {
  mediaId: number;
  url: string;
}

export const mediaApi = {
  async upload(file: File, userId?: number) {
    const formData = new FormData();
    formData.append('file', file);
    if (userId !== undefined) formData.append('userId', String(userId));

    const { data } = await http.post<MediaResponse>('/api/v1/media', formData, {
      headers: { 'Content-Type': undefined },
    });
    return data;
  },
};
