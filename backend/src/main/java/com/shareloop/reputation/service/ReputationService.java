package com.shareloop.reputation.service;

import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.entity.UserReputation;
import com.shareloop.reputation.repository.UserReputationRepository;
import com.shareloop.setting.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service cửa ngõ của module reputation (Lộ trình 4.7, SRS FR-91, FR-94, FR-100, BR-U01).
 * Module khác thông báo sự kiện liên quan đến uy tín người dùng qua service này.
 */
@Service
public class ReputationService {

    private final ConfigService configService;
    private final UserReputationRepository userReputationRepository;

    public ReputationService() {
        this(null, null);
    }

    public ReputationService(UserReputationRepository userReputationRepository) {
        this(null, userReputationRepository);
    }

    public ReputationService(ConfigService configService) {
        this(configService, null);
    }

    @Autowired
    public ReputationService(
            ConfigService configService,
            @Autowired(required = false) UserReputationRepository userReputationRepository) {
        this.configService = configService;
        this.userReputationRepository = userReputationRepository;
    }

    /**
     * Tính hạng tín nhiệm theo tổng điểm dựa trên ngưỡng cấu hình từ ConfigService (FR-100, BR-U01, Issue #96).
     *
     * @param totalPoints tổng điểm tích luỹ
     * @return hạng tín nhiệm tương ứng (BRONZE, SILVER, GOLD, DIAMOND)
     */
    public Rank getRank(int totalPoints) {
        if (configService == null) {
            return Rank.BRONZE;
        }

        int diamond = getThreshold("REPUTATION_DIAMOND_MIN", "reputation_diamond_min", 400);
        int gold = getThreshold("REPUTATION_GOLD_MIN", "reputation_gold_min", 150);
        int silver = getThreshold("REPUTATION_SILVER_MIN", "reputation_silver_min", 50);

        if (totalPoints >= diamond) {
            return Rank.DIAMOND;
        }
        if (totalPoints >= gold) {
            return Rank.GOLD;
        }
        if (totalPoints >= silver) {
            return Rank.SILVER;
        }
        return Rank.BRONZE;
    }

    /**
     * Lấy hạng tín nhiệm hiện tại của người dùng từ điểm tích luỹ (Issue #40, #96).
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
                .map(user -> getRank(user.getTotalPoints()))
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
        Rank rank = getRank(reputation.getTotalPoints());
        Double asDescribedRate = userReputationRepository.calculateAsDescribedRate(userId);

        return new ReputationSummary(userId, trustStars, rank, asDescribedRate);
    }

    /**
     * Chữ ký có sẵn để request gọi khi giao dịch chuyển sang COMPLETED (Issue #96).
     * Thân đầy đủ (cộng điểm, cập nhật sao, tính lại hạng) sẽ được hoàn thiện ở R4.
     *
     * @param requestId id của yêu cầu giao dịch vừa chuyển sang COMPLETED
     */
    public void onCompleted(long requestId) {
        // TODO(R4): cộng điểm, ghi log, cập nhật rank
    }

    /**
     * Trừ sao uy tín (TrustStars) của người dùng khi vi phạm (FR-91, FR-92, BR-U04, BR-U05, Issue #113, #114).
     *
     * @param userId ID người dùng bị trừ sao
     * @param amount số sao cần trừ
     * @param reason lý do trừ sao
     */
    @Transactional
    public void deductStar(Long userId, int amount, String reason) {
        // Stub for #114 dependency - to be implemented fully in #114
    }

    @Transactional
    public void deductStar(Long userId, int amount) {
        deductStar(userId, amount, null);
    }

    private int getThreshold(String upperKey, String lowerKey, int fallback) {
        try {
            return configService.getDecimal(upperKey).intValue();
        } catch (Exception e1) {
            try {
                return configService.getDecimal(lowerKey).intValue();
            } catch (Exception e2) {
                return fallback;
            }
        }
    }
}
