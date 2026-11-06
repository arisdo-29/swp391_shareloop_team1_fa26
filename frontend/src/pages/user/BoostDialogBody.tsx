import { Link } from 'react-router-dom';
import { Alert, Button, Icon } from '../../components/ui';
import type { BoostQuote } from '../../services/itemApi';
import {
  BOOST_BENEFITS,
  BOOST_REFUND_POLICY,
  boostRejection,
  formatDate,
  formatDateTime,
  formatTime,
} from '../../utils/boostMessages';

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
  const showConfirm = !rejection;

  // DialogFrame có sẵn padding; margin âm để header và footer có nền riêng tràn sát mép hộp thoại.
  return (
    <div className="-m-5 sm:-m-6">
      <div className="flex items-start justify-between gap-4 border-b border-border px-5 pb-3 pt-5 sm:px-6">
        <div>
          <h2 id={titleId} className="text-xl font-bold tracking-tight text-text-primary">
            {rejection ? 'Chưa đẩy được bài' : 'Xác nhận đẩy bài'}
          </h2>
          {showConfirm ? (
            <p className="mt-0.5 text-xs text-text-muted">Tăng độ tiếp cận bài đăng của bạn trên trang chủ</p>
          ) : null}
        </div>
        <button
          type="button"
          aria-label="Đóng"
          disabled={busy}
          onClick={onCancel}
          className="grid size-8 shrink-0 place-items-center rounded-full text-text-muted transition hover:bg-surface-low hover:text-text-primary disabled:opacity-50"
        >
          <Icon name="close" className="size-5" />
        </button>
      </div>

      <div className="space-y-3 px-5 py-4 sm:px-6">
        <ItemCard itemTitle={itemTitle} quote={showConfirm ? quote : null} />

        {loading ? (
          <p className="text-sm leading-6 text-text-muted">Đang tải báo giá...</p>
        ) : rejection ? (
          <div>
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
          <>
            <BoostMetrics quote={quote} boostDays={boostDays} />
            {/* Từ màn md hai khối nằm cạnh nhau để hộp thoại thấp lại, không phải cuộn. */}
            <div className="grid gap-3 md:grid-cols-2">
              <BoostTimeline quote={quote} />
              <BoostBenefits />
            </div>
          </>
        ) : (
          <p className="text-sm leading-6 text-text-muted">
            Không tải được báo giá. Vui lòng đóng hộp thoại và thử lại.
          </p>
        )}
      </div>

      <div className="flex items-center justify-end gap-3 border-t border-border bg-surface-low px-5 py-4 sm:px-6">
        <Button variant="ghost" disabled={busy} onClick={onCancel}>
          {rejection ? 'Đóng' : 'Hủy'}
        </Button>
        {showConfirm ? (
          <Button disabled={busy || loading || !quote?.allowed} onClick={onConfirm}>
            {busy ? (
              'Đang xử lý...'
            ) : (
              <>
                Xác nhận đẩy{' '}
                {quote ? (
                  <span className="ml-1 rounded-sm bg-white/15 px-2 py-0.5 text-xs font-bold">
                    {quote.fee} Credit
                  </span>
                ) : null}
              </>
            )}
          </Button>
        ) : null}
      </div>
    </div>
  );
}

/** Thẻ tên bài, kèm số suất trống khi là đợt đẩy mới (mua thêm cho bài đang đẩy không chiếm suất mới). */
function ItemCard({ itemTitle, quote }: { itemTitle: string; quote: BoostQuote | null }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-surface-low px-4 py-3">
      <div className="min-w-0">
        <span className="block text-2xs font-medium uppercase tracking-wider text-text-muted">Bài đăng</span>
        <span className="mt-0.5 block text-sm font-bold text-text-primary">{itemTitle}</span>
      </div>
      {quote && !quote.extending ? (
        <span className="inline-flex items-center gap-2 rounded-full border border-success/30 bg-surface px-3 py-1.5 text-xs font-semibold text-success">
          <span className="relative flex size-2">
            <span className="absolute inline-flex size-full rounded-full bg-success opacity-60 motion-safe:animate-ping" />
            <span className="relative inline-flex size-2 rounded-full bg-success" />
          </span>
          Còn {quote.slotsLeft} suất đẩy trống
        </span>
      ) : null}
    </div>
  );
}

