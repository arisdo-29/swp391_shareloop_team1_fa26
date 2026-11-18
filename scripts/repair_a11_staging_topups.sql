-- Chỉ chạy trên staging sau khi đối chiếu từng dòng nạp cũ. Không dùng cho dữ liệu VNPay thật.
-- Trước khi chạy, điền các ID đã xác minh và tỷ giá VND/Credit tương ứng vào bảng tạm dưới đây.
-- Tỷ giá phải cho ra số tiền hợp lệ theo ràng buộc payment_orders. Script không cộng lại số dư.
BEGIN;

CREATE TEMP TABLE a11_verified_legacy_topups (
    ledger_id bigint PRIMARY KEY,
    vnd_per_credit int NOT NULL CHECK (vnd_per_credit > 0)
) ON COMMIT DROP;

-- INSERT INTO a11_verified_legacy_topups VALUES (<ledger_id>, <vnd_per_credit>);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM a11_verified_legacy_topups) THEN
        RAISE EXCEPTION 'A11: cần điền danh sách dòng sổ cũ đã xác minh trước khi chạy';
    END IF;
END
$$;

SELECT l.id
FROM credit_ledger l JOIN a11_verified_legacy_topups v ON v.ledger_id = l.id
FOR UPDATE OF l;

INSERT INTO payment_orders (
    user_id, order_code, amount_vnd, credits, vnd_per_credit, provider, provider_txn_id, status,
    signature_verified, paid_at
)
SELECT l.user_id, 'LEGACY-' || l.id, l.amount::bigint * v.vnd_per_credit, l.amount, v.vnd_per_credit,
       'MOCK', 'LEGACY-' || l.id, 'SUCCESS', TRUE, l.created_at
FROM credit_ledger l JOIN a11_verified_legacy_topups v ON v.ledger_id = l.id
WHERE l.type = 'TOP_UP' AND l.amount > 0 AND l.payment_order_id IS NULL
ON CONFLICT (order_code) DO NOTHING;

UPDATE credit_ledger l
SET payment_order_id = o.id
FROM a11_verified_legacy_topups v
JOIN payment_orders o ON o.order_code = 'LEGACY-' || v.ledger_id
WHERE l.id = v.ledger_id AND l.payment_order_id IS NULL AND l.type = 'TOP_UP'
  AND o.provider = 'MOCK' AND o.status = 'SUCCESS' AND o.user_id = l.user_id
  AND o.credits = l.amount AND o.vnd_per_credit = v.vnd_per_credit
  AND o.amount_vnd = l.amount::bigint * v.vnd_per_credit AND o.paid_at = l.created_at;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM a11_verified_legacy_topups v
        WHERE NOT EXISTS (
            SELECT 1 FROM credit_ledger l JOIN payment_orders o ON o.id = l.payment_order_id
            WHERE l.id = v.ledger_id AND l.type = 'TOP_UP' AND l.amount > 0
              AND o.order_code = 'LEGACY-' || l.id AND o.provider = 'MOCK' AND o.status = 'SUCCESS'
              AND o.user_id = l.user_id AND o.credits = l.amount AND o.paid_at = l.created_at
              AND o.vnd_per_credit = v.vnd_per_credit AND o.amount_vnd = l.amount::bigint * v.vnd_per_credit
        )
    ) THEN
        RAISE EXCEPTION 'A11: có dòng sổ không khớp, hủy toàn bộ thay đổi';
    END IF;
END
$$;

UPDATE payment_orders o
SET paid_at = l.created_at
FROM credit_ledger l
WHERE l.payment_order_id = o.id AND l.user_id = o.user_id
  AND l.type = 'TOP_UP' AND l.note = 'Demo seed'
  AND o.order_code = 'DEMO-SEED-' || l.user_id AND o.provider = 'MOCK' AND o.status = 'SUCCESS'
  AND o.paid_at IS DISTINCT FROM l.created_at;

COMMIT;
