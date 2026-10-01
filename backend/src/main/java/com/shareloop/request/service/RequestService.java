package com.shareloop.request.service;

import com.shareloop.request.entity.RequestStatus;
import org.springframework.stereotype.Service;

/**
 * Service quản lý vòng đời yêu cầu xin/đổi đồ (Request) và state machine giao dịch (Lộ trình 4.6, 4.7).
 *
 * <p>Mọi thay đổi trạng thái Request chỉ diễn ra tại đây và luôn kiểm tra {@link RequestStatus#canTransition}.
 *
 * <p>Người gọi: item, user, chat, review/report.
 */
@Service
public class RequestService {

    private static final String TODO = "TODO BE3 - tuan 4/5/6";

    /**
     * Kiểm tra người dùng có đang trong giai đoạn giao nhận hay không (F03 bước 1, BR-U10).
     * Dùng cho UserService khi kiểm tra điều kiện đổi số điện thoại: không được đổi khi có request
     * ở trạng thái LOGISTICS_CONFIRMED hoặc AWAITING_HANDOVER.
     *
     * @param userId người dùng cần kiểm tra
     * @return true nếu đang có giao dịch chưa hoàn tất mà đã lộ thông tin liên lạc
     */
    public boolean hasActiveDelivery(long userId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Kiểm tra bài đăng đã có yêu cầu được chọn (RESERVED trở lên) hay chưa (F07 bước 1, BR-D04).
     * Dùng cho ItemService khi người dùng muốn sửa nội dung hoặc xoá bài đăng: chặn nếu đã có
     * request ở trạng thái RESERVED, AWAITING_LOGISTICS, LOGISTICS_CONFIRMED, AWAITING_HANDOVER, DISPUTED.
     *
     * @param itemId bài đăng cần kiểm tra
     * @return true nếu bài đã có đối tác và đang tiến hành giao dịch
     */
    public boolean hasReservedOrHigherRequest(long itemId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Gửi yêu cầu xin đồ hoặc đề nghị trao đổi đồ tới một bài đăng (F10 bước 1, BR-R01, BR-U02).
     *
     * @param itemId bài đăng cần xin/đổi
     * @param receiverId người gửi yêu cầu
     * @param offeredItemId bài đăng của người gửi làm món đề nghị (chỉ bắt buộc khi bài gốc là SWAP)
     * @return id của request vừa tạo ở trạng thái PENDING
     */
    public long createRequest(long itemId, long receiverId, Long offeredItemId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Chủ bài đăng chọn một yêu cầu để tiến hành giao dịch (F10 bước 3, BR-D01).
     *
     * <p>Khóa dòng bài đăng và món đề nghị, chuyển Request sang RESERVED, các request khác của bài tự động REJECTED.
     *
     * @param requestId yêu cầu được chọn
     * @param donorId chủ bài đăng
     */
    public void selectPartner(long requestId, long donorId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Người gửi tự huỷ yêu cầu khi còn ở trạng thái PENDING (F10, BR-R04).
     *
     * @param requestId yêu cầu muốn huỷ
     * @param receiverId người gửi yêu cầu
     */
    public void cancelRequest(long requestId, long receiverId) {
        throw new UnsupportedOperationException(TODO);
    }
}
