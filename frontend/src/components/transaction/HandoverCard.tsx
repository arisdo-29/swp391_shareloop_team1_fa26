import { useState } from 'react';
import { Alert, Button, Select, TextArea } from '../ui';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { ApiError } from '../../services/http';
import { canConfirmHandover, canReportHandoverIssue } from '../../utils/request';

export const NOT_AS_DESCRIBED_REPORTED_NOTICE =
  'Đã gửi báo cáo món đồ không đúng mô tả. Admin sẽ xem xét.';

interface HandoverCardProps {
  request: RequestResponse;
  currentUserId: number;
  viewerIsDonor: boolean;
  partnerName: string;
  onUpdated: (request: RequestResponse) => void;
  onOpenIssue: () => void;
  // Thông báo đưa lên trang vì khối này ẩn ngay khi giao dịch chuyển sang hoàn tất.
  onNotice: (notice: string) => void;
}

/** Khối "Giao nhận": chỉ hiện sau khi đã chốt lịch (đã trao liên lạc) và giao dịch đang ở bước giao nhận. */
export function HandoverCard({
  request,
  currentUserId,
  viewerIsDonor,
  partnerName,
  onUpdated,
  onOpenIssue,
  onNotice,
}: HandoverCardProps) {
  const [asDescribed, setAsDescribed] = useState('');
  const [notAsDescribedReason, setNotAsDescribedReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  if (!canConfirmHandover(request.status) || !request.contactRevealedAt) return null;

  const currentHandoverAt = viewerIsDonor ? request.donorHandoverAt : request.receiverHandoverAt;
  const otherHandoverAt = viewerIsDonor ? request.receiverHandoverAt : request.donorHandoverAt;

  const confirmHandover = async () => {
    if (submitting || currentHandoverAt) return;
    if (!viewerIsDonor && (!asDescribed || (asDescribed === 'no' && !notAsDescribedReason.trim()))) {
      setError('Vui lòng đánh giá món đồ và nhập lý do nếu không đúng mô tả.');
      return;
    }
    setSubmitting(true);
    setError('');
    // Ghi nhớ trước khi gửi vì ô chọn bị ẩn sau khi xác nhận xong.
    const reportsNotAsDescribed = !viewerIsDonor && asDescribed === 'no';
    try {
      const updated = await requestApi.confirmHandover(
        request.id,
        currentUserId,
        viewerIsDonor
          ? {}
          : {
              asDescribed: asDescribed === 'yes',
              notAsDescribedReason: asDescribed === 'no' ? notAsDescribedReason.trim() : undefined,
            },
      );
      onUpdated(updated);
      // Backend tự mở báo cáo khi bên nhận chọn Không đúng mô tả (FR-85).
      if (reportsNotAsDescribed) onNotice(NOT_AS_DESCRIBED_REPORTED_NOTICE);
    } catch (caught) {
      setError((caught as ApiError).message || 'Không xác nhận được việc bàn giao.');
    } finally {
      setSubmitting(false);
    }
  };

  const confirmLabel = currentHandoverAt
    ? viewerIsDonor
      ? 'Bạn đã xác nhận đã giao'
      : 'Bạn đã xác nhận đã nhận'
    : viewerIsDonor
      ? 'Tôi đã giao đồ'
      : 'Tôi đã nhận được đồ';

  return (
    <section className="card space-y-3 p-5 sm:p-6" aria-labelledby="handover-title">
      <h2 id="handover-title" className="section-title">
        Giao nhận
      </h2>
      {otherHandoverAt && !currentHandoverAt ? (
        <Alert tone="warning">
          {partnerName} đã xác nhận {viewerIsDonor ? 'đã nhận đồ' : 'đã giao đồ'}. Hãy xác nhận
          phía bạn.
        </Alert>
      ) : null}
      {!viewerIsDonor && !currentHandoverAt ? (
        <>
          <Select
            label="Món đồ có đúng mô tả không?"
            value={asDescribed}
            disabled={submitting}
            onChange={(event) => setAsDescribed(event.target.value)}
          >
            <option value="">Chọn đánh giá</option>
            <option value="yes">Đúng mô tả</option>
            <option value="no">Không đúng mô tả</option>
          </Select>
          {asDescribed === 'no' ? (
            <TextArea
              label="Lý do không đúng mô tả"
              value={notAsDescribedReason}
              disabled={submitting}
              onChange={(event) => setNotAsDescribedReason(event.target.value)}
            />
          ) : null}
        </>
      ) : null}
      {!currentHandoverAt ? (
        <p className="text-xs font-semibold text-warning">
          Sau khi hoàn tất, bạn chỉ có 24 giờ để khiếu nại.
        </p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Button
          disabled={Boolean(currentHandoverAt) || submitting}
          onClick={() => void confirmHandover()}
        >
          {confirmLabel}
        </Button>
        {canReportHandoverIssue(request.status, otherHandoverAt, currentHandoverAt) ? (
          <Button variant="outline" disabled={submitting} onClick={onOpenIssue}>
            {viewerIsDonor ? 'Tôi chưa giao được' : 'Tôi chưa nhận được'}
          </Button>
        ) : null}
      </div>
      {error ? (
        <div role="alert">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : null}
    </section>
  );
}
