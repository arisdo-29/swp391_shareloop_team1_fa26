ALTER TABLE requests
    ADD COLUMN exchange_shipping_window_start TIMESTAMPTZ,
    ADD COLUMN exchange_shipping_unsent_leg SMALLINT CHECK (exchange_shipping_unsent_leg IN (1, 2)),
    ADD COLUMN donor_item_reopen_at TIMESTAMPTZ,
    ADD COLUMN receiver_item_reopen_at TIMESTAMPTZ;

ALTER TABLE items
    ADD COLUMN exchange_shipping_hold_request_id BIGINT,
    ADD COLUMN exchange_shipping_restore_visibility BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE reports ADD COLUMN exchange_shipping_timeout BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO website_attributes (attr_group, attr_key, attr_value)
VALUES
    ('CONFIG', 'exchange_shipping_window_minutes', '30'),
    ('CONFIG', 'exchange_shipping_lead_minutes', '60'),
    ('CONFIG', 'exchange_shipping_morning_start', '480'),
    ('CONFIG', 'exchange_shipping_morning_end', '720'),
    ('CONFIG', 'exchange_shipping_afternoon_start', '780'),
    ('CONFIG', 'exchange_shipping_afternoon_end', '1020'),
    ('CONFIG', 'exchange_shipping_reopen_hours', '24')
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;

-- Chỉ kích hoạt cho các lịch còn đủ thời gian chuẩn bị và có khung gửi hợp lệ.
-- Giữ các xác nhận cũ, tránh tự mở khiếu nại hồi tố cho lịch đã qua.
UPDATE requests
SET exchange_shipping_window_start = meeting_time - INTERVAL '30 minutes'
WHERE NOT is_deleted
  AND delivery_method = 'SHIPPING'
  AND offered_item_id IS NOT NULL
  AND status IN ('LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER', 'DISPUTED')
  AND meeting_time >= CURRENT_TIMESTAMP + INTERVAL '1 hour'
  AND ((meeting_time AT TIME ZONE 'Asia/Ho_Chi_Minh')::time BETWEEN '08:30' AND '12:00'
       OR (meeting_time AT TIME ZONE 'Asia/Ho_Chi_Minh')::time BETWEEN '13:30' AND '17:00');

CREATE INDEX idx_requests_exchange_shipping_window
    ON requests (exchange_shipping_window_start, id)
    WHERE NOT is_deleted AND exchange_shipping_window_start IS NOT NULL
      AND status IN ('LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER');

CREATE INDEX idx_requests_exchange_shipping_reopen
    ON requests (id)
    WHERE NOT is_deleted AND (donor_item_reopen_at IS NOT NULL OR receiver_item_reopen_at IS NOT NULL);
