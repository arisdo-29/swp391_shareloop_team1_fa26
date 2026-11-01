-- L13: dọn khoá cấu hình không còn đọc ở đâu, để trang cấu hình và bảng website_attributes chỉ còn một nơi khai báo cho
-- mỗi con số. Xoá mềm (is_deleted = TRUE), không xoá cứng. item_ai_* đã xoá ở V202611011700 (R3), chat_ai_* ở
-- V202611011400 (R1), DISPUTE_WINDOW_DAYS và DISPUTE_RESPONSE_DAYS chữ hoa ở V202610291000.
--   - renew_expired_window_days: luồng gia hạn không dùng, chỉ còn trả ra ở bảng phí (đã bỏ field này).
--   - media_orphan_hours: không có job dọn ảnh nào đọc.
--   - boost_max_per_page: không có code nào đọc.
--   - dispute_window_days, dispute_response_days: đã thay bằng complaint_window_hours, response_window_hours (U08).
--   - REPUTATION_* chữ hoa: trùng khoá chữ thường mà ConfigKey và trang Admin đang dùng.
--   - REPORT_SERIOUS_REASONS chữ hoa: trùng report_serious_reasons, nay đã có trong ConfigKey để Admin sửa.
UPDATE website_attributes
SET is_deleted = TRUE
WHERE attr_group = 'CONFIG'
  AND attr_key IN (
    'renew_expired_window_days',
    'media_orphan_hours',
    'boost_max_per_page',
    'dispute_window_days',
    'dispute_response_days',
    'REPUTATION_SILVER_MIN',
    'REPUTATION_GOLD_MIN',
    'REPUTATION_DIAMOND_MIN',
    'REPUTATION_GIVE_DONOR',
    'REPUTATION_GIVE_RECEIVER',
    'REPUTATION_SWAP_EACH',
    'REPORT_SERIOUS_REASONS'
  )
  AND is_deleted = FALSE;
