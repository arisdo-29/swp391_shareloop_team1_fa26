package com.shareloop.listingfee.service;

import com.shareloop.listingfee.repository.BoostNoticeRow;
import com.shareloop.listingfee.repository.ItemFeeRepository;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Thông báo cho chủ bài về đợt đẩy (C15, SRS 4.6): sắp hết đẩy, đã hết đẩy, và dừng đẩy khi bài bị ẩn. Gửi qua
 * {@code NotificationService}, không đụng bảng notifications trực tiếp.
 *
 * <p>Chống gửi trùng mà không thêm cột: trước khi gửi, hỏi NotificationService đã có thông báo cùng loại, cùng link
 * chưa. Sắp hết đẩy dùng link chứa cả mốc boosted_until (khoá theo bài + kỳ đẩy); đã hết đẩy và dừng đẩy dùng link
 * /items/{id} kèm mốc của đợt. Mua thêm gói thì boosted_until lùi ra sau nên thông báo của kỳ mới vẫn được gửi.
 *
 * <p>Người gọi: {@code BoostNotificationJob} (mỗi giờ) và test.
 */
@Service
public class BoostNotificationService {

    public static final String BOOST_ENDING_SOON = "BOOST_ENDING_SOON";
    public static final String BOOST_ENDED = "BOOST_ENDED";
    public static final String BOOST_STOPPED = "BOOST_STOPPED";

    private static final Logger log = LoggerFactory.getLogger(BoostNotificationService.class);

    // Cùng định dạng giờ với các thông báo khác (ReportService), theo giờ Việt Nam.
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final ItemFeeRepository itemFeeRepository;
    private final NotificationService notificationService;
    private final ConfigService configService;
    private final TransactionTemplate transactionTemplate;

