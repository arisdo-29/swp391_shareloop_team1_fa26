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
}

// Các món của giao dịch: món chính của chủ bài, món đổi của người nhận (nếu có). Món đã đổi chủ nằm ở chỗ bên kia.
export function disputeItemRows(detail: AdminRequestDetail): DisputeItemRow[] {
  const donorName = detail.donor?.name ?? 'Chủ bài';
  const receiverName = detail.receiver.name ?? 'Người nhận';
  const rows: DisputeItemRow[] = [
    {
      itemId: detail.item.itemId,
      title: detail.item.title ?? 'Món chính',
      imageUrl: detail.item.imageUrl,
      ownerName: donorName,
      ownerId: detail.donor?.userId ?? null,
      holderName: receiverName,
    },
  ];
  if (detail.offeredItem) {
    rows.push({
      itemId: detail.offeredItem.itemId,
      title: detail.offeredItem.title ?? 'Món đổi',
      imageUrl: detail.offeredItem.imageUrl,
      ownerName: receiverName,
      ownerId: detail.receiver.userId,
      holderName: donorName,
    });
  }
  return rows;
}

// Mặc định theo luật chung cho chủ bài và người nhận: món của bên vi phạm là Ẩn, món của bên không vi phạm là Mở lại.
export function defaultItemChoices(rows: DisputeItemRow[], violatorId: number | null): Record<number, ItemChoice> {
  const choices: Record<number, ItemChoice> = {};
  rows.forEach((row) => {
    choices[row.itemId] = violatorId !== null && row.ownerId === violatorId ? 'HIDE' : 'REOPEN';
  });
  return choices;
}
