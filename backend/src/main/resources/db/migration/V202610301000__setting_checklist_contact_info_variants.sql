-- Q01 bổ sung: V202610291200 chỉ đổi câu contact_info khi câu trên DB khớp đúng từng chữ câu cũ. Trên Cloud, câu
-- này có thể đang là biến thể khác (câu hỏi gốc của seed, hoặc câu Admin đã bỏ phần "cờ AI" sau khi tắt tầng AI ở
-- SRS v11), nên vẫn hiện câu cũ. Migration này đổi mọi biến thể còn nhắc "cờ AI" hoặc trùng câu cũ khi bỏ phần đó.
-- Câu Admin đã tự viết lại theo ý khác thì giữ nguyên. Chạy lại nhiều lần cũng không đổi gì thêm.
UPDATE website_attributes
SET attr_value = 'Ảnh và nội dung không chứa số điện thoại, Zalo, link ngoài hay thông tin liên hệ ngầm.'
WHERE attr_group = 'CHECKLIST'
  AND attr_key = 'contact_info'
  AND is_deleted = FALSE
  AND attr_value <> 'Ảnh và nội dung không chứa số điện thoại, Zalo, link ngoài hay thông tin liên hệ ngầm.'
  AND (
    lower(attr_value) LIKE '%cờ ai%'
    OR rtrim(btrim(attr_value), '.?') IN (
      'Ảnh và mô tả không có thông tin liên hệ, kể cả viết ngầm',
      'Ảnh hoặc mô tả có thông tin liên hệ, kể cả viết ngầm'
    )
  );

-- Mô tả điều kiện đạt: thêm nếu mục chưa có (giống V202610291200), không đè mô tả Admin đã sửa.
UPDATE website_attributes
SET extra = COALESCE(extra, '{}'::jsonb) || jsonb_build_object(
  'description',
  'Đạt khi tiêu đề, mô tả và ảnh không có cách liên lạc cá nhân nào. Từ chối nếu có số điện thoại (kể cả viết lách như “o9o”, “không chín”), lời mời “ib zalo”, link mạng xã hội, hoặc số điện thoại, mã QR chèn trên ảnh.')
WHERE attr_group = 'CHECKLIST'
  AND attr_key = 'contact_info'
  AND is_deleted = FALSE
  AND (extra IS NULL OR NOT extra ? 'description');
