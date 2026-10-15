import { http } from './http';

export const mediaApiEndpoints = {
  upload: '/api/v1/media',
  file: (id: number) => `/api/v1/media/${id}`,
} as const;

export interface MediaResponse {
  mediaId: number;
  url: string;
}

export const mediaApi = {
  async upload(file: File): Promise<MediaResponse> {
    const formData = new FormData();
    formData.append('file', file);

    const { data } = await http.post<MediaResponse>(mediaApiEndpoints.upload, formData);
    return data;
  },
};
