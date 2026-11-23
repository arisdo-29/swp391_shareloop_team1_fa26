-- Lúc giao dịch quay lại sau khi Admin bác khiếu nại đề nghị huỷ không tố ai (GN-06). Job tự huỷ giao dịch kẹt tính lại mốc từ lúc này.
ALTER TABLE requests ADD COLUMN resumed_at TIMESTAMPTZ;
