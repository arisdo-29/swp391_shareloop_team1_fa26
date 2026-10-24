import { useCallback, useEffect, useState } from 'react';
import { adminConfigApi, type AdminConfigResponse } from '../../services/adminConfigApi';
import type { ApiError } from '../../services/http';

function configUpdateErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  // Với lỗi 400, Backend trả câu tiếng Việt nêu rõ giá trị sai ở đâu (ví dụ thứ tự điểm các hạng).
  if (apiError.status === 400) {
    return apiError.message || 'Giá trị hoặc lý do thay đổi chưa hợp lệ. Vui lòng kiểm tra lại.';
  }
  if (apiError.status === 403) return 'Tài khoản hiện tại không có quyền sửa cấu hình.';
  if (apiError.status === 404) return 'Không tìm thấy cấu hình cần cập nhật.';
  if (apiError.status === 401) return 'Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.';
  if (apiError.status === 0) return 'Không kết nối được máy chủ. Vui lòng thử lại.';
  return 'Không cập nhật được cấu hình. Vui lòng thử lại.';
}

/** Tải danh sách cấu hình và cung cấp hàm sửa một cấu hình (kèm lý do), dùng chung cho các trang cấu hình của admin. */
export function useAdminConfigs() {
  const [configs, setConfigs] = useState<AdminConfigResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    adminConfigApi
      .list()
      .then((response) => {
        if (active) setConfigs(response);
      })
      .catch((caught) => {
        if (!active) return;
        setConfigs([]);
        const status = (caught as Partial<ApiError>).status;
        setError(
          status === 403
            ? 'Tài khoản hiện tại không đủ quyền xem cấu hình hệ thống.'
            : 'Không tải được cấu hình hệ thống.',
        );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [reloadKey]);

  const reload = useCallback(() => setReloadKey((key) => key + 1), []);
  const clearSaveError = useCallback(() => setSaveError(''), []);

  /** Trả về cấu hình đã cập nhật, hoặc null khi lỗi (nội dung lỗi nằm ở saveError). */
  const update = useCallback(async (key: string, value: string, reason: string) => {
    setSaving(true);
    setSaveError('');
    try {
      const updated = await adminConfigApi.update(key, { value, reason });
      setConfigs((current) => current.map((entry) => (entry.key === updated.key ? updated : entry)));
      return updated;
    } catch (caught) {
      setSaveError(configUpdateErrorMessage(caught));
      return null;
    } finally {
      setSaving(false);
    }
  }, []);

  const updateByBody = useCallback(async (key: string, value: string, reason: string) => {
    setSaving(true);
    setSaveError('');
    try {
      const updated = await adminConfigApi.updateByBody({ key, value, reason });
      setConfigs((current) => current.map((entry) => (entry.key === updated.key ? updated : entry)));
      return updated;
    } catch (caught) {
      setSaveError(configUpdateErrorMessage(caught));
      return null;
    } finally {
      setSaving(false);
    }
  }, []);

  return {
    configs,
    loading,
    error,
    reload,
    saving,
    saveError,
    setSaveError,
    clearSaveError,
    update,
    updateByBody,
  };
}
