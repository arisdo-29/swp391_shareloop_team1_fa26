import type { RequestResponse } from '../services/requestApi';
import { formatDeadline } from './request';

// Giao hàng hai chiều (GN-12). Chiều 1 là món chính, chủ bài gửi cho người nhận. Chiều 2 là món đổi, người nhận gửi
// cho chủ bài, chỉ có ở Trao đổi. Cho tặng chỉ có chiều 1.

export type ShippingStep = 'WAITING_SHIP' | 'SHIPPED' | 'RECEIVED';

export const shippingStepLabels: Record<ShippingStep, string> = {
  WAITING_SHIP: 'Chờ gửi',
  SHIPPED: 'Đã gửi',
  RECEIVED: 'Đã nhận',
};

export const shippingStepOrder: ShippingStep[] = ['WAITING_SHIP', 'SHIPPED', 'RECEIVED'];

export interface ShippingLeg {
  leg: 1 | 2;
  itemTitle: string;
  // Chiều 1 do chủ bài gửi, chiều 2 do người nhận gửi.
  senderIsDonor: boolean;
  shippedAt?: string | null;
  receivedAt?: string | null;
  // Địa chỉ giao tới của chiều này (địa chỉ của bên nhận). Có thể null khi chưa được phép xem.
  address?: string | null;
  step: ShippingStep;
}

export function isShippingRequest(request: RequestResponse) {
  return request.deliveryMethod === 'SHIPPING';
}

// Các chiều của giao dịch: Cho tặng một chiều, Trao đổi hai chiều.
export function shippingLegs(request: RequestResponse): ShippingLeg[] {
  const first = buildLeg({
    leg: 1,
    itemTitle: request.itemTitle ?? 'Món chính',
    senderIsDonor: true,
    shippedAt: request.donorShippedAt,
    receivedAt: request.receiverHandoverAt,
    address: request.receiverAddress,
  });
  if (!request.offeredItemId) return [first];
  const second = buildLeg({
    leg: 2,
    itemTitle: request.offeredItemTitle ?? 'Món đổi',
    senderIsDonor: false,
    shippedAt: request.receiverShippedAt,
    receivedAt: request.donorHandoverAt,
    address: request.donorAddress,
  });
  return [first, second];
}

function buildLeg(input: Omit<ShippingLeg, 'step'>): ShippingLeg {
  const step: ShippingStep = input.receivedAt ? 'RECEIVED' : input.shippedAt ? 'SHIPPED' : 'WAITING_SHIP';
  return { ...input, step };
}

// Người xem gửi chiều này (chủ bài gửi chiều 1, người nhận gửi chiều 2) hay nhận chiều này.
export function viewerSendsLeg(leg: ShippingLeg, viewerIsDonor: boolean) {
  return leg.senderIsDonor === viewerIsDonor;
}

// Có cần địa chỉ nhận hàng của mình không: người nhận luôn cần, chủ bài chỉ cần khi Trao đổi.
export function viewerNeedsOwnAddress(request: RequestResponse, viewerIsDonor: boolean) {
  return !viewerIsDonor || Boolean(request.offeredItemId);
}

// Địa chỉ nhận hàng của chính người xem và trạng thái đã nhập của bên kia (không hiện nội dung trước khi chốt lịch).
export function ownShippingAddress(request: RequestResponse, viewerIsDonor: boolean) {
  return (viewerIsDonor ? request.donorAddress : request.receiverAddress) ?? '';
}

export function partnerAddressProvided(request: RequestResponse, viewerIsDonor: boolean) {
  const provided = viewerIsDonor ? request.receiverAddressProvided : request.donorAddressProvided;
  return Boolean(provided);
}

// Bên kia có cần nhập địa chỉ không: chủ bài chỉ cần khi Trao đổi, người nhận luôn cần.
export function partnerNeedsAddress(request: RequestResponse, viewerIsDonor: boolean) {
  return viewerIsDonor || Boolean(request.offeredItemId);
}

export function anyLegShipped(request: RequestResponse) {
  return shippingLegs(request).some((leg) => leg.step !== 'WAITING_SHIP');
}

// Dòng tóm tắt tiến độ các chiều cho khung chat và bước Giao nhận, ví dụ "Máy ảnh: Đã gửi. Mũ bảo hiểm: Chờ gửi".
export function shippingProgressSummary(request: RequestResponse) {
  return shippingLegs(request)
    .map((leg) => `${leg.itemTitle}: ${shippingStepLabels[leg.step]}`)
    .join('. ');
}

export function shippingFailureContext(request: RequestResponse, viewerIsDonor: boolean) {
  const legs = shippingLegs(request);
  const incoming = legs.find((leg) => !viewerSendsLeg(leg, viewerIsDonor));
  const other = legs.find((leg) => viewerSendsLeg(leg, viewerIsDonor));
  const incomingShipped = Boolean(incoming && incoming.step !== 'WAITING_SHIP');
  return { incomingShipped, opensDispute: incomingShipped || other?.step === 'RECEIVED' };
}

// Hậu quả của "Giao nhận không thành công" khi Giao hàng, theo từng chiều người xem nhận (GN-12). Backend vẫn quyết định.
export function shippingFailedConsequence(
  request: RequestResponse,
  viewerIsDonor: boolean,
  partnerName: string,
  responseHours: number,
  cancelResponseHours: number,
) {
  if (shippingFailureContext(request, viewerIsDonor).opensDispute) {
    return `Giao dịch chuyển sang sự cố giao nhận, chờ Admin xác minh. ${partnerName} có ${responseHours} giờ để giải trình.`;
  }
  const deadline = request.sendDeadlineAt ? ` Hạn gửi đến ${formatDeadline(request.sendDeadlineAt)}.` : '';
  return `${partnerName} chưa gửi hàng.${deadline} Quá hạn gửi mới báo được, khi đó giao dịch chưa huỷ ngay mà ${partnerName} được hỏi ý kiến trong ${cancelResponseHours} giờ. ${partnerName} bấm Tôi đã gửi hàng thì đề nghị tự rút.`;
}
