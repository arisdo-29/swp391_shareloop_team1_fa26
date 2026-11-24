package com.shareloop.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.shareloop.admin.dto.AdminItemFullDetail;
import com.shareloop.admin.dto.AdminItemHistoryEntry;
import com.shareloop.admin.dto.AdminItemHistoryEntry.ActorRole;
import com.shareloop.admin.dto.AdminItemRow;
import com.shareloop.admin.service.AdminItemService;
import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.service.ItemService;
import com.shareloop.report.dto.ConcludeReportRequest;
import com.shareloop.report.dto.ReportDecision;
import com.shareloop.report.service.ReportService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.user.service.AdminUserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý bài đăng của Admin trên PostgreSQL thật: lọc, ẩn, hiện lại, và các đường tự động không được hiện lại bài
 * do Admin ẩn. @Transactional: mỗi test rollback nên không cần tự dọn.
 */
@Transactional
class AdminItemIT extends IntegrationTest {

    @Autowired
    AdminItemService adminItemService;

    @Autowired
    ItemService itemService;

    @Autowired
    AdminUserService adminUserService;

    @Autowired
    ReportService reportService;

    @Autowired
    ActivityLogService activityLogService;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager entityManager;

    // ---------- Danh sách ----------

    @Test
    void list_filtersByStatusVisibilityKeywordAndOwner() {
        // given
        long admin = user("Quản trị");
        long owner = user("Chủ Sách Hiếm");
        long other = user("Người Khác");
        long visible = item(owner, "Sách cũ giá rẻ", "APPROVED");
        long hidden = item(owner, "Sách bị ẩn", "APPROVED");
        long pending = item(other, "Áo mưa", "PENDING_REVIEW");
        adminItemService.hide(admin, hidden, "Vi phạm nội quy");
        entityManager.flush();

        // when
        PageResponse<AdminItemRow> hiddenOnly =
                adminItemService.list(null, false, null, null, owner, null, null, null, false, 0, 50);
        PageResponse<AdminItemRow> pendingOnly = adminItemService.list(
                ItemStatus.PENDING_REVIEW, null, null, null, other, null, null, null, false, 0, 50);
        PageResponse<AdminItemRow> byTitle =
                adminItemService.list(null, null, null, null, owner, "SÁCH CŨ", null, null, false, 0, 50);
        PageResponse<AdminItemRow> byOwnerName =
                adminItemService.list(null, true, null, null, null, "chủ sách hiếm", null, null, false, 0, 50);

        // then
        assertThat(hiddenOnly.items()).extracting(AdminItemRow::id).containsExactly(hidden);
        assertThat(pendingOnly.items()).extracting(AdminItemRow::id).containsExactly(pending);
        assertThat(byTitle.items()).extracting(AdminItemRow::id).containsExactly(visible);
        assertThat(byOwnerName.items()).extracting(AdminItemRow::id).containsExactly(visible);
    }

    @Test
    void list_showsWhoHidTheItem() {
        // given: bài một do Admin ẩn, bài hai do chủ bài bị khoá, bài ba ẩn vì lý do khác (báo cáo hoặc hệ thống)
        long admin = user("Quản trị");
        long ownerA = user("Chủ A");
        long lockedOwner = user("Chủ B");
        long ownerC = user("Chủ C");
        long byAdmin = item(ownerA, "Ẩn bởi Admin", "APPROVED");
        long byLock = item(lockedOwner, "Ẩn do khoá", "APPROVED");
        long other = item(ownerC, "Ẩn do báo cáo", "APPROVED");
        adminItemService.hide(admin, byAdmin, "Hàng cấm");
        adminUserService.lock(admin, lockedOwner, "Vi phạm");
        jdbc.update("UPDATE items SET is_active = false WHERE id = ?", other);
        entityManager.flush();
        entityManager.clear();

        // when
        PageResponse<AdminItemRow> page =
                adminItemService.list(null, false, null, null, null, null, null, null, false, 0, 50);

        // then
        assertThat(sourceOf(page, byAdmin)).isEqualTo("ADMIN");
        assertThat(sourceOf(page, byLock)).isEqualTo("OWNER_LOCKED");
        assertThat(sourceOf(page, other)).isEqualTo("OTHER");
    }