/** Ba con số chính: thời lượng, chi phí, hiệu lực đến. boostDays null (chưa tải được phí) thì ghi "Một gói". */
function BoostMetrics({ quote, boostDays }: { quote: BoostQuote; boostDays: number | null }) {
  const duration = boostDays !== null ? `${boostDays} ngày` : 'Một gói';
  return (
    <div className="grid grid-cols-3 gap-2.5">
      <div className="rounded-lg border border-border bg-surface-low px-3 py-2.5">
        <p className="text-xs font-medium text-text-muted">{quote.extending ? 'Gia hạn thêm' : 'Thời lượng'}</p>
        <p className="mt-1 text-lg font-extrabold text-text-primary sm:text-xl">{duration}</p>
      </div>
      <div className="rounded-lg border border-primary-fixed bg-primary-soft px-3 py-2.5">
        <p className="text-xs font-medium text-primary">Chi phí đẩy</p>
        <p className="mt-1 text-lg font-extrabold text-primary sm:text-xl">{quote.fee} Credit</p>
      </div>
      <div className="rounded-lg border border-border bg-surface-low px-3 py-2.5">
        <p className="text-xs font-medium text-text-muted">{quote.extending ? 'Kết thúc lúc' : 'Hiệu lực đến'}</p>
        <p className="mt-1 text-sm font-bold leading-tight text-text-primary sm:text-base">
          {formatDate(quote.newBoostedUntil)}
        </p>
        <p className="mt-0.5 text-2xs font-medium text-text-muted">Lúc {formatTime(quote.newBoostedUntil)}</p>
      </div>
    </div>
  );
}

/** Hai trường hợp sau khi hết gói: mua thêm (tới chainEndsAt) và không mua thêm (nghỉ tới cooldownUntil, nếu có). */
function BoostTimeline({ quote }: { quote: BoostQuote }) {
  return (
    <div className="space-y-2.5 rounded-lg border border-border bg-surface p-4 shadow-sm">
      <h3 className="text-xs font-bold uppercase tracking-wider text-text-muted">Quy tắc gia hạn và lộ trình</h3>
      <div className="space-y-2">
        <div className="rounded-md border border-border bg-surface-low p-3 text-xs leading-relaxed text-text-secondary">
          <span className="mb-0.5 block font-semibold text-text-primary">Nếu mua thêm gói:</span>
          Đợt đẩy kéo dài tối đa tới{' '}
          <strong className="font-semibold text-primary">{formatDateTime(quote.chainEndsAt)}</strong>.
        </div>
        {/* cooldownUntil (backend tính): mốc được đẩy lại nếu mua gói này rồi không mua thêm. */}
        {quote.cooldownUntil ? (
          <div className="rounded-md border border-border bg-surface-low p-3 text-xs leading-relaxed text-text-secondary">
            <span className="mb-0.5 block font-semibold text-text-primary">Nếu không mua thêm:</span>
            Sau khi hết thời gian đẩy, bài đăng sẽ không được đẩy tiếp cho tới{' '}
            <strong className="font-semibold text-text-primary">{formatDateTime(quote.cooldownUntil)}</strong>. Số
            suất đẩy có giới hạn, nên mỗi bài phải chờ một khoảng thời gian giữa hai đợt đẩy để nhường lượt cho bài
            đăng khác.
          </div>
        ) : null}
      </div>
    </div>
  );
}

function BoostBenefits() {
  return (
    <div className="space-y-2 rounded-lg border border-border bg-surface-low p-4">
      <h3 className="text-xs font-bold uppercase tracking-wider text-text-muted">Quyền lợi hiển thị và lưu ý</h3>
      {BOOST_BENEFITS.map((benefit) => (
        <p key={benefit.label} className="flex items-start gap-2.5 text-xs leading-relaxed text-text-secondary sm:text-[13px]">
          <Icon name="check" className="mt-0.5 size-4 shrink-0 text-success" />
          <span>
            <strong className="font-semibold text-text-primary">{benefit.label}:</strong> {benefit.text}
          </span>
        </p>
      ))}
      <p className="flex items-start gap-2.5 rounded-md border border-warning/20 bg-warning-soft p-2.5 text-xs leading-relaxed text-warning">
        <Icon name="warning" className="mt-0.5 size-4 shrink-0" />
        <span>
          <strong className="font-semibold">Chính sách hoàn phí:</strong> {BOOST_REFUND_POLICY}
        </span>
      </p>
    </div>
  );
}
