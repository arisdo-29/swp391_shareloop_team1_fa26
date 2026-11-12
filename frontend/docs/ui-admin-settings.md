# Giao diện quản trị nội dung và vận hành

## Phạm vi và trạng thái ban đầu

Triển khai trên `feat/shareloop-ui-redesign`, tiếp tục từ các đợt giao diện trước. Đầu đợt không có thay đổi tracked hoặc staged. `docs/design/` đã tồn tại ở trạng thái untracked và được giữ nguyên. Build ban đầu đạt, có cảnh báo chunk lớn hơn 500 kB. Đã đọc hướng dẫn repository/frontend, README, DESIGN, package.json, package-lock.json và source liên quan.

Trang tham khảo không truy cập được qua công cụ đọc web trong phiên này. Thiết kế dùng theme, layout, Be Vietnam Pro, component bảng/form/dialog và các token sáng/tối đã có trong repo. Không thêm thư viện hoặc thay framework.

| Trang | Chức năng đã có trước khi sửa | Kết quả giao diện |
| --- | --- | --- |
| Danh mục | Danh sách, thêm/sửa, danh mục cha, đẩy tin, bật/tắt và đổi thứ tự bằng nút | Bảng rõ thứ tự, tên, quan hệ cha, phân loại, trạng thái và nhóm thao tác. Form thống nhất, loading khác trạng thái rỗng. |
| Khu vực | Danh sách có parentId/cấp, thêm/sửa và bật/tắt | Hiển thị đúng tên cha từ dữ liệu, không dựng cây hoặc reorder mới. Form và bảng responsive. |
| Checklist | Nội dung, mô tả, trạng thái, thêm/sửa, bật/tắt và đổi thứ tự | Bảng ưu tiên nội dung và mô tả, giữ cảnh báo thiếu mô tả, nhóm nút và dialog thống nhất. |
| Từ khóa | Danh sách phân trang phía client, thêm/sửa, bật/tắt và kiểm tra nội dung qua API | Bảng, trạng thái và vùng kiểm tra tách rõ. Giữ nguyên từ khóa, dấu và khoảng trắng khi hiển thị. Kết quả khớp chưa phải kết luận vi phạm. |
| Phí, hạn mức, cấu hình | Nhóm theo metadata backend, sửa từng mục kèm lý do | Nhóm rõ ràng, phân biệt giá trị hiện tại/mặc định, bổ sung nhãn đơn vị có nguồn và ngữ cảnh khi sửa. |
| Uy tín và hạng | Bảng hạng, sửa nội dung hạng, sửa quy tắc qua API cấu hình | Phân biệt điểm uy tín, số bài và ngày. Giữ các hạng và ngưỡng hiện tại, không tính khoảng điểm mới hoặc tổng hệ thống. |
| Nhật ký | Chỉ đọc, tìm người thao tác, lọc hành động/ngày, phân trang server | Bảng tra cứu và panel nội dung dài, ngữ cảnh thời gian/người/hành động/đối tượng. Giữ parser checklist và không hiển thị raw JSON không nhận diện. |
| Cảnh báo vận hành | Chỉ đọc, cặp giao dịch lặp và nhóm IP | Nhóm tín hiệu rõ ràng, giữ dữ liệu/ngưỡng nguồn, phân biệt cảnh báo với kết luận và không thêm hành động xử lý. |

Cả tám trang đều tồn tại. Không thêm các chức năng chưa có: xóa danh mục/khu vực/checklist/từ khóa, kéo thả, chỉnh hàng loạt, khôi phục cấu hình mặc định, chỉnh điểm thành viên tại trang Uy tín, chỉnh/xóa/export nhật ký, đánh dấu đã xem hoặc xử lý cảnh báo. Thuộc tính danh mục đang bị ẩn bởi `SHOW_CATEGORY_ATTRIBUTES = false` vẫn giữ nguyên.

## File thay đổi

