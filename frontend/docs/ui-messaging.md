# Tin nhắn và giao nhận — đợt 4

## Bảng bảo toàn hành vi trước khi đổi giao diện

| Trạng thái/khối | Vai trò | Điều kiện hiện có | Handler hiện có |
| --- | --- | --- | --- |
| PENDING, chọn người | Chủ bài | `canSelectPendingRequest`, chưa có yêu cầu cùng bài được chọn; kiểm lại trước khi gửi | `confirmRequestSelection`, `confirmSelection` qua dialog xác nhận |
| PENDING, hủy yêu cầu | Người xin | Trang tiến trình còn PENDING | `cancelRequest` |
| Gửi tin | Hai bên | `canChat`: RESERVED, AWAITING_LOGISTICS, LOGISTICS_CONFIRMED, AWAITING_HANDOVER, COMPLETED, DISPUTED; không đang gửi, có nội dung/ảnh/bài kèm | `send`, giữ `sendInFlight` và kiểm duyệt |
| CANCELLED | Hai bên được xem | `canViewChat` cho xem; composer bị khóa | Không mở thêm thao tác gửi |
| Gửi ảnh/kèm bài | Theo vai trò hiện có | Người nhận GIVE không gửi ảnh/kèm bài; ảnh mở ở LOGISTICS_CONFIRMED/AWAITING_HANDOVER/COMPLETED/DISPUTED; các điều kiện sending/chat/ảnh/bài kèm giữ nguyên | `send`, handler input file hiện có |
| GIVE còn thương lượng | Người nhận | `isOfferChangeOpen`, RESERVED/AWAITING_LOGISTICS và đúng người nhận | `proposeOfferChange` |
| Đề nghị đổi món PENDING | Chủ bài | Đúng tin đề nghị mới nhất, `offerChangeOpen`, `selectedAsDonor` | `acceptOfferChange`, `declineOfferChange` |
| Đổi món đang đề nghị | Người nhận | Có offeredItemId và RESERVED/AWAITING_LOGISTICS | `ChangeOfferedItem.save` |
| Lưu lịch | Hai bên | `canSchedule`, chưa trao liên lạc, kiểm địa điểm/giờ/cam kết bằng validator đang có | `saveSchedule` |
| Xác nhận lịch | Mỗi bên | AWAITING_LOGISTICS, lịch đã lưu hợp lệ, không còn sửa chưa lưu, đồng ý cam kết, phía mình chưa xác nhận | `confirmSchedule` |
| Xem liên hệ | Hai bên | Chỉ khi `contactRevealedAt` đã có | `loadContact` |
| Xác nhận giao/nhận | Theo vai trò | LOGISTICS_CONFIRMED/AWAITING_HANDOVER và `contactRevealedAt`, phía mình chưa xác nhận; người nhận phải chọn đúng/không đúng mô tả và lý do khi cần | `confirmHandover` |
| Báo sự cố giao nhận | Phía chưa xác nhận | AWAITING_HANDOVER, bên kia đã xác nhận, mình chưa xác nhận | `HandoverIssueDialog.submit` |
| Hủy giao dịch | Hai bên | `canCancelTransaction`, lý do 5–500 ký tự, giữ dialog và xử lý trạng thái cũ | `CancelTransactionDialog.submit` |
| Khiếu nại | Hai bên | COMPLETED và chưa `disputeExpired`; DISPUTED chỉ hiện thông tin hiện có | `DisputeDialog.submit` |
| Ảnh bàn giao/khiếu nại | Theo cờ hiện có | `HandoverMediaCard`, các hàm cho phép/thời hạn hiện có | Các handler upload/preview hiện có |

Deep link giữ nguyên: `/messages`, `/requests/:requestId`, `/messages/:conversationId/progress` và `?selectRequest=`. Kết nối SSE, fallback polling, refs chống trùng, sắp xếp/merge tin, đánh dấu đã đọc và thời gian định dạng không được thay đổi.

## Triển khai

Baseline là commit `2c15bf9` trên `feat/shareloop-ui-redesign`. Frontend sạch thay đổi lúc bắt đầu; thư mục `docs/design/` untracked được giữ nguyên. Đã kiểm tra hướng dẫn repo và nền theme/component các đợt trước. Phong cách tiếp tục dựa trên https://shareloop-demo-giao-dien.jauntyhydrangea.chatgpt.site/ và token hiện có.

- Desktop rộng: danh sách hội thoại trái, vùng chat giữa, panel món đồ và tiến trình bên phải. Không nhân đôi hoặc nhúng thêm các component gọi API của trang giao nhận vào khung chat; nút tiến trình vẫn mở đúng trang hiện có.
- Tablet/mobile: giữ cơ chế danh sách → hội thoại và nút quay lại hiện tại. Panel món đồ thu gọn bằng nút ẩn/hiện đã có; nút tiến trình được đặt trước thông tin món đồ. Composer nằm trong chiều cao `dvh`; khi viewport thấp, phần đầu chat thu gọn chiều cao để dành chỗ cho nội dung và ô nhập.
- Danh sách hội thoại: avatar từ dữ liệu hiện có, ảnh món đồ, đối tác, hình thức, trạng thái, thời gian và dấu chưa đọc. Giữ tìm kiếm/lọc cũ; không dựng preview tin cuối, số chưa đọc hoặc trạng thái online khi response chưa cung cấp.
- Bubble phân biệt gửi/nhận/hệ thống, hỗ trợ nội dung và URL dài, emoji, ảnh/thẻ món đang có. Giữ input một dòng và cơ chế submit/Enter hiện tại; không chuyển sang textarea hoặc đổi handler bàn phím.
- Trang tiến trình: nhóm lịch hẹn, bàn giao, trạng thái và thông tin đối tác; form, điều kiện và cảnh báo không đổi. Các bước tiếp tục dùng `completedStepCount` và dữ liệu thời gian hiện có, không thêm lịch sử/timestamp giả.
- Token sáng/tối cho sidebar, bubble, composer, card và dialog; không dùng CSS toàn cục để thay các trang đã hoàn thiện.