    @Test
    void list_excludesSoftDeletedItemsAndFiltersBoostedAndDate() {
        // given
        long owner = user("Chủ bài");
        long normal = item(owner, "Bài thường", "APPROVED");
        long boosted = item(owner, "Bài đang đẩy", "APPROVED");
        long deleted = item(owner, "Bài đã xoá", "APPROVED");
        jdbc.update("UPDATE items SET boosted_until = now() + interval '1 day' WHERE id = ?", boosted);
        jdbc.update("UPDATE items SET is_deleted = true WHERE id = ?", deleted);
        entityManager.flush();
        entityManager.clear();

        // when
        PageResponse<AdminItemRow> all =
                adminItemService.list(null, null, null, null, owner, null, null, null, false, 0, 50);
        PageResponse<AdminItemRow> boostedOnly =
                adminItemService.list(null, null, null, null, owner, null, null, null, true, 0, 50);
        PageResponse<AdminItemRow> oldRange = adminItemService.list(
                null, null, null, null, owner, null, LocalDate.of(2001, 1, 1), LocalDate.of(2001, 1, 31), false, 0, 50);

        // then
        assertThat(all.items()).extracting(AdminItemRow::id).containsExactlyInAnyOrder(normal, boosted);
        assertThat(boostedOnly.items()).extracting(AdminItemRow::id).containsExactly(boosted);
        assertThat(boostedOnly.items().get(0).boosting()).isTrue();
        assertThat(oldRange.items()).isEmpty();
    }

    // ---------- Ẩn ----------

    @Test
    void hide_approvedItem_cancelsRequestsNotifiesOwnerLogsAndKeepsPostFee() {
        // given: bài X của chủ bài, A xin X, và món X còn là món đổi trong yêu cầu của chủ bài tới bài Q của người khác
        long admin = user("Quản trị");
        long owner = user("Chủ bài");
        long requester = user("Người xin");
        long otherDonor = user("Chủ bài Q");
        long itemX = item(owner, "Bài X", "APPROVED");
        long itemQ = item(otherDonor, "Bài Q", "APPROVED");
        long toX = request(itemX, requester, null, "GIVE", "PENDING");
        long offeringX = request(itemQ, owner, itemX, "SWAP", "PENDING");
        jdbc.update(
                "INSERT INTO credit_ledger (user_id, amount, type, balance_after, item_id) VALUES (?, -5, 'POST_FEE', 15, ?)",
                owner,
                itemX);
        entityManager.flush();

        // when
        AdminItemFullDetail detail = adminItemService.hide(admin, itemX, "  Hàng giả theo báo cáo  ");

        // then: bài ẩn có cờ, hai yêu cầu tự huỷ, không hoàn phí
        entityManager.flush();
        assertThat(detail.summary().active()).isFalse();
        assertThat(detail.summary().hiddenSource()).isEqualTo("ADMIN");
        assertThat(detail.hiddenReason()).isEqualTo("Hàng giả theo báo cáo");
        assertThat(flagOf(itemX)).isTrue();
        assertThat(statusOfRequest(toX)).isEqualTo("CANCELLED");
        assertThat(statusOfRequest(offeringX)).isEqualTo("CANCELLED");
        assertThat(count("SELECT count(*) FROM credit_ledger WHERE item_id = ?", itemX))
                .isEqualTo(1);
        assertThat(detail.summary().postFeePaid()).isEqualTo(5);
        assertThat(detail.visibilityHistory()).hasSize(1);

        // và: chủ bài nhận thông báo kèm lý do, nhật ký ghi hành động
        assertThat(jdbc.queryForObject(
                        "SELECT body FROM notifications WHERE user_id = ? AND type = 'ITEM_HIDDEN_BY_ADMIN'",
                        String.class,
                        owner))
                .contains("Bài X")
                .contains("Hàng giả theo báo cáo");
        assertThat(count(
                        "SELECT count(*) FROM activity_logs WHERE type = 'ADMIN_ITEM_HIDDEN' AND target_id = ?", itemX))
                .isEqualTo(1);
    }

