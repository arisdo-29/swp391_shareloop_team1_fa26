-- Id các đối tác khác nhau của chuỗi giao dịch sạch hiện tại (F4). Chỉ hồi sao khi chuỗi đủ số đối tác khác nhau.
-- Mảng được xoá mỗi khi chuỗi về 0 (lúc hồi sao hoặc lúc bị trừ sao).
ALTER TABLE users ADD COLUMN clean_streak_partners BIGINT[] NOT NULL DEFAULT '{}';
