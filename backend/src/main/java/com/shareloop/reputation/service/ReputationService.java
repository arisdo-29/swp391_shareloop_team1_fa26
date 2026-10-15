package com.shareloop.reputation.service;

import com.shareloop.reputation.entity.Rank;
import org.springframework.stereotype.Service;

/**
 * Service cửa ngõ của module reputation (Lộ trình 4.7, SRS FR-91, FR-94).
 * Module khác thông báo sự kiện liên quan đến uy tín người dùng qua service này.
 */
@Service
public class ReputationService {

    /**
     * Lấy hạng tín nhiệm hiện tại của người dùng (Issue #40).
     * Tạm thời trả BRONZE, issue reputation thật sẽ tính theo điểm tích lũy.
     *
     * @param userId id của người dùng
     * @return hạng tín nhiệm (mặc định BRONZE)
     */
    public Rank getRank(Long userId) {
        return Rank.BRONZE;
    }

    /**
     * Ghi nhận sự kiện hoàn thành giao dịch để tính toán điểm tín nhiệm hoặc huy hiệu (Issue #96).
     *
     * @param requestId id của yêu cầu giao dịch vừa chuyển sang COMPLETED
     */
    public void onCompleted(long requestId) {
        // Chữ ký service chờ BE4 (@kopslngbtram2110) triển khai chi tiết ở Issue #96
    }
}
