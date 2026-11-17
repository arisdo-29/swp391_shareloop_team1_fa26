package com.shareloop.listingfee.service;

import com.shareloop.listingfee.repository.ExpiryNoticeRow;
import com.shareloop.listingfee.repository.ItemFeeRepository;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Nhắc chủ bài gia hạn trước khi bài hết hạn hiển thị (B19): còn tối đa 48 giờ gửi ITEM_EXPIRING_48H, còn tối đa 24 giờ
 * gửi ITEM_EXPIRING_24H. Gửi qua {@code NotificationService}, không đụng bảng notifications trực tiếp.
 *
 * <p>Chống gửi trùng theo khuôn BoostNotificationService: link chứa id bài và mốc expire_at, nên mỗi mốc nhắc chỉ gửi một
 * lần cho mỗi kỳ hạn; gia hạn xong expire_at đổi nên hạn mới lại được nhắc.
 *
 * <p>Người gọi: {@code ItemExpiryJob} (mỗi giờ) và test.
 */
@Service
public class ItemExpiryNotificationService {

    public static final String ITEM_EXPIRING_48H = "ITEM_EXPIRING_48H";
    public static final String ITEM_EXPIRING_24H = "ITEM_EXPIRING_24H";

    // Hai mốc nhắc gắn liền với tên loại thông báo (ITEM_EXPIRING_48H, ITEM_EXPIRING_24H) mà FE và tài liệu test dùng,
    // nên để hằng số cùng chỗ với tên loại thay vì ConfigKey: đổi số giờ thì tên loại cũng phải đổi theo.
    static final int FIRST_NOTICE_HOURS = 48;
    static final int LAST_NOTICE_HOURS = 24;

    private static final Logger log = LoggerFactory.getLogger(ItemExpiryNotificationService.class);

    // Tham số kỹ thuật (số dòng mỗi lần đọc), không phải con số nghiệp vụ; giống ItemExpiryService.
    private static final int BATCH_SIZE = 100;

    // Cùng định dạng giờ với các thông báo khác, theo giờ Việt Nam.
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final ItemFeeRepository itemFeeRepository;
    private final NotificationService notificationService;
    private final ConfigService configService;
    private final TransactionTemplate transactionTemplate;

    public ItemExpiryNotificationService(
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
     * Gửi nhắc gia hạn cho mọi bài APPROVED đang hiển thị còn tối đa 48 giờ là hết hạn. Bài còn tối đa 24 giờ nhận
     * ITEM_EXPIRING_24H (job lỡ mốc 48 giờ thì chỉ gửi mốc 24 giờ), còn lại nhận ITEM_EXPIRING_48H. Mỗi bài một
     * transaction riêng; bài lỗi ghi log WARN, lượt sau thử lại. Không ném lỗi nghiệp vụ.
     *
     * @param now mốc hiện tại (job truyền giờ hiện tại, test truyền mốc tự chọn)
     * @return số thông báo đã gửi
     */
    public int notifyExpiringSoon(Instant now) {
        // Đọc một lần cho cả lượt để mọi thông báo trong lượt ghi cùng phí và số ngày.
        int renewFee = configService.getInt(ConfigKey.RENEW_FEE);
        int renewDays = configService.getInt(ConfigKey.POST_DISPLAY_DAYS);
        Instant lastNoticeFrom = now.plus(LAST_NOTICE_HOURS, ChronoUnit.HOURS);
        Instant endsBefore = now.plus(FIRST_NOTICE_HOURS, ChronoUnit.HOURS);
        int sentCount = 0;
        long lastSeenId = 0;
        List<ExpiryNoticeRow> batch =
                itemFeeRepository.findItemsExpiringBefore(now, endsBefore, lastSeenId, BATCH_SIZE);
        while (!batch.isEmpty()) {
            for (ExpiryNoticeRow row : batch) {
                String type = row.getExpireAt().isAfter(lastNoticeFrom) ? ITEM_EXPIRING_48H : ITEM_EXPIRING_24H;
                if (sendInOwnTransaction(row, type, renewFee, renewDays)) {
                    sentCount++;
                }
            }
            lastSeenId = batch.get(batch.size() - 1).getId();
            batch = itemFeeRepository.findItemsExpiringBefore(now, endsBefore, lastSeenId, BATCH_SIZE);
        }
        return sentCount;
    }

    /**
     * Link của thông báo nhắc gia hạn: mở trang Hoạt động với sẵn hộp Gia hạn của bài. Tham số until (epoch giây của
     * expire_at) chỉ để khoá chống trùng theo từng kỳ hạn; FE bỏ qua.
     */
    public static String renewLink(long itemId, Instant expireAt) {
        return "/activities?renew=" + itemId + "&until=" + expireAt.getEpochSecond();
    }

    private boolean send(ExpiryNoticeRow row, String type, int renewFee, int renewDays) {
        String link = renewLink(row.getId(), row.getExpireAt());
        if (notificationService.hasNotificationSince(row.getDonorId(), type, link, Instant.EPOCH)) {
            return false;
        }
        String offer = renewFee > 0
                ? "Gia hạn " + renewDays + " ngày với " + renewFee + " Credit."
                : "Gia hạn miễn phí thêm " + renewDays + " ngày.";
        String body = itemLabel(row) + " sẽ hết hạn lúc " + TIME_FORMAT.format(row.getExpireAt()) + ". " + offer;
        notificationService.notify(row.getDonorId(), type, "Bài sắp hết hạn hiển thị", body, link);
        return true;
    }

    private static String itemLabel(ExpiryNoticeRow row) {
        String title = row.getTitle();
        return title == null || title.isBlank() ? "Bài của bạn" : "Bài «" + title + "»";
    }

    // TransactionTemplate thay @Transactional vì gọi trong cùng lớp (không qua proxy), giống BoostNotificationService.
    private boolean sendInOwnTransaction(ExpiryNoticeRow row, String type, int renewFee, int renewDays) {
        try {
            Boolean sent = transactionTemplate.execute(status -> send(row, type, renewFee, renewDays));
            return Boolean.TRUE.equals(sent);
        } catch (RuntimeException exception) {
            log.warn("Expiry reminder failed for item {}, will retry next run", row.getId(), exception);
            return false;
        }
    }
}
