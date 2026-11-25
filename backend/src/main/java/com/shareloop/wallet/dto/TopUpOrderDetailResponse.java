package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import java.time.Instant;

/**
 * Chi tiết một đơn nạp.
 *
 * @param userName họ tên chủ đơn; chỉ có trong API của Admin, API của người dùng để null
 * @param userEmail email chủ đơn; chỉ có trong API của Admin, API của người dùng để null
 * @param reviewedByName họ tên Admin đã duyệt hoặc từ chối; null khi đơn chưa được xử lý
 * @param failureReason lý do đơn FAILED cho người dùng đọc (tự hủy, hủy tại VNPay, mã lỗi VNPay); null với trạng thái
 *     khác
 * @param serverTime giờ server lúc trả response; FE lấy serverTime - giờ máy để bù lệch đồng hồ khi đếm ngược tới
 *     expiresAt
 * @param paymentUrl URL thanh toán đã dựng lúc tạo đơn (cùng vnp_TxnRef, vnp_ExpireDate); chỉ có trong API của chủ
 *     đơn và chỉ khi đơn còn PENDING trong hạn, còn lại null
 */
public record TopUpOrderDetailResponse(
        String orderCode,
        long userId,
        long amountVnd,
        int credits,
        int vndPerCredit,
        PaymentOrderStatus status,
        PaymentProvider provider,
        Instant createdAt,
        Instant expiresAt,
        Instant submittedAt,
        Long reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        String userName,
        String userEmail,
        String reviewedByName,
        String failureReason,
        Instant serverTime,
        String paymentUrl) {}
