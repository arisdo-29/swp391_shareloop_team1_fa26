import type { AdminRequestDetail } from '../services/adminRequestApi';

export type ItemChoice = 'REOPEN' | 'HIDE' | 'TRANSFERRED';

export const itemChoiceLabels: Record<ItemChoice, string> = {
  REOPEN: 'Mở lại',
  HIDE: 'Ẩn',
  TRANSFERRED: 'Đã đổi chủ',
};

// Câu giải thích hậu quả của từng lựa chọn, hiện ngay dưới ô chọn.
export const itemChoiceConsequences: Record<ItemChoice, string> = {
  REOPEN: 'Bài trở lại đang hiển thị để người khác xin.',
  HIDE: 'Bài bị ẩn, chỉ nút hiện lại của Admin mới hiện lại được.',
  TRANSFERRED:
    'Bài bị ẩn với lý do món đã được trao trong một giao dịch bị huỷ. Hệ thống báo hai bên liên hệ để hoàn trả, không tự hoàn trả và không giữ đồ hay tiền.',
};

export interface DisputeItemRow {
  itemId: number;
  title: string;
  imageUrl: string | null;
  ownerName: string;
  ownerId: number | null;
  holderName: string;
  // Giao hàng: chiều chứa món này đã được bên nhận bấm đã nhận. Gặp trực tiếp thì null (theo quy ước).
  legReceived: boolean | null;
}

// Các món của giao dịch: món chính của chủ bài, món đổi của người nhận (nếu có). Món đã đổi chủ nằm ở chỗ bên kia.
export function disputeItemRows(detail: AdminRequestDetail): DisputeItemRow[] {
  const donorName = detail.donor?.name ?? 'Chủ bài';
  const receiverName = detail.receiver.name ?? 'Người nhận';
  // Giao hàng: người đang giữ món theo dữ liệu từng chiều. Chiều đã nhận thì món ở chỗ người nhận chiều đó.
  const legs = detail.deliveryMethod === 'SHIPPING' ? (detail.shippingLegs ?? []) : [];
  const received = (leg: number) =>
    legs.length === 0 ? null : legs.some((row) => row.leg === leg && !!row.receivedAt);
  const mainReceived = received(1);
  const offeredReceived = received(2);
  const rows: DisputeItemRow[] = [
    {
      itemId: detail.item.itemId,
      title: detail.item.title ?? 'Món chính',
      imageUrl: detail.item.imageUrl,
      ownerName: donorName,
      ownerId: detail.donor?.userId ?? null,
      holderName: mainReceived === false ? donorName : receiverName,
      legReceived: mainReceived,
    },
  ];
  if (detail.offeredItem) {
    rows.push({
      itemId: detail.offeredItem.itemId,
      title: detail.offeredItem.title ?? 'Món đổi',
      imageUrl: detail.offeredItem.imageUrl,
      ownerName: receiverName,
      ownerId: detail.receiver.userId,
      holderName: offeredReceived === false ? receiverName : donorName,
      legReceived: offeredReceived,
    });
  }
  return rows;
}

// Mặc định theo luật chung cho chủ bài và người nhận: món của bên vi phạm là Ẩn, món của bên không vi phạm là Mở lại.
export function defaultItemChoices(
  rows: DisputeItemRow[],
  violatorId: number | null,
): Record<number, ItemChoice> {
  const choices: Record<number, ItemChoice> = {};
  rows.forEach((row) => {
    // Giao hàng mà chiều đã nhận thì món đã đổi chủ, mặc định Đã đổi chủ. Admin vẫn đổi được.
    if (row.legReceived === true) choices[row.itemId] = 'TRANSFERRED';
    else
      choices[row.itemId] = violatorId !== null && row.ownerId === violatorId ? 'HIDE' : 'REOPEN';
  });
  return choices;
}
