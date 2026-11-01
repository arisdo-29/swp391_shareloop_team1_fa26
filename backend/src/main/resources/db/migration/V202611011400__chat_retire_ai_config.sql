-- D-17, CH-04: bỏ AI kiểm tra tin nhắn chat để giảm độ trễ và không phụ thuộc dịch vụ ngoài.
-- Xoá mềm chat_ai_threshold và chat_ai_timeout_seconds khỏi cấu hình hệ thống.
UPDATE website_attributes
SET is_deleted = TRUE
WHERE attr_group = 'CONFIG'
  AND attr_key IN ('chat_ai_threshold', 'chat_ai_timeout_seconds')
  AND is_deleted = FALSE;
