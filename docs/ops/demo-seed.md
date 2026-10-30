# Dữ liệu demo (seed)

Dựng dữ liệu nền cho buổi demo bằng `DemoDataSeeder` (`backend/src/main/java/com/shareloop/demo/`) và
`backend/src/main/resources/db/demo/demo-seed.sql`. Seed **không** nằm trong migration Flyway, nên không chạy ở môi trường nào
khác ngoài khi bạn bật profile `demo`.

## Cách chạy

Bật thêm profile `demo` khi khởi động backend; seed tự chạy một lần lúc app khởi động xong.

```bash
# máy cá nhân (trong backend/, đã docker compose up -d)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo

# deploy (staging/prod): đặt biến môi trường rồi khởi động lại
SPRING_PROFILES_ACTIVE=prod,demo
DEMO_SEED_PASSWORD=<mật khẩu test tuỳ chọn>   # bỏ trống thì dùng Demo@12345
```

Sau buổi demo, bỏ `demo` khỏi `SPRING_PROFILES_ACTIVE` để các lần khởi động sau không seed nữa. Dữ liệu đã tạo vẫn nằm trong DB.

Chạy lại nhiều lần an toàn (idempotent): tài khoản trùng email bị bỏ qua, bài đăng trùng (cùng người đăng và tiêu đề) và giao dịch
trùng không tạo thêm, Credit chỉ cộng một lần (dòng sổ cái ghi chú `Demo seed`).

## Mật khẩu

Mật khẩu của mọi tài khoản demo là **mật khẩu TEST**, mặc định `Demo@12345`, đọc từ biến môi trường `DEMO_SEED_PASSWORD`.
Đây là mật khẩu công khai trong repo: **không dùng lại mật khẩu thật** và đừng để profile `demo` bật lâu trên môi trường
có người dùng thật. Mật khẩu được băm bằng `PasswordEncoder` (BCrypt), cùng bean mà đăng nhập (`AuthService`) dùng để so khớp.

## Dữ liệu được tạo

| Tài khoản | Vai trò | Ghi chú |
| --- | --- | --- |
| `admin.demo@shareloop.local` | Admin | Dùng vào trang quản trị |
| `an.demo@shareloop.local` | Người dùng | 200 Credit; người đăng các bài Cho/Trao đổi |
| `binh.demo@shareloop.local` | Người dùng | 200 Credit; người nhận trong giao dịch đã chốt lịch |
| `chi.demo@shareloop.local` | Người dùng | 200 Credit; còn **đúng 1 sao uy tín** (demo cấm tự động) |

- Bài Cho và Trao đổi đang hiển thị (`APPROVED`): giáo trình Java, vợt cầu lông (Trao đổi), nồi cơm điện.
- 1 giao dịch ở trạng thái `LOGISTICS_CONFIRMED` (đã chốt lịch): Bình nhận "Bộ ly thủy tinh" của An, hẹn gặp trực tiếp
  (bài ở trạng thái `RESERVED`).
- Tiêu đề bài bắt đầu bằng `Demo - ` để dễ phân biệt.

Seed chỉ tạo dữ liệu nền bằng schema hiện có, không phụ thuộc code chưa merge. Hành vi cấm tự động khi hết sao do module
reputation đảm nhiệm; seed chỉ đặt `trust_stars = 1`.
