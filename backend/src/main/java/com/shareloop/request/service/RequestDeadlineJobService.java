package com.shareloop.request.service;

import com.shareloop.request.entity.Request;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Các việc nền theo hạn của giao dịch đã chốt lịch (GN-06): đề nghị huỷ quá hạn, và các việc cùng nhóm thêm ở các mục
 * sau. Mỗi giao dịch xử lý trong một transaction riêng, lỗi một giao dịch không làm hỏng cả lượt. Logic nghiệp vụ nằm
 * ở RequestService, class này chỉ tìm giao dịch tới hạn.
 */
@Service
public class RequestDeadlineJobService {

    private static final Logger log = LoggerFactory.getLogger(RequestDeadlineJobService.class);

    // Xử lý theo lô nhỏ tối đa 100 bản ghi mỗi lượt như RequestJobService
    private static final int BATCH_SIZE = 100;

    private final RequestRepository requestRepository;
    private final RequestService requestService;
    private final ConfigService configService;
    private final TransactionTemplate transactionTemplate;

    public RequestDeadlineJobService(
            RequestRepository requestRepository,
            RequestService requestService,
            ConfigService configService,
            TransactionTemplate transactionTemplate) {
        this.requestRepository = requestRepository;
        this.requestService = requestService;
        this.configService = configService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Huỷ các đề nghị huỷ mà bên kia im lặng quá cancel_request_response_hours giờ (GN-06).
     *
     * @return số giao dịch đã huỷ
     */
    public int expireCancelRequests(Instant now) {
        int responseHours = configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS);
        Instant cutoff = now.minus(responseHours, ChronoUnit.HOURS);

        int count = 0;
        long lastSeenId = 0;
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        List<Request> batch = requestRepository.findExpiredCancelRequests(cutoff, lastSeenId, pageable);
        while (!batch.isEmpty()) {
            for (Request request : batch) {
                if (processInOwnTransaction(
                        request.getId(), () -> requestService.expireCancelRequest(request.getId(), now))) {
                    count++;
                }
            }
            lastSeenId = batch.get(batch.size() - 1).getId();
            batch = requestRepository.findExpiredCancelRequests(cutoff, lastSeenId, pageable);
        }
        return count;
    }

    /**
     * Giữ lịch cũ cho các đề nghị dời lịch đã hết hạn (GN-11).
     *
     * @return số đề nghị đã hết hạn
     */
    public int expireReschedules(Instant now) {
        int responseHours = configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS);
        Instant cutoff = now.minus(responseHours, ChronoUnit.HOURS);