    public BoostNotificationService(
            ItemFeeRepository itemFeeRepository,
            NotificationService notificationService,
            ConfigService configService,
            TransactionTemplate transactionTemplate) {
        this.itemFeeRepository = itemFeeRepository;
        this.notificationService = notificationService;
        this.configService = configService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Báo chủ bài khi bài đang đẩy (ACTIVE_BOOST_CONDITION) còn tối đa boost_ending_notice_hours giờ là hết đẩy, kèm
     * câu cho biết còn mua thêm gói được hay không (xem extendHint). Mỗi bài, mỗi mốc boosted_until chỉ báo một lần.
     * Không ném lỗi nghiệp vụ.
     *
     * @param now mốc hiện tại (job truyền giờ hiện tại, test truyền mốc tự chọn)
     * @return số thông báo đã gửi
     */
    public int notifyEndingSoon(Instant now) {
        int noticeHours = configService.getInt(ConfigKey.BOOST_ENDING_NOTICE_HOURS);
        Instant endsBefore = now.plus(noticeHours, ChronoUnit.HOURS);
        int sentCount = 0;
        for (BoostNoticeRow row : itemFeeRepository.findBoostsEndingBefore(now, endsBefore)) {
            if (runInOwnTransaction(row.getId(), () -> sendEndingSoon(row))) {
                sentCount++;
            }
        }
        return sentCount;
    }

    /**
     * Báo chủ bài khi bài đã hết đẩy, kèm mốc được đẩy lại (boosted_until + boost_cooldown_days). Chỉ xét các đợt kết
     * thúc trong thời gian nghỉ; mỗi bài, mỗi mốc boosted_until chỉ báo một lần. Không ném lỗi nghiệp vụ.
     *
     * @param now mốc hiện tại
     * @return số thông báo đã gửi
     */
    public int notifyEnded(Instant now) {
        int cooldownDays = configService.getInt(ConfigKey.BOOST_COOLDOWN_DAYS);
        Instant endedAfter = now.minus(cooldownDays, ChronoUnit.DAYS);
        int sentCount = 0;
        for (BoostNoticeRow row : itemFeeRepository.findBoostsEndedAfter(now, endedAfter)) {
            if (runInOwnTransaction(row.getId(), () -> sendEnded(row))) {
                sentCount++;
            }
        }
        return sentCount;
    }

    /**
     * Dừng đẩy các bài đang bị ẩn (is_active = false, chưa xoá mềm) rồi báo chủ bài; thời gian đẩy còn lại không
     * hoàn phí (SRS 4.6). Cắt boosted_until về now trước khi báo, trong cùng một transaction cho từng bài. Thời gian
     * nghỉ tính từ mốc bị dừng này. Không ném lỗi nghiệp vụ.
     *
     * @param now mốc hiện tại, cũng là boosted_until mới của bài bị dừng
     * @return số bài đã bị dừng đẩy
     */
    public int stopHiddenBoosts(Instant now) {
        int stoppedCount = 0;
        for (BoostNoticeRow row : itemFeeRepository.findHiddenActiveBoosts(now)) {
            if (runInOwnTransaction(row.getId(), () -> stopAndNotify(row, now))) {
                stoppedCount++;
            }
        }
        return stoppedCount;
    }

    // B24: khoá chống trùng theo bài + đúng mốc boosted_until (nằm trong link), không theo khoảng thời gian. Mua thêm
    // gói hoặc mốc bị chỉnh lại thì link đổi nên kỳ mới vẫn được báo; cùng một mốc thì chỉ báo một lần.
    // Không kiểm thêm thông báo kiểu cũ (link /items/{id}): khoá cũ theo khoảng thời gian chính là chỗ chặn nhầm khi
    // boosted_until bị chỉnh lại; đổi khoá chỉ có thể gửi thêm tối đa một thông báo cho bài đang trong cửa sổ.
    private boolean sendEndingSoon(BoostNoticeRow row) {
        String link = endingSoonLink(row.getId(), row.getBoostedUntil());
        if (notificationService.hasNotificationSince(row.getDonorId(), BOOST_ENDING_SOON, link, Instant.EPOCH)) {
            return false;
        }
        String body = itemLabel(row) + " hết đẩy lúc " + format(row.getBoostedUntil()) + ". " + extendHint(row);
        notificationService.notify(row.getDonorId(), BOOST_ENDING_SOON, "Bài sắp hết thời gian đẩy", body, link);
        return true;
    }

    /**
     * Câu cho biết còn mua thêm gói đẩy được không, theo cùng luật với BoostService: gói mới nối tiếp boosted_until,
     * không vượt boost_started_at + boost_max_days và không vượt expire_at. Không xét số dư và suất đẩy vì mua thêm
     * cho bài đang đẩy không chiếm suất mới, còn số dư có thể đổi trước lúc mua.
     */
    private String extendHint(BoostNoticeRow row) {
        int boostDays = configService.getInt(ConfigKey.BOOST_DAYS);
        Instant extendedUntil = row.getBoostedUntil().plus(boostDays, ChronoUnit.DAYS);
        Instant chainEndsAt = calculateChainEndsAt(row);
        if (chainEndsAt != null && extendedUntil.isAfter(chainEndsAt)) {
            int maxDays = configService.getInt(ConfigKey.BOOST_MAX_DAYS);
            int cooldownDays = configService.getInt(ConfigKey.BOOST_COOLDOWN_DAYS);
            Instant boostAgainAt = row.getBoostedUntil().plus(cooldownDays, ChronoUnit.DAYS);
            return "Đợt đẩy đã đủ tối đa " + maxDays + " ngày nên không mua thêm gói được; bài được đẩy lại từ "
                    + format(boostAgainAt) + ".";
        }
        if (row.getExpireAt() != null && extendedUntil.isAfter(row.getExpireAt())) {
            return "Bài sắp hết hạn hiển thị nên chưa mua thêm gói đẩy được; hãy gia hạn bài trước rồi đẩy tiếp.";
        }
        return "Bạn còn mua thêm được gói đẩy: mua trước lúc hết đẩy thì bài được đẩy tiếp tới " + format(extendedUntil)
                + ".";
    }

    /**
     * Link của thông báo sắp hết đẩy: mở trang Hoạt động với sẵn hộp Đẩy bài của bài đó. Tham số until (epoch giây của
     * boosted_until) chỉ để khoá chống trùng theo từng kỳ đẩy; FE bỏ qua.
     */
    public static String endingSoonLink(long itemId, Instant boostedUntil) {
        return "/activities?boost=" + itemId + "&until=" + boostedUntil.getEpochSecond();
    }

    private boolean sendEnded(BoostNoticeRow row) {
        if (alreadyNotified(row, BOOST_ENDED, row.getBoostedUntil())) {
            return false;
        }
        int cooldownDays = configService.getInt(ConfigKey.BOOST_COOLDOWN_DAYS);
        Instant boostAgainAt = row.getBoostedUntil().plus(cooldownDays, ChronoUnit.DAYS);
        String body = itemLabel(row) + " đã hết thời gian đẩy. Có thể đẩy lại từ " + format(boostAgainAt) + ".";
        notifyOwner(row, BOOST_ENDED, "Bài đã hết thời gian đẩy", body);
        return true;
    }

    // Cắt boosted_until trước: nếu không cắt thì Admin bỏ ẩn xong bài lại được đẩy tiếp phần thời gian còn lại. Sau
    // khi cắt, bài không còn khớp findHiddenActiveBoosts nên lượt sau không quét lại; vẫn kiểm trùng cho chắc.
    private boolean stopAndNotify(BoostNoticeRow row, Instant now) {
        if (itemFeeRepository.endHiddenBoost(row.getId(), now) == 0) {
            return false;
        }
        // Bài đẩy rất cũ không có last_boosted_at: coi mọi thông báo dừng đẩy trước đó của bài là đã gửi.
        Instant since = row.getLastBoostedAt() != null ? row.getLastBoostedAt() : Instant.EPOCH;
        if (!alreadyNotified(row, BOOST_STOPPED, since)) {
            String body = itemLabel(row) + " bị ẩn nên đã dừng đẩy; thời gian đẩy còn lại không được hoàn phí.";
            notifyOwner(row, BOOST_STOPPED, "Bài đã dừng đẩy", body);
        }
        return true;
    }

    private Instant calculateChainEndsAt(BoostNoticeRow row) {
        if (row.getBoostStartedAt() == null) {
            return null;
        }
        int maxDays = configService.getInt(ConfigKey.BOOST_MAX_DAYS);
        return row.getBoostStartedAt().plus(maxDays, ChronoUnit.DAYS);
    }

    private boolean alreadyNotified(BoostNoticeRow row, String type, Instant since) {
        return notificationService.hasNotificationSince(row.getDonorId(), type, itemLink(row), since);
    }

    private void notifyOwner(BoostNoticeRow row, String type, String title, String body) {
        notificationService.notify(row.getDonorId(), type, title, body, itemLink(row));
    }

    // Link cũng là khoá chống trùng: cùng loại + cùng link nghĩa là cùng một bài.
    private static String itemLink(BoostNoticeRow row) {
        return "/items/" + row.getId();
    }

    private static String itemLabel(BoostNoticeRow row) {
        String title = row.getTitle();
        return title == null || title.isBlank() ? "Bài của bạn" : "Bài «" + title + "»";
    }

    private static String format(Instant instant) {
        return TIME_FORMAT.format(instant);
    }

    // Mỗi bài một transaction riêng (giống ItemExpiryService): một bài lỗi chỉ hoàn tác chính nó, ghi log WARN và
    // lượt sau thử lại, không chặn các bài còn lại. TransactionTemplate thay @Transactional vì gọi trong cùng lớp.
    private boolean runInOwnTransaction(long itemId, Supplier<Boolean> work) {
        try {
            Boolean done = transactionTemplate.execute(status -> work.get());
            return Boolean.TRUE.equals(done);
        } catch (RuntimeException exception) {
            log.warn("Boost notification failed for item {}, will retry next run", itemId, exception);
            return false;
        }
    }
}
