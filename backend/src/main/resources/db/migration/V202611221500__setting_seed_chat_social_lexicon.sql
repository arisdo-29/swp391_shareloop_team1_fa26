-- Gắn cờ đường dẫn và tên mạng xã hội trong chat trước khi chốt lịch (D3). Mức REVIEW nên chỉ báo Admin, không chặn
-- tin nhắn để tránh bắt nhầm. Các từ quá ngắn như "fb" và "face" không thêm vì dễ khớp nhầm câu thường
-- (ví dụ "Face ID"). Bộ lọc từ khoá và biểu thức chính quy đều có thể bị lách (viết cách chữ, đổi ký tự).
INSERT INTO website_attributes (attr_group, attr_key, attr_value, extra, sort_order)
VALUES
  ('BANNED_KEYWORD', 'facebook.com', 'facebook.com', '{"category":"CONTACT","severity":"REVIEW","matchMode":"PHRASE","exceptions":[],"scope":"CHAT"}'::jsonb, 210),
  ('BANNED_KEYWORD', 'fb.com', 'fb.com', '{"category":"CONTACT","severity":"REVIEW","matchMode":"PHRASE","exceptions":[],"scope":"CHAT"}'::jsonb, 220),
  ('BANNED_KEYWORD', 'fb.me', 'fb.me', '{"category":"CONTACT","severity":"REVIEW","matchMode":"PHRASE","exceptions":[],"scope":"CHAT"}'::jsonb, 230),
  ('BANNED_KEYWORD', 'm.me', 'm.me', '{"category":"CONTACT","severity":"REVIEW","matchMode":"PHRASE","exceptions":[],"scope":"CHAT"}'::jsonb, 240),
  ('BANNED_KEYWORD', 'zalo.me', 'zalo.me', '{"category":"CONTACT","severity":"REVIEW","matchMode":"PHRASE","exceptions":[],"scope":"CHAT"}'::jsonb, 250),
  ('BANNED_KEYWORD', 'instagram.com', 'instagram.com', '{"category":"CONTACT","severity":"REVIEW","matchMode":"PHRASE","exceptions":[],"scope":"CHAT"}'::jsonb, 260),
  ('BANNED_KEYWORD', 'tiktok.com', 'tiktok.com', '{"category":"CONTACT","severity":"REVIEW","matchMode":"PHRASE","exceptions":[],"scope":"CHAT"}'::jsonb, 270),
  ('BANNED_KEYWORD', 't.me', 't.me', '{"category":"CONTACT","severity":"REVIEW","matchMode":"PHRASE","exceptions":[],"scope":"CHAT"}'::jsonb, 280),
  ('BANNED_KEYWORD', 'facebook', 'facebook', '{"category":"CONTACT","severity":"REVIEW","matchMode":"WORD","exceptions":[],"scope":"CHAT"}'::jsonb, 290),
  ('BANNED_KEYWORD', 'insta', 'insta', '{"category":"CONTACT","severity":"REVIEW","matchMode":"WORD","exceptions":[],"scope":"CHAT"}'::jsonb, 300);
