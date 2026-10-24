package com.shareloop.chat.repository;

import com.shareloop.chat.entity.Message;
import com.shareloop.chat.entity.MessageModeration;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository thao tác với bảng messages (ERD mục 7.4).
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Lấy tất cả tin nhắn của một yêu cầu giao dịch, sắp xếp theo ID tăng dần (thời gian tăng dần).
     */
    List<Message> findByRequestIdOrderByIdAsc(Long requestId);

    /**
     * Lấy các tin nhắn mới hơn afterId của một yêu cầu giao dịch dùng cho cơ chế polling.
     */
    List<Message> findByRequestIdAndIdGreaterThanOrderByIdAsc(Long requestId, Long afterId);

    List<Message> findTop3ByRequestIdAndModerationOrderByIdDesc(Long requestId, MessageModeration moderation);

    /**
     * Đếm số lượng tin nhắn theo requestId và trạng thái kiểm duyệt (FR-76, BR-C04).
     */
    long countByRequestIdAndModeration(Long requestId, MessageModeration moderation);
}
