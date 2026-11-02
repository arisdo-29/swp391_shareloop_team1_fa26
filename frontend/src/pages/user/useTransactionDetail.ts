import { useEffect, useState } from 'react';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { ApiError } from '../../services/http';

// forbidden: người ngoài giao dịch hoặc giao dịch không tồn tại (backend trả 403 hoặc 404).
export type TransactionLoadError = 'forbidden' | 'failed' | null;

/**
 * Tải chi tiết một giao dịch cho trang Tiến trình. Phải dùng GET /requests/{id} vì chỉ chi tiết mới có
 * handoverMedia; quyền xem do backend kiểm (chỉ hai bên của giao dịch).
 */
export function useTransactionDetail(requestId: number, currentUserId: number) {
  const [request, setRequest] = useState<RequestResponse | null>(null);
  const [offeredItem, setOfferedItem] = useState<ItemResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<TransactionLoadError>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!Number.isSafeInteger(requestId) || requestId <= 0) {
      setLoading(false);
      setError('forbidden');
      return;
    }
    let active = true;
    setLoading(true);
    setError(null);
    requestApi
      .get(requestId, currentUserId)
      .then((detail) => {
        if (active) setRequest(detail);
      })
      .catch((caught: ApiError) => {
        if (!active) return;
        setError(caught.status === 403 || caught.status === 404 ? 'forbidden' : 'failed');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [currentUserId, reloadKey, requestId]);

  // Món mang đổi chỉ để hiện tên và tình trạng; tải lỗi thì vẫn dùng offeredItemTitle của Request.
  const offeredItemId = request?.type === 'SWAP' ? request.offeredItemId : undefined;
  useEffect(() => {
    if (!offeredItemId) {
      setOfferedItem(null);
      return;
    }
    let active = true;
    itemApi
      .detail(offeredItemId)
      .then((item) => {
        if (active) setOfferedItem(item);
      })
      .catch(() => {
        if (active) setOfferedItem(null);
      });
    return () => {
      active = false;
    };
  }, [offeredItemId]);

  return {
    request,
    offeredItem,
    loading,
    error,
    // Thay Request bằng bản backend vừa trả sau một thao tác, không tự đổi trạng thái ở trình duyệt.
    // Phản hồi của thao tác không kèm handoverMedia (chỉ chi tiết mới có) nên giữ lại danh sách ảnh đang có.
    applyUpdate: (updated: RequestResponse) =>
      setRequest((current) => ({
        ...updated,
        handoverMedia: updated.handoverMedia ?? current?.handoverMedia ?? null,
      })),
    reload: () => setReloadKey((key) => key + 1),
  };
}
