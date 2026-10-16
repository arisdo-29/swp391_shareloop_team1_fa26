package com.shareloop.reputation.service;

import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.entity.UserReputation;
import com.shareloop.reputation.repository.UserReputationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service cửa ngõ của module reputation (Lộ trình 4.7, SRS FR-91, FR-94).
 * Module khác thông báo sự kiện liên quan đến uy tín người dùng qua service này.
 */
@Service
public class ReputationService {

    private final UserReputationRepository userReputationRepository;

    public ReputationService() {
        this.userReputationRepository = null;
    }

    @Autowired
    public ReputationService(UserReputationRepository userReputationRepository) {
        this.userReputationRepository = userReputationRepository;
    }

    /**
     * Lấy hạng tín nhiệm hiện tại của người dùng (Issue #40, #96).
     *
     * @param userId id của người dùng
     * @return hạng tín nhiệm (mặc định BRONZE)
     */
    @Transactional(readOnly = true)
    public Rank getRank(Long userId) {
        if (userId == null || userReputationRepository == null) {
            return Rank.BRONZE;
        }
        return userReputationRepository
                .findById(userId)
                .map(UserReputation::getRankLevel)
                .orElse(Rank.BRONZE);
    }

    /**
     * Lấy thông tin uy tín tổng quan của người dùng (FR-65, Issue #110).
     * Bao gồm TrustStars (mặc định 5), hạng (mặc định BRONZE) và tỉ lệ đúng mô tả (%).
     *
     * @param userId id của người dùng
     * @return tóm tắt uy tín người dùng
     */
    @Transactional(readOnly = true)
    public ReputationSummary getReputationSummary(Long userId) {
        if (userId == null) {
            return new ReputationSummary(null, 5, Rank.BRONZE, null);
        }
        if (userReputationRepository == null) {
            return new ReputationSummary(userId, 5, Rank.BRONZE, null);
        }

        UserReputation reputation = userReputationRepository.findById(userId).orElse(null);
        if (reputation == null) {
            return new ReputationSummary(userId, 5, Rank.BRONZE, null);
        }

        int trustStars = reputation.getTrustStars();
        Rank rank = reputation.getRankLevel() != null ? reputation.getRankLevel() : Rank.BRONZE;
        Double asDescribedRate = userReputationRepository.calculateAsDescribedRate(userId);

        return new ReputationSummary(userId, trustStars, rank, asDescribedRate);
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
