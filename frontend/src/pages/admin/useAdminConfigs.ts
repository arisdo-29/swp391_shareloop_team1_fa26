import { useCallback, useEffect, useRef, useState } from 'react';
import {
  adminConfigApi,
  type AdminConfigResponse,
  type BatchUpdateAdminConfigRequest,
  type ConfigPreviewResponse,
  type ConfigRuleMessage,
} from '../../services/adminConfigApi';
import type { ApiError } from '../../services/http';

/** Kết quả một lần xem trước. stale nghĩa là đã có lần gọi mới hơn, nơi gọi bỏ qua kết quả này. */
export type PreviewOutcome =
  | { status: 'ok'; preview: ConfigPreviewResponse }
  | { status: 'error'; message: string }
  | { status: 'stale' };

/**
 * Kết quả lưu theo lô. Khi lỗi: errors là danh sách lỗi luật (400), preview có khi Backend trả 409
 * (cảnh báo chưa xác nhận), message là câu báo chung khi không có hai thứ trên.
 */
export type BatchSaveResult =
  | { ok: true; configs: AdminConfigResponse[] }
  | { ok: false; message: string; errors: ConfigRuleMessage[]; preview: ConfigPreviewResponse | null };

function configUpdateErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  // Với lỗi 400, Backend trả câu tiếng Việt nêu rõ giá trị sai ở đâu (ví dụ thứ tự điểm các hạng).
  if (apiError.status === 400) {
    return apiError.message || 'Giá trị hoặc lý do thay đổi chưa hợp lệ. Vui lòng kiểm tra lại.';
  }
  if (apiError.status === 403) return 'Tài khoản hiện tại không có quyền sửa cấu hình.';
  if (apiError.status === 404) return 'Không tìm thấy cấu hình cần cập nhật.';
  if (apiError.status === 409) {
    return apiError.code === 'SETTING_CONFIG_WARNING_NOT_ACKNOWLEDGED'
      ? 'Thay đổi có cảnh báo. Hãy tích xác nhận đã hiểu cảnh báo rồi lưu lại.'
      : 'Thay đổi này kéo theo cấu hình liên quan hoặc có cảnh báo. Hãy xem lại phần cập nhật kèm rồi lưu lại.';
  }
  if (apiError.status === 401) return '';
  if (apiError.status === 0) return 'Không kết nối được máy chủ. Vui lòng thử lại.';
  return 'Không cập nhật được cấu hình. Vui lòng thử lại.';
}

function previewErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.status === 400) return apiError.message || 'Giá trị chưa hợp lệ.';
  if (apiError.status === 401) return '';
  return 'Không kiểm tra được các cấu hình liên quan. Vui lòng thử lại.';
}

function isPreview(value: unknown): value is ConfigPreviewResponse {
  const candidate = value as Partial<ConfigPreviewResponse> | null;
  return Boolean(candidate && Array.isArray(candidate.changes) && Array.isArray(candidate.warnings));
}

/** Lấy danh sách lỗi luật (400) hoặc bản xem trước (409) từ phần details Backend trả về. */
function parseBatchFailure(caught: unknown) {
  const apiError = caught as Partial<ApiError>;
  const details = apiError.details;
  const errors =
    apiError.status === 400 && Array.isArray(details?.errors)
      ? (details.errors as ConfigRuleMessage[])
      : [];
  const preview = apiError.status === 409 && isPreview(details?.preview) ? details.preview : null;
  return { message: configUpdateErrorMessage(caught), errors, preview };
}

function mergeConfigs(current: AdminConfigResponse[], updated: AdminConfigResponse[]) {
  return current.map((entry) => updated.find((item) => item.key === entry.key) ?? entry);
}

/** Tải danh sách cấu hình, xem trước và lưu theo lô (kèm lý do), dùng chung cho các trang cấu hình của admin. */
export function useAdminConfigs() {
  const [configs, setConfigs] = useState<AdminConfigResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  // Mã của lần xem trước mới nhất: kết quả về muộn của lần cũ (mạng chậm, gõ nhanh) sẽ bị bỏ.
  const latestPreviewId = useRef(0);
  const previewAbort = useRef<AbortController | null>(null);

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

  /** Huỷ lần xem trước đang chạy và bỏ qua kết quả của nó (dùng khi đóng hộp thoại hoặc giá trị không còn hợp lệ). */
  const cancelPreview = useCallback(() => {
    latestPreviewId.current += 1;
    previewAbort.current?.abort();
    previewAbort.current = null;
  }, []);

  useEffect(() => cancelPreview, [cancelPreview]);

  const preview = useCallback(async (key: string, value: string): Promise<PreviewOutcome> => {
    previewAbort.current?.abort();
    const controller = new AbortController();
    previewAbort.current = controller;
    latestPreviewId.current += 1;
    const requestId = latestPreviewId.current;
    try {
      const result = await adminConfigApi.preview(key, value, controller.signal);
      if (requestId !== latestPreviewId.current) return { status: 'stale' };
      return { status: 'ok', preview: result };
    } catch (caught) {
      if (requestId !== latestPreviewId.current) return { status: 'stale' };
      return { status: 'error', message: previewErrorMessage(caught) };
    }
  }, []);

  /** Lưu cả lô. Thành công thì gộp mọi cấu hình trả về (kể cả cấu hình đồng bộ) vào danh sách đang hiện. */
  const updateBatch = useCallback(
    async (request: BatchUpdateAdminConfigRequest): Promise<BatchSaveResult> => {
      setSaving(true);
      setSaveError('');
      try {
        const response = await adminConfigApi.updateBatch(request);
        setConfigs((current) => mergeConfigs(current, response.configs));
        return { ok: true, configs: response.configs };
      } catch (caught) {
        const failure = parseBatchFailure(caught);
        // Có danh sách lỗi hoặc cảnh báo cụ thể thì hộp thoại hiện chúng, không hiện thêm câu chung.
        setSaveError(failure.errors.length > 0 || failure.preview ? '' : failure.message);
        return { ok: false, ...failure };
      } finally {
        setSaving(false);
      }
    },
    [],
  );

  return {
    configs,
    loading,
    error,
    reload,
    saving,
    saveError,
    clearSaveError,
    preview,
    cancelPreview,
    updateBatch,
  };
}
