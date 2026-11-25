-- Giao hàng hai chiều (GN-12). Chiều 1 là món chính chủ bài gửi cho người nhận, chiều 2 là món đổi người nhận gửi cho
-- chủ bài. donor_handover_at khi giao hàng nghĩa là chủ bài đã nhận món đổi (chiều 2), receiver_handover_at nghĩa là
-- người nhận đã nhận món chính (chiều 1).
ALTER TABLE requests ADD COLUMN receiver_address VARCHAR(255);
ALTER TABLE requests ADD COLUMN donor_address VARCHAR(255);
ALTER TABLE requests ADD COLUMN donor_shipped_at TIMESTAMPTZ;
ALTER TABLE requests ADD COLUMN donor_tracking_code VARCHAR(50);
ALTER TABLE requests ADD COLUMN receiver_shipped_at TIMESTAMPTZ;
ALTER TABLE requests ADD COLUMN receiver_tracking_code VARCHAR(50);

-- Giao dịch Giao hàng cũ lưu địa chỉ nhận hàng ở meeting_place, chép sang cột riêng. Không tự suy ngày gửi cho dữ liệu cũ.
UPDATE requests SET receiver_address = meeting_place WHERE delivery_method = 'SHIPPING';
