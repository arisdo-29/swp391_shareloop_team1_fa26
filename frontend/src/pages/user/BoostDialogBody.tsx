import { Link } from 'react-router-dom';
import { Alert, Button } from '../../components/ui';
import type { BoostQuote } from '../../services/itemApi';
import { BOOST_BENEFITS_TEXT, boostConfirmLines, boostRejection } from '../../utils/boostMessages';

/** Lỗi khi bấm Xác nhận (POST /boost) mà báo giá mới không giải thích được, ví dụ thiếu Credit. */
export interface BoostFailure {
  code: string | null;
  message: string;
}

interface BoostDialogBodyProps {
  titleId: string;
  itemTitle: string;
  loading: boolean;
  quote: BoostQuote | null;
  boostDays: number | null;
  failure: BoostFailure | null;
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  onRenew: () => void;
}

/**
 * Nội dung hộp đẩy bài ở "Bài của tôi". Được đẩy: hộp xác nhận với các mốc và quyền lợi lấy từ quote (không hiện số dư
 * ví). Không được đẩy (quote.allowed = false hoặc POST lỗi): popup lý do và khi nào làm lại được, kèm nút hành động.
 */
export function BoostDialogBody({
  titleId,
  itemTitle,
  loading,
  quote,
  boostDays,
  failure,
  busy,
  onConfirm,
  onCancel,
  onRenew,
}: BoostDialogBodyProps) {
  const rejectionSource =
    failure ?? (quote && !quote.allowed ? { code: quote.reasonCode, message: quote.reason ?? '' } : null);
  const rejection = rejectionSource ? boostRejection(rejectionSource.code, quote, rejectionSource.message) : null;

  return (
    <>
      <h2 id={titleId} className="text-xl font-bold">
        {rejection ? 'Chưa đẩy được bài' : 'Xác nhận đẩy bài'}
      </h2>
      <p className="mt-2 text-sm font-semibold">{itemTitle}</p>

      {loading ? (
        <p className="mt-2 text-sm leading-6 text-text-muted">Đang tải báo giá...</p>
      ) : rejection ? (
        <div className="mt-4">
          <Alert tone="warning">{rejection.message}</Alert>
          {rejection.action === 'renew' ? (
            <Button className="mt-3" onClick={onRenew}>
              Gia hạn bài
            </Button>
          ) : null}
          {rejection.action === 'topUp' ? (
            <Link className="mt-3 inline-block" to="/credit">
              <Button>Nạp Credit</Button>
            </Link>
          ) : null}
        </div>
      ) : quote ? (
        <div className="mt-3 space-y-2 text-sm leading-6">
          {boostConfirmLines(quote, boostDays).map((line) => (
            <p key={line} className="text-text-primary">
              {line}
            </p>
          ))}
          <p className="text-xs leading-5 text-text-muted">{BOOST_BENEFITS_TEXT}</p>
        </div>
      ) : (
        <p className="mt-2 text-sm leading-6 text-text-muted">
          Không tải được báo giá. Vui lòng đóng hộp thoại và thử lại.
        </p>
      )}

      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" disabled={busy} onClick={onCancel}>
          {rejection ? 'Đóng' : 'Hủy'}
        </Button>
        {!rejection ? (
          <Button disabled={busy || loading || !quote?.allowed} onClick={onConfirm}>
            {busy ? 'Đang xử lý...' : 'Xác nhận'}
          </Button>
        ) : null}
      </div>
    </>
  );
}
