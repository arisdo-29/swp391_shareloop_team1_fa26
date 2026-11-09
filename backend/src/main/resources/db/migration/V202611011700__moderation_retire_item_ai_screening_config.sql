-- D-03, KD-01: Bỏ hẳn AI trong duyệt bài, chỉ còn kiểm tra tự động (tầng 1) và Admin duyệt (Business Rule v12).
UPDATE website_attributes
SET is_deleted = TRUE
WHERE attr_group = 'CONFIG'
  AND attr_key IN ('item_ai_screening_enabled', 'item_ai_timeout_seconds', 'item_ai_retries')
  AND is_deleted = FALSE;
