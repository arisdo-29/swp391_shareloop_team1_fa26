# Rà soát các issue frontend trong báo cáo Miều R1–R3

Ngày đối chiếu: 19/10/2026. Tài liệu nguồn: `F:\fall_26\project_swp_group1_2\mieu-r1-r3-fe-review-status.md`.

Phạm vi thực hiện là frontend theo xác nhận của người phụ trách. Không bổ sung API backend. Các sửa đổi nằm trong workspace nhánh `feat/vnpay-topup`, chưa commit hoặc push. Báo cáo nguồn bên ngoài repo được giữ nguyên.

Đã đóng trên GitHub với lý do completed: #28, #85, #100, #101, #102, #119, #130, #133. Đã bổ sung ghi chú và giữ mở #67, #118, #131, #132. Các issue đóng trong đợt này ghi rõ phần sửa mới chưa commit/push và phạm vi kiểm chứng chưa bao gồm staging.

## Kết quả đối chiếu

| Issue | Kết quả trong workspace | Bằng chứng / giới hạn |
|---|---|---|
| #13, #14 | Đã có triển khai, giữ trạng thái đóng | Đăng ký, OTP và đăng nhập gọi `authApi`. |
| #28 | Đủ phần triển khai frontend | `Post.tsx` tải ảnh qua media API, giữ thứ tự ảnh, cho đổi thứ tự, map lỗi theo field. Contract hiện tại dùng `images`/`defectImages` chứa URL do API upload trả về, không gửi trường `mediaIds` như mô tả issue cũ. |
| #67 | Giữ mở, chờ xác minh môi trường | HTTP client, auth DTO, axios và khai báo biến Vite đã có. Cần người phụ trách xác minh cấu hình môi trường local/production và `VITE_API_BASE_URL` trên Vercel. Không đọc hoặc sửa file môi trường. |
| #83 | Giữ trạng thái đóng | Ví đã có `walletApi` và `paymentApi`; không thay luồng nạp tiền trong đợt này. |
| #84 | Giữ trạng thái đóng; sửa tương thích contract mới | Checklist dùng `{id,key,statement}`, gửi `answers: [{id,passed}]`. Khi nhận `REVIEW_CHECKLIST_OUTDATED`, tải lại và yêu cầu tích lại. |
| #85 | Đủ phần triển khai frontend | Home, Browse và ProductDetail đã gọi `itemApi`/`catalogApi`; lọc danh mục, khu vực và từ khóa qua query API. |
| #100 | Đã bổ sung phần thiếu | Polling `afterId`, gộp tin theo ID, bảo vệ khi đổi chat, hiển thị lỗi backend; không khóa chat khi Request tiến sang bước sau Reserved. |
| #101 | Đã bổ sung phần thiếu | Activities dùng danh sách Request thật. `/requests/:requestId` mở đúng giao dịch. Cho sửa/chốt lịch ở đúng trạng thái; đổi giờ UTC sang giờ địa phương khi nhập; chỉ xác nhận bàn giao ở bước hợp lệ. Người nhận phải chọn đúng/không đúng mô tả, ghi lý do khi không đúng. |
| #102 | Đã bổ sung phần thiếu | Thông báo có liên kết chi tiết, đánh dấu đã đọc và mở `/requests/:id`. Liên kết ngoài bị bỏ qua. |
| #118 | Giữ mở, thiếu API backend | Đã sửa contract duyệt bài. Backend/contract hiện chỉ có API tạo báo cáo và kết luận; chưa có API Admin đọc danh sách, hồ sơ chi tiết và bằng chứng. Trang khiếu nại hiển thị chưa sẵn sàng thay vì hồ sơ mẫu. |
| #119 | Đã nối API | Báo cáo ba sổ, đối chiếu nạp, lịch sử Credit theo người dùng/ngày/trang, khóa/mở khóa kèm lý do. Nhật ký hỗ trợ metadata `page` mới, tên/nhãn do backend trả về, checklist có `statement`, bộ lọc loại từ API. |
| #130 | Đã nối API | Tải quota trước thao tác, gọi search thật, hiển thị quota/phí từ backend và kết quả fallback; thông báo mỗi lần chuyển trang cũng tiêu lượt theo contract. Bỏ module preview. Không tự trừ Credit trong trình duyệt. |
| #131 | Giữ mở, thiếu API backend | Hồ sơ, đổi mật khẩu và đổi số điện thoại đã nối API. `GET /me` chưa trả điểm, hạng và lịch sử; `ReputationService` chỉ là service nội bộ. Trang uy tín không hiển thị lịch sử giả cho tài khoản API. Cần endpoint đọc dữ liệu uy tín cá nhân trước khi nối nốt. |
| #132 | Làm một phần, giữ mở | Đã bỏ nạp seed và lưu nghiệp vụ vào localStorage, khởi tạo store rỗng; bỏ preview tìm kiếm. Còn các reducer/màn hình legacy và `passwordSecurity.ts` cần dọn tiếp, dữ liệu kiểm chứng domain còn giữ riêng. Một số màn quản trị/uy tín chưa có API để thay hoàn toàn. |
| #133 | Đã bổ sung phần thiếu | Có thẻ ghim, chọn bài đã duyệt của mình để chia sẻ, gửi `refItemId`, hiển thị `itemCard` trong tin và mở chi tiết bài. |

