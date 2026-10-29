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
import com.shareloop.reputation.dto.RepeatedPair;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.dto.RequestParticipantProjection;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.entity.UserReputation;
import com.shareloop.reputation.repository.UserReputationRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.UserErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service cửa ngõ của module reputation (Lộ trình 4.7, SRS FR-91, FR-92, FR-94, FR-95, FR-97, FR-98, FR-99,
 * FR-100, BR-U01, BR-U05, BR-U08, BR-U09, BR-U11). Module khác thông báo sự kiện liên quan đến uy tín người dùng qua
 * service này.
 */
@Service
public class ReputationService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** SRS 6.12: tỉ lệ đúng mô tả chỉ hiện khi người đăng có từ 3 giao dịch đã được đánh giá trở lên. */
    static final int MIN_RATED_FOR_AS_DESCRIBED_RATE = 3;

    private final ConfigService configService;
    private final UserReputationRepository userReputationRepository;
    private final ActivityLogService activityLogService;
    private final ActivityLogQueryService activityLogQueryService;
    private final ObjectProvider<ItemService> itemServiceProvider;
    private final NotificationService notificationService;
    private final Clock clock;

    public ReputationService() {
        this(null, null, null, null, (ObjectProvider<ItemService>) null, null, Clock.systemUTC());
    }

    public ReputationService(UserReputationRepository userReputationRepository) {
        this(null, userReputationRepository, null, null, (ObjectProvider<ItemService>) null, null, Clock.systemUTC());
    }

    public ReputationService(ConfigService configService) {
        this(configService, null, null, null, (ObjectProvider<ItemService>) null, null, Clock.systemUTC());
    }

    public ReputationService(ConfigService configService, UserReputationRepository userReputationRepository) {
        this(
                configService,
                userReputationRepository,
                null,
                null,
                (ObjectProvider<ItemService>) null,
                null,
                Clock.systemUTC());
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
                notificationService,
                Clock.systemUTC());
    }

    public ReputationService(
            ConfigService configService,
            UserReputationRepository userReputationRepository,
            ActivityLogService activityLogService,
            ActivityLogQueryService activityLogQueryService,
            ItemService itemService,
            NotificationService notificationService,
            Clock clock) {
        this(
                configService,
                userReputationRepository,
                activityLogService,
                activityLogQueryService,
                itemService != null ? new SimpleObjectProvider<>(itemService) : null,
                notificationService,
                clock);
    }

    @Autowired
    public ReputationService(
            ConfigService configService,
            @Autowired(required = false) UserReputationRepository userReputationRepository,
            @Autowired(required = false) ActivityLogService activityLogService,
            @Autowired(required = false) ActivityLogQueryService activityLogQueryService,
            @Autowired(required = false) ObjectProvider<ItemService> itemServiceProvider,
            @Autowired(required = false) NotificationService notificationService,
            Clock clock) {
        this.configService = configService;
        this.userReputationRepository = userReputationRepository;
        this.activityLogService = activityLogService;
        this.activityLogQueryService = activityLogQueryService;
        this.itemServiceProvider = itemServiceProvider;
        this.notificationService = notificationService;
        this.clock = clock;
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

        // Đọc đúng key mà trang Admin sửa (ConfigKey, viết thường), không đọc key viết hoa cũ của migration
        int diamond = configInt(ConfigKey.REPUTATION_DIAMOND_MIN);
        int gold = configInt(ConfigKey.REPUTATION_GOLD_MIN);
        int silver = configInt(ConfigKey.REPUTATION_SILVER_MIN);

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
     * Bao gồm TrustStars (mặc định 5), hạng (mặc định BRONZE) và tỉ lệ đúng mô tả thang 0–1 (null khi dưới 3 giao
     * dịch đã được đánh giá).
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

        return new ReputationSummary(userId, trustStars, rank, asDescribedRate(userId));
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

        Double accuracyRate = asDescribedRate(userId);

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
     * <p>Chống cày điểm và hồi sao (Issue #150), ngưỡng đọc từ ConfigService:
     * <ul>
     *   <li>BR-U08, FR-98: từ giao dịch hoàn tất thứ 3 giữa cùng một cặp (tính cả hai chiều) trong 90 ngày thì cộng 0
     *   điểm, vẫn ghi nhật ký POINTS kèm lý do.</li>
     *   <li>BR-U11, FR-99: mỗi người tối đa 30 điểm mỗi ngày theo giờ Việt Nam; chạm trần thì chỉ cộng phần còn lại.</li>
     *   <li>BR-U09, FR-94: mỗi giao dịch hoàn tất là một giao dịch sạch; đủ 10 giao dịch sạch liên tiếp mà chưa đủ 5 sao
     *   thì hồi 1 sao và ghi nhật ký STAR_RESTORED.</li>
     * </ul>
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
            donorPts = configInt(ConfigKey.REPUTATION_GIVE_DONOR);
            receiverPts = configInt(ConfigKey.REPUTATION_GIVE_RECEIVER);
        } else {
            int swapPts = configInt(ConfigKey.REPUTATION_SWAP_EACH);
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

        // 5. BR-U08: đếm sau khi đã khoá hai người để giao dịch cùng cặp hoàn tất song song không cùng lọt điểm
        int pairWindowDays = configInt(ConfigKey.REPUTATION_PAIR_WINDOW_DAYS);
        Instant now = clock.instant();
        long pairTransactionNo = userReputationRepository.countCompletedBetween(
                        donorId, receiverId, now.minus(Duration.ofDays(pairWindowDays)), requestId)
                + 1;
        CompletionContext context = new CompletionContext(
                requestId,
                participant.getItemId(),
                participant.getItemTitle(),
                pairTransactionNo,
                pairWindowDays,
                pairTransactionNo >= configInt(ConfigKey.REPUTATION_PAIR_NO_POINTS_FROM),
                LocalDate.ofInstant(now, VIETNAM_ZONE),
                configInt(ConfigKey.DAILY_POINTS_CAP),
                configInt(ConfigKey.REPUTATION_CLEAN_STREAK_FOR_STAR));

        // 6. Cộng điểm trong trần ngày, tính lại hạng ngay lập tức và ghi chuỗi giao dịch sạch
        if (donor != null) {
            applyCompletion(donor, donorPts, "DONOR", "vai trò cho/tặng", context);
        }
        if (receiver != null) {
            applyCompletion(receiver, receiverPts, "RECEIVER", "vai trò nhận/đổi", context);
        }
    }

    private void applyCompletion(
            UserReputation user, int basePoints, String role, String roleLabel, CompletionContext context) {
        int granted =
                user.claimDailyPoints(context.repeatedPair() ? 0 : basePoints, context.dailyCap(), context.today());
        int newTotal = user.getTotalPoints() + granted;
        Rank newRank = getRank(newTotal);
        user.addPoints(granted, newRank);

        int starsBefore = user.getTrustStars();
        boolean starRestored = user.recordCleanTransaction(context.streakForStar());
        userReputationRepository.save(user);

        if (activityLogService == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>(Map.of(
                "points", granted,
                "basePoints", basePoints,
                "totalPoints", newTotal,
                "rank", newRank.name(),
                "role", role,
                "pairTransactionNo", context.pairTransactionNo(),
                "pointsToday", user.getPointsToday()));
        // Nhật ký Admin dùng itemId để hiện tên món thay cho "Yêu cầu #id"
        if (context.itemId() != null) {
            data.put("itemId", context.itemId());
        }
        activityLogService.log(
                ActivityType.POINTS.name(),
                user.getId(),
                "REQUEST",
                context.requestId(),
                data,
                pointsReason(context, roleLabel, basePoints, granted));
        if (starRestored) {
            activityLogService.log(
                    ActivityType.STAR_RESTORED.name(),
                    null,
                    "USER",
                    user.getId(),
                    Map.of(
                            "before",
                            starsBefore,
                            "after",
                            user.getTrustStars(),
                            "cleanStreak",
                            context.streakForStar()),
                    "Hồi 1 sao sau " + context.streakForStar() + " giao dịch sạch liên tiếp");
        }
    }

    private static String pointsReason(CompletionContext context, String roleLabel, int basePoints, int granted) {
        // Gọi giao dịch bằng tên món thay cho "giao dịch #id": người dùng không biết id là gì
        String transaction = (context.itemTitle() == null || context.itemTitle().isBlank()
                        ? "giao dịch"
                        : "giao dịch món “" + context.itemTitle() + "”")
                + " (" + roleLabel + ")";
        if (context.repeatedPair()) {
            return "Không cộng điểm " + transaction + ": giao dịch thứ " + context.pairTransactionNo()
                    + " giữa cùng hai người trong " + context.pairWindowDays() + " ngày";
        }
        if (granted == 0 && basePoints > 0) {
            return "Không cộng điểm " + transaction + ": đã đạt trần " + context.dailyCap() + " điểm trong ngày";
        }
        if (granted < basePoints) {
            return "Cộng " + granted + "/" + basePoints + " điểm hoàn tất " + transaction + ": chạm trần "
                    + context.dailyCap() + " điểm trong ngày";
        }
        return "Cộng " + granted + " điểm hoàn tất " + transaction;
    }

    /**
     * Trừ sao uy tín (TrustStars) của người dùng khi vi phạm (FR-91, FR-92, BR-U04, BR-U05, Issue #114).
     *
     * <p>Quy tắc:
     * <ul>
     *   <li>Trừ số lượng sao chỉ định, không để âm (tối thiểu 0 sao).</li>
     *   <li>Vi phạm làm mất chuỗi giao dịch sạch: clean_streak về 0 (BR-U09, Issue #150).</li>
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
        user.resetCleanStreak();
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
                        "Điểm uy tín của bạn đã về 0. Quyền đăng bài và gửi yêu cầu giao dịch đã bị vô hiệu hoá. Vui lòng gửi khiếu nại để được xem xét cấp sao ân hạn.",
                        // FE không có trang /me/reputation (ra 404), tab uy tín nằm trong trang hồ sơ
                        "/profile?tab=reputation");
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

    /**
     * Cặp người dùng giao dịch với nhau nhiều bất thường, dùng cho trang cảnh báo của Admin (FR-113, BR-U08, Issue
     * #148). Cùng cách đếm cặp với luật không cộng điểm ở {@link #onCompleted}: tính cả hai chiều, chỉ giao dịch
     * COMPLETED.
     *
     * @param since mốc bắt đầu cửa sổ tính cặp (gồm)
     * @param threshold chỉ lấy cặp có số giao dịch lớn hơn số này
     * @return các cặp, nhiều giao dịch nhất lên trước
     */
    @Transactional(readOnly = true)
    public List<RepeatedPair> findRepeatedPairs(Instant since, int threshold) {
        if (userReputationRepository == null) {
            return List.of();
        }
        return userReputationRepository.findRepeatedPairs(since, threshold).stream()
                .map(row -> new RepeatedPair(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        toInstant(row[3])))
                .toList();
    }

    // Native query trả timestamptz dưới dạng Instant hoặc OffsetDateTime tuỳ cấu hình Hibernate, giống ReviewService.
    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }

    /**
     * Tỉ lệ đúng mô tả theo thang 0–1 (0.92 nghĩa là 92%), dùng chung cho hồ sơ, danh sách yêu cầu và /me/reputation để
     * mọi nơi cùng một thang. Dưới {@value #MIN_RATED_FOR_AS_DESCRIBED_RATE} giao dịch đã được đánh giá thì trả null vì
     * quá ít dữ liệu (SRS 6.12).
     */
    private Double asDescribedRate(Long userId) {
        if (userReputationRepository == null
                || userReputationRepository.countRatedCompletedAsDonor(userId) < MIN_RATED_FOR_AS_DESCRIBED_RATE) {
            return null;
        }
        Double percent = userReputationRepository.calculateAsDescribedRate(userId);
        return percent != null ? percent / 100.0 : null;
    }

    // configService null chỉ gặp ở unit test dựng service rút gọn; khi đó dùng giá trị mặc định của ConfigKey.
    private int configInt(ConfigKey key) {
        return configService != null ? configService.getInt(key) : key.getDefaultValue();
    }

    /** Thông số chung của một lần hoàn tất giao dịch, dùng cho cả hai bên. */
    private record CompletionContext(
            long requestId,
            Long itemId,
            String itemTitle,
            long pairTransactionNo,
            int pairWindowDays,
            boolean repeatedPair,
            LocalDate today,
            int dailyCap,
            int streakForStar) {}

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
