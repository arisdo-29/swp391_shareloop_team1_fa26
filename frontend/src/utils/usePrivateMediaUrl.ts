import { useEffect, useState } from 'react';
import { http } from '../services/http';

export interface PrivateMediaState {
  /** Blob URL dùng cho thẻ img, null khi chưa tải xong hoặc tải lỗi. */
  url: string | null;
  loading: boolean;
  failed: boolean;
}

/**
 * Tải ảnh riêng tư (ảnh chat, ảnh giao nhận) bằng http có token rồi hiện bằng blob URL, vì thẻ img không gửi được
 * header đăng nhập. Blob URL được thu hồi khi component gỡ hoặc đường dẫn đổi. Ảnh công khai thì vẫn dùng
 * resolveMediaUrl, không qua hook này.
 */
export function usePrivateMediaUrl(path: string | null | undefined): PrivateMediaState {
  const [state, setState] = useState<PrivateMediaState>({ url: null, loading: Boolean(path), failed: false });

  useEffect(() => {
    if (!path) {
      setState({ url: null, loading: false, failed: false });
      return;
    }
    let cancelled = false;
    let objectUrl: string | null = null;
    setState({ url: null, loading: true, failed: false });
    http
      .get<Blob>(path, { responseType: 'blob' })
      .then(({ data }) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(data);
        setState({ url: objectUrl, loading: false, failed: false });
      })
      .catch(() => {
        if (!cancelled) setState({ url: null, loading: false, failed: true });
      });
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [path]);

  return state;
}
