-- Hạn mức bài đăng tách theo hình thức: Cho tặng tối đa 5 bài cho mọi hạng, Trao đổi theo hạng 5/8/15/không giới hạn.
INSERT INTO website_attributes (attr_group, attr_key, attr_value)
VALUES ('CONFIG', 'max_active_give_items', '5')
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;

-- Chỉ đổi dòng còn giữ giá trị mặc định cũ (3/5/10) để không ghi đè giá trị Admin đã chỉnh.
UPDATE website_attributes SET attr_value = '5'
WHERE attr_group = 'CONFIG' AND attr_key = 'max_active_items_bronze' AND attr_value = '3' AND NOT is_deleted;
UPDATE website_attributes SET attr_value = '8'
WHERE attr_group = 'CONFIG' AND attr_key = 'max_active_items_silver' AND attr_value = '5' AND NOT is_deleted;
UPDATE website_attributes SET attr_value = '15'
WHERE attr_group = 'CONFIG' AND attr_key = 'max_active_items_gold' AND attr_value = '10' AND NOT is_deleted;