        int count = 0;
        long lastSeenId = 0;
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        List<Request> batch = requestRepository.findExpiredReschedules(now, cutoff, lastSeenId, pageable);
        while (!batch.isEmpty()) {
            for (Request request : batch) {
                if (processInOwnTransaction(
                        request.getId(), () -> requestService.expireReschedule(request.getId(), now))) {
                    count++;
                }
            }
            lastSeenId = batch.get(batch.size() - 1).getId();
            batch = requestRepository.findExpiredReschedules(now, cutoff, lastSeenId, pageable);
        }
        return count;
    }

    /**
     * Tự ghi nhận Đã nhận cho từng chiều Giao hàng đã gửi mà bên nhận im lặng quá hạn (GN-09, GN-12).
     *
     * @return số giao dịch có chiều được tự ghi nhận
     */
    public int autoReceiveShippingLegs(Instant now) {
        int days = configService.getInt(ConfigKey.AUTO_CONFIRM_DAYS_SHIPPING);
        Instant cutoff = now.minus(days, ChronoUnit.DAYS);
        return forEach(
                (after, page) -> requestRepository.findShippingLegsOverdue(cutoff, after, page),
                requestId -> requestService.autoReceiveShippingLegs(requestId, now));
    }

    /**
     * Nhắc hạn gửi hàng (GN-12): tới ngày gửi dự kiến và khi quá hạn gửi.
     *
     * @return số giao dịch đã gửi nhắc
     */
    public int remindShipping(Instant now) {
        return forEach(
                (after, page) -> requestRepository.findShippingWithUnsentLeg(now, after, page),
                requestId -> requestService.remindShipping(requestId, now));
    }

    /**
     * Giao dịch Giao hàng kẹt một chiều: chiều kia đã nhận, chiều này chưa gửi quá hạn cộng số ngày chờ thì chuyển sang
     * DISPUTED và mở báo cáo để Admin xử lý (GN-12).
     *
     * @return số giao dịch đã chuyển sang DISPUTED
     */
    public int openShippingOneLegStuck(Instant now) {
        int staleDays = configService.getInt(ConfigKey.CONFIRMED_STALE_CANCEL_DAYS);
        int sendDays = configService.getInt(ConfigKey.SHIPPING_SEND_DEADLINE_DAYS);
        Instant cutoff = now.minus(staleDays + sendDays, ChronoUnit.DAYS);
        return forEach(
                (after, page) -> requestRepository.findShippingOneLegStuck(cutoff, after, page),
                requestId -> requestService.openShippingOneLegStuck(requestId, now));
    }

    /**
     * Tự huỷ giao dịch Gặp trực tiếp đã chốt lịch mà quá giờ hẹn confirmed_stale_cancel_days ngày không ai bấm gì
     * (GN-12).
     *
     * @return số giao dịch đã huỷ
     */
    public int cancelStaleConfirmed(Instant now) {
        int staleDays = configService.getInt(ConfigKey.CONFIRMED_STALE_CANCEL_DAYS);
        int sendDays = configService.getInt(ConfigKey.SHIPPING_SEND_DEADLINE_DAYS);
        int inPerson = forEachConfirmedPastMeeting(
                now.minus(staleDays, ChronoUnit.DAYS),
                requestId -> requestService.cancelStaleConfirmed(requestId, now));
        // Giao hàng: chưa chiều nào gửi, quá hạn gửi cộng số ngày chờ
        Instant shippingCutoff = now.minus(staleDays + sendDays, ChronoUnit.DAYS);
        int shipping = forEach(
                (after, page) -> requestRepository.findShippingNothingSent(shippingCutoff, after, page),
                requestId -> requestService.cancelStaleConfirmed(requestId, now));
        return inPerson + shipping;
    }

    /**
     * Nhắc một lần khi đã qua giờ hẹn 24 giờ mà chưa bên nào bấm gì (GN-12).
     *
     * @return số giao dịch đã gửi nhắc
     */
    public int remindStaleConfirmed(Instant now) {
        return forEachConfirmedPastMeeting(
                now.minus(1, ChronoUnit.DAYS), requestId -> requestService.remindStaleConfirmed(requestId, now));
    }

    // Duyệt theo lô nhỏ các giao dịch do truy vấn trả về, mỗi giao dịch một transaction riêng.
    private int forEach(
            java.util.function.BiFunction<Long, Pageable, List<Request>> finder,
            java.util.function.LongPredicate action) {
        int count = 0;
        long lastSeenId = 0;
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        List<Request> batch = finder.apply(lastSeenId, pageable);
        while (!batch.isEmpty()) {
            for (Request request : batch) {
                if (processInOwnTransaction(request.getId(), () -> action.test(request.getId()))) {
                    count++;
                }
            }
            lastSeenId = batch.get(batch.size() - 1).getId();
            batch = finder.apply(lastSeenId, pageable);
        }
        return count;
    }

    private int forEachConfirmedPastMeeting(Instant cutoff, java.util.function.LongPredicate action) {
        int count = 0;
        long lastSeenId = 0;
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        List<Request> batch = requestRepository.findConfirmedPastMeeting(cutoff, lastSeenId, pageable);
        while (!batch.isEmpty()) {
            for (Request request : batch) {
                if (processInOwnTransaction(request.getId(), () -> action.test(request.getId()))) {
                    count++;
                }
            }
            lastSeenId = batch.get(batch.size() - 1).getId();
            batch = requestRepository.findConfirmedPastMeeting(cutoff, lastSeenId, pageable);
        }
        return count;
    }

    private boolean processInOwnTransaction(long requestId, java.util.function.BooleanSupplier action) {
        try {
            Boolean result = transactionTemplate.execute(status -> action.getAsBoolean());
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException exception) {
            log.warn("Deadline job failed for request {}, skipping: {}", requestId, exception.getMessage(), exception);
            return false;
        }
    }
}
