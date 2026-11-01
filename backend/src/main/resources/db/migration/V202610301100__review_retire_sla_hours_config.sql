-- KD-06, TD-04 (nhóm chốt): bỏ thông báo nhắc Admin bài chờ duyệt quá 24 giờ vì dễ làm trôi thông báo quan trọng.
-- ReviewSlaJob đã gỡ nên review_sla_hours không còn đọc ở đâu. Xoá mềm để trang cấu hình Admin không còn mục thừa.
UPDATE website_attributes
SET is_deleted = TRUE
WHERE attr_group = 'CONFIG'
  AND attr_key = 'review_sla_hours'
  AND is_deleted = FALSE;
