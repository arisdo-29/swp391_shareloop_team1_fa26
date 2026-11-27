package com.shareloop.request;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mã lỗi của module request; tiền tố REQUEST_ và là một phần của contract (docs/api/request.md). */
public enum RequestErrorCode implements ErrorCode {
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu giao dịch."),
    REQUEST_MESSAGES_NOT_VIEWABLE(
            HttpStatus.CONFLICT, "Chỉ xem được tin nhắn khi giao dịch có báo cáo hoặc khiếu nại."),
    REQUEST_INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Chuyển trạng thái yêu cầu không hợp lệ."),
    REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bạn đã có một yêu cầu đang hoạt động cho bài đăng này."),
    REQUEST_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Bạn đã đạt giới hạn số yêu cầu đang chờ xử lý."),
    REQUEST_SELF_OFFER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Không thể gửi yêu cầu cho bài đăng của chính mình."),
    REQUEST_OFFERED_ITEM_IN_OTHER_TRANSACTION(
            HttpStatus.CONFLICT, "Món người này mang đổi đang nằm trong một giao dịch khác."),
    REQUEST_TOO_MANY_ACTIVE_TRANSACTIONS(
            HttpStatus.CONFLICT, "Bạn đang có nhiều giao dịch chưa xong. Hoàn tất bớt rồi gửi thêm yêu cầu."),
    REQUEST_RECEIVER_TOO_BUSY(HttpStatus.CONFLICT, "Người này đang có quá nhiều giao dịch chưa xong."),
    REQUEST_RESEND_COOLDOWN(HttpStatus.CONFLICT, "Bạn vừa huỷ yêu cầu cho bài này. Chưa thể gửi lại ngay."),
    REQUEST_ITEM_CATEGORY_DISABLED(HttpStatus.CONFLICT, "Danh mục của bài đang tạm ngưng."),
    REQUEST_ITEM_NOT_AVAILABLE(HttpStatus.CONFLICT, "Bài đăng không ở trạng thái sẵn sàng để gửi yêu cầu."),
    REQUEST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng."),
    REQUEST_SWAP_OFFERED_ITEM_REQUIRED(
            HttpStatus.UNPROCESSABLE_ENTITY, "Yêu cầu trao đổi bắt buộc phải chọn món đề nghị."),
    REQUEST_OFFERED_ITEM_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Bài đăng cho tặng không nhận món đề nghị trao đổi."),
    REQUEST_OFFERED_ITEM_CHANGE_LOCKED(HttpStatus.CONFLICT, "Chỉ có thể đổi món đề nghị trước khi chốt lịch hẹn."),
    REQUEST_OFFERED_ITEM_UNAVAILABLE(HttpStatus.CONFLICT, "Món người này đề nghị đổi không còn khả dụng."),
    REQUEST_ITEM_UNDER_REVIEW(
            HttpStatus.CONFLICT, "Bài đang chờ duyệt lại sau khi sửa, bạn chọn người sau khi bài được duyệt."),
    REQUEST_SCHEDULE_LOCKED(HttpStatus.CONFLICT, "Lịch hẹn đã được xác nhận và bị khoá, không thể chỉnh sửa."),
    REQUEST_SCHEDULE_PAST_TIME(HttpStatus.BAD_REQUEST, "Thời gian hẹn giao nhận phải ở tương lai."),
    REQUEST_SCHEDULE_WINDOW_EXPIRED(HttpStatus.CONFLICT, "Giao dịch đã hết hạn chốt lịch."),
    REQUEST_WAIVER_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng đồng ý cam kết trách nhiệm giao nhận."),
    REQUEST_SCHEDULE_NOT_SET(HttpStatus.BAD_REQUEST, "Chưa có thông tin lịch hẹn để xác nhận."),
    REQUEST_SCHEDULE_CHANGED(HttpStatus.CONFLICT, "Lịch giao nhận đã thay đổi. Hãy xem lại lịch trước khi xác nhận."),
    REQUEST_CONTACT_NOT_REVEALED(
            HttpStatus.CONFLICT, "Thông tin liên lạc chỉ được hiển thị sau khi hai bên đã chốt lịch hẹn."),
    REQUEST_REFUSE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Chỉ bên nhận món đồ mới được từ chối giao nhận."),
    REQUEST_HANDOVER_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "Bạn đã xác nhận giao nhận cho yêu cầu này rồi."),
    REQUEST_MEDIA_REQUIRED(HttpStatus.BAD_REQUEST, "Danh sách ảnh/video minh chứng không được rỗng."),
    REQUEST_MEDIA_STAGE_INVALID(HttpStatus.BAD_REQUEST, "Giai đoạn đính kèm minh chứng không hợp lệ."),
    REQUEST_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác trên yêu cầu này."),
    REQUEST_REPUTATION_BLOCKED(HttpStatus.FORBIDDEN, "Tài khoản có 0 sao uy tín, không thể gửi yêu cầu giao dịch."),
    REQUEST_ADMIN_BLOCKED(HttpStatus.FORBIDDEN, "Quản trị viên không được gửi yêu cầu giao dịch."),
    REQUEST_BEFORE_MEETING_TIME(
            HttpStatus.CONFLICT, "Chưa tới giờ hẹn. Bạn có thể đề nghị dời lịch hoặc đề nghị huỷ giao dịch."),
    REQUEST_CANCEL_REQUEST_PENDING(HttpStatus.CONFLICT, "Đã có một đề nghị huỷ đang chờ phản hồi."),
    REQUEST_CANCEL_REQUEST_NOT_FOUND(HttpStatus.CONFLICT, "Không có đề nghị huỷ nào đang chờ phản hồi."),
    REQUEST_CANCEL_REASON_INVALID(HttpStatus.BAD_REQUEST, "Lý do huỷ không hợp lệ ở thời điểm này."),
    REQUEST_SCHEDULE_TOO_FAR(HttpStatus.BAD_REQUEST, "Giờ hẹn quá xa. Chỉ hẹn trong vòng vài ngày tới."),
    REQUEST_RESCHEDULE_LIMIT_REACHED(HttpStatus.CONFLICT, "Giao dịch đã dời lịch đủ số lần cho phép."),
    REQUEST_RESCHEDULE_NOT_ALLOWED(
            HttpStatus.CONFLICT, "Chỉ dời lịch được trước giờ hẹn và khi chưa bên nào bấm hoàn tất hoặc gửi hàng."),
    REQUEST_RESCHEDULE_PENDING(HttpStatus.CONFLICT, "Đã có một đề nghị dời lịch đang chờ phản hồi."),
    REQUEST_RESCHEDULE_NOT_FOUND(HttpStatus.CONFLICT, "Không có đề nghị dời lịch nào đang chờ phản hồi."),
    REQUEST_RESCHEDULE_EXPIRED(HttpStatus.CONFLICT, "Đề nghị dời lịch đã hết hạn. Lịch cũ được giữ nguyên."),
    REQUEST_SHIPPING_ADDRESS_REQUIRED(
            HttpStatus.BAD_REQUEST, "Cần đủ địa chỉ nhận hàng của các bên trước khi chốt lịch giao hàng."),
    REQUEST_LEG_NOT_SHIPPED(HttpStatus.CONFLICT, "Bên kia chưa gửi hàng nên chưa thể xác nhận đã nhận."),
    REQUEST_LEG_ALREADY_SHIPPED(HttpStatus.CONFLICT, "Bạn đã gửi hàng cho giao dịch này rồi."),
    REQUEST_LEG_NOT_APPLICABLE(HttpStatus.CONFLICT, "Giao dịch này không có chiều giao hàng đó dành cho bạn."),
    REQUEST_SEND_DEADLINE_NOT_PASSED(HttpStatus.CONFLICT, "Bên kia chưa quá hạn gửi hàng nên chưa thể báo chưa nhận."),
    REQUEST_NOT_SHIPPING(HttpStatus.CONFLICT, "Chỉ giao dịch Giao hàng mới có thao tác này."),
    REQUEST_ITEM_OUTCOMES_REQUIRED(
            HttpStatus.BAD_REQUEST, "Cần chọn cách xử lý cho từng món của giao dịch trước khi chấp thuận khiếu nại."),
    REQUEST_OFFER_CHANGE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "Chỉ giao dịch Cho đang chờ chốt lịch mới được chuyển sang Trao đổi bằng món đề nghị.");

    private final HttpStatus status;
    private final String defaultMessage;

    RequestErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
