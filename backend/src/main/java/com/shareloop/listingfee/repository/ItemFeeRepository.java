package com.shareloop.listingfee.repository;

import com.shareloop.listingfee.entity.ItemFee;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemFeeRepository extends JpaRepository<ItemFee, Long> {

    /**
     * Đọc cột phí của bài và khoá dòng items đến hết transaction (SELECT ... FOR UPDATE). PostFeeService và
     * RenewService luôn gọi SAU khi CreditService đã khoá dòng users, để mọi luồng khoá theo cùng thứ tự users →
     * items. ItemExpiryService chỉ khoá dòng items, không khoá users, nên không tạo vòng chờ với các luồng trên.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ItemFee f where f.id = :id")
    Optional<ItemFee> lockById(@Param("id") Long id);

    /**
     * Đọc nhẹ chủ bài và cột phí, không khoá, không tạo entity managed; rỗng khi không có bài hoặc bài đã xoá mềm.
     * Dùng native query vì donor_id đã do entity Item (module item) map, ItemFee không được map lại cột này nên JPQL
     * không với tới được. Alias để trong ngoặc kép vì PostgreSQL tự hạ chữ thường alias không quote.
     *
     * <p>approved_at chỉ cần biết có hay không nên trả sẵn boolean (everApproved); cột này thuộc module item/review
     * nên ItemFee cũng không map. status, category_id, is_active cũng thuộc module item, chỉ đọc để kiểm điều kiện
     * gia hạn và đẩy bài; offer_type cũng vậy, chỉ đọc để biết bài có mất phí đăng không (chỉ SWAP mất phí).
     */
    @Query(
            value = "SELECT donor_id AS \"donorId\", fee_state AS \"feeState\", pending_fee AS \"pendingFee\","
                    + " pending_fee_type AS \"pendingFeeType\", (approved_at IS NOT NULL) AS \"everApproved\","
                    + " free_edit_used AS \"freeEditUsed\", post_fee_paid AS \"postFeePaid\", status AS \"status\","
                    + " category_id AS \"categoryId\", is_active AS \"active\", offer_type AS \"offerType\""
                    + " FROM items WHERE id = :itemId AND is_deleted = false",
            nativeQuery = true)
    Optional<ItemFeeSnapshot> findFeeSnapshotById(@Param("itemId") Long itemId);

    /**
     * Id các bài APPROVED đã quá expire_at (job hết hạn bài, FR-29, BR-D03), không khoá. Native query vì cột status
     * do entity Item (module item) map, ItemFee không map lại được. Phân trang kiểu keyset (id > afterId, tăng dần):
     * người gọi truyền id cuối của lô trước, nên một bài xử lý lỗi không bị lấy lại mãi trong cùng một lượt.
     */
    @Query(
            value = "SELECT id FROM items"
                    + " WHERE status = 'APPROVED' AND expire_at < :now AND is_deleted = false AND id > :afterId"
                    + " ORDER BY id LIMIT :limit",
            nativeQuery = true)
    List<Long> findExpiredApprovedItemIds(
            @Param("now") Instant now, @Param("afterId") long afterId, @Param("limit") int limit);

    /**
     * Hạn hiển thị và hạn đẩy của nhiều bài trong một lần đọc, không khoá. Dùng khi module item trả chi tiết hoặc
     * danh sách bài: một query cho cả trang thay vì một query mỗi bài. Không lọc is_deleted vì người gọi đã lọc bài
     * khi đọc danh sách. Bài không có trong kết quả thì người gọi coi như cả hai cột null. Bài đẩy trước khi có cột
     * boost_started_at thì lấy last_boosted_at, giống cách BoostService xử lý dữ liệu cũ.
     */
    @Query(
            value = "SELECT id AS \"id\", expire_at AS \"expireAt\", boosted_until AS \"boostedUntil\","
                    + " COALESCE(boost_started_at, last_boosted_at) AS \"boostStartedAt\""
                    + " FROM items WHERE id IN (:ids)",
            nativeQuery = true)
    List<ItemDisplayWindowRow> findDisplayWindowRows(@Param("ids") Collection<Long> ids);

    @Query(value = "SELECT id FROM items WHERE boosted_until > :now AND is_deleted = false", nativeQuery = true)
    List<Long> findActiveBoostedIds(@Param("now") Instant now);

    /**
     * Khoá tư vấn (advisory lock) dùng cho suất đẩy bài toàn hệ thống. Số rất lớn để không trùng khoá
     * pg_advisory_xact_lock(ownerId) mà ItemService đang dùng theo id người dùng.
     */
    long BOOST_SLOT_LOCK_KEY = 8_315_026_110_600_001L;

    /**
     * Điều kiện "bài đang đẩy" dùng chung cho các query đếm suất: còn hạn đẩy, đã duyệt, đang hiển thị, chưa xoá và
     * chưa quá hạn hiển thị. Bài bị ẩn (is_active = false) KHÔNG được tính là đang đẩy nên trả suất ngay, không chờ
     * tới boosted_until; job thông báo còn cắt boosted_until của bài bị ẩn về lúc dừng (xem endHiddenBoost).
     */
    String ACTIVE_BOOST_CONDITION = " boosted_until > :now AND status = 'APPROVED' AND is_active AND NOT is_deleted"
            + " AND (expire_at IS NULL OR expire_at > :now)";

    /** Số bài đang đẩy trên toàn hệ thống (so với boost_max_active). */
    @Query(value = "SELECT COUNT(*) FROM items WHERE" + ACTIVE_BOOST_CONDITION, nativeQuery = true)
    long countActiveBoosts(@Param("now") Instant now);

    /**
     * Số bài đang đẩy của một người (so với boost_max_per_user). Native query vì donor_id do entity Item map, ItemFee
     * không map lại cột này.
     */
    @Query(
            value = "SELECT COUNT(*) FROM items WHERE donor_id = :donorId AND" + ACTIVE_BOOST_CONDITION,
            nativeQuery = true)
    long countActiveBoostsByDonor(@Param("donorId") Long donorId, @Param("now") Instant now);

    /** Mốc hết đẩy sớm nhất trong các bài đang đẩy: lúc đó có thêm một suất trống. Rỗng khi không bài nào đang đẩy. */
    @Query(value = "SELECT MIN(boosted_until) FROM items WHERE" + ACTIVE_BOOST_CONDITION, nativeQuery = true)
    Optional<Instant> findEarliestActiveBoostEnd(@Param("now") Instant now);

    /** Như {@link #findEarliestActiveBoostEnd} nhưng chỉ trong các bài của một người. */
    @Query(
            value = "SELECT MIN(boosted_until) FROM items WHERE donor_id = :donorId AND" + ACTIVE_BOOST_CONDITION,
            nativeQuery = true)
    Optional<Instant> findEarliestActiveBoostEndByDonor(@Param("donorId") Long donorId, @Param("now") Instant now);

    /**
     * Khoá suất đẩy toàn hệ thống đến hết transaction: hai người cùng mua suất cuối thì chạy lần lượt, người sau đếm
     * lại sẽ thấy đã hết suất. pg_advisory_xact_lock trả void nên gọi trong FROM để query có một dòng kết quả.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(" + BOOST_SLOT_LOCK_KEY + ")", nativeQuery = true)
    Integer lockBoostSlots();

    /**
     * Phần SELECT dùng chung cho các query tìm bài cần thông báo về đợt đẩy. Native query vì donor_id và title do
     * entity Item map, ItemFee không map lại.
     */
    String BOOST_NOTICE_SELECT = "SELECT id AS \"id\", donor_id AS \"donorId\", title AS \"title\","
            + " boosted_until AS \"boostedUntil\", COALESCE(boost_started_at, last_boosted_at) AS \"boostStartedAt\","
            + " last_boosted_at AS \"lastBoostedAt\" FROM items WHERE";

    /**
     * Bài đang đẩy sẽ hết đẩy trước mốc {@code endsBefore} (để báo sắp hết đẩy). Số bài đang đẩy bị chặn bởi
     * boost_max_active nên danh sách luôn nhỏ, không cần phân trang.
     */
    @Query(
            value = BOOST_NOTICE_SELECT + ACTIVE_BOOST_CONDITION + " AND boosted_until <= :endsBefore ORDER BY id",
            nativeQuery = true)
    List<BoostNoticeRow> findBoostsEndingBefore(@Param("now") Instant now, @Param("endsBefore") Instant endsBefore);

    /**
     * Bài đang hiển thị đã hết đẩy trong khoảng (endedAfter, now] (để báo đã hết đẩy). Người gọi truyền endedAfter =
     * now - boost_cooldown_days: quá thời gian nghỉ thì thông báo không còn ý nghĩa, nên không quét các đợt cũ hơn.
     */
    @Query(
            value = BOOST_NOTICE_SELECT
                    + " boosted_until <= :now AND boosted_until > :endedAfter AND status = 'APPROVED' AND is_active"
                    + " AND NOT is_deleted AND (expire_at IS NULL OR expire_at > :now) ORDER BY id",
            nativeQuery = true)
    List<BoostNoticeRow> findBoostsEndedAfter(@Param("now") Instant now, @Param("endedAfter") Instant endedAfter);

    /** Bài còn hạn đẩy nhưng đang bị ẩn (is_active = false) và chưa xoá mềm: cần dừng đẩy. */
    @Query(
            value = BOOST_NOTICE_SELECT + " boosted_until > :now AND NOT is_active AND NOT is_deleted ORDER BY id",
            nativeQuery = true)
    List<BoostNoticeRow> findHiddenActiveBoosts(@Param("now") Instant now);

    /**
     * Kết thúc đợt đẩy của một bài đang bị ẩn: gán boosted_until = now (SRS 4.6: bài bị ẩn mất thời gian đẩy còn lại).
     * Dùng UPDATE native có điều kiện thay vì khoá rồi sửa entity: câu lệnh tự kiểm lại "còn đẩy, đang ẩn, chưa xoá"
     * ngay lúc ghi, nên bài vừa được Admin bỏ ẩn sau khi job đọc danh sách sẽ không bị cắt nhầm.
     *
     * @return 1 nếu đã cắt, 0 nếu bài không còn thoả điều kiện
     */
    // @Modifying: báo Spring Data đây là câu lệnh ghi (UPDATE), chạy bằng executeUpdate và trả số dòng bị đổi.
    @Modifying
    @Query(
            value = "UPDATE items SET boosted_until = :now"
                    + " WHERE id = :itemId AND boosted_until > :now AND NOT is_active AND NOT is_deleted",
            nativeQuery = true)
    int endHiddenBoost(@Param("itemId") Long itemId, @Param("now") Instant now);

    /**
     * Các bài của một người đang giữ phí (fee_state = HELD, pending_fee > 0), bài mới cập nhật xếp trước. Native query
     * vì title, status, offer_type thuộc module item, ItemFee không map các cột này. Tổng pending_fee của các dòng này
     * bằng users.held_credit.
     */
    @Query(
            value = "SELECT id AS \"id\", title AS \"title\", offer_type AS \"offerType\", status AS \"status\","
                    + " pending_fee AS \"pendingFee\", pending_fee_type AS \"pendingFeeType\","
                    + " updated_at AS \"updatedAt\""
                    + " FROM items WHERE donor_id = :userId AND fee_state = 'HELD' AND pending_fee > 0"
                    + " AND is_deleted = false ORDER BY updated_at DESC",
            nativeQuery = true)
    List<HeldFeeRow> findHeldFeeRows(@Param("userId") Long userId);
}
