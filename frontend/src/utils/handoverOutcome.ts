import type { CancelRequestCode, RequestResponse } from '../services/requestApi';
import { isShippingRequest, shippingLegs, viewerSendsLeg } from './shipping';

// Câu chữ cho các nút kết quả giao nhận (docs/api/request.md mục 10, 14, 15). Backend vẫn là nơi quyết định nhánh.

export interface CancelReasonOption {
  code: CancelRequestCode;
  label: string;
}

// Lý do đề nghị huỷ trước giờ hẹn.
export const BEFORE_MEETING_REASONS: CancelReasonOption[] = [
  { code: 'BUSY', label: 'Tôi bận đột xuất' },
  { code: 'NO_LONGER_NEEDED', label: 'Tôi không còn nhu cầu' },
  { code: 'OTHER', label: 'Lý do khác' },
];

// Lý do "Giao nhận không thành công" từ giờ hẹn trở đi. "Không đến điểm hẹn" chỉ có ở Gặp trực tiếp, "chưa gửi hàng"
// chỉ có ở Giao hàng.
export function afterMeetingReasons(isShipping: boolean, incomingShipped = false): CancelReasonOption[] {
  if (isShipping && incomingShipped) {
    return [
      { code: 'OTHER', label: 'Chưa nhận được hàng' },
      { code: 'CANNOT_CONTACT', label: 'Không liên hệ được với bên kia' },
    ];
  }
  return [
    isShipping
      ? { code: 'COUNTERPART_NOT_SHIPPED', label: 'Bên kia chưa gửi hàng' }
      : { code: 'COUNTERPART_NO_SHOW', label: 'Bên kia không đến điểm hẹn' },
    { code: 'CANNOT_CONTACT', label: 'Không liên hệ được với bên kia' },
    { code: 'OTHER', label: 'Lý do khác' },
  ];
}

// Chưa tới giờ hẹn thì chỉ có "Đề nghị huỷ" và "Đề nghị dời lịch". Chỉ để chọn nút, backend vẫn kiểm lại.
export function isBeforeMeetingTime(meetingTime?: string | null, now: Date = new Date()) {
  if (!meetingTime) return false;
  const time = new Date(meetingTime).getTime();
  return !Number.isNaN(time) && now.getTime() < time;
}

// Hậu quả của "Giao nhận không thành công" phụ thuộc việc bên kia đã bấm hoàn tất hay chưa.
export function handoverFailedConsequence(
  partnerConfirmed: boolean,
  partnerName: string,
  responseHours: number,
  cancelResponseHours: number,
) {
  return partnerConfirmed
    ? `${partnerName} đã xác nhận hoàn tất nên giao dịch chuyển sang sự cố giao nhận, chờ Admin xác minh. ${partnerName} có ${responseHours} giờ để giải trình.`
    : cancelRequestConsequence(partnerName, cancelResponseHours);
}

// Đề nghị huỷ không huỷ ngay, bên kia được hỏi ý kiến (GN-06).
export function cancelRequestConsequence(partnerName: string, cancelResponseHours: number) {
  return `Giao dịch chưa bị huỷ ngay. ${partnerName} được hỏi có đồng ý không và có ${cancelResponseHours} giờ để trả lời. Đồng ý hoặc im lặng thì giao dịch huỷ, bài đăng và món đổi được mở lại, không bên nào bị trừ sao. Không đồng ý thì chuyển sang sự cố giao nhận, chờ Admin xác minh.`;
}

// Từ chối nhận là huỷ giao dịch kèm báo cáo vi phạm, không phải khiếu nại (GN-07).
export const REFUSE_HANDOVER_CONSEQUENCE =
  'Giao dịch sẽ bị huỷ. Bài của bạn được mở lại. Món bị từ chối sẽ tạm ẩn cho đến khi Admin xem xét.';

export function refuseHandoverConsequence(request: RequestResponse, viewerIsDonor: boolean) {
  if (!isShippingRequest(request)) return REFUSE_HANDOVER_CONSEQUENCE;

  const outcome = 'Giao dịch sẽ bị huỷ. Món bị từ chối sẽ tạm ẩn cho đến khi Admin xem xét.';
  const outbound = shippingLegs(request).find((leg) => viewerSendsLeg(leg, viewerIsDonor));
  if (!outbound) return outcome;
  if (outbound.shippedAt) {
    return `${outcome} Bài của món bạn đã gửi được giữ ẩn để Admin kiểm tra việc hoàn trả.`;
  }
  return `${outcome} Bài của món bạn chưa gửi được mở lại.`;
}

export const REFUSE_HANDOVER_REASON_LABEL = 'Không đúng mô tả';