Tên file trong báo cáo nguồn có một số chỗ đã cũ: trang đăng bài là `Post.tsx`, chuông thông báo là `NotificationMenu.tsx`. Không dùng sự hiện diện của service API để kết luận toàn bộ UI đã hoạt động.

R2 lọc chat LLM (#143) là phần Optional của backend, không thực hiện trong phạm vi frontend này. R3 sàng lọc bài tự động vẫn do backend xử lý; frontend hiển thị cờ và rủi ro trong hàng chờ.

## Kiểm tra

- `npm.cmd run lint`: đạt, không có cảnh báo.
- `npm.cmd run build`: đạt TypeScript và Vite; còn cảnh báo kích thước bundle trên 500 kB.
- `npm.cmd run test:api`: 10/10 ca đạt. Kiểm tra contract quota/search, `afterId`, thẻ bài, checklist theo ID, hai dạng phân trang nhật ký, lọc ngày, khóa/mở khóa, trạng thái giao nhận, dữ liệu khởi tạo và mã lỗi 403/409.
- `git diff --check`: đạt.

Các test API dùng adapter HTTP trong bộ nhớ, không gọi backend thật. Chưa kiểm thử trình duyệt hai tài khoản hoặc nghiệm thu staging/Vercel. Không chạy build/test backend vì không sửa backend.

## Kiểm tra tay sau khi người phụ trách đưa code lên môi trường chạy

1. Tạo tài khoản, xác thực OTP, đăng nhập; tải lại trang để kiểm tra khôi phục phiên bằng `/me`.
2. Đăng bài với 3–8 ảnh, đổi thứ tự, thử thiếu mô tả trao đổi và ảnh lỗi. Kiểm tra lỗi field, chặn nội dung và thiếu Credit.
3. Admin duyệt checklist mới; thay checklist ở phiên khác và kiểm tra yêu cầu tích lại khi bị outdated.
4. Dùng hai tài khoản: gửi yêu cầu, chọn một người, mở thông báo, kiểm tra đúng Request. Gửi tin từ cả hai phía, đổi chat khi đang gửi, chia sẻ thẻ bài.
5. Đề xuất lịch, phía kia cập nhật/đồng ý, cả hai chốt lịch rồi xem liên hệ. Xác nhận giao nhận và thử đánh giá không đúng mô tả kèm lý do.
6. Trang tìm bằng AI: kiểm tra quota trước khi tìm, phí khi hết lượt, đổi trang, fallback và cập nhật ví sau khi dùng dịch vụ.
7. Admin lọc ngày báo cáo, xem đối chiếu và lịch sử Credit; khóa/mở khóa kèm lý do, kiểm tra nhật ký và phân trang. API từ chối người không có quyền với 403.
