import { http } from './http';

export const mediaApiEndpoints = {
  upload: '/api/v1/media',
  file: (id: number) => `/api/v1/media/${id}`,
} as const;

export interface MediaResponse {
  mediaId: number;
  url: string;
}

export async function uploadMedia(file: File, accessToken: string): Promise<MediaResponse> {
  const form = new FormData();
  form.append('file', file);
  const response = await fetch(mediaApiEndpoints.upload, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
    body: form,
  });
  if (!response.ok) throw new Error(`Upload failed: ${response.status}`);
  return response.json() as Promise<MediaResponse>;
}

export const mediaApi = {
  async upload(file: File, userId?: number): Promise<MediaResponse> {
    const formData = new FormData();
    formData.append('file', file);
    if (userId !== undefined) formData.append('userId', String(userId));

    const { data } = await http.post<MediaResponse>(mediaApiEndpoints.upload, formData);
    return data;
  },
};
