-- Chuẩn hoá mã type của nhật ký gọi AI về dạng chữ hoa như các type khác (ActivityType.AI_USAGE).
UPDATE activity_logs SET type = 'AI_USAGE' WHERE type = 'AiUsage';
