package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.common.exception.ErrorCode;
import com.shareloop.item.dto.CreateItemRequest;
import com.shareloop.item.dto.UpdateItemRequest;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.service.BoostService;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.listingfee.service.RenewService;
import com.shareloop.review.dto.ChecklistAnswer;
import com.shareloop.review.service.ReviewService;
import com.shareloop.search.service.AiQuotaService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.service.CreditService;
import com.shareloop.wallet.service.ManualTopUpService;
import com.shareloop.wallet.service.PaymentService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Nền chung cho bộ test tiền (Issue #136): dựng user / bài / media, gọi service thật và kiểm bất biến ví sau mỗi ca.
 *
 * <p>Không @Transactional ở lớp test: mỗi lời gọi service tự commit thật, nên ca đồng thời mới có hiệu lực. Dữ liệu do
 * test tạo ra được dọn trong {@link #cleanUp()}.
 */
// provider=manual để PaymentService tạo đơn nạp MANUAL (cùng cấu hình với ManualTopUpIT).
@TestPropertySource(properties = {"app.payment.provider=manual", "app.payment.mock.enabled=true"})
abstract class MoneyFlowTestBase extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    // Cố ý khai báo giống ManualTopUpIT (cùng provider=manual và spy này) để hai lớp dùng chung một ApplicationContext
    // đã cache: mỗi context giữ một pool kết nối, thêm context mới làm Postgres của test vượt max_connections.
    @MockitoSpyBean
    ActivityLogService audit;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ConfigService configService;

    @Autowired
    CreditService creditService;

    @Autowired
    PaymentService paymentService;

    @Autowired
    ManualTopUpService manualTopUpService;

    @Autowired
    ItemService itemService;

    @Autowired
    PostFeeService postFeeService;

    @Autowired
    ReviewService reviewService;

    @Autowired
    RenewService renewService;

    @Autowired
    BoostService boostService;

    @Autowired
    AiQuotaService aiQuotaService;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Long adminId;

    // Thứ tự xoá theo khoá ngoại: bảng con trước, users sau cùng. Chỉ xoá dữ liệu do test này tạo.
    @AfterEach
    void cleanUp() {
        for (long userId : createdUserIds) {
            jdbc.sql("DELETE FROM activity_logs WHERE actor_id = :id OR (target_type = 'ITEM' AND target_id IN "
                            + "(SELECT id FROM items WHERE donor_id = :id)) OR (target_type = 'PAYMENT_ORDER' AND "
                            + "target_id IN (SELECT id FROM payment_orders WHERE user_id = :id))")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM notifications WHERE user_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM credit_ledger WHERE user_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM item_media_mappings WHERE item_id IN (SELECT id FROM items WHERE donor_id = :id)")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM item_attribute_mappings WHERE item_id IN (SELECT id FROM items WHERE donor_id = :id)")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM items WHERE donor_id = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM media_files WHERE created_by = :id")
                    .param("id", userId)
                    .update();
            jdbc.sql("DELETE FROM payment_orders WHERE user_id = :id OR reviewed_by = :id")
                    .param("id", userId)
                    .update();
        }
        for (long userId : createdUserIds) {
            jdbc.sql("DELETE FROM users WHERE id = :id").param("id", userId).update();
        }
        createdUserIds.clear();
        adminId = null;
    }

    // ---------- bất biến ví ----------

    /**
     * Kiểm bốn bất biến của ví một user; gọi sau MỌI ca.
     *
     * <p>Sổ cái lưu amount ÂM cho dòng trừ phí (writeDebitLedger) và DƯƠNG cho nạp tiền / hoàn phí / ADMIN_ADJUST, nên
     * tổng đại số của amount phải bằng credit_balance.
     */
    protected void assertWalletConsistent(long userId) {
        long balance = balanceOf(userId);
        long held = heldOf(userId);

        // (a) credit_balance = tổng credit_ledger.amount
        long ledgerSum = jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM credit_ledger WHERE user_id = :id")
                .param("id", userId)
                .query(Long.class)
                .single();
        assertThat(ledgerSum)
                .as("(a) tổng credit_ledger.amount của user %d phải bằng credit_balance", userId)
                .isEqualTo(balance);

        // (b) balance_after của dòng mới nhất (id lớn nhất) = credit_balance hiện tại
        Optional<Long> latestBalanceAfter = jdbc.sql(
                        "SELECT balance_after FROM credit_ledger WHERE user_id = :id ORDER BY id DESC LIMIT 1")
                .param("id", userId)
                .query(Long.class)
                .optional();
        latestBalanceAfter.ifPresent(latest -> assertThat(latest)
                .as("(b) balance_after của dòng ledger mới nhất")
                .isEqualTo(balance));
        assertBalanceAfterIsRunningSum(userId);

        // (c) held_credit = tổng pending_fee của các bài đang fee_state = 'HELD'
        long heldByItems = jdbc.sql("SELECT COALESCE(SUM(pending_fee), 0) FROM items "
                        + "WHERE donor_id = :id AND fee_state = 'HELD'")
                .param("id", userId)
                .query(Long.class)
                .single();
        assertThat(held)
                .as("(c) held_credit phải bằng tổng pending_fee của bài đang HELD")
                .isEqualTo(heldByItems);

        // (d) 0 <= held_credit <= credit_balance
        assertThat(held).as("(d) held_credit không âm").isGreaterThanOrEqualTo(0);
        assertThat(balance).as("(d) credit_balance không âm").isGreaterThanOrEqualTo(0);
        assertThat(held).as("(d) held_credit không vượt credit_balance").isLessThanOrEqualTo(balance);
    }

    // Mỗi dòng ledger (theo id) phải có balance_after đúng bằng tổng dồn các amount tới dòng đó.
    private void assertBalanceAfterIsRunningSum(long userId) {
        List<long[]> rows = jdbc.sql("SELECT amount, balance_after FROM credit_ledger WHERE user_id = :id ORDER BY id")
                .param("id", userId)
                .query((rs, rowNumber) -> new long[] {rs.getLong("amount"), rs.getLong("balance_after")})
                .list();
        long runningSum = 0;
        for (long[] row : rows) {
            runningSum += row[0];
            assertThat(row[1]).as("balance_after phải bằng tổng dồn của sổ cái").isEqualTo(runningSum);
        }
    }

    // ---------- dựng dữ liệu ----------

    protected long createUser() {
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        long userId = jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status) "
                        + "VALUES (:email, 'test-only', 'MoneyFlow IT', :phone, 'ACTIVE') RETURNING id")
                .param("email", "moneyflow-" + sequence + "-" + System.nanoTime() + "@test.local")
                .param("phone", String.format("0974%06d", sequence))
                .query(Long.class)
                .single();
        createdUserIds.add(userId);
        return userId;
    }

    protected long adminId() {
        if (adminId == null) {
            adminId = createUser();
        }
        return adminId;
    }

    /**
     * Cộng Credit bằng SQL ở bước dựng dữ liệu, kèm dòng ADMIN_ADJUST để bất biến (a) đúng ngay từ đầu. Chỉ gọi khi
     * ví chưa có khoản giữ nào, vì dòng ledger ghi balance_after = số dư mới.
     */
    protected void grantCredit(long userId, int amount) {
        long balanceAfter = jdbc.sql("UPDATE users SET credit_balance = credit_balance + :amount WHERE id = :id "
                        + "RETURNING credit_balance")
                .param("amount", amount)
                .param("id", userId)
                .query(Long.class)
                .single();
        jdbc.sql("INSERT INTO credit_ledger (user_id, amount, type, balance_after, note, created_by) "
                        + "VALUES (:userId, :amount, 'ADMIN_ADJUST', :balanceAfter, 'Dựng dữ liệu test', :adminId)")
                .param("userId", userId)
                .param("amount", amount)
                .param("balanceAfter", balanceAfter)
                .param("adminId", adminId())
                .update();
    }

    /** Nạp Credit đúng đường MANUAL: tạo đơn, user báo đã chuyển khoản, Admin duyệt. Trả số Credit được cộng. */
    protected int topUpManually(long userId, int credits) {
        int vndPerCredit = configService.getInt(ConfigKey.VND_PER_CREDIT);
        String orderCode = paymentService
                .createTopUpOrder(userId, (long) credits * vndPerCredit)
                .orderCode();
        manualTopUpService.markPaid(userId, orderCode);
        manualTopUpService.approve(adminId(), orderCode, "Đã nhận tiền");
        return credits;
    }

    /** Tạo đơn nạp MANUAL và đã bấm "đã chuyển khoản", chưa duyệt. Trả mã đơn. */
    protected String createSubmittedOrder(long userId, int credits) {
        int vndPerCredit = configService.getInt(ConfigKey.VND_PER_CREDIT);
        String orderCode = paymentService
                .createTopUpOrder(userId, (long) credits * vndPerCredit)
                .orderCode();
        manualTopUpService.markPaid(userId, orderCode);
        return orderCode;
    }

    protected int minTopUpCredits() {
        return configService.getInt(ConfigKey.TOPUP_MIN_CREDITS);
    }

    /** Tạo bài qua ItemService.createItem (giữ phí đăng như API thật). Cần 3 media UPLOADED của chính user. */
    protected long createItem(long userId, OfferType offerType) {
        return createItem(userId, offerType, uploadedImages(userId)).id();
    }

    protected com.shareloop.item.dto.ItemResponse createItem(long userId, OfferType offerType, List<String> images) {
        String desiredItem = offerType == OfferType.SWAP ? "Sách khác" : null;
        CreateItemRequest request = new CreateItemRequest(
                plainCategoryId(),
                districtId(),
                offerType,
                "Sách lập trình Java",
                "Còn mới, đầy đủ các trang",
                desiredItem,
                ItemCondition.GOOD,
                null,
                null,
                images);
        return itemService.createItem(userId, request);
    }

    /** Gửi bản sửa (hoặc gửi lại bài bị từ chối) qua ItemService.updateItem; ảnh giữ nguyên. */
    protected void editItem(long itemId, long userId, OfferType offerType, String title) {
        String desiredItem = offerType == OfferType.SWAP ? "Sách khác" : null;
        UpdateItemRequest request = new UpdateItemRequest(
                plainCategoryId(),
                districtId(),
                offerType,
                title,
                "Mô tả đã chỉnh sửa",
                desiredItem,
                ItemCondition.GOOD,
                null,
                null,
                null,
                null,
                null);
        itemService.updateItem(itemId, userId, request);
    }

    protected void approveItem(long itemId) {
        List<ChecklistAnswer> answers = configService.checklistItems().stream()
                .map(item -> new ChecklistAnswer(item.id(), true))
                .toList();
        reviewService.approve(itemId, answers, adminId());
    }

    protected void rejectItem(long itemId) {
        reviewService.reject(itemId, "Ảnh bị mờ", adminId());
    }

    /** Bài GIVE đã duyệt qua service (phí đăng 0), rồi đã dùng lượt sửa miễn phí: lần sửa kế tiếp mất EDIT_FEE. */
    protected long giveItemWithFreeEditConsumed(long userId) {
        long itemId = createItem(userId, OfferType.GIVE);
        approveItem(itemId);
        editItem(itemId, userId, OfferType.GIVE, "Sửa lần một");
        approveItem(itemId);
        return itemId;
    }

    /** Bài đã duyệt dựng thẳng bằng SQL (dữ liệu ban đầu cho gia hạn / đẩy bài): còn hạn 20 ngày, đã trả phí đăng. */
    protected long insertApprovedItem(long donorId) {
        return jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, "
                        + "status, approved_at, post_fee_paid, fee_state, pending_fee, pending_fee_type, "
                        + "free_edit_used, expire_at) "
                        + "VALUES (:donorId, :categoryId, :areaId, 'GIVE', 'Bài đã duyệt', 'Mô tả', 'GOOD', "
                        + "'APPROVED', now(), true, 'CHARGED', 0, 'POST', false, now() + interval '20 days') "
                        + "RETURNING id")
                .param("donorId", donorId)
                .param("categoryId", plainCategoryId())
                .param("areaId", districtId())
                .query(Long.class)
                .single();
    }

    /** Bài Trao đổi đã duyệt để kiểm tra các luồng phí gia hạn có thu Credit. */
    protected long insertApprovedSwapItem(long donorId) {
        long itemId = insertApprovedItem(donorId);
        jdbc.sql("UPDATE items SET offer_type = 'SWAP', desired_item = 'Sách khác' WHERE id = :itemId")
                .param("itemId", itemId)
                .update();
        return itemId;
    }

    /** Số media UPLOADED (đủ ITEM_PHOTOS_MIN) của user, dạng đường dẫn như FE gửi lên. */
    protected List<String> uploadedImages(long userId) {
        int count = configService.getInt(ConfigKey.ITEM_PHOTOS_MIN);
        List<String> images = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long mediaId = jdbc.sql("INSERT INTO media_files (storage_key, mime_type, size_bytes, created_by, status) "
                            + "VALUES (gen_random_uuid()::text, 'image/png', 10, :userId, 'UPLOADED') RETURNING id")
                    .param("userId", userId)
                    .query(Long.class)
                    .single();
            images.add("/api/v1/media/" + mediaId);
        }
        return images;
    }

    // Danh mục dùng chung: đang bật, được đẩy bài và không đòi "trạng thái hoạt động" (khác nhóm Điện tử).
    private long plainCategoryId() {
        return jdbc.sql("SELECT c.id FROM item_categories c WHERE c.is_active AND NOT c.is_deleted AND c.is_boostable "
                        + "AND NOT EXISTS (SELECT 1 FROM item_attributes a WHERE a.category_id = c.id "
                        + "AND NOT a.is_deleted AND a.name = 'Trạng thái hoạt động') ORDER BY c.id LIMIT 1")
                .query(Long.class)
                .single();
    }

    private long districtId() {
        return jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
    }

    // ---------- đọc trạng thái ----------

    protected long balanceOf(long userId) {
        return jdbc.sql("SELECT credit_balance FROM users WHERE id = :id")
                .param("id", userId)
                .query(Long.class)
                .single();
    }

    protected long heldOf(long userId) {
        return jdbc.sql("SELECT held_credit FROM users WHERE id = :id")
                .param("id", userId)
                .query(Long.class)
                .single();
    }

    protected long ledgerCount(long userId) {
        return jdbc.sql("SELECT count(*) FROM credit_ledger WHERE user_id = :id")
                .param("id", userId)
                .query(Long.class)
                .single();
    }

    protected long ledgerCount(long userId, String type) {
        return jdbc.sql("SELECT count(*) FROM credit_ledger WHERE user_id = :id AND type = :type")
                .param("id", userId)
                .param("type", type)
                .query(Long.class)
                .single();
    }

    /** Số tiền (có dấu) của dòng ledger mới nhất thuộc type cho trước. */
    protected int latestLedgerAmount(long userId, String type) {
        return jdbc.sql(
                        "SELECT amount FROM credit_ledger WHERE user_id = :id AND type = :type ORDER BY id DESC LIMIT 1")
                .param("id", userId)
                .param("type", type)
                .query(Integer.class)
                .single();
    }

    protected String feeStateOf(long itemId) {
        return jdbc.sql("SELECT fee_state FROM items WHERE id = :id")
                .param("id", itemId)
                .query(String.class)
                .single();
    }

    protected String statusOf(long itemId) {
        return jdbc.sql("SELECT status FROM items WHERE id = :id")
                .param("id", itemId)
                .query(String.class)
                .single();
    }

    protected int fee(ConfigKey key) {
        return configService.getInt(key);
    }

    // ---------- lỗi nghiệp vụ ----------

    protected void assertBusinessError(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    /** Ví không đủ Credit khả dụng: mã WALLET_INSUFFICIENT_BALANCE, HTTP 402. */
    protected void assertInsufficientBalance(Throwable thrown) {
        assertBusinessError(thrown, WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        assertThat(((BusinessException) thrown).getErrorCode().status().value()).isEqualTo(402);
    }

    // ---------- chạy đồng thời ----------

    /** Kết quả một luồng: error = null nghĩa là thành công. */
    protected record Outcome(Throwable error) {
        boolean succeeded() {
            return error == null;
        }
    }

    /**
     * Chạy các tác vụ cùng lúc, mỗi tác vụ một luồng riêng (nên mỗi lời gọi service tự mở transaction riêng). Hai
     * CountDownLatch giữ mọi luồng ở vạch xuất phát tới khi đủ người, để các lời gọi thật sự chen nhau.
     */
    protected List<Outcome> runConcurrently(List<Runnable> tasks) throws Exception {
        CountDownLatch allReady = new CountDownLatch(tasks.size());
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                Callable<Outcome> runner = () -> {
                    allReady.countDown();
                    startSignal.await();
                    try {
                        task.run();
                        return new Outcome(null);
                    } catch (Throwable error) {
                        return new Outcome(error);
                    }
                };
                futures.add(executor.submit(runner));
            }
            assertThat(allReady.await(10, TimeUnit.SECONDS)).isTrue();
            startSignal.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(20, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            startSignal.countDown();
            executor.shutdownNow();
        }
    }

    protected long countSucceeded(List<Outcome> outcomes) {
        return outcomes.stream().filter(Outcome::succeeded).count();
    }
}
