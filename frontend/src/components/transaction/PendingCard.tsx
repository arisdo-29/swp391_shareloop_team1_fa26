import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button } from '../ui';
import { SelectRequestDialog } from '../messages/SelectRequestDialog';
import type { ApiConversation } from '../messages/conversation';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { ApiError } from '../../services/http';
import { canSelectPendingRequest } from '../../utils/request';
import { productDetailFromRequestPath } from '../../utils/productDetailNavigation';

interface PendingCardProps {
  request: RequestResponse;
  conversation: ApiConversation;
  currentUserId: number;
  viewerIsDonor: boolean;
  partnerName: string;
  onUpdated: (request: RequestResponse) => void;
}

const selectErrorMessages: Record<string, string> = {
  REQUEST_FORBIDDEN: 'Bạn không có quyền chọn người cho bài đăng này.',
  REQUEST_NOT_FOUND: 'Không tìm thấy yêu cầu này.',
  REQUEST_ITEM_NOT_FOUND: 'Không tìm thấy bài đăng liên quan.',
  REQUEST_INVALID_STATE_TRANSITION:
    'Bài đăng đã có người được chọn hoặc yêu cầu này không còn khả dụng. Danh sách đã được cập nhật.',
};

/** Yêu cầu đang chờ chủ bài chọn: chủ bài chọn người này, người xin huỷ yêu cầu của mình. */
export function PendingCard(props: PendingCardProps) {
  if (props.request.status !== 'PENDING') return null;
  return (
    <section className="card space-y-3 p-5 sm:p-6" aria-labelledby="pending-title">
      <h2 id="pending-title" className="section-title">
        Chờ chọn người nhận
      </h2>
      {props.viewerIsDonor ? (
        <DonorPendingActions {...props} />
      ) : (
        <ReceiverPendingActions {...props} />
      )}
    </section>
  );
}

function ReceiverPendingActions({ request, currentUserId, onUpdated }: PendingCardProps) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const cancelRequest = async () => {
    setSubmitting(true);
    setError('');
    try {
      onUpdated(await requestApi.cancel(request.id, currentUserId));
    } catch (caught) {
      setError((caught as ApiError).message || 'Không cập nhật được yêu cầu.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <p className="text-sm leading-6 text-text-secondary">
        Yêu cầu đang chờ chủ bài chọn người nhận.
      </p>
      <Button variant="outline" disabled={submitting} onClick={() => void cancelRequest()}>
        Hủy yêu cầu
      </Button>
      {error ? <Alert tone="error">{error}</Alert> : null}
    </>
  );
}

function DonorPendingActions({
  request,
  conversation,
  currentUserId,
  partnerName,
  onUpdated,
}: PendingCardProps) {
  const navigate = useNavigate();
  const [siblingStatuses, setSiblingStatuses] = useState<RequestResponse['status'][] | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Các yêu cầu khác cùng bài quyết định còn được chọn hay không (đã có người được chọn thì thôi).
  useEffect(() => {
    let active = true;
    requestApi
      .forItem(request.itemId, currentUserId)
      .then((requests) => {
        if (active) setSiblingStatuses(requests.map((entry) => entry.status));
      })
      .catch(() => {
        if (active) setSiblingStatuses([]);
      });
    return () => {
      active = false;
    };
  }, [currentUserId, request.itemId]);

  const canSelect =
    siblingStatuses !== null &&
    canSelectPendingRequest(
      request.status,
      request.donorId ?? undefined,
      currentUserId,
      siblingStatuses,
    );

  const confirmSelection = async () => {
    setSubmitting(true);
    setError('');
    try {
      // Kiểm lại ngay trước khi chọn vì có thể vừa chọn người khác ở tab khác.
      const refreshed = await requestApi.forItem(request.itemId, currentUserId);
      const statuses = refreshed.map((entry) => entry.status);
      setSiblingStatuses(statuses);
      if (
        !canSelectPendingRequest(
          request.status,
          request.donorId ?? undefined,
          currentUserId,
          statuses,
        )
      ) {
        setError(selectErrorMessages.REQUEST_INVALID_STATE_TRANSITION);
        return;
      }
      onUpdated(await requestApi.select(request.id, currentUserId));
      setDialogOpen(false);
    } catch (caught) {
      const apiError = caught as ApiError;
      setError(
        selectErrorMessages[apiError.code] ||
          'Không thể chọn người này. Vui lòng tải lại trang và thử lại.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <p className="text-sm leading-6 text-text-secondary">
        Người này đang chờ bạn chọn. Chọn xong thì cuộc trò chuyện mới mở.
      </p>
      {siblingStatuses === null ? null : canSelect ? (
        <Button
          disabled={submitting}
          onClick={() => {
            setError('');
            setDialogOpen(true);
          }}
        >
          Chọn người này
        </Button>
      ) : (
        <p className="text-xs text-text-secondary">Bài đăng đã có yêu cầu được chọn.</p>
      )}
      {dialogOpen ? (
        <SelectRequestDialog
          conversation={conversation}
          participantName={partnerName}
          submitting={submitting}
          error={error}
          onCancel={() => {
            if (!submitting) setDialogOpen(false);
          }}
          onViewItem={(itemId) => {
            navigate(productDetailFromRequestPath(itemId, request.id, true));
          }}
          onConfirm={() => void confirmSelection()}
        />
      ) : null}
    </>
  );
}
