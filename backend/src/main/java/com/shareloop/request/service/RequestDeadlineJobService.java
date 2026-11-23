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
