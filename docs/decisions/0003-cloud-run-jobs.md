# 0003 — Endpoint kích hoạt job định kỳ cho Cloud Run qua Cloud Scheduler

- Trạng thái: đã chốt.
- Bối cảnh: Backend ShareLoop chuyển sang Google Cloud Run ở chế độ request-based billing. Khi không có HTTP request đến, CPU bị thu hồi (0 CPU), khiến các job nội bộ `@Scheduled` của Spring không thể tự kích hoạt theo lịch.
- Quyết định:
  - Mở endpoint nội bộ `POST /internal/jobs/run` do `InternalJobController` (package `com.shareloop.ops`) đảm nhiệm, chỉ được khởi tạo khi `app.jobs.enabled=true`.
  - Endpoint được bảo vệ bằng header `X-Job-Token`, so sánh bằng phương thức so sánh hằng thời gian (`MessageDigest.isEqual`) với cấu hình `app.jobs.trigger-token` (biến môi trường `JOBS_TRIGGER_TOKEN`). Nếu token cấu hình rỗng, thiếu header hoặc sai token thì trả về HTTP 403 Forbidden và không chạy bất kỳ job nào.
  - Khi token hợp lệ, endpoint lần lượt gọi phương thức `run()` của 5 job nền (`ItemExpiryJob`, `RequestJob`, `PendingAccountCleanupJob`, `PaymentExpiryJob`, `ReviewSlaJob`). Nếu một job xảy ra lỗi ngoại lệ, hệ thống ghi log error và vẫn tiếp tục thực thi các job kế tiếp. Kết quả trả về HTTP 200 kèm JSON ánh xạ từng job sang trạng thái `"ok"` hoặc `"failed"`.
  - Cấu hình Cloud Scheduler gọi endpoint này định kỳ mỗi giờ một lần (vào phút thứ 5 của mỗi giờ: `5 * * * *`).
- Lý do không gọi mỗi 5 phút:
  - Database Neon PostgreSQL có cơ chế tự động ngủ (auto-suspend) khi không có truy vấn để tiết kiệm tài nguyên. Nếu gọi job mỗi 5 phút, database sẽ bị đánh thức liên tục 24/7 và vượt quá giới hạn 100 Compute Unit (CU)-giờ/tháng của gói miễn phí Neon. Chu kỳ 1 giờ/lần vừa đảm bảo các hạn mức/SLA được xử lý đúng hạn, vừa cho phép database ngủ và giữ chi phí ở mức tối thiểu.
- Hệ quả: Các job vẫn giữ nguyên annotation `@Scheduled` và logic nghiệp vụ độc lập, tương thích hoàn toàn khi chạy local hoặc trên các nền tảng server khác.