## File thay đổi

- `src/pages/user/Messages.tsx`, `src/pages/user/messaging.css` (mới).
- `src/components/messages/ChatHeader.tsx`, `ChatMessage.tsx`, `ConversationList.tsx`.
- `src/components/messages/OfferChangeDialogs.tsx`, `SelectRequestDialog.tsx`: class trình bày/theme của dialog, giữ props/callback.
- `src/pages/user/TransactionProgress.tsx`, `src/pages/user/transaction-progress.css` (mới).
- `src/components/transaction/StepsCard.tsx`, `TransactionDialogs.tsx`: trình bày theo theme, caption giải thích trạng thái.
- `src/components/messages/ConversationList.test.tsx`: giữ kiểm tra thumbnail/placeholder/deep link; thay assertion chiều cao 84 px cũ bằng kiểm tra bấm mở đúng hội thoại một lần.
- `docs/ui-messaging.md`: bảng trạng thái và báo cáo này.

## Bảo toàn logic

Đối chiếu snapshot lúc bắt đầu: phần ngoài JSX và toàn bộ handler JSX hiện có của các file runtime đã sửa đều giữ nguyên sau chuẩn hóa định dạng. Không sửa backend, API/service/schema, router/guard, auth, token, realtime, hook dữ liệu, utility, dependencies hoặc file môi trường.

Không thay key, vị trí mount của `ApiMessages`, effect/subscription, cleanup, timer, polling, ref chống gửi lặp, xử lý sau gửi, sort/merge message hoặc đánh dấu đã đọc. `messagesEndRef` vẫn nằm cuối cùng trong chính vùng cuộn tin nhắn; effect cuộn cũ không đổi. Không thêm nguồn dữ liệu hay request nghiệp vụ.

`ScheduleCard`, `HandoverCard`, `PendingCard`, `HandoverMediaCard`, `ChangeOfferedItem`, `useTransactionDetail` và các API của chúng không đổi. Các trạng thái COMPLETED/DISPUTED vẫn có khả năng chat theo `canChat` hiện có; CANCELLED chỉ xem.

## Kiểm tra và giới hạn

- Kết quả cuối: `npm run build`, `npm run lint` và `git diff --check` đạt. Vitest toàn frontend đạt **593/593** test, không bỏ qua test; `npm run test:api` đạt **28/28**.
- Build baseline đạt; cảnh báo bundle chính hơn 500 kB đã tồn tại trước khi sửa.
- Kiểm tra liên quan chat/tiến trình/lịch hẹn được chạy; test giao diện cũ phụ thuộc chiều cao cố định được cập nhật để kiểm tra hành vi mở hội thoại, không tắt hoặc xóa test.
- Chrome đã kiểm tra Tin nhắn và Tiến trình tại 390/768/1440 px, sáng/tối; không tràn ngang toàn trang hoặc vùng chat, nội dung URL dài xuống dòng.
- Kiểm tra deep link, đổi hội thoại, dấu đã đọc, draft khi ẩn/hiện panel hoặc gửi lỗi, gửi lại, nhận tin qua polling có `afterId`, merge không nhân đôi.
- Kiểm tra lịch hẹn/đồng ý cam kết, giới hạn hiện liên hệ, nút giao/nhận đúng vai trò, trạng thái cập nhật từ response, yêu cầu lý do hủy và composer bị khóa sau hủy. Kiểm tra quay lại/mở hội thoại mobile và viewport thu thấp.

Các luồng ghi trong kiểm thử trình duyệt dùng request interception và response thử nghiệm. Không gửi tin hoặc thao tác giao dịch với người thật/production. Không có fixture trong source ứng dụng. Chưa có môi trường backend test và hai tài khoản test thật để xác minh SSE/reconnect/hai phía qua mạng; vì vậy không coi lượt kiểm thử này là end-to-end realtime thực. Kiểm tra realtime bằng các test hiện có và đối chiếu mã không thay thế phần đó.

API hiện tại dùng tải ban đầu và polling `afterId`; không có nút tải thêm lịch sử cũ trong giao diện ban đầu nên không thêm hành vi đó. Chưa có dữ liệu đếm chưa đọc, online/typing/read receipt hoặc preview tin cuối riêng; không dựng thông tin giả.

Không redesign nội dung ví, khiếu nại hoặc admin; giữ các điểm vào và dialog giao dịch hiện có. Không commit, push, PR, merge hoặc deploy.

## Local

Frontend: **http://localhost:5173/**. Đăng nhập bằng tài khoản có sẵn để xem `/messages`, mở một hội thoại từ Hoạt động hoặc dùng deep link hiện có; nút **Xem tiến trình** đi tới `/messages/:conversationId/progress`.
