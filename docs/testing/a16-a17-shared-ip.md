# A16 và A17 – Đăng ký và cảnh báo cùng IP

1. Mở Cấu hình hệ thống. Mục **Số tài khoản mới mỗi IP** đã được bỏ. Tại **Uy tín và Hạng**, xác nhận ngưỡng mặc định **5** và cửa sổ mặc định **30 ngày**. Thử lưu ngưỡng **1** và kiểm tra hệ thống báo lỗi. Đặt ngưỡng **3**.
2. Dùng cùng máy và cùng Wi-Fi để đăng ký 4 tài khoản test với email và số điện thoại khác nhau. Cả 4 lượt đều đến bước nhập OTP. Hoàn thành OTP cho 3 tài khoản và để tài khoản thứ tư chờ xác thực.
3. Mở `/admin/alerts`. Nhóm IP phải có đúng 3 tài khoản đã xác thực, hiển thị họ tên, email, ngày đăng ký, sao, trạng thái, chú thích mạng dùng chung và ngưỡng **3 trong 30 ngày**. Link **Cấu hình cảnh báo IP** mở trang Uy tín và Hạng.
4. Đặt ngưỡng **4**, quay lại Cảnh báo và xác nhận nhóm vừa tạo biến mất. Nếu không có nhóm khác, màn hình hiển thị **Không có cảnh báo**. Đặt lại **3** để xác nhận nhóm xuất hiện, rồi trả ngưỡng về **5** sau test.
