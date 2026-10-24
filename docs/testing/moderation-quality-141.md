# Đo chất lượng bộ lọc — issue #141

## Bộ dữ liệu và cách chạy

Bộ dữ liệu tổng hợp theo SRS 7.5 gồm [100 tin chat](../../backend/src/test/resources/moderation-quality/chat.psv) (50 vi phạm, 50 hợp lệ) và [60 bài đăng](../../backend/src/test/resources/moderation-quality/posts.psv) (30 vi phạm, 30 hợp lệ). Mỗi dòng có ID, nhãn `violation`, loại ca và nội dung. Các địa chỉ `example.com/.org/.net` và số trong mẫu chỉ dùng để thử bộ lọc, không lấy từ người dùng thật.

Chạy trong `backend/`:

```powershell
.\mvnw.cmd -Dtest=ModerationQualityIT test
```

`ModerationQualityIT` gọi `ChatContactFilter.checkViolation` trước khi chốt lịch giao nhận và `ContentModerationService.checkPost` với lexicon seed từ PostgreSQL Testcontainers. Dòng `QUALITY` trong kết quả test cho biết TP, FN, FP, TN và ID các ca sai. Test yêu cầu đủ 160 mẫu, nhãn hợp lệ, ID không trùng, và không được giảm dưới mức nền đã đo. Không gọi dịch vụ AI bên ngoài.

## Kết quả tầng 1 (19/10/2026)

| Luồng đo | TP | FN | FP | TN | Bắt đúng TP/(TP+FN) | Chặn nhầm FP/(FP+TN) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Chat: quyết định chặn | 40 | 10 | 0 | 50 | 80,0% | 0,0% |
| Chat: riêng bộ phát hiện liên hệ | 30 | 10 | 0 | 50 | 75,0% | 0,0% |
| Bài đăng: gắn cờ `BLOCK` hoặc `REVIEW` | 26 | 4 | 1 | 29 | 86,7% | 3,3% |
| Bài đăng: chỉ `BLOCK` | 25 | 5 | 1 | 29 | 83,3% | 3,3% |

`TP` là mẫu vi phạm bị phát hiện; `FN` là mẫu vi phạm bị bỏ sót; `FP` là mẫu hợp lệ bị chặn/gắn cờ; `TN` là mẫu hợp lệ đi qua. Dòng chat chỉ dùng 40 mẫu vi phạm liên hệ và 50 mẫu hợp lệ. `REVIEW` của bài đăng là gắn cờ để Admin xem, không phải chặn đăng vĩnh viễn; vì vậy có cả hai cách đếm.

### Mẫu cần xử lý

- Chat bỏ sót `C031–C035`: liên hệ bằng tên hoặc handle Zalo, Facebook, Instagram, Telegram. `C036–C040`: số/email/URL viết né regex. Tầng 1 hiện không chặn các ca này.
- Bài đăng bỏ sót `P027–P029`: tên hoặc handle mạng xã hội ngoài Zalo; `P030`: số viết hoàn toàn bằng chữ. `P026` (Zalo) được gắn `REVIEW`, nên xuất hiện trong FN của chỉ số `BLOCK` nhưng không phải FN của chỉ số gắn cờ.
- `P051` là ca hợp lệ có chuỗi “cần sa kê” và bị lexicon `cần sa` gắn `BLOCK`. Đây là ca chặn nhầm cần xem lại ngoại lệ hoặc cách khớp theo ngữ cảnh.

Mức 80,0% của chat tầng 1 chưa đạt mục tiêu NFR-11 (bắt đúng ít nhất 90% tin chứa liên hệ, chặn nhầm nhiều nhất 5%). Các ca mạng xã hội và lách chữ là đầu vào thích hợp để đánh giá chat tầng 2 ở #143. Có thể cân nhắc bổ sung quy tắc `REVIEW` cho tên/handle mạng xã hội trong bài đăng; cần đo lại FP trước khi đổi lexicon đang dùng trên hệ thống.

## Tầng 1 + tầng 2

**Chưa có số liệu hợp lệ.** Lọc chat bằng LLM cho ca mơ hồ thuộc #143 chưa có trong `develop`. AI sàng lọc bài đăng đã có nhưng lần đo này không gọi nhà cung cấp AI thật: chưa có một đợt chạy kiểm định có kiểm soát với model và cấu hình cố định. Không dùng kết quả mock làm số đo chất lượng.

Sau khi #143 merge, chạy cùng bộ 160 mẫu theo hai chế độ tầng 1 và tầng 1 + tầng 2, lưu model, thời điểm, cấu hình ngưỡng, TP/FN/FP/TN và ID ca sai; báo cáo riêng kết quả chat và bài đăng. Bộ dữ liệu này được soạn trước khi đo, nhưng chỉ là mẫu tổng hợp nhỏ nên không đại diện cho tỷ lệ lỗi của người dùng thật.
