# API module reputation

Module quản lý điểm tín nhiệm, sao uy tín (`trust_stars`), tỉ lệ đúng mô tả và hạng (`Rank`) của người dùng theo Lộ trình 4.7 và SRS v10 (FR-65, FR-91, FR-92, FR-94, FR-97, FR-100, BR-U01, BR-U04, BR-U05, BR-U11).

Người sở hữu: BE4 (@kopslngbtram2110).

---

## 1. Dịch vụ nội bộ (`ReputationService`)

Module `reputation` cung cấp các phương thức nghiệp vụ để các module khác gọi trực tiếp qua Spring Service:

### 1.1. `Rank getRank(int totalPoints)`
Tính hạng tín nhiệm tương ứng từ tổng điểm tích luỹ dựa trên các ngưỡng cấu hình từ `ConfigService`:
- `REPUTATION_SILVER_MIN` (mặc định: `50`): Ngưỡng lên hạng Bạc (`SILVER`).
- `REPUTATION_GOLD_MIN` (mặc định: `150`): Ngưỡng lên hạng Vàng (`GOLD`).
- `REPUTATION_DIAMOND_MIN` (mặc định: `400`): Ngưỡng lên hạng Kim Cương (`DIAMOND`).
- Dưới ngưỡng Bạc: Hạng Đồng (`BRONZE`).

### 1.2. `Rank getRank(Long userId)`
Lấy hạng tín nhiệm hiện tại của người dùng bằng cách truy vấn `total_points` của người dùng từ cơ sở dữ liệu và tính theo ngưỡng cấu hình hiện hành. Trả về `BRONZE` nếu người dùng chưa có bản ghi hoặc `userId` là `null`.

### 1.3. `ReputationSummary getReputationSummary(Long userId)`
Lấy bản tóm tắt thông tin uy tín người dùng phục vụ hiển thị (FR-65, Issue #110):
- `userId`: ID người dùng.
- `trustStars`: Số sao uy tín (0–5 sao, mặc định 5).
- `rank`: Hạng tín nhiệm tính theo tổng điểm (`BRONZE`, `SILVER`, `GOLD`, `DIAMOND`).
- `asDescribedRate`: Tỉ lệ % đánh giá đúng mô tả từ các giao dịch hoàn tất.

### 1.4. `void onCompleted(long requestId)`
Tiếp nhận sự kiện giao dịch chuyển sang trạng thái `COMPLETED` (FR-97, FR-100, Issue #114):
- **Cộng điểm theo loại giao dịch**:
  - Giao dịch Cho (`GIVE`): Người cho nhận `REPUTATION_GIVE_DONOR` (mặc định `10` điểm); người nhận nhận `REPUTATION_GIVE_RECEIVER` (mặc định `2` điểm).
  - Giao dịch Trao đổi (`SWAP`): Cả hai bên đều nhận `REPUTATION_SWAP_EACH` (mặc định `6` điểm).
- **Tính lại hạng ngay lập tức**: Cập nhật trực tiếp `rank_level` của người dùng tương ứng với tổng điểm mới.
- **Chống cộng trùng (Idempotent)**: Kiểm tra qua nhật ký `activity_logs` loại `POINTS`, bỏ qua nếu request đã được cộng điểm trước đó.
- **Ghi nhật ký**: Ghi nhận thay đổi điểm vào `activity_logs` với `type = 'POINTS'`.

### 1.5. `void deductStar(Long userId, int amount, String reason)`
Trừ sao uy tín của người dùng khi có vi phạm được kết luận (FR-91, FR-92, BR-U04, BR-U05, Issue #114):
- Trừ số sao chỉ định, chặn không cho âm (tối thiểu `0` sao).
- Ghi nhận nhật ký vào `activity_logs` với `type = 'STARS'`.
- Khi số sao chuyển từ `> 0` về đúng `0` sao:
  - Ẩn toàn bộ bài đăng đang hiển thị của người này (`ItemService.hideAllOfUser`).
  - Gửi thông báo cảnh báo qua `NotificationService`.
  - Không gửi thông báo hoặc ẩn bài lặp lại nếu trước đó tài khoản đã ở mức `0` sao.

### 1.6. `boolean canPost(Long userId)` / `boolean canRequest(Long userId)`
Kiểm tra quyền đăng bài mới và gửi yêu cầu giao dịch:
- Trả về `false` khi `trust_stars <= 0` (BR-U05).
- Trả về `true` khi người dùng còn sao uy tín (`trust_stars > 0`).

---

## 2. Các mô hình DTO & Enum

### `Rank`
Enum đại diện cho hạng tín nhiệm của người dùng:
- `BRONZE`: Hạng Đồng (mặc định).
- `SILVER`: Hạng Bạc.
- `GOLD`: Hạng Vàng.
- `DIAMOND`: Hạng Kim Cương.

### `ReputationSummary`
Record trả về thông tin tổng quan về uy tín:
```java
public record ReputationSummary(
    Long userId,
    Integer trustStars,
    Rank rank,
    Double asDescribedRate
) {}
```
