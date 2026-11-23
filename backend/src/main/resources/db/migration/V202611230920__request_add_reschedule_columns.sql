-- Đề nghị dời lịch sau khi chốt lịch (D1). Chỉ lưu đề nghị đang chờ và số lần đã dời, không thêm trạng thái mới.
ALTER TABLE requests ADD COLUMN reschedule_proposed_by BIGINT;
ALTER TABLE requests ADD COLUMN reschedule_proposed_at TIMESTAMPTZ;
ALTER TABLE requests ADD COLUMN reschedule_meeting_time TIMESTAMPTZ;
ALTER TABLE requests ADD COLUMN reschedule_meeting_place VARCHAR(255);
ALTER TABLE requests ADD COLUMN reschedule_count INTEGER NOT NULL DEFAULT 0;
