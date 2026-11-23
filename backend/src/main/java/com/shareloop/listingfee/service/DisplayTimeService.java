package com.shareloop.listingfee.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.listingfee.ListingFeeErrorCode;
import com.shareloop.listingfee.dto.DisplayRefund;
import com.shareloop.listingfee.entity.ItemFee;
import com.shareloop.listingfee.repository.ItemFeeRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cộng bù thời gian hiển thị cho chủ bài khi bài phải nằm chờ vì lý do không phải lỗi của họ. Gom một chỗ để mọi
 * đường dùng chung cách cộng và cách ghi nhật ký.
 *
 * <p>Hai đường: bản sửa của bài đã duyệt được duyệt lại thì cộng số giờ chờ duyệt (B1), và giao dịch huỷ khiến bài
 * được nhả thì cộng số giờ bài đã nằm trong giao dịch (B2).
 */
@Service
// REQUIRED: chạy trong transaction của người gọi (duyệt bài), lỗi thì hoàn tác cả khoản cộng bù.
@Transactional
public class DisplayTimeService {

    private final ItemFeeRepository itemFeeRepository;
    private final ConfigService configService;
    private final Clock clock;
    private final ActivityLogService activityLogService;

    public DisplayTimeService(
            ItemFeeRepository itemFeeRepository,
            ConfigService configService,
            Clock clock,
            ActivityLogService activityLogService) {
        this.itemFeeRepository = itemFeeRepository;
        this.configService = configService;
        this.clock = clock;
        this.activityLogService = activityLogService;
    }

    /**
     * Bản sửa của bài đã từng duyệt vừa được duyệt (B1, BD-09): cộng số giờ từ lúc gửi bản sửa tới giờ vào hạn hiển
     * thị, và vào hạn đẩy nếu đợt đẩy còn chạy lúc gửi. Xoá mốc sau khi cộng. Bài duyệt lần đầu không có mốc nên
     * không được cộng gì.
     */
    public void compensateEditReview(ItemFee itemFee) {
        Instant submittedAt = itemFee.getEditSubmittedAt();
        if (submittedAt == null) {
            return;
        }
        Duration waited = Duration.between(submittedAt, clock.instant());
        itemFee.clearEditSubmitted();
        if (waited.isNegative() || waited.isZero()) {
            return;
        }
        itemFee.addDisplayTime(waited, submittedAt);
        activityLogService.log(
                ActivityType.ITEM_DISPLAY_COMPENSATED.name(),
                null,
                "ITEM",
                itemFee.getId(),
                Map.of("hours", waited.toHours(), "reason", "EDIT_REVIEW"),
                "Cộng bù " + waited.toHours() + " giờ chờ duyệt bản sửa vào hạn hiển thị");
    }

    /**
     * Bài vừa được nhả về Đang hiển thị sau khi giao dịch huỷ (B2): cộng vào hạn hiển thị, và hạn đẩy nếu đang chạy,
     * số giờ từ lúc giữ chỗ tới giờ, tối đa reserved_display_refund_max_days ngày. Cộng xong mà bài vẫn quá hạn thì
     * đặt hạn mới bằng lúc nhả cộng reserved_release_grace_hours giờ để chủ bài kịp gia hạn. Đây là chỗ duy nhất tính
     * việc này, mọi đường huỷ đều đi qua ItemService.
     *
     * @return phần đã cộng, rỗng khi không có gì để cộng (chưa có hạn hiển thị hoặc thời gian giữ chỗ bằng 0)
     */
    public Optional<DisplayRefund> refundReservedTime(long itemId, Instant reservedAt) {
        // Khoá dòng items để không lẫn với gia hạn hoặc job hết hạn chạy cùng lúc.
        ItemFee itemFee = itemFeeRepository
                .lockById(itemId)
                .orElseThrow(() -> new BusinessException(ListingFeeErrorCode.LISTINGFEE_ITEM_NOT_FOUND));
        Instant now = clock.instant();
        Duration held = Duration.between(reservedAt, now);
        Duration maxRefund = Duration.ofDays(configService.getInt(ConfigKey.RESERVED_DISPLAY_REFUND_MAX_DAYS));
        Duration refund = held.compareTo(maxRefund) > 0 ? maxRefund : held;
        if (itemFee.getExpireAt() == null || refund.isNegative() || refund.isZero()) {
            return Optional.empty();
        }
        itemFee.addDisplayTime(refund, reservedAt);
        boolean graceApplied = false;
        if (!itemFee.getExpireAt().isAfter(now)) {
            int graceHours = configService.getInt(ConfigKey.RESERVED_RELEASE_GRACE_HOURS);
            itemFee.extendExpireAt(now.plus(Duration.ofHours(graceHours)));
            graceApplied = true;
        }
        activityLogService.log(
                ActivityType.ITEM_DISPLAY_COMPENSATED.name(),
                null,
                "ITEM",
                itemId,
                Map.of("hours", refund.toHours(), "reason", "RESERVATION_CANCELLED"),
                "Trả lại " + refund.toHours() + " giờ bài nằm trong giao dịch đã huỷ vào hạn hiển thị");
        return Optional.of(new DisplayRefund(refund, itemFee.getExpireAt(), graceApplied));
    }
}
