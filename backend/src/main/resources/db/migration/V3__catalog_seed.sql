-- Danh mục dùng được theo SRS 6.17; danh mục cấm vẫn được ghi nhận nhưng không cho chọn.
INSERT INTO item_categories (name, is_restricted, is_active, sort_order)
VALUES
  ('Sách, truyện, giáo trình, văn phòng phẩm', FALSE, TRUE, 1),
  ('Quần áo, giày dép, túi, phụ kiện thời trang', FALSE, TRUE, 2),
  ('Đồ gia dụng, nhà bếp', FALSE, TRUE, 3),
  ('Điện tử và phụ kiện', FALSE, TRUE, 4),
  ('Đồ chơi, đồ dùng trẻ em', FALSE, TRUE, 5),
  ('Thể thao, dã ngoại', FALSE, TRUE, 6),
  ('Nội thất nhỏ, đồ trang trí', FALSE, TRUE, 7),
  ('Dược phẩm và thiết bị y tế', TRUE, FALSE, 8);

-- areas.level chỉ có hai cấp: thành phố và quận.
INSERT INTO areas (name, level) VALUES ('TP. Hồ Chí Minh', 1);

INSERT INTO areas (name, parent_id, level)
SELECT district.name, city.id, 2
FROM (VALUES
  ('Quận 1'),
  ('Quận 3'),
  ('Quận 5'),
  ('Quận Bình Thạnh'),
  ('Quận Gò Vấp')
) AS district(name)
CROSS JOIN areas AS city
WHERE city.name = 'TP. Hồ Chí Minh' AND city.level = 1;
