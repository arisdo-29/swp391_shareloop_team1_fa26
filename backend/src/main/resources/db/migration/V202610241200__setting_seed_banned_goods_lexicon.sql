-- Seed từ khoá BLOCK phạm vi POST cho vật phẩm cấm (FR-42, BR-A04, BR-A05, SRS 6.17.2).
-- Chỉ khớp đúng dấu mới bị chặn; gõ không dấu chỉ bị gắn cờ REVIEW (LexiconMatcher).
-- Ngoại lệ phải chứa nguyên từ khoá thì mới có tác dụng, vì khớp theo "bao trùm".
-- Từ có hai cách bỏ dấu (hoá/hóa, tuỳ/tùy) được seed cả hai dạng.
INSERT INTO website_attributes (attr_group, attr_key, attr_value, extra, sort_order)
VALUES
  -- BR-A04: vật phẩm cần sang tên pháp lý
  ('BANNED_KEYWORD', 'biển số', 'biển số', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":["khung biển số","ốp biển số"],"scope":"POST"}'::jsonb, 100),
  ('BANNED_KEYWORD', 'cà vẹt', 'cà vẹt', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 110),
  ('BANNED_KEYWORD', 'sổ đỏ', 'sổ đỏ', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 120),
  ('BANNED_KEYWORD', 'sổ hồng', 'sổ hồng', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 130),
  ('BANNED_KEYWORD', 'giấy tờ tuỳ thân', 'giấy tờ tuỳ thân', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 140),
  ('BANNED_KEYWORD', 'giấy tờ tùy thân', 'giấy tờ tùy thân', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 150),
  ('BANNED_KEYWORD', 'cccd', 'cccd', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"WORD","exceptions":["ví đựng cccd","bao đựng cccd"],"scope":"POST"}'::jsonb, 160),
  ('BANNED_KEYWORD', 'căn cước công dân', 'căn cước công dân', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":["ví đựng căn cước công dân","bao đựng căn cước công dân"],"scope":"POST"}'::jsonb, 170),
  ('BANNED_KEYWORD', 'chứng minh nhân dân', 'chứng minh nhân dân', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 180),
  ('BANNED_KEYWORD', 'cmnd', 'cmnd', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"WORD","exceptions":["ví đựng cmnd","bao đựng cmnd"],"scope":"POST"}'::jsonb, 190),
  ('BANNED_KEYWORD', 'hộ chiếu', 'hộ chiếu', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":["bìa hộ chiếu","ví đựng hộ chiếu","vỏ bọc hộ chiếu"],"scope":"POST"}'::jsonb, 200),
  ('BANNED_KEYWORD', 'sim chính chủ', 'sim chính chủ', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 210),
  ('BANNED_KEYWORD', 'tài khoản ngân hàng', 'tài khoản ngân hàng', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 220),
  ('BANNED_KEYWORD', 'tài khoản game', 'tài khoản game', '{"category":"OWNERSHIP_TRANSFER","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 230),
  -- BR-A04: vật phẩm quy đổi trực tiếp ra tiền mặt
  ('BANNED_KEYWORD', 'vàng miếng', 'vàng miếng', '{"category":"CASH_EQUIVALENT","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 300),
  ('BANNED_KEYWORD', 'ngoại tệ', 'ngoại tệ', '{"category":"CASH_EQUIVALENT","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 310),
  ('BANNED_KEYWORD', 'thẻ cào', 'thẻ cào', '{"category":"CASH_EQUIVALENT","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 320),
  ('BANNED_KEYWORD', 'voucher', 'voucher', '{"category":"CASH_EQUIVALENT","severity":"BLOCK","matchMode":"WORD","exceptions":["hộp đựng voucher","sổ đựng voucher"],"scope":"POST"}'::jsonb, 330),
  ('BANNED_KEYWORD', 'tiền mã hoá', 'tiền mã hoá', '{"category":"CASH_EQUIVALENT","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 340),
  ('BANNED_KEYWORD', 'tiền mã hóa', 'tiền mã hóa', '{"category":"CASH_EQUIVALENT","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 350),
  -- BR-A05: thực phẩm và đồ uống
  ('BANNED_KEYWORD', 'thực phẩm tươi sống', 'thực phẩm tươi sống', '{"category":"FOOD_BEVERAGE","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 400),
  ('BANNED_KEYWORD', 'đồ ăn chế biến', 'đồ ăn chế biến', '{"category":"FOOD_BEVERAGE","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 410),
  ('BANNED_KEYWORD', 'thực phẩm chức năng', 'thực phẩm chức năng', '{"category":"FOOD_BEVERAGE","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 420),
  ('BANNED_KEYWORD', 'sữa công thức', 'sữa công thức', '{"category":"FOOD_BEVERAGE","severity":"BLOCK","matchMode":"PHRASE","exceptions":["hộp đựng sữa công thức","hộp chia sữa công thức","bình pha sữa công thức","máy pha sữa công thức"],"scope":"POST"}'::jsonb, 430),
  ('BANNED_KEYWORD', 'sữa bột', 'sữa bột', '{"category":"FOOD_BEVERAGE","severity":"BLOCK","matchMode":"PHRASE","exceptions":["hộp đựng sữa bột","hộp chia sữa bột","bình pha sữa bột","máy pha sữa bột"],"scope":"POST"}'::jsonb, 440),
  ('BANNED_KEYWORD', 'đồ ăn dặm', 'đồ ăn dặm', '{"category":"FOOD_BEVERAGE","severity":"BLOCK","matchMode":"PHRASE","exceptions":["bộ đồ ăn dặm","dụng cụ đồ ăn dặm"],"scope":"POST"}'::jsonb, 450),
  ('BANNED_KEYWORD', 'bột ăn dặm', 'bột ăn dặm', '{"category":"FOOD_BEVERAGE","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"POST"}'::jsonb, 460),
  ('BANNED_KEYWORD', 'rượu', 'rượu', '{"category":"FOOD_BEVERAGE","severity":"BLOCK","matchMode":"WORD","exceptions":["ly rượu","cốc rượu","kệ rượu","tủ rượu","bình rượu","bình ngâm rượu","hũ ngâm rượu"],"scope":"POST"}'::jsonb, 470),
  ('BANNED_KEYWORD', 'bia', 'bia', '{"category":"FOOD_BEVERAGE","severity":"BLOCK","matchMode":"WORD","exceptions":["bìa","bia mộ","bia đá"],"scope":"POST"}'::jsonb, 480),
  ('BANNED_KEYWORD', 'thuốc lá', 'thuốc lá', '{"category":"FOOD_BEVERAGE","severity":"BLOCK","matchMode":"PHRASE","exceptions":["gạt tàn thuốc lá","hộp đựng thuốc lá"],"scope":"POST"}'::jsonb, 490)
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;