    @Test
    void hide_whenItemReserved_throwsHideNotAllowed() {
        // given
        long admin = user("Quản trị");
        long owner = user("Chủ bài");
        long itemId = item(owner, "Bài đã giữ chỗ", "RESERVED");

        // when, then
        assertThatThrownBy(() -> adminItemService.hide(admin, itemId, "Vi phạm nội quy"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode()).isEqualTo(ItemErrorCode.ITEM_HIDE_NOT_ALLOWED));
        assertThat(flagOf(itemId)).isFalse();
    }

    @Test
    void hide_whenRunningTransactionExists_throwsAndKeepsRequests() {
        // given: bài còn APPROVED nhưng đã có giao dịch được chọn và đang chạy
        long admin = user("Quản trị");
        long owner = user("Chủ bài");
        long requester = user("Người nhận");
        long itemId = item(owner, "Bài có giao dịch", "APPROVED");
        long running = request(itemId, requester, null, "GIVE", "AWAITING_LOGISTICS");

        // when, then
        assertThatThrownBy(() -> adminItemService.hide(admin, itemId, "Vi phạm nội quy"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode()).isEqualTo(ItemErrorCode.ITEM_HIDE_NOT_ALLOWED));
        assertThat(statusOfRequest(running)).isEqualTo("AWAITING_LOGISTICS");
        AdminItemFullDetail detail = adminItemService.getDetail(itemId);
        assertThat(detail.hasRunningTransaction()).isTrue();
        assertThat(detail.runningRequestId()).isEqualTo(running);
    }

    @Test
    void hide_whenAlreadyHidden_throwsAlreadyHidden() {
        // given
        long admin = user("Quản trị");
        long owner = user("Chủ bài");
        long itemId = item(owner, "Bài đã ẩn", "APPROVED");
        adminItemService.hide(admin, itemId, "Lần một");

        // when, then
        assertThatThrownBy(() -> adminItemService.hide(admin, itemId, "Lần hai"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ItemErrorCode.ITEM_ALREADY_HIDDEN));
    }

    // ---------- Hiện lại ----------

