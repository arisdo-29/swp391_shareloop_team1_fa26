# Đặc tả ShareLoop (docs/srs)

SRS Final Report cập nhật 09/11/2026 là **nguồn chính**. Business Rule v12 chỉ giải thích thêm.

## Các file

| File | Vai trò |
|---|---|
| `SRS_ShareLoop_FinalReport_09-11.docx` | Bản gốc, **sửa ở đây** |
| `SRS_ShareLoop_FinalReport_09-11.pdf` | Bản đọc (xuất từ .docx) |
| `SRS_ShareLoop_FinalReport_09-11.md` | Bản tra cứu, tạo bằng pandoc, **không sửa tay** |
| `ShareLoop_BusinessRule_v12.docx` | Bản gốc của Business Rule v12 |
| `ShareLoop_BusinessRule_v12_09-11.md` | Bản chữ của Business Rule v12: giải thích mục A–H (ví dụ, lý do chọn con số). Mục I–R chỉ có trong SRS mục 5.1 |

## Tra cứu nhanh

| Cần tìm | Ở đâu trong SRS |
|---|---|
| Luật nghiệp vụ (nhóm A–R) | Mục 5.1 |
| Bảng cấu hình | Mục 5.2 |
| Thông báo lỗi (MSG) | Mục 5.4 |
| Quyết định D-01 … D-26 | Mục 5.5 |
| Use case (UC-01 …) | Mục 2.2.2 |
| Đặc tả từng chức năng | Mục 3.x |

Mã luật dạng `TK-01`, `TT-04`, `YC-03` được dùng trong code và `docs/api`.

## Cập nhật khi SRS đổi

1. Sửa file `.docx` bằng Word.
2. Cập nhật mục lục (Ctrl+A, F9).
3. Xuất PDF.
4. Tạo lại `.md`:
   ```
   pandoc SRS_ShareLoop_FinalReport_09-11.docx -t gfm --wrap=none -o SRS_ShareLoop_FinalReport_09-11.md
   ```
   rồi xoá các dòng ảnh nhúng (dòng bắt đầu bằng `![`) để file nhẹ.

## Tài liệu cũ

SRS v10, Luồng hoạt động & ERD v10, Lộ trình Backend và các file rà soát tháng 10 đã bị xoá. Cần xem lại thì tìm trong lịch sử git trước commit này.
