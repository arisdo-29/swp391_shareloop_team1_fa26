package com.shareloop.reputation.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.audit.service.ActivityLogQueryService;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.reputation.dto.MyReputationResponse;
import com.shareloop.reputation.dto.PointHistoryDto;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.dto.RequestParticipantProjection;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.entity.UserReputation;
import com.shareloop.reputation.repository.UserReputationRepository;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.UserErrorCode;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service cửa ngõ của module reputation (Lộ trình 4.7, SRS FR-91, FR-92, FR-94, FR-97, FR-100, BR-U01, BR-U05).
 * Module khác thông báo sự kiện liên quan đến uy tín người dùng qua service này.
 */
@Service
public class ReputationService {

    private final ConfigService configService;
    private final UserReputationRepository userReputationRepository;
    private final ActivityLogService activityLogService;
    private final ActivityLogQueryService activityLogQueryService;
    private final ObjectProvider<ItemService> itemServiceProvider;
    private final NotificationService notificationService;

    public ReputationService() {
        this(null, null, null, null, (ObjectProvider<ItemService>) null, null);
    }

    public ReputationService(UserReputationRepository userReputationRepository) {
        this(null, userReputationRepository, null, null, (ObjectProvider<ItemService>) null, null);
    }

    public ReputationService(ConfigService configService) {
        this(configService, null, null, null, (ObjectProvider<ItemService>) null, null);
    }

    public ReputationService(ConfigService configService, UserReputationRepository userReputationRepository) {
        this(configService, userReputationRepository, null, null, (ObjectProvider<ItemService>) null, null);
    }

    public ReputationService(
            ConfigService configService,
            UserReputationRepository userReputationRepository,
            ActivityLogService activityLogService,
            ItemService itemService,
            NotificationService notificationService) {
        this(
                configService,
                userReputationRepository,
                activityLogService,
                null,
                itemService != null ? new SimpleObjectProvider<>(itemService) : null,
                notificationService);
    }

    public ReputationService(
            ConfigService configService,
            UserReputationRepository userReputationRepository,
            ActivityLogService activityLogService,
            ActivityLogQueryService activityLogQueryService,
            ItemService itemService,
            NotificationService notificationService) {
        this(
                configService,
                userReputationRepository,
                activityLogService,
                activityLogQueryService,
                itemService != null ? new SimpleObjectProvider<>(itemService) : null,
                notificationService);
    }

    @Autowired
    public ReputationService(
            ConfigService configService,
            @Autowired(required = false) UserReputationRepository userReputationRepository,
            @Autowired(required = false) ActivityLogService activityLogService,
            @Autowired(required = false) ActivityLogQueryService activityLogQueryService,
            @Autowired(required = false) ObjectProvider<ItemService> itemServiceProvider,
            @Autowired(required = false) NotificationService notificationService) {
        this.configService = configService;
        this.userReputationRepository = userReputationRepository;
        this.activityLogService = activityLogService;
        this.activityLogQueryService = activityLogQueryService;
        this.itemServiceProvider = itemServiceProvider;
        this.notificationService = notificationService;
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
     * Xem thông tin điểm, hạng, TrustStars, tỉ lệ đúng mô tả và lịch sử biến động điểm của chính người dùng (FR-101, Issue #128).
     *
     * @param userId ID người dùng đang đăng nhập
     * @param page số trang (bắt đầu từ 0)
     * @param size số lượng bản ghi mỗi trang (1-50)
     * @return thông tin uy tín cá nhân và phân trang lịch sử
     */
    @Transactional(readOnly = true)
    public MyReputationResponse getMyReputation(Long userId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        UserReputation reputation = userReputationRepository != null
                ? userReputationRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND))
                : null;

        int totalPoints = reputation != null ? reputation.getTotalPoints() : 0;
        int trustStars = reputation != null ? reputation.getTrustStars() : 5;
        Rank rank = getRank(totalPoints);

        Double rawRate =
                userReputationRepository != null ? userReputationRepository.calculateAsDescribedRate(userId) : null;
        Double accuracyRate = rawRate != null ? rawRate / 100.0 : null;

        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 50);

