// Câu chữ cho ba nút kết quả giao nhận (docs/api/request.md mục 10, 14, 15). Backend vẫn là nơi quyết định nhánh.

// Hậu quả của "Giao nhận không thành công" phụ thuộc việc bên kia đã bấm hoàn tất hay chưa.
export function handoverFailedConsequence(partnerConfirmed: boolean, partnerName: string) {
  return partnerConfirmed
    ? `${partnerName} đã xác nhận hoàn tất nên giao dịch chuyển sang sự cố giao nhận, chờ Admin xác minh. ${partnerName} có 48 giờ để giải trình.`
    : 'Giao dịch bị hủy, bài đăng và món đổi (nếu có) được mở lại. Không bên nào bị trừ uy tín.';
}

// Từ chối nhận là huỷ giao dịch kèm báo cáo vi phạm, không phải khiếu nại (GN-07).
export const REFUSE_HANDOVER_CONSEQUENCE =
  'Giao dịch sẽ bị huỷ. Bài của bạn được mở lại. Món bị từ chối sẽ tạm ẩn cho đến khi Admin xem xét.';

export const REFUSE_HANDOVER_REASON_LABEL = 'Không đúng mô tả';

// Danh sách lý do theo hình thức giao nhận và vai của người xem (người gửi là chủ bài, người nhận là người xin).
export function handoverFailedReasons(isShipping: boolean, viewerIsDonor: boolean) {
  if (!isShipping) {
    return [
      viewerIsDonor ? 'Người nhận không đến điểm hẹn' : 'Người giao không đến điểm hẹn',
      'Không liên hệ được với bên còn lại',
    ];
  }
  return [
    'Hàng bị thất lạc hoặc hư hỏng khi vận chuyển',
    viewerIsDonor ? 'Người nhận không nhận hàng' : 'Người gửi không gửi hàng như đã hẹn',
    'Không liên hệ được với bên còn lại',
  ];
}
