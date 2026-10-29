-- Q01: checklist duyệt bài nói rõ điều kiện đạt. Câu hiển thị mới theo bảng của Tân (28/10), mô tả "Đạt khi... Từ chối
-- nếu..." lưu ở extra.description của chính dòng CHECKLIST. Chữ hiển thị không dùng dấu chấm phẩy (DESIGN.md).
CREATE TEMPORARY TABLE checklist_q01_map (
  attr_key     VARCHAR(100) NOT NULL,
  old_text     TEXT NOT NULL,
  new_text     TEXT NOT NULL,
  description  TEXT NOT NULL
);

INSERT INTO checklist_q01_map (attr_key, old_text, new_text, description) VALUES
  ('ownership_transfer',
   'Không cần sang tên, đăng ký hay công chứng để đổi chủ.',
   'Món đồ không thuộc diện tài sản phải đăng ký sang tên hay công chứng.',
   'Đạt khi là đồ trao tay bình thường như quần áo, sách vở, đồ điện tử, đồ gia dụng. Từ chối nếu là nhà đất, sổ đỏ, xe máy hoặc ô tô phải sang tên giấy tờ.'),
  ('cash_exchange',
   'Không quy đổi trực tiếp ra tiền mặt được.',
   'Không rao bán, không yêu cầu phụ thêm tiền mặt.',
   'Đạt khi bài chỉ cho tặng hoặc trao đổi, không đòi bên nhận đưa tiền. Từ chối nếu có ghi giá tiền (“bán lại 200k”, “bù thêm 50k”) hoặc món đồ là tiền mặt, thẻ cào, vàng miếng, tiền ảo.'),
  ('food_safety',
   'Không phải thực phẩm, hoặc là thực phẩm còn nguyên tem, nhìn rõ hạn và hạn còn trên 30 ngày.',
   'Không phải thực phẩm, hoặc là đồ đóng gói còn nguyên tem, hạn dùng trên 30 ngày.',
   'Đạt khi không phải thực phẩm, hoặc là thực phẩm khô đóng gói kín của nhà sản xuất, còn tem nhãn và hạn dùng trên 30 ngày. Từ chối nếu là đồ ăn tự nấu, đồ tươi sống, đã khui bao bì hoặc hạn còn dưới 30 ngày.'),
  ('restricted_goods',
   'Không phải thuốc, thực phẩm chức năng, thiết bị y tế hay mỹ phẩm đã mở.',
   'Không phải thuốc, thực phẩm chức năng, thiết bị y tế hay mỹ phẩm đã mở nắp.',
   'Đạt khi là đồ dùng thông thường, không thuộc lĩnh vực y dược. Từ chối nếu là thuốc (tân dược, kê đơn, đông y), thực phẩm chức năng, máy đo đường huyết hay huyết áp, hoặc mỹ phẩm đã mở nắp dùng dở.'),
  ('real_photos',
   'Ảnh là ảnh thật của đúng món đồ, đủ số lượng, không lấy trên mạng.',
   'Ảnh chụp thực tế rõ nét của đúng món đồ, từ 3 ảnh trở lên, không dùng ảnh mạng.',
   'Đạt khi có từ 3 ảnh chụp thật, rõ nét, đủ góc của đúng món đồ. Từ chối nếu dùng ảnh tải từ mạng hay sàn thương mại điện tử (ảnh nền trắng quảng cáo), ảnh mờ vỡ nét, hoặc chỉ có 1 đến 2 ảnh sơ sài.'),
  ('contact_info',
   'Ảnh và mô tả không có thông tin liên hệ, kể cả viết ngầm (đã xem cờ AI).',
   'Ảnh và nội dung không chứa số điện thoại, Zalo, link ngoài hay thông tin liên hệ ngầm.',
   'Đạt khi tiêu đề, mô tả và ảnh không có cách liên lạc cá nhân nào. Từ chối nếu có số điện thoại (kể cả viết lách như “o9o”, “không chín”), lời mời “ib zalo”, link mạng xã hội, hoặc số điện thoại, mã QR chèn trên ảnh.'),
  ('condition_match',
   'Tình trạng khai báo khớp với ảnh; đồ lỗi đã mô tả lỗi.',
   'Tình trạng chọn trong form khớp với ảnh, khuyết tật hoặc hư hỏng đã được mô tả chi tiết.',
   'Đạt khi tình trạng đã chọn (Mới, Như mới, Còn tốt, Có lỗi) tương xứng với ảnh, đồ có lỗi thì mô tả rõ và có ảnh chụp cận chỗ lỗi. Từ chối nếu đồ cũ, trầy xước nặng mà khai Mới, hoặc giấu chỗ hư hỏng.'),
  ('category_match',
   'Danh mục đúng với món đồ.',
   'Danh mục và thẻ phân loại phản ánh đúng món đồ.',
   'Đạt khi món đồ nằm đúng danh mục, ví dụ nồi cơm điện ở Đồ gia dụng, sách giáo khoa ở Sách và văn phòng phẩm. Từ chối nếu chọn danh mục không liên quan, ví dụ đăng quần áo nhưng chọn Điện tử.'),
  ('desired_item',
   'Bài Cho tặng, hoặc bài Trao đổi đã mô tả món mong muốn.',
   'Nhu cầu trao đổi rõ ràng, không ghi chung chung.',
   'Đạt khi là bài Cho tặng, hoặc bài Trao đổi có ô món mong muốn ghi nhu cầu cụ thể, ví dụ “Muốn đổi bàn học hoặc balo”. Từ chối nếu bài Trao đổi ghi vô nghĩa như “asdf”, “gì cũng được”, “tùy tâm”, “123”.'),
  ('authenticity',
   'Không có dấu hiệu hàng nhái, không bật nhãn chính hãng khi chứng từ không khớp.',
   'Không có dấu hiệu hàng giả/nhái.',
   'Đạt khi không có dấu hiệu nhái thương hiệu, bài bật nhãn Chính hãng thì có hoá đơn, phiếu bảo hành hoặc chứng từ hợp lệ. Từ chối nếu là hàng nhái lộ liễu (“Gucci Quảng Châu”, “Nike rep 1:1”) hoặc bật nhãn Chính hãng mà không có chứng từ.');

-- Đổi câu hiển thị chỉ khi vẫn là câu cũ, để không ghi đè mục Admin đã tự sửa.
UPDATE website_attributes attribute
SET attr_value = q01.new_text
FROM checklist_q01_map q01
WHERE attribute.attr_group = 'CHECKLIST'
  AND attribute.attr_key = q01.attr_key
  AND attribute.attr_value = q01.old_text
  AND attribute.is_deleted = FALSE;

-- Thêm mô tả cho mục chưa có mô tả (kể cả mục Admin đã sửa câu), không đè mô tả đã có.
UPDATE website_attributes attribute
SET extra = COALESCE(attribute.extra, '{}'::jsonb) || jsonb_build_object('description', q01.description)
FROM checklist_q01_map q01
WHERE attribute.attr_group = 'CHECKLIST'
  AND attribute.attr_key = q01.attr_key
  AND attribute.is_deleted = FALSE
  AND (attribute.extra IS NULL OR NOT attribute.extra ? 'description');

DROP TABLE checklist_q01_map;
