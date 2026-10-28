-- SRS v11 (N01): bài đăng chỉ còn hai tầng, chặn tại form (lexicon + regex) và Admin duyệt theo checklist.
-- Tắt tầng AI gắn cờ bằng cấu hình thay vì xoá code, để sau này Admin bật lại được (item_ai_screening_enabled = true).
INSERT INTO website_attributes (attr_group, attr_key, attr_value)
VALUES
  ('CONFIG', 'item_ai_screening_enabled', 'false')
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;
