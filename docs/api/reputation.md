# API module reputation

Module quản lý điểm tín nhiệm, sao uy tín (`trust_stars`), tỉ lệ đúng mô tả và hạng (`Rank`) của người dùng theo Lộ trình 4.7 và SRS v10 (FR-65, FR-91, FR-92, FR-94, FR-95, FR-97, FR-98, FR-99, FR-100, BR-U01, BR-U04, BR-U05, BR-U08, BR-U09, BR-U11).

Người sở hữu: BE4 (@kopslngbtram2110).

---

## 1. Dịch vụ nội bộ (`ReputationService`)

Module `reputation` cung cấp các phương thức nghiệp vụ để các module khác gọi trực tiếp qua Spring Service:

### 1.1. `Rank getRank(int totalPoints)`
Tính hạng tín nhiệm tương ứng từ tổng điểm tích luỹ dựa trên các ngưỡng cấu hình đọc qua `ConfigService(ConfigKey)`,
đúng key viết thường mà trang Admin sửa (key viết hoa cũ do migration seed không còn được đọc):
- `reputation_silver_min` (mặc định: `50`): Ngưỡng lên hạng Bạc (`SILVER`).
- `reputation_gold_min` (mặc định: `150`): Ngưỡng lên hạng Vàng (`GOLD`).
- `reputation_diamond_min` (mặc định: `400`): Ngưỡng lên hạng Kim Cương (`DIAMOND`).
- Dưới ngưỡng Bạc: Hạng Đồng (`BRONZE`).

### 1.2. `Rank getRank(Long userId)`
Lấy hạng tín nhiệm hiện tại của người dùng bằng cách truy vấn `total_points` của người dùng từ cơ sở dữ liệu và tính theo ngưỡng cấu hình hiện hành. Trả về `BRONZE` nếu người dùng chưa có bản ghi hoặc `userId` là `null`.

