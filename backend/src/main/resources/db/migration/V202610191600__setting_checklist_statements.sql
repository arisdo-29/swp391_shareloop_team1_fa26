-- Checklist duyệt bài đổi từ câu hỏi chiều âm ("Có phải...?") sang câu điều kiện "đạt thì tích".
-- Lý do: ReviewService bắt mọi đáp án là true, nên với câu hỏi chiều âm snapshot lưu nghĩa ngược.
--
-- Bảng ánh xạ dùng chung cho cả ba bước: câu seed cũ (V202610151200) -> attr_key -> câu mới.
CREATE TEMPORARY TABLE checklist_statement_map (
  old_text  TEXT NOT NULL,
  attr_key  VARCHAR(100) NOT NULL,
  new_text  TEXT NOT NULL
);

INSERT INTO checklist_statement_map (old_text, attr_key, new_text) VALUES
  ('Món đồ có cần sang tên, đăng ký, công chứng để đổi chủ không?', 'ownership_transfer',
   'Không cần sang tên, đăng ký hay công chứng để đổi chủ.'),
  ('Món đồ có quy đổi trực tiếp ra tiền mặt được không?', 'cash_exchange',
   'Không quy đổi trực tiếp ra tiền mặt được.'),
  ('Nếu là thực phẩm: còn nguyên tem, nhìn rõ hạn, hạn còn trên 30 ngày?', 'food_safety',
   'Không phải thực phẩm, hoặc là thực phẩm còn nguyên tem, nhìn rõ hạn và hạn còn trên 30 ngày.'),
  ('Có phải thuốc, thực phẩm chức năng, thiết bị y tế, mỹ phẩm đã mở?', 'restricted_goods',
   'Không phải thuốc, thực phẩm chức năng, thiết bị y tế hay mỹ phẩm đã mở.'),
  ('Ảnh là ảnh thật của đúng món đồ, đủ số lượng, không lấy trên mạng?', 'real_photos',
   'Ảnh là ảnh thật của đúng món đồ, đủ số lượng, không lấy trên mạng.'),
  ('Ảnh hoặc mô tả có thông tin liên hệ, kể cả viết ngầm? (xem cờ AI)', 'contact_info',
   'Ảnh và mô tả không có thông tin liên hệ, kể cả viết ngầm (đã xem cờ AI).'),
  ('Tình trạng khai báo có khớp với ảnh? Đồ lỗi đã mô tả lỗi?', 'condition_match',
   'Tình trạng khai báo khớp với ảnh; đồ lỗi đã mô tả lỗi.'),
  ('Danh mục có đúng với món đồ?', 'category_match',
   'Danh mục đúng với món đồ.'),
  ('Bài Trao đổi đã có mô tả món mong muốn?', 'desired_item',
   'Bài Cho tặng, hoặc bài Trao đổi đã mô tả món mong muốn.'),
  ('Có dấu hiệu hàng nhái, hoặc bật nhãn chính hãng mà chứng từ không khớp?', 'authenticity',
   'Không có dấu hiệu hàng nhái, không bật nhãn chính hãng khi chứng từ không khớp.');

-- Bước 1: đổi câu trong cấu hình. Chỉ đổi khi câu vẫn là câu seed cũ,
-- để không ghi đè mục Admin đã tự sửa qua màn hình quản lý checklist.
UPDATE website_attributes attribute
SET attr_value = statement_map.new_text
FROM checklist_statement_map statement_map
WHERE attribute.attr_group = 'CHECKLIST'
  AND attribute.attr_key = statement_map.attr_key
  AND attribute.attr_value = statement_map.old_text
  AND attribute.is_deleted = FALSE;

-- Bước 2: chuyển snapshot đã duyệt từ {"question","passed"} sang {"key","statement","passed"}.
-- WITH ORDINALITY giữ đúng thứ tự phần tử; phần tử không khớp câu cũ nào thì giữ nguyên.
-- Ghi chú: trigger updated_at của items sẽ đổi theo các dòng bị cập nhật; chấp nhận vì hiện chỉ là dữ liệu test.
UPDATE items item
SET review_snapshot = jsonb_set(item.review_snapshot, '{checks}', converted.checks)
FROM (
  SELECT source.id,
         jsonb_agg(
           CASE
             WHEN statement_map.attr_key IS NULL THEN check_element.value
             ELSE jsonb_build_object(
                    'key', statement_map.attr_key,
                    'statement', statement_map.new_text,
                    'passed', check_element.value -> 'passed')
           END
           ORDER BY check_element.position) AS checks
  FROM items source
  CROSS JOIN LATERAL jsonb_array_elements(source.review_snapshot -> 'checks')
       WITH ORDINALITY AS check_element(value, position)
  LEFT JOIN checklist_statement_map statement_map
         ON statement_map.old_text = check_element.value ->> 'question'
  WHERE jsonb_typeof(source.review_snapshot -> 'checks') = 'array'
  GROUP BY source.id
) converted
WHERE item.id = converted.id
  AND item.review_snapshot -> 'checks' IS DISTINCT FROM converted.checks;

-- Bước 3: làm tương tự cho dữ liệu nhật ký duyệt bài (activity_logs.data cùng dạng {"checks": [...]}).
UPDATE activity_logs activity_log
SET data = jsonb_set(activity_log.data, '{checks}', converted.checks)
FROM (
  SELECT source.id,
         jsonb_agg(
           CASE
             WHEN statement_map.attr_key IS NULL THEN check_element.value
             ELSE jsonb_build_object(
                    'key', statement_map.attr_key,
                    'statement', statement_map.new_text,
                    'passed', check_element.value -> 'passed')
           END
           ORDER BY check_element.position) AS checks
  FROM activity_logs source
  CROSS JOIN LATERAL jsonb_array_elements(source.data -> 'checks')
       WITH ORDINALITY AS check_element(value, position)
  LEFT JOIN checklist_statement_map statement_map
         ON statement_map.old_text = check_element.value ->> 'question'
  WHERE source.type = 'REVIEW_APPROVE'
    AND jsonb_typeof(source.data -> 'checks') = 'array'
  GROUP BY source.id
) converted
WHERE activity_log.id = converted.id
  AND activity_log.data -> 'checks' IS DISTINCT FROM converted.checks;

DROP TABLE checklist_statement_map;
