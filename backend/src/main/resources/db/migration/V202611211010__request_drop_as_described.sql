-- Bỏ hẳn câu hỏi "Món đồ có đúng mô tả không?" và tỉ lệ % đúng mô tả (21/11/2026).
-- Uy tín chỉ còn sao, điểm, hạng. Lý do khiếu nại NOT_AS_DESCRIBED ở bảng reports vẫn giữ.
ALTER TABLE requests DROP COLUMN IF EXISTS as_described;