        Page<PointHistoryDto> history = Page.empty();
        if (activityLogQueryService != null) {
            Page<ActivityLogDto> logs = activityLogQueryService.search(
                    userId, ActivityType.POINTS.code(), null, null, pageNumber, pageSize);
            history = logs.map(log -> {
                int delta = 0;
                if (log.data() != null && log.data().get("points") instanceof Number n) {
                    delta = n.intValue();
                }
                String reason = log.reason() != null && !log.reason().isBlank() ? log.reason() : "REQUEST_COMPLETED";
                return new PointHistoryDto(delta, reason, log.createdAt());
            });
        }

        return new MyReputationResponse(totalPoints, rank, trustStars, accuracyRate, history);
    }

    /**
     * Tiếp nhận sự kiện giao dịch chuyển sang trạng thái COMPLETED (FR-97, FR-100, Issue #114).
     *
     * <p>Cộng điểm cho hai bên theo cấu hình:
     * <ul>
     *   <li>Giao dịch Cho (GIVE): người cho nhận điểm donor (mặc định +10), người nhận nhận điểm receiver (mặc định +2).</li>
     *   <li>Giao dịch Trao đổi (SWAP): mỗi bên nhận điểm swap (mặc định +6).</li>
     * </ul>
     * Tính lại hạng ngay lập tức và ghi nhật ký hoạt động. Chống cộng điểm hai lần cho cùng một yêu cầu (Idempotency).
     *
     * @param requestId id của yêu cầu giao dịch vừa chuyển sang COMPLETED
     */
    @Transactional
    public void onCompleted(long requestId) {
        if (userReputationRepository == null) {
            return;
        }

        // 1. Chống cộng trùng cho cùng một request (Idempotent)
        if (userReputationRepository.isReputationAppliedForRequest(requestId)) {
            return;
        }

        // 2. Tìm thông tin hai bên tham gia giao dịch
        RequestParticipantProjection participant = userReputationRepository.findRequestParticipant(requestId);
        if (participant == null) {
            return;
        }

        Long donorId = participant.getDonorId();
        Long receiverId = participant.getReceiverId();
        if (donorId == null || receiverId == null) {
            return;
        }

        // 3. Đọc điểm cấu hình từ ConfigService
        String type = participant.getType();
        int donorPts;
        int receiverPts;
        if ("GIVE".equalsIgnoreCase(type)) {
            donorPts = getThreshold("REPUTATION_GIVE_DONOR", "reputation_give_donor", 10);
            receiverPts = getThreshold("REPUTATION_GIVE_RECEIVER", "reputation_give_receiver", 2);
        } else {
            int swapPts = getThreshold("REPUTATION_SWAP_EACH", "reputation_swap_each", 6);
            donorPts = swapPts;
            receiverPts = swapPts;
        }

        // 4. Lock theo thứ tự tăng dần của ID để ngăn ngừa deadlock
        Long firstId = Math.min(donorId, receiverId);
        Long secondId = Math.max(donorId, receiverId);

        UserReputation firstUser =
                userReputationRepository.findByIdForUpdate(firstId).orElse(null);
        UserReputation secondUser = firstId.equals(secondId)
                ? firstUser
                : userReputationRepository.findByIdForUpdate(secondId).orElse(null);

        UserReputation donor = donorId.equals(firstId) ? firstUser : secondUser;
        UserReputation receiver = receiverId.equals(firstId) ? firstUser : secondUser;

        // 5. Cộng điểm và tính lại hạng ngay lập tức
        if (donor != null) {
            int newTotal = donor.getTotalPoints() + donorPts;
            Rank newRank = getRank(newTotal);
            donor.addPoints(donorPts, newRank);
            userReputationRepository.save(donor);

            if (activityLogService != null) {
                activityLogService.log(
                        ActivityType.POINTS.name(),
                        donorId,
                        "REQUEST",
                        requestId,
                        Map.of("points", donorPts, "totalPoints", newTotal, "rank", newRank.name(), "role", "DONOR"),
                        "Cộng " + donorPts + " điểm hoàn tất giao dịch #" + requestId + " (vai trò cho/tặng)");
            }
        }

        if (receiver != null) {
            int newTotal = receiver.getTotalPoints() + receiverPts;
            Rank newRank = getRank(newTotal);
            receiver.addPoints(receiverPts, newRank);
            userReputationRepository.save(receiver);

            if (activityLogService != null) {
                activityLogService.log(
                        ActivityType.POINTS.name(),
                        receiverId,
                        "REQUEST",
                        requestId,
                        Map.of(
                                "points",
                                receiverPts,
                                "totalPoints",
                                newTotal,
                                "rank",
                                newRank.name(),
                                "role",
                                "RECEIVER"),
                        "Cộng " + receiverPts + " điểm hoàn tất giao dịch #" + requestId + " (vai trò nhận/đổi)");
            }
        }
    }

    /**
     * Trừ sao uy tín (TrustStars) của người dùng khi vi phạm (FR-91, FR-92, BR-U04, BR-U05, Issue #114).
     *
     * <p>Quy tắc:
     * <ul>
     *   <li>Trừ số lượng sao chỉ định, không để âm (tối thiểu 0 sao).</li>
     *   <li>Ghi nhật ký hành động (activity_logs STARS).</li>
     *   <li>Nếu số sao trước đó > 0 và sau khi trừ về đúng 0 sao: vô hiệu hoá quyền đăng bài bằng cách ẩn toàn bộ
     *   bài đăng đang hiển thị của người này và gửi thông báo cảnh báo (chỉ gửi đúng 1 lần khi chạm mốc 0).</li>
     * </ul>
     *
     * @param userId ID người dùng bị trừ sao
     * @param amount số sao cần trừ
     * @param reason lý do trừ sao
     */
    @Transactional
    public void deductStar(Long userId, int amount, String reason) {
        if (userId == null || amount <= 0 || userReputationRepository == null) {
            return;
        }

        UserReputation user = userReputationRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) {
            return;
        }

        int before = user.getTrustStars();
        int newStars = Math.max(0, before - amount);
        user.setTrustStars((short) newStars);
        userReputationRepository.save(user);

        if (activityLogService != null) {
            activityLogService.log(
                    ActivityType.STARS.name(),
                    null,
                    "USER",
                    userId,
                    Map.of("amount", amount, "before", before, "after", newStars),
                    reason != null ? reason : "Trừ " + amount + " sao uy tín");
        }

        // Chỉ xử lý khi trước đó còn sao và sau khi trừ chạm mốc 0 sao
        if (before > 0 && newStars == 0) {
            if (itemServiceProvider != null) {
                itemServiceProvider.ifAvailable(service -> service.hideAllOfUser(userId));
            }
            if (notificationService != null) {
                notificationService.notify(
                        userId,
                        "TRUST_STARS_DEPLETED",
                        "Sao uy tín đã về 0",
                        "Điểm uy tín (TrustStars) của bạn đã về 0. Quyền đăng bài và gửi yêu cầu giao dịch đã bị vô hiệu hoá. Vui lòng gửi khiếu nại để được xem xét cấp sao ân hạn.",
                        "/me/reputation");
            }
        }
    }

    @Transactional
    public void deductStar(Long userId, int amount) {
        deductStar(userId, amount, null);
    }

    /**
     * Kiểm tra người dùng có quyền đăng bài mới hay không (BR-U05).
     * Bị vô hiệu hoá khi TrustStars = 0.
     *
     * @param userId ID người dùng
     * @return true nếu còn quyền đăng bài, false nếu đã bị vô hiệu hoá
     */
    @Transactional(readOnly = true)
    public boolean canPost(Long userId) {
        if (userId == null || userReputationRepository == null) {
            return true;
        }
        return userReputationRepository
                .findById(userId)
                .map(u -> u.getTrustStars() > 0)
                .orElse(true);
    }

    /**
     * Kiểm tra người dùng có quyền gửi yêu cầu giao dịch hay không (BR-U05).
     * Bị vô hiệu hoá khi TrustStars = 0.
     *
     * @param userId ID người dùng
     * @return true nếu còn quyền gửi yêu cầu, false nếu đã bị vô hiệu hoá
     */
    @Transactional(readOnly = true)
    public boolean canRequest(Long userId) {
        if (userId == null || userReputationRepository == null) {
            return true;
        }
        return userReputationRepository
                .findById(userId)
                .map(u -> u.getTrustStars() > 0)
                .orElse(true);
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

    /**
     * Lớp hỗ trợ gói ItemService thành ObjectProvider khi dùng trong unit test.
     */
    private static class SimpleObjectProvider<T> implements ObjectProvider<T> {
        private final T instance;

        SimpleObjectProvider(T instance) {
            this.instance = instance;
        }

        @Override
        public T getObject(Object... args) {
            return instance;
        }

        @Override
        public T getIfAvailable() {
            return instance;
        }

        @Override
        public T getIfUnique() {
            return instance;
        }

        @Override
        public T getObject() {
            return instance;
        }
    }
}