### 1.3. `ReputationSummary getReputationSummary(Long userId)`
Lấy bản tóm tắt thông tin uy tín người dùng phục vụ hiển thị (FR-65, Issue #110):
- `userId`: ID người dùng.
- `trustStars`: Số sao uy tín (0–5 sao, mặc định 5).
- `rank`: Hạng tín nhiệm tính theo tổng điểm (`BRONZE`, `SILVER`, `GOLD`, `DIAMOND`).
- `asDescribedRate`: Tỉ lệ đúng mô tả khi người dùng là chủ bài, thang **0–1** (vd `0.92` là 92%), cùng thang với
  `accuracyRate` của `/me/reputation`. Chỉ trả khi có **từ 3 giao dịch `COMPLETED` đã được đánh giá** "đúng mô tả?"
  trở lên, dưới 3 thì `null` (SRS 6.12).

### 1.4. `void onCompleted(long requestId)`
Tiếp nhận sự kiện giao dịch chuyển sang trạng thái `COMPLETED` (FR-97, FR-100, Issue #114; FR-94, FR-98, FR-99, Issue #150):
- **Cộng điểm theo loại giao dịch**:
  - Giao dịch Cho (`GIVE`): Người cho nhận `reputation_give_donor` (mặc định `10` điểm); người nhận nhận `reputation_give_receiver` (mặc định `2` điểm).
  - Giao dịch Trao đổi (`SWAP`): Cả hai bên đều nhận `reputation_swap_each` (mặc định `6` điểm).
- **Không cộng điểm giao dịch lặp cặp (FR-98, BR-U08)**: Đếm giao dịch `COMPLETED` giữa cùng hai người, tính cả hai chiều cho/nhận, có `completed_at` trong `reputation_pair_window_days` ngày gần nhất (mặc định `90`). Từ giao dịch thứ `reputation_pair_no_points_from` (mặc định `3`) trở đi, cả hai bên được cộng `0` điểm nhưng vẫn ghi nhật ký `POINTS` kèm lý do.
- **Trần điểm mỗi ngày (FR-99, BR-U11)**: Mỗi người được cộng tối đa `daily_points_cap` điểm mỗi ngày (mặc định `30`), tính theo ngày giờ Việt Nam (cột `points_today`, `points_date`). Sang ngày mới thì `points_today` đếm lại từ 0; chạm trần thì chỉ cộng phần còn lại.
- **Hồi sao (FR-94, BR-U09)**: Mỗi giao dịch hoàn tất là một giao dịch sạch: `clean_streak` của cả hai bên +1, kể cả giao dịch lặp cặp không được điểm. Đủ `reputation_clean_streak_for_star` (mặc định `10`) mà đang dưới 5 sao thì +1 sao, `clean_streak` về 0 và ghi nhật ký `STAR_RESTORED`. Đang 5 sao thì không hồi thêm.
- **Tính lại hạng ngay lập tức**: Cập nhật trực tiếp `rank_level` của người dùng tương ứng với tổng điểm mới.
- **Chống cộng trùng (Idempotent)**: Kiểm tra qua nhật ký `activity_logs` loại `POINTS`, bỏ qua nếu request đã được cộng điểm trước đó.
- **Ghi nhật ký**: Ghi nhận thay đổi điểm vào `activity_logs` với `type = 'POINTS'`; `data` gồm `points` (điểm thực cộng), `basePoints` (điểm theo loại giao dịch), `totalPoints`, `rank`, `role`, `pairTransactionNo`, `pointsToday`, `itemId` (bài của giao dịch). Lý do gọi giao dịch bằng tên món, vd `Cộng 10 điểm hoàn tất giao dịch món “Nồi cơm điện” (vai trò cho/tặng)`, không dùng `#id`.
- **Hết sao**: khi về 0 sao, gửi thông báo `TRUST_STARS_DEPLETED` có link `/profile?tab=reputation` (tab uy tín trong trang hồ sơ).

### 1.5. `void deductStar(Long userId, int amount, String reason)`
Trừ sao uy tín của người dùng khi có vi phạm được kết luận (FR-91, FR-92, BR-U04, BR-U05, Issue #114):
- Trừ số sao chỉ định, chặn không cho âm (tối thiểu `0` sao).
- Đặt `clean_streak = 0`: vi phạm làm mất chuỗi giao dịch sạch (BR-U09, Issue #150).
- Ghi nhận nhật ký vào `activity_logs` với `type = 'STARS'`.
- Khi số sao chuyển từ `> 0` về đúng `0` sao:
  - Ẩn toàn bộ bài đăng đang hiển thị của người này (`ItemService.hideAllOfUser`).
  - Gửi thông báo cảnh báo qua `NotificationService`.
  - Không gửi thông báo hoặc ẩn bài lặp lại nếu trước đó tài khoản đã ở mức `0` sao.

> Chưa làm: BR-U09 "về 0 sao lần hai (đã dùng sao ân hạn) thì khoá vĩnh viễn". Đang chờ lead chốt có làm và khoá bằng cách nào.

### 1.6. `boolean canPost(Long userId)` / `boolean canRequest(Long userId)`
Kiểm tra quyền đăng bài mới và gửi yêu cầu giao dịch:
- Trả về `false` khi `trust_stars <= 0` (BR-U05).
- Trả về `true` khi người dùng còn sao uy tín (`trust_stars > 0`).

### 1.7. `GraceStarResponse grantGraceStar(Long adminId, Long userId, String reason)`
Admin cấp 1 sao ân hạn (FR-95, BR-U09, UC-51, Issue #150). API: [admin.md mục 6](admin.md#6-post-apiv1adminusersuseridgrace-star).
- Chỉ cấp khi người dùng đang có `0` sao và `grace_star_used = false`. Cấp xong `trust_stars = 1`, `grace_star_used = true` (chỉ đổi `false → true`).
- Ghi nhật ký `activity_logs` với `type = 'GRACE_STAR'`, `actor_id` là Admin, `reason` là lý do Admin nhập.
- Lỗi: `USER_NOT_FOUND` (404), `REPUTATION_GRACE_STAR_ALREADY_USED` (409), `REPUTATION_GRACE_STAR_NOT_ELIGIBLE` (409).

### 1.8. Cấu hình (đọc qua `ConfigService`, Admin sửa được)

| Khoá | Mặc định | Ý nghĩa |
|---|---|---|
| `daily_points_cap` | `30` | Trần điểm mỗi người mỗi ngày (BR-U11) |
| `reputation_pair_window_days` | `90` | Số ngày của cửa sổ tính giao dịch lặp cặp (BR-U08) |
| `reputation_pair_no_points_from` | `3` | Từ giao dịch thứ mấy giữa cùng cặp thì không cộng điểm (BR-U08) |
| `reputation_pair_alert_threshold` | `5` | Vượt số giao dịch cùng cặp này thì cảnh báo Admin; dùng ở FR-113 (issue #148), #150 chưa đọc |
| `reputation_clean_streak_for_star` | `10` | Số giao dịch sạch liên tiếp để hồi 1 sao (BR-U09) |

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

---

## 3. GET /api/v1/me/reputation

Xem thông tin uy tín cá nhân của chính người dùng đang đăng nhập (FR-101, Issue #128). Bao gồm tổng điểm, hạng, số sao uy tín, tỉ lệ đúng mô tả và lịch sử biến động điểm (mới nhất lên trước, có phân trang).

- **Xác thực:** Bắt buộc Bearer Token (JWT). ID người dùng được xác định qua `@CurrentUserId` từ token đăng nhập.
- **Query Parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Giới hạn | Mô tả |
|---|---|---|---|---|---|
| `page` | `int` | Không | `0` | $\ge 0$ | Chỉ số trang (bắt đầu từ 0) |
| `size` | `int` | Không | `20` | `1–50` | Số lượng bản ghi mỗi trang |

- **Thành công:** `200 OK`, trả về `MyReputationResponse`.

```json
{
  "totalPoints": 320,
  "rank": "GOLD",
  "trustStars": 4,
  "accuracyRate": 0.92,
  "history": {
    "content": [
      {
        "delta": 0,
        "reason": "Không cộng điểm giao dịch món “Bàn học gỗ” (vai trò cho/tặng): giao dịch thứ 3 giữa cùng hai người trong 90 ngày",
        "createdAt": "2026-10-19T09:00:00Z"
      },
      {
        "delta": 10,
        "reason": "Cộng 10 điểm hoàn tất giao dịch món “Nồi cơm điện” (vai trò cho/tặng)",
        "createdAt": "2026-10-18T10:00:00Z"
      }
    ],
    "page": {
      "size": 20,
      "number": 0,
      "totalElements": 2,
      "totalPages": 1
    }
  }
}
```

Giao dịch hoàn tất nhưng không được cộng đủ điểm vẫn có một dòng lịch sử; `reason` giải thích lý do:
- Lặp cặp (BR-U08): `delta = 0`, `"Không cộng điểm giao dịch món “Bàn học gỗ” (vai trò cho/tặng): giao dịch thứ 3 giữa cùng hai người trong 90 ngày"`.
- Chạm trần ngày (BR-U11): `"Cộng 5/10 điểm hoàn tất giao dịch món “Áo khoác” (vai trò cho/tặng): chạm trần 30 điểm trong ngày"`; đã đạt trần thì `delta = 0`, `"Không cộng điểm giao dịch món “Ghế xếp” (vai trò cho/tặng): đã đạt trần 30 điểm trong ngày"`.
Lý do gọi giao dịch bằng tên món, không dùng `#id`. `accuracyRate` theo thang 0–1, `null` khi dưới 3 giao dịch đã được đánh giá.

- **Mã lỗi:**

| Mã HTTP | Ý nghĩa | Mô tả |
|---|---|---|
| `200` | Thành công | Trả về thông tin uy tín và lịch sử điểm |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `404` | Not Found | `USER_NOT_FOUND`: Tài khoản không tồn tại |