- `src/pages/admin/AdminPages.tsx`: bảng danh mục/khu vực/checklist/từ khóa, form dùng chung, bộ lọc và panel nhật ký.
- `src/pages/admin/AdminConfigs.tsx`: nhóm cấu hình, bảng giá trị và đơn vị.
- `src/pages/admin/ConfigEditDialog.tsx`: ngữ cảnh giá trị hiện tại/mặc định và bố cục form.
- `src/pages/admin/AdminReputation.tsx`: bảng hạng, quy tắc, đơn vị và nút/form.
- `src/pages/admin/AdminAnomalies.tsx`: nhóm cảnh báo, ngưỡng từ response, bảng và chú thích tín hiệu.
- `src/pages/admin/AdminSettingsParts.tsx`: component trình bày tiêu đề khối và nhãn đơn vị. Đơn vị đối chiếu với `backend/.../setting/ConfigKey.java`, không thay giá trị hoặc quy đổi. Key chưa có nguồn đơn vị không được suy diễn.
- `src/pages/admin/admin-settings.css`: style riêng theo lớp của các trang trong đợt này, dùng token hiện có và tái sử dụng CSS panel chi tiết.
- `src/pages/admin/AdminAreas.test.tsx`: kiểm tra quan hệ cha, không mutation khi xem, validation tên, payload và lỗi bật/tắt.
- `src/pages/admin/AdminAuditLogs.test.tsx`: thêm kiểm tra nội dung dài là văn bản, mở panel không request mới, Escape và không hiển thị trường nhạy cảm của JSON checklist.
- `docs/ui-admin-settings.md`: báo cáo này.

## Ranh giới giữ nguyên

Đã so source đầu đợt với source cuối bằng AST: toàn bộ code ngoài JSX của năm file runtime có sẵn giữ nguyên. Toàn bộ callback, disabled/value/checked, min/max/maxLength và route/link trong JSX cũ đều được giữ. Chỉ thêm callback đóng panel `setExpanded(false)` phục vụ UI, tái sử dụng state mở chi tiết có sẵn. Panel văn bản dùng `AdminRecordDetails` với state UI đã có trong component chung.

Giữ nguyên effect và sự kiện focus/visibility của checklist, khóa mutation, validation độ dài 255/1000, chống gửi trùng, kiểm tra thứ tự ID, sort và cách cập nhật sau mutation. Giữ nguyên payload/trim/chế độ khớp/ngoại lệ của từ khóa. Giữ nguyên validation ngưỡng hạng, -1 không giới hạn, lý do và cơ chế lưu tuần tự các cấu hình hạng thay đổi. Endpoint riêng của `chat_ai_threshold` không đổi.

Không thay backend, database, services/API client, DTO/type, hook `useAdminConfigs`, route/guard, authentication, query/cache, formatter hoặc công thức nghiệp vụ. Không sửa CSS toàn cục, dependency/lockfile, môi trường hoặc triển khai. Không thêm request khi mở panel. Không thêm mặc định để gửi dữ liệu khi tải lỗi.

## Kiểm tra

- Build ban đầu và sau triển khai đạt. Cảnh báo bundle lớn hơn 500 kB đã có từ đầu, không nâng dependency hoặc refactor bundle trong đợt này.
- ESLint đạt. CSS/component/test mới qua Prettier. `git diff --check` sạch.
- Toàn bộ Vitest: **602/602 đạt**, không bỏ/tắt test. Chạy với `--maxWorkers=2` để tránh tranh tài nguyên Windows. Bộ liên quan ban đầu sau sửa có 60/60 đạt, bổ sung năm ca cho khu vực và nhật ký.
- Sau hoàn thiện markup, chạy lại ba file test cảnh báo/nhật ký/khu vực: **15/15 đạt**. Build và lint cuối cùng tiếp tục đạt.
- Kiểm tra API có sẵn: **28/28 đạt**, dùng HTTP adapter trong bộ nhớ.
- Chrome local: tám trang và bảy form/panel, mỗi nơi **390/768/1440 px**, sáng/tối, tổng **90 tổ hợp**. Không tràn ngang trang/dialog, bảng cuộn trong khung. Đã xem ảnh desktop và mobile, tên/mô tả/từ khóa/email dài, giá trị chuỗi dài và dữ liệu thiếu.
- Kiểm tra browser các luồng: sửa danh mục và payload, reorder có sẵn, thêm khu vực và lỗi bật/tắt, sửa checklist/lỗi/lưu/reorder, phân trang từ khóa và kiểm tra nội dung, lý do và lỗi lưu cấu hình, điều kiện biên hạng, lọc ngày/phân trang nhật ký và panel.
- Kiểm tra bổ sung browser: loading của cả tám trang, tìm/chọn người thao tác và lọc actorId, focus bàn phím trong panel và trả focus khi đóng, route member/guest bị chặn đúng guard hiện có, cảnh báo chỉ đọc.
- Checklist sau chỉnh trong dữ liệu thử vẫn được trang duyệt bài đọc, chỉ hiện tiêu chí active và giữ nút xác nhận disabled khi chưa hoàn tất. Source trang duyệt bài không thay đổi.
- Không render HTML từ nội dung log. JSON không nhận diện và trường token kiểm thử trong JSON checklist không được hiển thị. Giữ nguyên parser cũ, không suy diễn trước/sau hoặc kết quả hành động khi response không có.

