import { useState } from 'react';
import type { Item } from '../../types/domain';
import { DialogFrame } from '../Dialog';
import { OfferedItemOptions } from '../OfferedItemOptions';
import { ConversionTopUpNotice } from '../OfferConversionNotice';
import { Button, TextArea } from '../ui';

// Giới hạn lời nhắn theo docs/api/chat.md (ProposeOfferChangeRequest.content).
const OFFER_NOTE_MAX_LENGTH = 500;

/** Người nhận chọn một bài APPROVED của mình để đề nghị đưa vào đổi trong giao dịch Cho tặng. */
export function ProposeOfferChangeDialog({
  items,
  hasPendingOffer,
  submitting,
  error,
  onCancel,
  onSubmit,
}: {
  items: Array<Pick<Item, 'id' | 'title' | 'images' | 'district'>>;
  hasPendingOffer: boolean;
  submitting: boolean;
  error: string;
  onCancel: () => void;
  onSubmit: (offeredItemId: number, content: string) => void;
}) {
  const [selectedItemId, setSelectedItemId] = useState('');
  const [content, setContent] = useState('');

  return (
    <DialogFrame className="chat-action-dialog" labelledBy="propose-offer-change-title" busy={submitting} onClose={onCancel}>
      <h2 id="propose-offer-change-title" className="text-xl font-bold">
        {hasPendingOffer ? 'Đổi món đề nghị' : 'Đề nghị kèm món đổi'}
      </h2>
      <p className="mt-2 text-sm leading-6 text-text-secondary">
        Nếu chủ bài đồng ý, giao dịch chuyển sang Trao đổi. Bạn không mất phí.
      </p>
      {hasPendingOffer ? (
        <p className="mt-2 rounded-md bg-warning-soft p-3 text-xs leading-5 text-warning">
          Bạn đang có một đề nghị chờ chủ bài trả lời. Gửi đề nghị mới sẽ thay đề nghị cũ.
        </p>
      ) : null}
      <fieldset className="mt-4 min-w-0 space-y-2">
        <legend className="label">Chọn món bạn mang đổi</legend>
        <OfferedItemOptions items={items} selectedItemId={selectedItemId} onSelect={setSelectedItemId} />
        {!items.length ? (
          <p className="rounded-md bg-warning-soft p-3 text-xs leading-5 text-warning">
            Bạn chưa có món đã duyệt và còn hạn để đề nghị trao đổi.
          </p>
        ) : null}
      </fieldset>
      <div className="mt-4">
        <TextArea
          label="Lời nhắn (không bắt buộc)"
          value={content}
          maxLength={OFFER_NOTE_MAX_LENGTH}
          rows={3}
          onChange={(event) => setContent(event.target.value)}
        />
      </div>
      {error ? (
        <p role="alert" className="mt-3 rounded-md bg-error-soft p-3 text-sm text-error">
          {error}
        </p>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" disabled={submitting} onClick={onCancel}>
          Hủy
        </Button>
        <Button
          disabled={submitting || !selectedItemId}
          onClick={() => onSubmit(Number(selectedItemId), content.trim())}
        >
          {submitting ? 'Đang gửi...' : 'Gửi đề nghị'}
        </Button>
      </div>
    </DialogFrame>
  );
}

/** Chủ bài xác nhận đồng ý đề nghị; backend trừ phí đăng Trao đổi nên phải ghi rõ số Credit. */
export function AcceptOfferChangeDialog({
  itemTitle,
  offeredItemTitle,
  conversionFee,
  submitting,
  error,
  needsTopUp,
  onCancel,
  onConfirm,
}: {
  itemTitle: string;
  offeredItemTitle: string;
  conversionFee: number | null;
  submitting: boolean;
  error: string;
  needsTopUp: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  // Không có phí từ backend thì không tự đoán số Credit.
  const feeText = conversionFee === null ? 'phí đăng Trao đổi' : `${conversionFee} Credit phí đăng Trao đổi`;
  return (
    <DialogFrame className="chat-action-dialog" labelledBy="accept-offer-change-title" busy={submitting} onClose={onCancel}>
      <h2 id="accept-offer-change-title" className="text-xl font-bold">
        Đồng ý trao đổi
      </h2>
      <p className="mt-2 text-sm leading-6 text-text-secondary">
        {`Đồng ý trao đổi ${itemTitle} lấy ${offeredItemTitle}? Bài chuyển sang Trao đổi và bạn trả ${feeText}.`}
      </p>
      {needsTopUp ? <ConversionTopUpNotice fee={conversionFee} /> : null}
      {error ? (
        <p role="alert" className="mt-3 rounded-md bg-error-soft p-3 text-sm text-error">
          {error}
        </p>
      ) : null}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" disabled={submitting} onClick={onCancel}>
          Hủy
        </Button>
        <Button disabled={submitting} onClick={onConfirm}>
          {submitting ? 'Đang xử lý...' : 'Đồng ý'}
        </Button>
      </div>
    </DialogFrame>
  );
}
