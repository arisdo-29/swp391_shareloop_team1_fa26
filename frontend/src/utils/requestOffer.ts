import type { ItemRequest } from '../types/domain';

export type RequestOfferLabel = 'Trao đổi' | 'Xin nhận';

// fee là số Credit backend trả trong conversionFee; null khi backend chưa trả, giao diện không tự đoán số.
export type OfferConversion = { to: 'SWAP'; fee: number | null } | { to: 'GIVE' };

type OfferFields = Pick<ItemRequest, 'offeredItemId' | 'itemOfferType' | 'conversionFee'>;

// Chú thích suy ra từ việc có kèm món hay không, không từ request.type:
// type là loại giao dịch và chỉ đổi khi chủ bài chọn người, nên yêu cầu PENDING gửi chéo loại sẽ sai nhãn.
export function requestOfferLabel(request: Pick<ItemRequest, 'offeredItemId'>): RequestOfferLabel {
  return request.offeredItemId ? 'Trao đổi' : 'Xin nhận';
}

// Câu hỏi trong hộp xác nhận chọn người, dựng theo chú thích của yêu cầu.
export function selectionQuestion(
  label: RequestOfferLabel,
  requesterName: string,
  itemTitle: string,
  offeredItemTitle: string,
): string {
  if (label === 'Trao đổi') {
    return `Bạn muốn trao đổi ${itemTitle} lấy ${offeredItemTitle} với ${requesterName}?`;
  }
  return `Bạn muốn chọn ${requesterName} để nhận ${itemTitle}?`;
}

// Cho biết chọn yêu cầu này có làm bài đổi hình thức không (CD-02, CD-04 trong docs/api/request.md).
// Chỉ so sánh để hiển thị cảnh báo; việc đổi hình thức và trừ Credit do backend làm.
export function offerConversion(request: OfferFields): OfferConversion | null {
  const label = requestOfferLabel(request);
  if (label === 'Trao đổi' && request.itemOfferType === 'gift') {
    return { to: 'SWAP', fee: request.conversionFee ?? null };
  }
  if (label === 'Xin nhận' && request.itemOfferType === 'trade') {
    return { to: 'GIVE' };
  }
  return null;
}
