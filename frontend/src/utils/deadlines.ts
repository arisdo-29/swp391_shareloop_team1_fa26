import type { RequestResponse } from '../services/requestApi';
import { formatDeadline } from './request';
import { shippingLegs } from './shipping';

// Các mốc hạn do backend tính và trả trong RequestResponse (GN-13). FE chỉ dựng câu, không tự cộng ngày.

// "15:00 ngày 05/12" từ "15:00 05/12" của formatDeadline.
function clockAndDate(value: string) {
  const [clock, date] = formatDeadline(value).split(' ');
  return `${clock} ngày ${date}`;
}

export interface DeadlineLine {
  key: string;
  text: string;
  // Mốc dùng để chọn "mốc gần nhất" ở khung chat.
  at: string;
}

/**
 * Danh sách mốc thời gian hiện ở trang Tiến trình giao dịch. Mỗi dòng là một câu dễ hiểu, ví dụ
 * "Tự huỷ nếu chưa chốt lịch trước 15:00 ngày 02/12".
 */
export function deadlineLines(
  request: RequestResponse,
  viewerIsDonor: boolean,
  partnerName: string,
): DeadlineLine[] {
  const lines: DeadlineLine[] = [];
  const add = (key: string, at: string | null | undefined, text: (when: string) => string) => {
    if (at) lines.push({ key, at, text: text(clockAndDate(at)) });
  };

  const waitingSchedule = request.status === 'RESERVED' || request.status === 'AWAITING_LOGISTICS';
  add('autoCancel', request.autoCancelAt, (when) =>
    waitingSchedule
      ? `Tự huỷ nếu chưa chốt lịch trước ${when}`
      : request.deliveryMethod === 'SHIPPING'
        ? `Tự huỷ nếu chưa chiều nào được gửi trước ${when}`
        : `Tự huỷ nếu chưa bên nào xác nhận giao nhận trước ${when}`,
  );
  add('cancelRequest', request.cancelRequestExpiresAt, (when) =>
    `Hạn phản hồi đề nghị huỷ là ${when}, im lặng thì giao dịch tự huỷ`,
  );
  add('reschedule', request.rescheduleExpiresAt, (when) =>
    `Hạn phản hồi đề nghị dời lịch là ${when}, hết hạn thì giữ lịch cũ`,
  );

  const anyUnsent = shippingLegs(request).some((leg) => leg.step === 'WAITING_SHIP');
  if (request.deliveryMethod === 'SHIPPING' && anyUnsent) {
    add('sendDeadline', request.sendDeadlineAt, (when) => `Hạn gửi hàng là ${when}`);
  }

  if (request.deliveryMethod === 'SHIPPING') {
    const autoAt = [request.leg1AutoReceiveAt, request.leg2AutoReceiveAt];
    shippingLegs(request).forEach((leg, index) => {
      const viewerReceives = leg.senderIsDonor === !viewerIsDonor;
      add(`autoReceive${leg.leg}`, autoAt[index], (when) =>
        viewerReceives
          ? `Tự ghi nhận bạn đã nhận món ${leg.itemTitle} lúc ${when} nếu bạn không phản hồi`
          : `Tự ghi nhận ${partnerName} đã nhận món ${leg.itemTitle} lúc ${when} nếu họ không phản hồi`,
      );
    });
  } else {
    const viewerConfirmed = viewerIsDonor ? request.donorHandoverAt : request.receiverHandoverAt;
    add('autoConfirm', request.autoConfirmAt, (when) =>
      viewerConfirmed
        ? `Tự hoàn tất lúc ${when} nếu ${partnerName} không phản hồi`
        : `Tự hoàn tất lúc ${when} nếu bạn không phản hồi`,
    );
  }
  return lines;
}

// Mốc gần nhất cho dòng thu gọn ở khung chat; không có mốc nào thì null.
export function nearestDeadlineLine(
  request: RequestResponse,
  viewerIsDonor: boolean,
  partnerName: string,
): DeadlineLine | null {
  const lines = deadlineLines(request, viewerIsDonor, partnerName);
  if (lines.length === 0) return null;
  return lines.reduce((nearest, line) => (new Date(line.at) < new Date(nearest.at) ? line : nearest));
}
