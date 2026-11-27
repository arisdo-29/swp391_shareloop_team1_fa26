import { useEffect, useRef, useState } from 'react';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { ApiError } from '../../services/http';

// forbidden: người ngoài giao dịch hoặc giao dịch không tồn tại (backend trả 403 hoặc 404).
export type TransactionLoadError = 'forbidden' | 'failed' | null;

const scheduleFields = [
  'scheduleRevision',
  'deliveryMethod',
  'meetingTime',
  'meetingPlace',
  'donorAddress',
  'receiverAddress',
  'donorAddressProvided',
  'receiverAddressProvided',
  'donorScheduleOkAt',
  'receiverScheduleOkAt',
  'donorWaiverAt',
  'receiverWaiverAt',
  'contactRevealedAt',
  'rescheduleProposedBy',
  'rescheduleProposedAt',
  'rescheduleMeetingTime',
  'rescheduleMeetingPlace',
  'rescheduleExpiresAt',
] as const satisfies ReadonlyArray<keyof RequestResponse>;

const hasScheduleChange = (current: RequestResponse, incoming: RequestResponse) =>
  scheduleFields.some((field) => (current[field] ?? null) !== (incoming[field] ?? null));

interface TransactionDetail {
  request: RequestResponse;
  viewerId: number;
  scheduleUpdateCount: number;
}

/**
 * Tải chi tiết một giao dịch cho trang Tiến trình. Phải dùng GET /requests/{id} vì chỉ chi tiết mới có
 * handoverMedia; quyền xem do backend kiểm (chỉ hai bên của giao dịch).
 */
export function useTransactionDetail(requestId: number, currentUserId: number) {
  const [detail, setDetail] = useState<TransactionDetail | null>(null);
  const current = detail?.request.id === requestId && detail.viewerId === currentUserId ? detail : null;
  const request = current?.request ?? null;
  const [offeredItem, setOfferedItem] = useState<ItemResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<TransactionLoadError>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const localUpdateVersion = useRef(0);

  useEffect(() => {
    if (!Number.isSafeInteger(requestId) || requestId <= 0) {
      setLoading(false);
      setError('forbidden');
      return;
    }
    let active = true;
    const versionAtStart = localUpdateVersion.current;
    setLoading(true);
    setError(null);
    requestApi
      .get(requestId, currentUserId)
      .then((incoming) => {
        if (!active) return;
        if (versionAtStart !== localUpdateVersion.current) {
          setReloadKey((key) => key + 1);
          return;
        }
        setDetail((previous) => {
          const sameView = previous?.request.id === incoming.id && previous.viewerId === currentUserId;
          if (!sameView) return { request: incoming, viewerId: currentUserId, scheduleUpdateCount: 0 };
          return {
            ...previous,
            request: incoming,
            scheduleUpdateCount: previous.scheduleUpdateCount + Number(hasScheduleChange(previous.request, incoming)),
          };
        });
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
    scheduleUpdateCount: current?.scheduleUpdateCount ?? 0,
    // Thay Request bằng bản backend vừa trả sau một thao tác, không tự đổi trạng thái ở trình duyệt.
    // Phản hồi của thao tác không kèm handoverMedia (chỉ chi tiết mới có) nên giữ lại danh sách ảnh đang có.
    applyUpdate: (updated: RequestResponse) => {
      localUpdateVersion.current++;
      setDetail((previous) => {
        if (updated.id !== previous?.request.id || previous.viewerId !== currentUserId) return previous;
        if ((updated.scheduleRevision ?? 0) < (previous.request.scheduleRevision ?? 0)) return previous;
        return {
          ...previous,
          request: { ...updated, handoverMedia: updated.handoverMedia ?? previous.request.handoverMedia ?? null },
          scheduleUpdateCount: hasScheduleChange(previous.request, updated) ? 0 : previous.scheduleUpdateCount,
        };
      });
    },
    reload: () => setReloadKey((key) => key + 1),
  };
}
