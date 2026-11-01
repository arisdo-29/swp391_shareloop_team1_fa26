-- M6: bỏ thực phẩm khỏi checklist duyệt bài. Xoá mềm câu food_safety (không xoá cứng, nhật ký duyệt cũ vẫn giữ câu
-- trong snapshot). Chỉ đụng nhóm CHECKLIST: các từ khoá cấm về thực phẩm (BANNED_KEYWORD, V202610241200) vẫn giữ nguyên.
UPDATE website_attributes
SET is_deleted = TRUE
WHERE attr_group = 'CHECKLIST'
  AND attr_key = 'food_safety'
  AND is_deleted = FALSE;
