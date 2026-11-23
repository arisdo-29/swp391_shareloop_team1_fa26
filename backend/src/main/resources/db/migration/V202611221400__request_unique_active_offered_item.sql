-- Một món chỉ được đem đổi ở một giao dịch đang chạy (C3). Hai luồng chọn người cùng lúc không thể cùng giữ một
-- món đổi: luồng sau bị chặn ở database thay vì chạy tiếp rồi lỗi 500.
-- Nếu dữ liệu cũ đã có món đổi bị dùng ở hai giao dịch đang chạy thì tạo index sẽ lỗi: kiểm bằng câu
-- SELECT offered_item_id, count(*) FROM requests WHERE offered_item_id IS NOT NULL AND status IN
-- ('RESERVED', 'AWAITING_LOGISTICS', 'LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER', 'DISPUTED')
-- GROUP BY offered_item_id HAVING count(*) > 1 và xử lý các giao dịch trùng trước khi chạy.
CREATE UNIQUE INDEX ux_requests_active_offered_item ON requests (offered_item_id)
  WHERE offered_item_id IS NOT NULL
    AND status IN ('RESERVED', 'AWAITING_LOGISTICS', 'LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER', 'DISPUTED');
