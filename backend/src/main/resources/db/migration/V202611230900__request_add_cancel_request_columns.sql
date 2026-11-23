-- Đề nghị huỷ có xác nhận của bên kia (E1). Chỉ lưu đề nghị đang chờ, không thêm trạng thái mới.
-- Giải trình khi không đồng ý nằm ở khiếu nại của module report, không cần cột ở đây.
ALTER TABLE requests ADD COLUMN cancel_requested_by BIGINT;
ALTER TABLE requests ADD COLUMN cancel_requested_at TIMESTAMPTZ;
ALTER TABLE requests ADD COLUMN cancel_request_code VARCHAR(30);
ALTER TABLE requests ADD COLUMN cancel_request_reason VARCHAR(500);
