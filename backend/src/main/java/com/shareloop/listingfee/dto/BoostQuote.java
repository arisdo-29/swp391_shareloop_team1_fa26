package com.shareloop.listingfee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Báo giá một lần đẩy bài (GET /items/{id}/boost-quote). Chỉ đọc: không trừ tiền, không đổi dữ liệu. Số liệu tính
 * bằng đúng các hàm mà {@code BoostService.boost} dùng, nên số FE thấy và số bị trừ khi đẩy luôn khớp nhau. Mọi mốc
 * thời gian đều do backend tính sẵn để FE chỉ hiển thị. Không trả số dư ví.
 *
 * @param fee số Credit sẽ bị trừ nếu đẩy ngay (boost_fee)
 * @param newBoostedUntil thời điểm hết đẩy mới nếu đẩy ngay
 * @param allowed có được đẩy ngay bây giờ không
 * @param reason lý do không được đẩy (tiếng Việt, hiển thị thẳng cho người dùng); null khi allowed = true
 * @param reasonCode mã lỗi mà POST /boost sẽ trả (để FE chọn popup); null khi allowed = true
 * @param boostStartedAt mốc bắt đầu đợt đẩy: của đợt hiện tại nếu bài đang đẩy, hoặc now nếu là đợt mới
 * @param chainEndsAt mốc đẩy muộn nhất của đợt = boostStartedAt + boost_max_days
 * @param cooldownUntil mốc được mở đợt đẩy mới, cộng boost_cooldown_days vào một trong hai mốc: được đẩy thì cộng vào
 *     newBoostedUntil (nếu mua gói này rồi không mua thêm); không được đẩy mà bài đang đẩy hoặc đang trong thời gian
 *     nghỉ thì cộng vào boosted_until hiện tại; null khi bài chưa từng đẩy hoặc đã nghỉ đủ
 * @param extending bài đang được đẩy (mua thêm, không chiếm suất mới) hay không
 * @param slotsLeft số suất đẩy còn trống trên toàn hệ thống, không âm
 * @param earliestSlotAt lúc sớm nhất có thêm suất trống; chỉ có khi slotsLeft = 0
 * @param userActiveBoosts số bài của người gọi đang được đẩy
 * @param userEarliestBoostEndAt lúc sớm nhất một bài của người gọi hết đẩy; chỉ có khi đã chạm boost_max_per_user
 * @param expireAt hạn hiển thị của bài; null khi bài chưa có hạn
 */
@Schema(description = "Báo giá một lần đẩy bài: phí, các mốc thời gian, suất còn trống và có được đẩy không")
public record BoostQuote(
        @Schema(description = "Số Credit sẽ bị trừ khi đẩy", example = "5")
        int fee,

        @Schema(description = "Thời điểm hết đẩy mới nếu đẩy ngay, ISO-8601 UTC", example = "2026-11-09T08:00:00Z")
        Instant newBoostedUntil,

        @Schema(description = "Có được đẩy ngay bây giờ không", example = "true")
        boolean allowed,

        @Schema(description = "Lý do không được đẩy; null khi được phép", example = "null", nullable = true)
        String reason,

        @Schema(
                description = "Mã lỗi khi không được đẩy, vd LISTINGFEE_BOOST_SOLD_OUT; null khi được phép",
                example = "null",
                nullable = true)
        String reasonCode,

        @Schema(
                description = "Mốc bắt đầu đợt đẩy (đợt hiện tại, hoặc now nếu là đợt mới)",
                example = "2026-11-06T08:00:00Z")
        Instant boostStartedAt,

        @Schema(
                description = "Mốc đẩy muộn nhất của đợt = boostStartedAt + boost_max_days",
                example = "2026-11-12T08:00:00Z")
        Instant chainEndsAt,

        @Schema(
                description = "Mốc được mở đợt đẩy mới. Được đẩy: newBoostedUntil + boost_cooldown_days."
                        + " Không được đẩy mà bài đang đẩy hoặc đang nghỉ: boosted_until hiện tại"
                        + " + boost_cooldown_days. null khi chưa từng đẩy hoặc đã nghỉ đủ",
                example = "2026-11-12T08:00:00Z",
                nullable = true)
        Instant cooldownUntil,

        @Schema(description = "Bài đang được đẩy (mua thêm, không chiếm suất mới)", example = "false")
        boolean extending,

        @Schema(description = "Số suất đẩy còn trống trên toàn hệ thống", example = "12")
        long slotsLeft,

        @Schema(
                description = "Lúc sớm nhất có thêm suất trống; chỉ có khi slotsLeft = 0",
                example = "null",
                nullable = true)
        Instant earliestSlotAt,

        @Schema(description = "Số bài của bạn đang được đẩy", example = "1")
        long userActiveBoosts,

        @Schema(
                description = "Lúc sớm nhất một bài của bạn hết đẩy; chỉ có khi đã đủ số bài đang đẩy tối đa",
                example = "null",
                nullable = true)
        Instant userEarliestBoostEndAt,

        @Schema(description = "Hạn hiển thị của bài", example = "2026-12-06T08:00:00Z", nullable = true)
        Instant expireAt) {}