    @Test
    void unhide_clearsFlagShowsItemNotifiesOwnerAndLogs() {
        // given
        long admin = user("Quản trị");
        long owner = user("Chủ bài");
        long itemId = item(owner, "Bài cần hiện lại", "APPROVED");
        adminItemService.hide(admin, itemId, "Ẩn nhầm");

        // when
        AdminItemFullDetail detail = adminItemService.unhide(admin, itemId, "Đã kiểm tra lại");

        // then
        entityManager.flush();
        assertThat(detail.summary().active()).isTrue();
        assertThat(flagOf(itemId)).isFalse();
        assertThat(detail.visibilityHistory()).hasSize(2);
        assertThat(count(
                        "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'ITEM_UNHIDDEN_BY_ADMIN'",
                        owner))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM activity_logs WHERE type = 'ADMIN_ITEM_UNHIDDEN' AND target_id = ?",
                        itemId))
                .isEqualTo(1);
    }

    @Test
    void unhide_whenItemVisible_throwsNotHidden() {
        long admin = user("Quản trị");
        long itemId = item(user("Chủ bài"), "Bài đang hiện", "APPROVED");

        assertThatThrownBy(() -> adminItemService.unhide(admin, itemId, "Không cần"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ItemErrorCode.ITEM_NOT_HIDDEN));
    }

    @Test
    void unhide_whenOwnerLocked_throwsUnhideNotAllowed() {
        // given: bài bị ẩn khi khoá tài khoản chủ bài
        long admin = user("Quản trị");
        long owner = user("Chủ bài bị khoá");
        long itemId = item(owner, "Bài của người bị khoá", "APPROVED");
        adminUserService.lock(admin, owner, "Vi phạm");
        entityManager.flush();

        // when, then
        assertThatThrownBy(() -> adminItemService.unhide(admin, itemId, "Thử hiện lại"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode()).isEqualTo(ItemErrorCode.ITEM_UNHIDE_NOT_ALLOWED));
        assertThat(isActive(itemId)).isFalse();
    }

    // ---------- Bài do Admin ẩn không bị hiện lại bởi đường nào khác ----------

    @Test
    void dismissedReport_doesNotShowItemHiddenByAdmin_butShowsOrdinaryHiddenItem() {
        // given: hai bài cùng bị ẩn, một do Admin, một do tự ẩn vì báo cáo; mỗi bài có một báo cáo đang mở
        long admin = user("Quản trị");
        long owner = user("Chủ bài");
        long byAdmin = item(owner, "Bài Admin ẩn", "APPROVED");
        long ordinary = item(owner, "Bài tự ẩn", "APPROVED");
        adminItemService.hide(admin, byAdmin, "Vi phạm nội quy");
        jdbc.update("UPDATE items SET is_active = false WHERE id = ?", ordinary);
        long reportOnAdminHidden = report(user("Người báo 1"), byAdmin);
        long reportOnOrdinary = report(user("Người báo 2"), ordinary);
        entityManager.flush();
        entityManager.clear();

        // when: Admin bác bỏ cả hai báo cáo
        reportService.conclude(
                admin, reportOnAdminHidden, new ConcludeReportRequest(ReportDecision.DISMISS, "Không có cơ sở"));
        reportService.conclude(
                admin, reportOnOrdinary, new ConcludeReportRequest(ReportDecision.DISMISS, "Không có cơ sở"));

        // then
        assertThat(isActive(byAdmin)).isFalse();
        assertThat(isActive(ordinary)).isTrue();
    }

    @Test
    void reapprovalAfterEdit_doesNotShowItemHiddenByAdmin() {
        // given: bài đã duyệt hôm qua, báo cáo được chấp nhận sau đó, Admin ẩn bài, chủ bài sửa nên bài chờ duyệt lại
        long admin = user("Quản trị");
        long owner = user("Chủ bài");
        long reporter = user("Người báo");
        long itemId = item(owner, "Bài được duyệt lại", "APPROVED");
        jdbc.update("UPDATE items SET approved_at = now() - interval '1 day' WHERE id = ?", itemId);
        jdbc.update(
                "INSERT INTO reports (reporter_id, target_type, item_id, target_user_id, reason, description, stage,"
                        + " status, stars_deducted, resolved_at) VALUES (?, 'ITEM', ?, ?, 'COUNTERFEIT', 'Hàng giả',"
                        + " 'BEFORE', 'RESOLVED', 1, now() - interval '1 hour')",
                reporter,
                itemId,
                owner);
        adminItemService.hide(admin, itemId, "Vi phạm nội quy");
        jdbc.update("UPDATE items SET status = 'PENDING_REVIEW' WHERE id = ?", itemId);
        entityManager.flush();
        entityManager.clear();

        // when: Admin duyệt lại bài
        itemService.markApproved(itemId);

        // then: bài đã APPROVED nhưng vẫn ẩn vì do Admin ẩn
        entityManager.flush();
        assertThat(statusOf(itemId)).isEqualTo("APPROVED");
        assertThat(isActive(itemId)).isFalse();
    }

    @Test
    void unlockingOwner_doesNotShowItemHiddenByAdminBeforeLock() {
        // given: chủ bài có một bài Admin đã ẩn và một bài đang hiện; sau đó tài khoản bị khoá
        long admin = user("Quản trị");
        long owner = user("Chủ bài");
        long hiddenByAdmin = item(owner, "Bài Admin ẩn", "APPROVED");
        long visible = item(owner, "Bài đang hiện", "APPROVED");
        adminItemService.hide(admin, hiddenByAdmin, "Vi phạm nội quy");
        adminUserService.lock(admin, owner, "Vi phạm");

        // when
        adminUserService.unlock(admin, owner, "Đã giải trình");

        // then: bài ẩn lúc khoá hiện lại, bài Admin ẩn vẫn ẩn
        assertThat(isActive(visible)).isTrue();
        assertThat(isActive(hiddenByAdmin)).isFalse();
    }

    @Test
    void unhideItems_skipsItemsHiddenByAdmin() {
        // given
        long admin = user("Quản trị");
        long owner = user("Chủ bài");
        long hiddenByAdmin = item(owner, "Bài Admin ẩn", "APPROVED");
        long ordinary = item(owner, "Bài tự ẩn", "APPROVED");
        adminItemService.hide(admin, hiddenByAdmin, "Vi phạm nội quy");
        jdbc.update("UPDATE items SET is_active = false WHERE id = ?", ordinary);
        entityManager.flush();
        entityManager.clear();

        // when
        itemService.unhideItems(List.of(hiddenByAdmin, ordinary));

        // then
        assertThat(isActive(hiddenByAdmin)).isFalse();
        assertThat(isActive(ordinary)).isTrue();
    }

    // ---------- Chi tiết ----------

    @Test
    void getDetail_includesFeeLinesRequestsReportsAndHistory() {
        // given
        long admin = user("Quản trị");
        long owner = user("Chủ bài");
        long requester = user("Người xin");
        long itemId = item(owner, "Bài có đủ thông tin", "APPROVED");
        long requestId = request(itemId, requester, null, "GIVE", "PENDING");
        long reportId = report(user("Người báo"), itemId);
        jdbc.update(
                "INSERT INTO credit_ledger (user_id, amount, type, balance_after, item_id) VALUES (?, -5, 'POST_FEE', 15, ?)",
                owner,
                itemId);
        activityLogService.log(ActivityType.REVIEW_REJECT.code(), admin, "ITEM", itemId, Map.of(), "Ảnh chưa rõ.");
        entityManager.flush();

        // when
        AdminItemFullDetail detail = adminItemService.getDetail(itemId);

        // then
        assertThat(detail.summary().ownerName()).isEqualTo("Chủ bài");
        assertThat(detail.summary().pendingRequestCount()).isEqualTo(1);
        assertThat(detail.feeLines()).hasSize(1);
        assertThat(detail.requests()).extracting(row -> row.id()).containsExactly(requestId);
        assertThat(detail.requests().get(0).receiverName()).isEqualTo("Người xin");
        assertThat(detail.reports()).extracting(row -> row.id()).containsExactly(reportId);
        assertThat(detail.hasRunningTransaction()).isFalse();
        assertThat(detail.hiddenReason()).isNull();
        assertThat(detail.postHistory())
                .extracting(
                        AdminItemHistoryEntry::action,
                        AdminItemHistoryEntry::actorName,
                        AdminItemHistoryEntry::actorRole,
                        AdminItemHistoryEntry::reason)
                .containsExactlyInAnyOrder(
                        tuple("Từ chối bài", "Quản trị", ActorRole.ADMIN, "Ảnh chưa rõ."),
                        tuple("Đăng bài và gửi duyệt", "Chủ bài", ActorRole.OWNER, null),
                        tuple("Gửi yêu cầu xin nhận", "Người xin", ActorRole.USER, null),
                        tuple("Gửi báo cáo liên quan đến bài", "Người báo", ActorRole.USER, null));
        assertThat(detail.postHistory())
                .extracting(AdminItemHistoryEntry::createdAt)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(detail.visibilityHistory()).isEmpty();
    }

    @Test
    void getDetail_usesStoredRejectionReasonWhenLegacyItemHasNoAuditLog() {
        long admin = user("Quản trị viên cũ");
        long itemId = item(user("Chủ bài cũ"), "Bài trước đây", "REJECTED");
        jdbc.update("""
                UPDATE items
                SET created_at = '2026-11-10T05:00:00Z', review_submitted_at = '2026-11-11T05:00:00Z',
                    rejected_at = '2026-11-12T05:00:00Z', rejected_by = ?, reject_reason = ?
                WHERE id = ?
                """, admin, "Chọn sai danh mục món đồ.", itemId);

        assertThat(adminItemService.getDetail(itemId).postHistory())
                .extracting(
                        AdminItemHistoryEntry::action,
                        AdminItemHistoryEntry::actorName,
                        AdminItemHistoryEntry::reason,
                        AdminItemHistoryEntry::createdAt)
                .containsExactly(
                        tuple(
                                "Từ chối bài",
                                "Quản trị viên cũ",
                                "Chọn sai danh mục món đồ.",
                                Instant.parse("2026-11-12T05:00:00Z")),
                        tuple("Gửi bản sửa để duyệt", "Chủ bài cũ", null, Instant.parse("2026-11-11T05:00:00Z")),
                        tuple("Đăng bài và gửi duyệt", "Chủ bài cũ", null, Instant.parse("2026-11-10T05:00:00Z")));
    }

    @Test
    void getDetail_whenItemDeleted_throwsItemNotFound() {
        long itemId = item(user("Chủ bài"), "Bài sẽ bị xoá", "APPROVED");
        jdbc.update("UPDATE items SET is_deleted = true WHERE id = ?", itemId);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> adminItemService.getDetail(itemId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ItemErrorCode.ITEM_NOT_FOUND));
    }

    // ---------- Helper ----------

    private String sourceOf(PageResponse<AdminItemRow> page, long itemId) {
        return page.items().stream()
                .filter(row -> row.id() == itemId)
                .findFirst()
                .orElseThrow()
                .hiddenSource();
    }

    private long user(String fullName) {
        return jdbc.queryForObject(
                """
                INSERT INTO users (email, password_hash, full_name, phone, status)
                VALUES (?, 'unused-test-hash', ?, ?, 'ACTIVE') RETURNING id
                """,
                Long.class,
                UUID.randomUUID() + "@example.test",
                fullName,
                UUID.randomUUID().toString().substring(0, 12));
    }

    private long item(long donor, String title, String status) {
        return jdbc.queryForObject("""
                INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)
                VALUES (?, (SELECT id FROM item_categories ORDER BY id LIMIT 1),
                        (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),
                        'GIVE', ?, 'Mô tả', 'GOOD', ?) RETURNING id
                """, Long.class, donor, title, status);
    }

    private long request(long itemId, long receiver, Long offeredItemId, String type, String status) {
        return jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, offered_item_id, type, status) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                itemId,
                receiver,
                offeredItemId,
                type,
                status);
    }

    private long report(long reporter, long itemId) {
        return jdbc.queryForObject("""
                INSERT INTO reports (reporter_id, target_type, item_id, reason, description, stage, status)
                VALUES (?, 'ITEM', ?, 'OTHER', 'Báo cáo IT', 'BEFORE', 'OPEN') RETURNING id
                """, Long.class, reporter, itemId);
    }

    private boolean flagOf(long itemId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT hidden_by_admin FROM items WHERE id = ?", Boolean.class, itemId);
    }

    private boolean isActive(long itemId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, itemId);
    }

    private String statusOf(long itemId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId);
    }

    private String statusOfRequest(long requestId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId);
    }

    private long count(String sql, Object argument) {
        entityManager.flush();
        return jdbc.queryForObject(sql, Long.class, argument);
    }
}
