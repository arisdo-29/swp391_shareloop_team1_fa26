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
- [ ] Backend (`backend/`) – module: <!-- auth / item / request / chat / credit / admin / report -->
- [ ] Tài liệu (`docs/`)

## Checklist

- [ ] Nhánh tách từ `develop` và PR trỏ vào `develop`
- [ ] Đã chạy được ở máy local
- [ ] Đã có test cho phần logic mới (bắt buộc với chuyển trạng thái `Request` và mọi nhánh trừ Credit)
- [ ] Không commit `.env`, khoá API, mật khẩu hay file cấu hình cá nhân
- [ ] Nếu thêm hoặc đổi endpoint: đã cập nhật `docs/api/` và báo bạn FE

## Chạm vào vùng nhạy cảm?

- [ ] `CreditService` / luồng tiền → cần review bởi người ngoài module
- [ ] `RequestService` / chuyển trạng thái → cần review bởi người ngoài module
- [ ] File dùng chung (`pom.xml`, `common/`, `config/`, `security/`) → đã báo cả nhóm

## Ảnh chụp / ghi chú thêm

<!-- Với thay đổi giao diện: đính kèm ảnh trước và sau. -->
