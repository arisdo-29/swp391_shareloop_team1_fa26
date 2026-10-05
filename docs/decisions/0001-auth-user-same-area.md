# 0001 — Module auth được inject UserRepository

- Trạng thái: đã chốt.
- Bối cảnh: Lộ trình mục 4.8 xếp auth và user cùng một dòng, cùng người sở hữu (BE1).
- Quyết định: module `auth` được inject trực tiếp `user.repository.UserRepository` (đăng nhập, đăng ký, OTP đều
  đọc/ghi bảng `users`). Module `user` cũng dùng DTO `auth.dto.AuthUserResponse` cho `GET /api/v1/me`.
- Giới hạn: đây là ngoại lệ chỉ cho cặp auth ↔ user. Mọi module khác vẫn phải gọi qua `UserService`,
  không inject `UserRepository`.
- Hệ quả: nếu sau này auth và user đổi người sở hữu thì phải xem lại quyết định này.
