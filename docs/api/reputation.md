# API module reputation

Module quản lý điểm tín nhiệm, sao uy tín (`trust_stars`), tỉ lệ đúng mô tả và hạng (`Rank`) của người dùng theo Lộ trình 4.7 và SRS v10 (FR-65, FR-91, FR-94, FR-100, BR-U01, BR-U11).

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
- `trustStars`: Số sao uy tín (1–5 sao, mặc định 5).
- `rank`: Hạng tín nhiệm tính theo tổng điểm (`BRONZE`, `SILVER`, `GOLD`, `DIAMOND`).
- `asDescribedRate`: Tỉ lệ % đánh giá đúng mô tả từ các giao dịch hoàn tất.

### 1.4. `void onCompleted(Long requestId)`
Chữ ký tiếp nhận sự kiện giao dịch chuyển sang trạng thái `COMPLETED` (Issue #96). Thân đầy đủ (cộng điểm theo loại giao dịch, cập nhật chuỗi giao dịch sạch `clean_streak`, tính lại hạng và ghi nhận activity log) được triển khai chi tiết ở cột mốc R4.

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
    int trustStars,
    Rank rank,
    Double asDescribedRate
) {}
```

