-- Lưu Admin nào duyệt / từ chối bài và thời điểm từ chối ngay trên items,
-- để không phải dò activity_logs mỗi khi cần biết người chịu trách nhiệm.
ALTER TABLE items
  ADD COLUMN approved_by BIGINT,
  ADD COLUMN rejected_by BIGINT,
  ADD COLUMN rejected_at TIMESTAMPTZ;
