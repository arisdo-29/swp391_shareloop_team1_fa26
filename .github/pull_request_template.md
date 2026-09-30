## Mô tả

<!-- Thay đổi này làm gì và vì sao? Gắn link issue nếu có: Closes #123 -->

## Loại thay đổi

- [ ] `feat` – tính năng mới
- [ ] `fix` – sửa lỗi
- [ ] `refactor` – tái cấu trúc, không đổi hành vi
- [ ] `test` – thêm hoặc sửa test
- [ ] `docs` / `chore` – tài liệu, cấu hình

## Phạm vi

- [ ] Frontend (`frontend/`)
- [ ] Backend (`backend/`) – module: <!-- auth / user / wallet / listingfee / catalog / media / item / moderation / search / request / chat / notification / setting / review / report / reputation / admin / audit -->
- [ ] Tài liệu (`docs/`)

## Checklist

- [ ] Nhánh tách từ `develop` và PR trỏ vào `develop`
- [ ] Đã chạy được ở máy local
- [ ] Đã có test cho phần logic mới (bắt buộc với chuyển trạng thái `Request` / `Item` và mọi nhánh trừ Credit)
- [ ] Không commit `.env`, khoá API, mật khẩu hay file cấu hình cá nhân
- [ ] Nếu thêm hoặc đổi endpoint: đã cập nhật `docs/api/` và báo bạn FE
- [ ] Commit và mô tả PR không có dòng ghi công AI
- [ ] Đã chạy `./mvnw spotless:apply` và `./mvnw verify` (nếu sửa backend)

## Chạm vào vùng nhạy cảm?

- [ ] `wallet` (`CreditService`) / luồng tiền → cần review bởi người ngoài module
- [ ] `listingfee` (phí đăng, sửa, gia hạn, đẩy bài) → cần review bởi người ngoài module
- [ ] Chuyển trạng thái `Request` / `Item` → cần review bởi người ngoài module
- [ ] File dùng chung (`pom.xml`, `common/`, `config/`, `application.yml`, `db/migration/`) → đã báo cả nhóm

## Ảnh chụp / ghi chú thêm

<!-- Với thay đổi giao diện: đính kèm ảnh trước và sau. -->