Kiểm tra browser dùng request interception với fixture nằm trong script tạm ngoài repository và phiên trình duyệt kiểm thử riêng. Mọi request API được chặn/trả response kiểm thử, gồm cả mutation. Không sửa cấu hình, từ khóa, danh mục, hạng hoặc dữ liệu production. Fixture và phiên kiểm thử không được đưa vào ứng dụng. Kiểm tra quyền ở đây xác nhận frontend guard, không thay thế kiểm thử quyền backend thật.

## Giới hạn còn lại

- Chưa xác minh thao tác ghi hoặc dữ liệu thực với tài khoản/backend sandbox. Các response thành công/lỗi trong browser là response kiểm thử có kiểm soát.
- API không có mã công khai cho danh mục/khu vực, trường bắt buộc riêng cho từng checklist, kết quả độc lập hoặc before/after cho nhật ký. Không tạo các dữ liệu đó hoặc hiện ID kỹ thuật thay thế.
- Tìm kiếm danh mục/khu vực/checklist/từ khóa, export nhật ký, xóa mục, thử thuật toán từ khóa cục bộ và xử lý cảnh báo không có handler tương ứng tại các trang hiện tại, nên không thêm.
- Giữ hành vi cũ: lỗi tải ở một số trang danh sách có thể đồng thời hiện trạng thái rỗng hoặc dữ liệu cũ. Kiểm tra từ khóa không có state riêng phân biệt chưa kiểm tra với kết quả không khớp. Trang cảnh báo không có handler thử lại. Không thay logic để sửa các điểm này.
- Bảng hạng vẫn dùng bốn hàng hạng được định nghĩa sẵn và ngưỡng Đồng 0 như trước, kể cả response danh sách cấu hình thành công nhưng rỗng. Các cấu hình thiếu hiển thị dấu gạch, không thêm giá trị gửi thay thế.
- Logic lưu hạng vẫn gửi lần lượt từng cấu hình thay đổi, không phải một transaction nguyên tử. Không thay cơ chế này trong phạm vi UI.
- Parser log giữ cách xử lý văn bản có sẵn, không bổ sung bộ lọc dữ liệu nhạy cảm ở frontend thay trách nhiệm backend. Panel không dump `log.data`, không thêm raw JSON hoặc trường xác thực mới.

## Xem local

Dev server ở **http://localhost:5173**, dùng cấu hình backend hiện tại của repo. Đăng nhập tài khoản admin sẵn có để xem dữ liệu thật.

- http://localhost:5173/admin/categories
- http://localhost:5173/admin/districts
- http://localhost:5173/admin/checklist
- http://localhost:5173/admin/keywords
- http://localhost:5173/admin/settings/fees
- http://localhost:5173/admin/reputation
- http://localhost:5173/admin/audit-logs
- http://localhost:5173/admin/alerts

Không stage, commit, push, tạo PR, merge hoặc deploy trong đợt này. `docs/design/` không bị sửa hoặc đưa vào phạm vi.
