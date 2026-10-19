# Kiểm thử demo #118

## Chuẩn bị

- Backend đã chạy Flyway, bao gồm migration `V202610200300__report_evidence_and_response_deadline.sql`.
- Frontend kết nối đúng backend; dùng hai tài khoản Admin và hai bên của một giao dịch thật đã `COMPLETED`, còn trong cửa sổ khiếu nại cấu hình.
- Chuẩn bị bài đang `PENDING_REVIEW` có phí đăng đang giữ, một báo cáo `REQUEST`,
  một báo cáo `ITEM`, một hồ sơ không ảnh và một hồ sơ có ảnh không còn khả dụng.
- Không dùng dữ liệu store hoặc thay trạng thái bằng localStorage để demo.

## Tạo hồ sơ có bằng chứng hai bên bằng API

1. Bên gửi upload ảnh qua `POST /api/v1/media` (multipart `file`), giữ `mediaId`.
2. Gửi `POST /api/v1/reports` bằng JWT bên gửi:

   ```json
   {"targetType":"REQUEST","requestId":88,"reason":"NOT_AS_DESCRIBED","description":"Đồ khác mô tả","evidenceMediaIds":[12]}
   ```

   Thay ID bằng dữ liệu thật. Response phải có `id` hồ sơ riêng và `AWAITING_RESPONSE`.
3. Bên bị báo cáo upload ảnh của mình, sau đó gọi `POST /api/v1/reports/{reportId}/response`:

   ```json
   {"response":"Nội dung giải trình","evidenceMediaIds":[15]}
   ```

   Phải nhận `204`. Người ngoài giao dịch không được tạo khiếu nại giao dịch hoặc giải trình hộ.
4. Đăng nhập Admin, mở `/admin/complaints`. Lọc trạng thái/đối tượng, chuyển trang,
   mở đúng report ID. Kiểm tra người gửi, bên bị báo cáo, bài/giao dịch liên quan,
   nội dung, hai nhóm ảnh, giải trình, thời điểm gửi/phản hồi và hạn giải trình.
5. URL `/api/v1/media/{evidenceId}` phải trả `404`; URL evidence Admin phải trả `401`
   khi không có token, `403` với token user thường. Chỉ Admin đọc được ảnh đúng hồ sơ.
6. Hồ sơ không ảnh vẫn đọc/kết luận được. Ảnh mất có thông báo thay thế riêng;
   nội dung hồ sơ và ảnh khác vẫn hiển thị.

## Kết luận và tranh chấp thao tác

1. Chọn `UPHOLD`, nhập ghi chú ≤ 500 ký tự, kiểm tra bước xác nhận rồi gửi.
   Chỉ báo thành công sau HTTP 200. Kiểm tra `RESOLVED`, trừ đúng một sao,
   audit log có Admin/note; báo cáo ITEM ẩn đúng bài. Lý do nghiêm trọng phải ẩn
   các bài khác của người vi phạm theo cấu hình hiện có.
2. Dùng hồ sơ khác chọn `DISMISS`: nhận `DISMISSED`, sao và bài giữ nguyên;
   audit ghi nhận bác bỏ. Mở lại thấy kết luận đã lưu, không có nút gửi tiếp.
3. Hai Admin mở cùng hồ sơ chưa kết luận. Admin A gửi trước; Admin B gửi sau:
   B nhận thông báo đã xử lý từ HTTP 409 và hồ sơ được tải lại. Không trừ sao lần hai.
4. Giữ bộ lọc `OPEN` khi kết luận một hồ sơ; danh sách tải lại theo đúng bộ lọc,
   hồ sơ vừa xử lý biến mất khỏi danh sách nhưng chi tiết vẫn hiển thị kết luận mới.
5. Đổi nhanh hồ sơ/bộ lọc khi mạng chậm: response cũ không thay nội dung mới.
   Mất mạng phải có lỗi/thử lại; tài khoản user thường bị chặn cả route và API.

## Duyệt bài và phí đăng

1. Mở `/admin/moderation`, kiểm tra danh sách/ảnh/checklist lấy từ API.
2. Tích đủ điều kiện rồi duyệt: payload `answers: [{id, passed}]`, API 204;
   bài `APPROVED`, phí đang giữ được trừ đúng một lần.
3. Từ chối bài khác bằng lý do: API 204, bài `REJECTED`, phí được nhả khỏi khoản giữ.
4. Đối chiếu wallet trước/sau với ledger; việc mở trang/ảnh không đổi số dư.
5. Thay checklist từ phiên Admin khác rồi duyệt bằng checklist cũ:
   phải nhận `REVIEW_CHECKLIST_OUTDATED`, tải lại và bắt tích lại các điều kiện.

Các bước trên là checklist kiểm thử tay; kết quả test tự động không thay thế việc
chạy demo trên trình duyệt với tài khoản và storage của môi trường triển khai.
