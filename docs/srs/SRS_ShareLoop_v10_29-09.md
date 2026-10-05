TÀI LIỆU ĐẶC TẢ YÊU CẦU PHẦN MỀM · SOFTWARE REQUIREMENTS SPECIFICATION
SHARELOOP
Nền tảng Cho – Nhận – Trao đổi đồ cũ cộng đồng
PHIÊN BẢN v10
PostgreSQL · 5 Credit mỗi bài đăng · Bỏ xác thực SMS · Kiểm duyệt bằng lexicon và AI · Dữ liệu 17
bảng
Môn học: SWP391 — Nhóm 1, học kỳ Fall 2026
Đội hình: 5 thành viên (1 Frontend ReactJS, 4 Backend Java Spring Boot)
Công nghệ: Java 21 · Spring Boot 4.1.1 · ReactJS · PostgreSQL 16
Ngày phát hành: 29/09/2026 (bản cập nhật — bổ sung cột chuẩn cho bảng chính theo yêu cầu giảng
viên, C34)

SRS ShareLoop — phiên bản v10
MỤC LỤC
Hướng dẫn đọc tài liệu...............................................................................................................................5
0. Tóm tắt toàn bộ hệ thống.......................................................................................................................6
1. Giới thiệu & Phạm vi..............................................................................................................................8
1.1. Bối cảnh tài liệu...............................................................................................................................8
1.2. Những thay đổi của v10 so với v9...................................................................................................8
1.3. Phạm vi hệ thống.............................................................................................................................9
1.4. Thuật ngữ......................................................................................................................................10
2. Kiến trúc hệ thống................................................................................................................................11
2.1. Sơ đồ kiến trúc..............................................................................................................................11
2.2. Trách nhiệm của từng tầng...........................................................................................................11
2.3. Công nghệ và công cụ...................................................................................................................12
2.4. Nguyên tắc thiết kế xuyên suốt.....................................................................................................13
2.5. Chuyển sang PostgreSQL.............................................................................................................13
2.6. Lưu trữ ảnh [v9].............................................................................................................................14
3. Mô hình Actor.......................................................................................................................................15
3.1. Hai actor kế thừa từ User..............................................................................................................15
3.2. Hệ thống bên ngoài.......................................................................................................................15
4. Mô hình Credit & Thu phí.....................................................................................................................16
4.1. Nguyên tắc.....................................................................................................................................16
4.2. Biểu phí chính thức v10.................................................................................................................16
4.3. Vì sao chuyển sang thu phí đăng bài............................................................................................17
4.4. Vòng đời phí của một bài đăng.....................................................................................................17
4.5. Quy tắc sửa bài [v10 — đã chốt]...................................................................................................18
4.6. Đẩy bài (Boost)..............................................................................................................................19
4.7. Sổ tài chính và các màn hình........................................................................................................19
4.8. Lỗ hổng "đăng bài Cho để né phí Trao đổi" đã được đóng...........................................................19
4.9. Khoảng trống sau khi trao thông tin liên lạc..................................................................................19
5. Tài khoản & xác thực...........................................................................................................................20
5.1. Bỏ xác thực SMS...........................................................................................................................20
5.2. Quy tắc định danh mới..................................................................................................................20
5.3. Rủi ro khi không xác thực số điện thoại........................................................................................20
6. Luồng nghiệp vụ...................................................................................................................................21
6.1. Luồng tổng thể...............................................................................................................................21
6.2. Tài khoản.......................................................................................................................................22
6.3. Nạp Credit......................................................................................................................................25
6.4. Đăng bài & kiểm duyệt ba tầng.....................................................................................................26
6.5. Sửa bài..........................................................................................................................................28
6.6. Gia hạn và đẩy bài.........................................................................................................................29
6.7. Tìm kiếm và AI trợ lý tìm đồ...........................................................................................................30
6.8. Gửi yêu cầu và chọn người...........................................................................................................31
6.9. Chuyển bài Cho thành Trao đổi.....................................................................................................32
Trang 2 / 83

SRS ShareLoop — phiên bản v10
6.10. Khung chat có kiểm soát.............................................................................................................32
6.11. Chốt lịch và trao thông tin liên lạc................................................................................................35
6.12. Giao nhận và hoàn tất.................................................................................................................36
6.13. Vòng đời của Request và bài đăng.............................................................................................37
6.14. Khiếu nại và tranh chấp...............................................................................................................39
6.15. Uy tín, điểm thưởng và hạng.......................................................................................................40
6.16. Xác thực hàng chính hãng [v9]....................................................................................................40
6.17. Phạm vi sản phẩm, chất lượng & hàng cấm [v10]......................................................................41
7. Kiểm soát nội dung: lexicon và AI........................................................................................................43
7.1. So sánh lexicon và AI....................................................................................................................43
7.2. Thiết kế lexicon..............................................................................................................................43
7.3. Hợp đồng dữ liệu khi gọi AI sàng lọc bài đăng..............................................................................44
7.4. Hợp đồng khi gọi AI kiểm duyệt chat [v9]......................................................................................45
7.5. Đo chất lượng bộ lọc trước khi demo............................................................................................45
8. Phân tích phương án AI cho dự án......................................................................................................46
8.1. Bối cảnh và ràng buộc...................................................................................................................46
8.2. Các vai trò AI trong v10.................................................................................................................46
8.3. Các phương án kỹ thuật................................................................................................................46
8.4. Thiết kế trợ lý tìm đồ: tra cứu trước, diễn giải sau........................................................................47
8.5. Kiểm soát chi phí...........................................................................................................................47
8.6. An toàn khi dùng AI.......................................................................................................................47
8.7. Chọn nhà cung cấp LLM................................................................................................................47
9. Yêu cầu chức năng..............................................................................................................................48
9.1. Cách đọc và quy ước ưu tiên........................................................................................................48
9.2. Nhóm A — Tài khoản & xác thực..................................................................................................48
9.3. Nhóm B — Ví Credit & thanh toán.................................................................................................48
9.4. Nhóm C — Bài đăng......................................................................................................................49
9.5. Nhóm D — Phí đăng bài & đẩy bài................................................................................................49
9.6. Nhóm E — Kiểm duyệt bài đăng ba tầng......................................................................................50
9.7. Nhóm F — Tìm kiếm & AI trợ lý.....................................................................................................50
9.8. Nhóm G — Yêu cầu & ghép giao dịch...........................................................................................51
9.9. Nhóm H — Khung chat có kiểm soát............................................................................................51
9.10. Nhóm I — Chốt lịch & trao liên lạc...............................................................................................52
9.11. Nhóm J — Xác nhận giao nhận...................................................................................................52
9.12. Nhóm K — Khiếu nại & uy tín......................................................................................................52
9.13. Nhóm L — Điểm thưởng & hạng.................................................................................................53
9.14. Nhóm M — Quản trị & báo cáo...................................................................................................53
9.15. Nhóm N — Tác vụ nền & thông báo............................................................................................54
9.16. Nhóm O — Có thì tốt...................................................................................................................54
10. Yêu cầu phi chức năng......................................................................................................................55
11. Business Rules...................................................................................................................................56
11.1. Nhóm User (BR-U)......................................................................................................................56
Trang 3 / 83

SRS ShareLoop — phiên bản v10
11.2. Nhóm Donor — người đăng bài (BR-D)......................................................................................56
11.3. Nhóm Receiver — người gửi yêu cầu (BR-R).............................................................................57
11.4. Nhóm Admin & Kiểm duyệt (BR-A).............................................................................................57
11.5. Nhóm Giao dịch & Vận chuyển (BR-T)........................................................................................58
11.6. Nhóm Thu phí & Credit (BR-P)....................................................................................................58
11.7. Nhóm Trao đổi & AI (BR-S).........................................................................................................59
11.8. Nhóm Khung chat (BR-C)............................................................................................................59
12. Đánh giá tổng thể dự án.....................................................................................................................61
12.1. Ưu điểm.......................................................................................................................................61
12.2. Nhược điểm.................................................................................................................................61
12.3. Rủi ro của toàn dự án..................................................................................................................62
12.4. Rủi ro nghiệp vụ giữ lại từ các bản trước....................................................................................62
12.5. Rủi ro mới phát sinh từ v10.........................................................................................................63
12.6. Phương án cắt giảm nếu chậm tiến độ.......................................................................................63
13. Thiết kế dữ liệu (17 bảng)..................................................................................................................65
13.1. Cấu trúc mapping giữ theo giảng viên.........................................................................................65
13.2. Danh sách bảng...........................................................................................................................65
13.3. Những gì đã gộp để giảm số bảng..............................................................................................68
13.4. Nguyên tắc để code đơn giản.....................................................................................................68
13.5. Cột chuẩn của bảng chính (C34).................................................................................................68
14. Góp ý của giảng viên và cách xử lý...................................................................................................70
15. Tổng kết những điểm cần chốt..........................................................................................................71
15.1. Nhóm 1 — chốt trước khi code...................................................................................................71
15.2. Nhóm 2 — giá trị cấu hình...........................................................................................................71
16. Kế hoạch từ tuần 4 đến tuần 9...........................................................................................................73
16.1. Đã xong đến hết tuần 3...............................................................................................................73
16.2. Lịch theo tuần..............................................................................................................................73
16.3. Quy tắc giữ tiến độ......................................................................................................................73
17. Đặc tả Use Case................................................................................................................................75
17.1. Quy ước.......................................................................................................................................75
17.2. Use case của User (cả Member và Admin).................................................................................78
17.3. Use case của Member.................................................................................................................78
17.4. Use case của Admin....................................................................................................................79
17.5. Use case dùng lại (không có actor gọi trực tiếp).........................................................................80
17.6. Đặc tả chi tiết các use case then chốt.........................................................................................80
Phụ lục A. Lịch sử phiên bản...................................................................................................................83
Phụ lục B. Vì sao chọn mô hình P2P thay vì kho vật lý...........................................................................83
Nếu mở bằng Microsoft Word mà mục lục chưa hiện số trang: bấm Ctrl+A rồi F9, chọn "Update entire table".
Trang 4 / 83

SRS ShareLoop — phiên bản v10
Hướng dẫn đọc tài liệu
Bản v10 là bản đặc tả đầy đủ, thay thế hoàn toàn v8 và v9. Người đọc không cần mở lại hai bản trước.
Tài liệu chia thành năm phần:
• Chương 0 — Tóm tắt hệ thống. Đọc trước. Mọi phần sau chỉ là chi tiết của chương này.
• Phần I (chương 1–3) — Tổng quan. Phạm vi, thay đổi so với v9, kiến trúc (chuyển sang
PostgreSQL), actor.
• Phần II (chương 4–8) — Nghiệp vụ. Mô hình thu phí theo bài đăng, tài khoản (bỏ xác thực
SMS), toàn bộ luồng nghiệp vụ kèm sơ đồ, kiểm soát nội dung bằng lexicon và AI, phân tích
phương án AI.
• Phần III (chương 9–12) — Yêu cầu & đánh giá. Yêu cầu chức năng, phi chức năng, business
rule; đánh giá ưu điểm, nhược điểm và rủi ro của toàn dự án.
• Phần IV (chương 13–16) — Chuẩn bị triển khai. Thiết kế dữ liệu tối ưu 17 bảng, trả lời góp ý
giảng viên, bảng tổng kết điểm cần chốt, kế hoạch tuần 4 – tuần 9. Hình ERD chi tiết và từ điển dữ
liệu nằm trong tài liệu riêng "Luồng hoạt động & ERD v10".
• Phần V (chương 17) — Đặc tả Use Case kèm bốn sơ đồ use case v10.
Quy ước ký hiệu:
• Khối viền cam là điểm còn chờ chốt. Trạng thái mới nhất của mọi điểm (đã chốt hay chưa) gom ở
chương 15.
• Khối viền xanh lá là quyết định thiết kế kèm lý do.
• Khối viền xanh dương là ghi chú giải thích.
• Dấu [v10] trong bảng đánh dấu nội dung mới hoặc đã sửa ở bản này; [v9] là nội dung đưa vào từ
v9.
• Mọi số tiền tính theo tỉ lệ 1.000 VNĐ = 1 Credit. Credit là số nguyên.
Trang 5 / 83

SRS ShareLoop — phiên bản v10
0. Tóm tắt toàn bộ hệ thống
Hệ thống giải quyết việc gì
ShareLoop là nền tảng web giúp cộng đồng cho, nhận và trao đổi đồ cũ. Người dùng đăng bài về món
đồ không còn dùng; người khác gửi yêu cầu xin hoặc đề nghị đổi bằng một món đồ đã đăng của mình;
hai bên thống nhất qua khung chat có kiểm soát rồi tự hẹn giao nhận. Mục tiêu là đưa đồ còn tốt đến
đúng người cần và giảm rủi ro khi hai người lạ giao dịch.
Ai dùng hệ thống
Hai actor kế thừa từ User: Member và Admin. Một Member dùng một tài khoản cho mọi vai: đăng bài
thì là người cho, gửi yêu cầu thì là người nhận. Bốn hệ thống bên ngoài: cổng thanh toán VNPay, dịch
vụ LLM, dịch vụ email SMTP và dịch vụ lưu trữ ảnh. Hệ thống không còn dùng SMS.
Hệ thống kiếm tiền bằng cách nào
Người dùng nạp tiền qua cổng thanh toán và nhận Credit theo tỉ lệ 1.000đ = 1 Credit. Từ v10, doanh
thu nằm ở bước đăng bài chứ không nằm ở bước giao dịch: mỗi bài đăng, dù Cho hay Trao đổi,
mất 5 Credit (5.000đ). Phí được giữ lại khi bài chờ duyệt và chỉ trừ thật khi bài được duyệt. Đẩy bài
lên đầu kết quả mất phí, và trợ lý AI tính phí sau 5 lượt miễn phí mỗi ngày. Gửi yêu cầu, chat, chốt lịch
và hoàn tất giao dịch đều miễn phí cho cả hai bên. Sửa bài khi còn bị chặn tự động hoặc khi chưa
được duyệt thì miễn phí; sau khi bài được duyệt, người đăng được sửa miễn phí thêm 1 lần, từ lần sau
mỗi lần sửa tính 5 Credit. Credit đi một chiều: nạp vào rồi tiêu, không rút ra.
Một giao dịch diễn ra thế nào
Người dùng gửi yêu cầu tới một bài đăng. Chủ bài chọn đúng một người. Hệ thống mở khung chat,
ghim thẻ sản phẩm của cả hai bên ở đầu khung. Trong chat, thông tin liên lạc bị chặn bằng bộ lọc hai
tầng. Khi cả hai bên xác nhận lịch hẹn, hệ thống gửi email và số điện thoại của mỗi bên cho bên còn
lại. Sau khi gặp nhau, hai bên cùng xác nhận đã giao, đã nhận; bên nhận trả lời thêm câu hỏi "món đồ
có đúng mô tả không".
Hệ thống giữ an toàn bằng cách nào
Bài đăng qua ba tầng kiểm duyệt: (1) chặn cứng tại form bằng quy tắc và danh sách từ khoá cấm
(lexicon), kể cả số điện thoại, email, đường dẫn trong nội dung; (2) AI đọc văn bản bài đăng và gắn cờ
các dấu hiệu khó bắt bằng quy tắc; (3) Admin duyệt thủ công theo checklist cấu hình được. AI không tự
duyệt và không tự từ chối bài nào. Sau giao dịch, người dùng có 7 ngày để khiếu nại; Admin xác minh
rồi mới trừ sao uy tín. Hệ thống không bồi thường tiền hàng; chế tài chỉ ở cấp tài khoản.
AI đóng vai trò gì
Ba vai trò, đều chỉ đọc văn bản, không đọc ảnh và không tự thực hiện hành động: trợ lý tìm đồ bằng
câu nói tự nhiên; kiểm duyệt chat tầng 2 cho các tin nhắn mơ hồ; sàng lọc bài đăng tầng 2 để gắn
cờ hỗ trợ Admin. Tính năng AI gợi ý ghép đôi đã bị bỏ từ v9.
Trang 6 / 83

SRS ShareLoop — phiên bản v10
Hình 1 — Luồng tổng thể của ShareLoop v10
Trang 7 / 83

SRS ShareLoop — phiên bản v10
1. Giới thiệu & Phạm vi
1.1. Bối cảnh tài liệu
Bản v10 hợp nhất ba nguồn: nội dung đầy đủ của v8, các thay đổi của v9 (25/09/2026), và các quyết
định mới sau buổi góp ý của giảng viên ngày 26/09/2026. Mục tiêu của v10 là trở thành đầu vào trực
tiếp cho giai đoạn thiết kế dữ liệu và viết API. Bản này không vẽ lại ERD; chương 13 chỉ liệt kê những
thay đổi dữ liệu mà ERD v10 phải phản ánh. Bản cập nhật ngày 28/09/2026 đồng bộ toàn bộ tài liệu
với các quyết định đã chốt ở chương 15: phí sửa từ lần thứ hai 5 Credit, AI trợ lý 2 Credit/lượt, ERD 17
bảng đã chốt, backend Java 21, và bộ công cụ phát triển của nhóm.
1.2. Những thay đổi của v10 so với v9
# Thay đổi trong v10 Lý do Xem mục
1 Chuyển cơ sở dữ liệu từ SQL Server sang Deploy online dễ và rẻ hơn (nhiều dịch vụ 2.5
PostgreSQL. managed), có sẵn unaccent, pg_trgm,
JSONB phục vụ tìm kiếm tiếng Việt và cấu hình
động. Một dialect duy nhất cho cả dev và
deploy.
2 Chuyển nguồn thu từ phí giao dịch sang phí Góp ý giảng viên: đăng bài tốn tiền, trao đổi 4
đăng bài: mọi bài 5 Credit (5.000đ); bỏ phí không tốn tiền. Đồng thời xoá khoảng trống "hai
Swap 2 Credit/bên và phí Give 4 Credit. người đã biết nhau né phí" của v8 và lỗ hổng
"đăng bài Cho để né phí Trao đổi".
3 Thêm đẩy bài (boost) có phí. Góp ý giảng viên: thêm tính năng để bài lên tốt 4.6
hơn. Là nguồn thu tự nguyện.
4 Sửa bài: miễn phí khi còn bị chặn tự động Góp ý giảng viên. Không phạt người dùng vì lỗi 4.5
hoặc chưa được duyệt; sau khi duyệt được bị chặn tự động; chặn chiêu đăng nội dung sạch
sửa miễn phí 1 lần, từ lần sau tính phí; mỗi rồi sửa để chèn thông tin cấm.
lần sửa phải duyệt lại.
5 Bỏ ba chốt kiểm tra số dư và cơ chế Hold Giao dịch không còn thu phí nên không còn gì 4.4
trong giao dịch. Hold chỉ còn dùng cho phí để giữ.
đăng bài chờ duyệt.
6 Bỏ xác thực SMS. Chỉ xác thực email bằng SMS giả lập không chứng minh được gì khi 5
OTP; mỗi email gắn đúng một số điện thoại, demo; đăng ký Brandname cần pháp nhân. Giữ
số điện thoại không trùng giữa các tài khoản. ràng buộc định danh bằng quy tắc 1–1.
7 Kiểm duyệt bài đăng lên ba tầng: lexicon + Góp ý giảng viên: tự động phát hiện email, số 6.4, 7
regex chặn tại form, AI gắn cờ, Admin duyệt. điện thoại, từ khoá cấm trong bài. AI chỉ gắn cờ
nên quyết định cuối vẫn giải thích được.
8 Lexicon có hai mức (BLOCK / REVIEW) và Trả lời câu hỏi "có từ khoá cấm là cấm bài đúng 7.2
danh sách ngoại lệ. không": không phải lúc nào cũng cấm, tránh
chặn nhầm "súng nước", "súng bắn keo".
9 Chốt phạm vi danh mục được đăng, ràng Góp ý giảng viên về loại, chất lượng, scope 6.17
buộc chất lượng (tình trạng, ảnh, mô tả lỗi) hạng mục và hàng fake.
và hàng nhái.
10 Thêm câu hỏi "đúng mô tả?" khi xác nhận Góp ý giảng viên: tổng quan sản phẩm có đúng 6.12
nhận hàng, và lý do khiếu nại ngoài đời không.
NotAsDescribed.
11 Chat: gửi thẻ bài đăng vào khung chat; với Góp ý giảng viên: đưa bài đăng vào box chat, 6.10
Trao đổi cho phép đề xuất đổi món đề nghị thấy được sản phẩm của hai bên, vẫn kiểm
bằng một bài đã duyệt khác. soát đồ cấm khi trao đổi.
Trang 8 / 83

SRS ShareLoop — phiên bản v10
# Thay đổi trong v10 Lý do Xem mục
12 Thêm chương phân tích phương án AI cho Nhóm cần căn cứ để chọn cách dùng AI phù 8
dự án. hợp năng lực (4 backend Java, không có nền
tảng ML).
13 Đánh mã lại FR, BR, UC cho khớp mô hình Nhiều yêu cầu cũ gắn với phí giao dịch đã 9, 11, 17
mới; vẽ lại bốn sơ đồ use case. không còn.
14 Vẽ đầy đủ sơ đồ luồng cho mọi nghiệp vụ và Mỗi sơ đồ dùng trực tiếp làm đặc tả cho một 6
sơ đồ trạng thái của bài đăng. nhóm API.
15 Thiết kế dữ liệu còn 17 bảng (giới hạn ≤ 20), Ít entity, ít repository, ít join; code đơn giản hơn. 13
giữ cấu trúc mapping của giảng viên.
16 Thêm đánh giá ưu điểm, nhược điểm, rủi ro Chuẩn bị cho câu hỏi khi bảo vệ và để nhóm 12
của toàn dự án. biết chỗ cần cắt giảm nếu chậm.
Các thay đổi của v9 được giữ nguyên trong v10: tỉ lệ 1.000đ = 1 Credit; bỏ AI gợi ý ghép đôi; kiểm
duyệt chat hai tầng (regex + LLM); checklist duyệt bài cấu hình được; xác thực hàng chính hãng bằng
chứng từ; interface lưu trữ ảnh StorageService.
1.3. Phạm vi hệ thống
1.3.1. Trong phạm vi
• Tài khoản: đăng ký có xác thực email bằng OTP, khai báo một số điện thoại, đăng nhập, hồ sơ,
đổi và quên mật khẩu.
• Ví Credit: nạp qua VNPay (sandbox), xem số dư khả dụng và số đang giữ, lịch sử ví.
• Bài đăng: hai hình thức Cho và Trao đổi; phí 5 Credit mỗi bài; sửa (miễn phí trước khi duyệt và 1
lần sau khi duyệt), gỡ, gia hạn, đẩy bài; vòng đời 30 ngày; gắn nhãn hàng chính hãng có chứng
từ.
• Kiểm duyệt ba tầng: chặn cứng tại form (lexicon + regex), AI gắn cờ, Admin duyệt theo checklist.
• Tìm đồ: tìm kiếm và lọc thủ công miễn phí (hỗ trợ gõ không dấu); trang AI trợ lý tìm đồ.
• Giao dịch: gửi yêu cầu, chọn người, chat có kiểm soát (chặn liên hệ hai tầng, thẻ sản phẩm), chốt
lịch, trao liên lạc, xác nhận hai chiều, state machine có timeout.
• Uy tín & điểm thưởng: TrustStars, điểm, hạng, phục hồi sao, chống cày điểm.
• Khiếu nại & tranh chấp: report trước và sau khi hoàn tất, giải trình, chế tài cấp tài khoản.
• Quản trị: danh mục và thuộc tính, khu vực, lexicon, checklist, cấu hình phí và hạn mức, khoá tài
khoản, giám sát bất thường, báo cáo tài chính.
1.3.2. Ngoài phạm vi
• Không định vị realtime, không tính quãng đường, không tích hợp đơn vị vận chuyển.
• Không bồi thường tiền hàng hay phí vận chuyển, không hoàn tác giao dịch vật lý.
• Không xây kho vật lý.
• Không rút Credit ra tiền mặt, không thưởng Credit cho bất kỳ hành vi nào.
• Không gửi SMS và không xác thực số điện thoại bằng OTP [v10].
• AI không phân tích hình ảnh và không tự ra quyết định duyệt hay từ chối.
• Không huấn luyện hay tự vận hành mô hình AI riêng; chỉ gọi API của một dịch vụ LLM sẵn có.
Trang 9 / 83

SRS ShareLoop — phiên bản v10
1.4. Thuật ngữ
Thuật ngữ Ý nghĩa
Member Actor chính. Một tài khoản vừa có thể cho, vừa xin, vừa trao đổi.
Donor / Receiver Không phải actor, không phải loại tài khoản. Là vai trò trong một giao dịch: người đăng
bài là Donor, người gửi yêu cầu là Receiver.
Item Một bài đăng về một món đồ.
Give / Swap Hai hình thức bài đăng: Cho (cho không) và Trao đổi (đổi hai chiều).
Request Một yêu cầu xin đồ hoặc đề nghị trao đổi gửi tới một Item. Vòng đời giao dịch bám theo
bản ghi này.
Credit Đơn vị tiêu dùng nội bộ, 1.000 VNĐ = 1 Credit, số nguyên. Chỉ có từ nạp tiền, nhả Hold
hoặc điều chỉnh của Admin.
Phí đăng bài (PostFee) [v10] 5 Credit thu khi một bài (Cho hoặc Trao đổi) được duyệt lần đầu.
Hold Credit bị khoá tạm, chưa trừ thật. Trong v10 chỉ phát sinh khi một bài có phí đang chờ
duyệt.
Đẩy bài (Boost) [v10] Trả Credit để bài được xếp đầu kết quả trong một số ngày.
Lexicon [v10] Danh sách từ khoá cấm do Admin quản lý, mỗi từ có danh mục, mức độ
(BLOCK/REVIEW) và danh sách ngoại lệ.
Cờ AI [v10] Kết quả AI sàng lọc bài đăng gắn kèm bài trong hàng chờ Admin. Chỉ là gợi ý,
không phải quyết định.
TrustStars Thanh uy tín dạng sao, mặc định 5 sao, bị trừ khi Admin xác minh vi phạm.
ContactRevealedAt Thời điểm hệ thống trao email và số điện thoại cho hai bên, ngay khi hai bên xác nhận
lịch hẹn.
Trang 10 / 83

SRS ShareLoop — phiên bản v10
2. Kiến trúc hệ thống
2.1. Sơ đồ kiến trúc
Hình 2 — Kiến trúc tổng thể ShareLoop v10
2.2. Trách nhiệm của từng tầng
| Tầng | Trách nhiệm | Tuyệt đối không được làm |
| ---- | ----------- | ------------------------ |
React SPA Hiển thị, thu thập dữ liệu, gọi REST API, chặn  Coi kiểm tra ở trình duyệt là đủ. Mọi
|     | sớm lỗi nhập hiển nhiên. | rule phải kiểm lại ở máy chủ. |
| --- | ------------------------ | ----------------------------- |
Spring Security + JWT Xác thực token, gắn danh tính vào ngữ cảnh,  Phân quyền bằng trường "loại tài
chặn truy cập trái quyền. khoản Donor/Receiver". Chỉ kiểm tra
quyền sở hữu bản ghi và cờ IsAdmin.
Controller Nhận HTTP, kiểm tra định dạng đầu vào, gọi  Chứa business rule, truy cập repository
|     | đúng một service. | trực tiếp, tính tiền. |
| --- | ----------------- | --------------------- |
Service Toàn bộ business rule, mở/đóng transaction,  Trả entity thô ra ngoài. Luôn đi qua
|     | chuyển trạng thái. | DTO để không lộ số điện thoại trước  |
| --- | ------------------ | ------------------------------------ |
ContactRevealedAt.
ContentModerationService  Một service dùng chung cho bài đăng và tin  Mỗi module tự viết bộ lọc riêng. Hai bộ
[v10] nhắn: chuẩn hoá văn bản, lexicon, regex, gọi  lọc lệch nhau sẽ bị người dùng lách.
AI tầng 2.
Trang 11 / 83

SRS ShareLoop — phiên bản v10
| Tầng | Trách nhiệm | Tuyệt đối không được làm |
| ---- | ----------- | ------------------------ |
Repository (Spring Data  Truy vấn và ghi dữ liệu PostgreSQL. Chứa logic nghiệp vụ trong câu truy
| JPA) |     | vấn. |
| ---- | --- | ---- |
Scheduler Job theo giờ: huỷ Request treo, hết hạn bài,  Chạy lặp gây tác dụng hai lần. Mọi job
|     | hết hạn đẩy bài, hết hạn đơn nạp. | phải idempotent. |
| --- | --------------------------------- | ---------------- |
Integration PaymentGatewayClient, AiClient,  Gọi thẳng HTTP tới bên thứ ba từ
|     | NotificationSender (chỉ còn email),  | Service. |
| --- | ------------------------------------ | -------- |
StorageService.
2.3. Công nghệ và công cụ
| Hạng mục | Lựa chọn | Ghi chú |
| -------- | -------- | ------- |
Backend Java 21 (LTS) + Spring Boot 4.1.1 (Web  Bốn thành viên backend dùng chung JDK 21
MVC, Security, Data JPA, Validation,  (Temurin) và bản Spring Boot 4.1.1 khoá
|     | Scheduling, Mail) | trong pom.xml (nhánh đang được hỗ trợ;  |
| --- | ----------------- | --------------------------------------- |
nhánh 3.5 đã hết hỗ trợ miễn phí; trùng môi
trường bài thi thực hành môn Java); build
bằng Maven Wrapper.
Frontend ReactJS (Vite, React Router, Axios,  Một thành viên phụ trách. Deploy Vercel.
TailwindCSS)
Cơ sở dữ liệu [v10] PostgreSQL 16 Dev: chạy bằng Docker. Deploy: dịch vụ
managed có gói miễn phí hoặc cùng nhà
cung cấp với nơi lưu ảnh (C06 — đã chốt).
Quản lý schema bằng Flyway.
Extension PostgreSQL  unaccent, pg_trgm (bắt buộc);  Tìm kiếm không dấu, gần đúng; tìm kiếm ngữ
| [v10]           | pgvector (tuỳ chọn) | nghĩa nếu còn thời gian.              |
| --------------- | ------------------- | ------------------------------------- |
| Migration [v10] | Flyway              | Không dùng ddl-auto=update ngoài môi  |
trường thử nghiệm cá nhân.
Kiểm thử JUnit 5, Mockito, Testcontainers  Test repository chạy trên PostgreSQL thật,
|     | (PostgreSQL) | không dùng H2. |
| --- | ------------ | -------------- |
Cổng thanh toán VNPay sandbox Đã chốt (C11). Dự phòng: service giả lập giữ
nguyên kiến trúc webhook.
AI Một LLM API thương mại gọi qua  Chọn nhà cung cấp ở chương 8 (C07 — chờ
|     | AiClient | thử nghiệm). |
| --- | -------- | ------------ |
Email SMTP thật (Gmail App Password hoặc  Gửi OTP và thông báo.
dịch vụ thử nghiệm)
Lưu trữ ảnh [v9] StorageService: Local (dev), S3- DB chỉ lưu StorageKey. Nơi lưu khi deploy
|                   | compatible (deploy)                     | chốt sau khi web chạy ổn định (C12). |
| ----------------- | --------------------------------------- | ------------------------------------ |
| Đóng gói & deploy | Docker (1 image Spring Boot) trên VPS;  |                                      |
GitHub Actions build + test
Công cụ IDE: IntelliJ IDEA, Antigravity, VS Code.  Mọi mã do AI sinh ra phải được người trong
Trợ lý AI: Claude Code, Codex, Gemini…  nhóm đọc lại, nhất là mã chạm vào Credit và
|     | Git + GitHub. | chuyển trạng thái. Quy ước dùng chung cho  |
| --- | ------------- | ------------------------------------------ |
mọi IDE và trợ lý AI (định dạng mã,
AGENTS.md) nằm trong tài liệu Lộ trình triển
khai.
Trang 12 / 83

SRS ShareLoop — phiên bản v10
2.4. Nguyên tắc thiết kế xuyên suốt
• Tiền chỉ được đụng tới trong CreditService. Mọi thao tác chạy trong transaction có khoá dòng
Users (SELECT … FOR UPDATE).
• Trạng thái Request và trạng thái Item chỉ đổi trong service sở hữu nó và chỉ theo các mũi tên ở sơ
đồ trạng thái.
• Mọi lời gọi ra ngoài có thời gian chờ và đường lui. AI lỗi thì hệ thống vẫn chạy được, chỉ kém tiện
hơn.
• Thông tin liên lạc là dữ liệu nhạy cảm, chỉ đưa vào DTO sau ContactRevealedAt.
• Mọi tham số phí, hạn mức, mốc thời gian đọc từ cấu hình (WebsiteAttributes), không hard-
code.
• Mọi hành động của Admin ghi ActivityLogs kèm người thực hiện, thời điểm, lý do.
2.5. Chuyển sang PostgreSQL
2.5.1. Vì sao chuyển
| Tiêu chí | SQL Server (v9) | PostgreSQL (v10) |
| -------- | --------------- | ---------------- |
Deploy online Cần Azure SQL hoặc container SQL Server  Nhiều dịch vụ managed có gói miễn phí
|     | (≥ 2 GB RAM), khó chạy trên VPS gói rẻ. | hoặc rẻ; image Docker nhẹ. |
| --- | --------------------------------------- | -------------------------- |
Dev và deploy cùng  Chỉ khi deploy lên Azure SQL. Luôn cùng dialect: dev Docker, deploy
| dialect |     | managed. |
| ------- | --- | -------- |
Tìm kiếm tiếng Việt Full-text tiếng Việt hạn chế, bỏ dấu phải tự  unaccent bỏ dấu, pg_trgm so khớp gần
|     | xử lý. | đúng, full-text tsvector. |
| --- | ------ | ------------------------- |
Dữ liệu cấu hình và kết  Lưu JSON dạng chuỗi. JSONB có index, truy vấn được bên trong
| quả AI             |     | (checklist, cờ AI).                     |
| ------------------ | --- | --------------------------------------- |
| Khoá dòng cho ví   | Có. | Có: SELECT … FOR UPDATE, ràng buộc      |
| Credit             |     | CHECK không cho số dư âm.               |
| Mở rộng AI sau này | —   | pgvector cho tìm kiếm ngữ nghĩa, không  |
cần thêm hệ quản trị khác.
Chi phí học Nhóm quen hơn. Cú pháp SQL gần giống; khác biệt chủ yếu
ở kiểu dữ liệu và vài hàm.
2.5.2. Những điểm khác cần chú ý khi viết
| SQL Server | PostgreSQL | Ghi chú |
| ---------- | ---------- | ------- |
NVARCHAR VARCHAR / TEXT PostgreSQL mặc định UTF-8, không cần
tiền tố N.
| BIT | BOOLEAN |     |
| --- | ------- | --- |
DATETIME / DATETIME2 TIMESTAMPTZ Lưu UTC, hiển thị theo Asia/Ho_Chi_Minh.
| IDENTITY         | BIGINT GENERATED ALWAYS AS  | Trong JPA dùng           |
| ---------------- | --------------------------- | ------------------------ |
|                  | IDENTITY                    | GenerationType.IDENTITY. |
| TOP n, GETDATE() | LIMIT n, now()              |                          |
Tên bảng Users, cột  users, credit_balance Dùng snake_case chữ thường; tên có
| CreditBalance |     | chữ hoa bắt buộc phải đặt trong nháy kép.  |
| ------------- | --- | ------------------------------------------ |
Spring Boot tự đổi camelCase sang
snake_case.
Trang 13 / 83

SRS ShareLoop — phiên bản v10
SQL Server PostgreSQL Ghi chú
Enum dạng chuỗi VARCHAR + CHECK hoặc Không dùng kiểu ENUM riêng của
@Enumerated(STRING) PostgreSQL để dễ thêm giá trị bằng
Flyway.
Quyết định: chuyển ngay, trước khi viết dòng backend đầu tiên
Backend hiện chưa có mã (thư mục backend/ mới có .gitignore), nên chi phí chuyển gần bằng 0. Nếu
để sau khi đã viết native query thì mỗi câu phải sửa tay. Tên bảng và cột ở ERD v10 sẽ viết theo
snake_case.
2.6. Lưu trữ ảnh [v9]
Mọi thao tác với tệp đi qua interface StorageService với hai bản cài đặt: LocalStorageService (dev) và
S3StorageService (bất kỳ dịch vụ tương thích S3). Cơ sở dữ liệu chỉ lưu StorageKey, không lưu URL
tuyệt đối, nên đổi nhà cung cấp chỉ cần đổi cấu hình. Chứng từ hàng chính hãng lưu ở vùng private,
chỉ trả về bằng URL có hạn (presigned). Dev dùng Local, thử nghiệm MinIO. Nơi lưu khi deploy (C12)
chốt sau khi web chạy ổn định, trước lần deploy thử ở tuần 7; ứng viên là Cloudflare R2 hoặc AWS
S3 khu vực Singapore (phân tích trong tài liệu Lộ trình triển khai). Nhờ StorageService, chốt muộn
không làm đổi mã nghiệp vụ.
Trang 14 / 83

SRS ShareLoop — phiên bản v10
3. Mô hình Actor
3.1. Hai actor kế thừa từ User
• Member — người dùng thông thường. Vai Donor hay Receiver được suy ra tại thời điểm chạy
bằng cách so UserId đang đăng nhập với Items.DonorId và Requests.ReceiverId.
• Admin — quản trị viên, cũng là một bản ghi trong bảng người dùng với cờ IsAdmin = true.
Không tách Donor và Receiver thành actor riêng. Nếu mã nguồn xuất hiện DonorController và
ReceiverController thì mô hình đã bị hiểu sai.
3.2. Hệ thống bên ngoài
Tác nhân Trách nhiệm Cách giao tiếp
Cổng thanh toán (VNPay Nhận tiền thật, báo kết quả để cộng Credit. Tạo đơn → chuyển hướng →
sandbox) ShareLoop không giữ tiền, không xử lý thẻ. cổng gọi webhook có chữ ký về
ShareLoop.
Dịch vụ AI (LLM API) [v10] Ba nhiệm vụ: trợ lý tìm đồ, kiểm duyệt chat Gọi qua AiClient, gửi văn bản
tầng 2, sàng lọc bài đăng tầng 2. Không đọc ảnh, trong khối dữ liệu tách biệt, nhận
không tự hành động. JSON.
Dịch vụ Email (SMTP) [v10] Gửi OTP khi đăng ký, đổi số điện thoại, quên Qua NotificationSender.
mật khẩu; gửi thông báo giao dịch và thông tin liên
lạc. Không còn kênh SMS.
Dịch vụ lưu trữ ảnh [v9] Lưu ảnh bài đăng, ảnh minh chứng, chứng từ hàng Qua StorageService.
chính hãng.
Cập nhật sơ đồ use case
Actor ngoài "Email/SMS Service" trên sơ đồ đổi tên thành "Email Service". Actor "AI Service" nối thêm vào use
case sàng lọc bài đăng (UC-60). Xem chương 17.
Trang 15 / 83

SRS ShareLoop — phiên bản v10
4. Mô hình Credit & Thu phí
4.1. Nguyên tắc
• Một chiều: tiền mặt → Credit → tiêu trong hệ thống. Không rút, không thưởng, không quy đổi
ngược.
• Tỉ lệ cố định: 1.000 VNĐ = 1 Credit. Nạp tối thiểu 10 Credit, số tiền nạp là bội số của 1.000đ.
• Thu ở bước đăng bài, không thu ở bước giao dịch [v10]. Gửi yêu cầu, chat, chốt lịch, hoàn tất
giao dịch đều miễn phí.
• Một mức phí cho mọi bài: 5 Credit (5.000đ) cho mỗi bài, dù là Cho hay Trao đổi. Người cho
không mất thêm gì ở bước giao dịch.
• Cảnh báo trước khi trừ: mọi thao tác có phí đều có popup ghi rõ số Credit.
• Không trừ khi chưa có dịch vụ: phí đăng bài chỉ trừ thật khi bài được duyệt; bị từ chối thì nhả lại
toàn bộ.
4.2. Biểu phí chính thức v10
| Khoản mục | Mức phí | Ai trả | Thời điểm trừ | Ghi chú |
| --------- | ------- | ------ | ------------- | ------- |
Nạp Credit Tối thiểu 10 Credit  — Khi webhook xác nhận Bội số 1.000đ.
(10.000đ)
Đăng bài (Cho hoặc Trao  5 Credit (5.000đ) / 30  Người  Hold khi gửi duyệt, trừ  Bị từ chối thì nhả. Hạng Vàng
đổi) [v10] ngày hiển thị — đã  đăng khi được duyệt lần  trở lên hiển thị 45 ngày cùng
|     | chốt |     | đầu | mức phí. |
| --- | ---- | --- | --- | -------- |
Gia hạn bài [v10] 5 Credit / 30 ngày —  Người  Ngay khi bấm gia hạn Không duyệt lại vì nội dung
|     | đã chốt (C16) | đăng |     | không đổi. |
| --- | ------------- | ---- | --- | ---------- |
Sửa bài khi bị chặn tự  0 — — Chặn ở tầng 1 thì bài chưa tồn
| động hoặc chưa từng      |     |     |     | tại; bài chờ duyệt hoặc bị trả về  |
| ------------------------ | --- | --- | --- | ---------------------------------- |
| được duyệt [v10]         |     |     |     | thì sửa tự do.                     |
| Sửa bài lần đầu sau khi  | 0   | —   | —   | Mỗi bài có đúng 1 lượt này.        |
được duyệt [v10]
Sửa bài từ lần thứ hai  5 Credit/lần — đã chốt  Người  Hold khi gửi bản sửa,  Bản sửa bị từ chối thì nhả,
sau khi được duyệt [v10] (C14b) đăng trừ khi bản sửa được  không tính lượt.
duyệt
Đẩy bài [v10] 5 Credit / 3 ngày — đã  Người  Ngay khi bấm Chỉ bài Approved còn hạn.
|                        | chốt (C15) | đăng |     | Cộng dồn tối đa 14 ngày. |
| ---------------------- | ---------- | ---- | --- | ------------------------ |
| Chuyển Cho → Trao đổi  | 0          | —    | —   | Bài đã trả phí đăng bài. |
[v10]
| Tìm kiếm & lọc thủ công | 0   | —   | —   | Không giới hạn. |
| ----------------------- | --- | --- | --- | --------------- |
Gửi yêu cầu, chat, chốt  0 [v10] — — Bỏ phí Swap 2 Credit/bên và
| lịch, hoàn tất |     |     |     | phí Give 4 Credit của v9. |
| -------------- | --- | --- | --- | ------------------------- |
AI trợ lý tìm đồ [v9] 2 Credit/lượt — đã  Người hỏi Ngay trước khi gọi  5 lượt miễn phí/ngày cho tài
|     | chốt (C17) |     | LLM | khoản đã từng nạp; tài khoản  |
| --- | ---------- | --- | --- | ----------------------------- |
chưa nạp có 1 lượt dùng thử.
Toàn bộ mức phí đã chốt
Đã chốt: mọi bài đăng 5 Credit (C01, C02); luật sửa bài (C14); phí sửa từ lần thứ hai 5 Credit (C14b); gia hạn
5 Credit/30 ngày (C16); đẩy bài 5 Credit/3 ngày (C15); AI trợ lý 2 Credit/lượt sau lượt miễn phí (C17). Mọi con
số vẫn nằm trong cấu hình (website_attributes) nên Admin đổi được khi cần mà không sửa mã.
Trang 16 / 83

SRS ShareLoop — phiên bản v10
4.3. Vì sao chuyển sang thu phí đăng bài
4.3.1. Lập luận
• Đúng chỗ phát sinh chi phí. Chi phí lớn nhất của hệ thống là kiểm duyệt: mỗi bài tốn một lượt AI
sàng lọc và thời gian Admin. Chi phí này phát sinh khi đăng bài, bất kể sau đó có giao dịch hay
không.
• Xoá khoảng trống thất thu của v8. v8 phải chấp nhận việc hai người đã có số của nhau thì giao
dịch trực tiếp ở lần sau để né phí (BR-T12 cũ). Khi doanh thu nằm ở bước đăng bài, việc họ liên
lạc riêng không còn làm hệ thống mất tiền. Chính v8 đã ghi rằng cách xử lý đúng là đổi mô hình
doanh thu.
• Luồng giao dịch đơn giản hẳn. Bỏ được ba chốt kiểm tra số dư, cửa sổ ân hạn 24 giờ, Hold hai
bên và các nhánh hoàn tiền khi giao dịch huỷ. Đây là phần logic dễ sai nhất của v8/v9.
• Lọc bài rác. Mất phí thì người dùng cân nhắc trước khi đăng; lượng bài vào hàng chờ Admin
giảm và chất lượng tăng.
• Một mức phí cho mọi bài, không có lỗ hổng. Nếu chỉ bài Trao đổi mất phí, người dùng sẽ đăng
mọi thứ dưới dạng Cho rồi đổi trong chat. Thu cùng một mức cho mọi bài thì không còn động cơ
lách, và luồng chuyển Cho → Trao đổi không cần thu phí.
• Có nguồn thu tự nguyện. Đẩy bài là khoản người dùng tự chọn trả khi muốn bán nhanh, không
ép buộc ai.
4.3.2. Vì sao bài Cho cũng mất phí
5.000đ thấp hơn giá trị của hầu hết món đồ được đem cho, nên không đủ để cản một người thật lòng
muốn cho đi, nhưng đủ để loại bài rác và bài lách luật. Để không làm người cho thiệt thêm: phí chỉ trừ
khi bài được duyệt, bị từ chối thì nhả lại toàn bộ; người cho không mất gì ở bước giao dịch; điểm
thưởng của người cho vẫn cao nhất (+10 mỗi giao dịch).
4.3.3. Cái giá phải trả
Mất đi Cách bù
Tài khoản số dư 0 không còn bị chặn gửi yêu cầu Giới hạn số Request đang Pending đồng thời (BR-U02), xác
(v8 dùng đây làm lá chắn chính chống tài khoản thực email, giới hạn tài khoản theo IP, chặn email dùng một
ảo). lần.
Giao dịch giả để cày điểm không còn tốn tiền. Giữ BR-U08 (cặp giao dịch lặp), thêm trần điểm mỗi ngày (BR-
U11). Điểm và hạng không quy ra tiền.
Nguồn cung bài Cho có thể giảm vì người cho cũng Phí thấp, chỉ trừ khi được duyệt; điểm thưởng người cho cao
phải trả 5.000đ. nhất; theo dõi tỉ lệ bài Cho/Trao đổi sau demo để điều chỉnh (có
thể miễn phí vài bài Cho đầu mỗi tháng — cấu hình được).
Doanh thu không còn tỉ lệ thuận với số giao dịch Doanh thu tỉ lệ với số bài được duyệt và số lượt đẩy bài; dễ dự
hoàn tất. báo hơn vì không phụ thuộc giao dịch có thành hay không.
Tài khoản mới phải nạp tối thiểu 10 Credit mới Gói nạp nhỏ nhất đủ cho 2 bài; hiển thị rõ ngay trên nút đăng
đăng được bài đầu tiên. bài.
4.4. Vòng đời phí của một bài đăng
Hold không còn dùng cho giao dịch. Nó chỉ còn một việc: giữ phí của bài đang chờ duyệt, để người
dùng không bị trừ tiền cho một bài có thể bị từ chối.
Trang 17 / 83

SRS ShareLoop — phiên bản v10
Sự kiện Tác động lên ví Ghi sổ
Gửi bài đi duyệt (Cho hoặc Trao held_credit += 5 (kiểm tra số dư khả items.fee_state = HELD,
đổi) dụng trước) pending_fee_type = POST
Admin duyệt lần đầu (Approved) held_credit -= 5, credit_balance credit_ledger POST_FEE (−5),
-= 5 fee_state = CHARGED,
post_fee_paid = true
Admin từ chối (Rejected) held_credit -= 5 fee_state = RELEASED;
không ghi credit_ledger vì chưa
trừ
Người đăng rút bài khi còn chờ held_credit -= 5 fee_state = RELEASED
duyệt
Sửa lại bài bị từ chối và gửi lại Nếu chưa từng trả phí đăng bài Phí đăng bài chỉ trừ một lần trong
(post_fee_paid = false): giữ lại 5 đời bài
Credit; nếu đã trả: không giữ gì
Bài đã Approved bị gỡ (tự gỡ, vi Không hoàn —
phạm), hết hạn, hoặc giao dịch
không thành
Số dư khả dụng = credit_balance − held_credit. Mọi phép kiểm tra trước khi thu phí dùng số dư
khả dụng. Hai cột này có ràng buộc CHECK (>= 0) ở mức cơ sở dữ liệu.
4.5. Quy tắc sửa bài [v10 — đã chốt]
Tình huống Có mất phí sửa không Có tính lượt không
Bài bị tầng 1 chặn tự động (từ khoá cấm, số Không. Bài chưa được tạo, người dùng Không
điện thoại…) khi bấm gửi sửa ngay trên form bao nhiêu lần cũng
được.
Bài đang chờ duyệt hoặc bị Admin trả về và Không. Bài chưa qua tay Admin lần nào Không
chưa từng được duyệt hoặc đang được sửa theo yêu cầu.
Bài đã được duyệt, lần sửa đầu tiên Không. Có — dùng lượt miễn phí
khi bản sửa được duyệt
Bài đã được duyệt, lần sửa thứ hai trở đi Có — phí sửa 5 Credit (C14b), Hold khi Có
gửi, trừ khi bản sửa được duyệt
Bản sửa bị Admin từ chối Nhả khoản đang giữ Không tính lượt; sửa lại
theo đúng dòng tương ứng
ở trên
Sửa lại một bản sửa đang chờ duyệt (chưa Không thêm phí Không tính thêm lượt
được xét)
• Chỉ sửa được khi bài chưa có Request nào ở trạng thái Reserved trở lên (BR-D04).
• Mọi lần sửa đều quay lại kiểm duyệt đủ ba tầng. Trong lúc chờ duyệt lại, bài tạm ẩn khỏi tìm
kiếm; các Request đang Pending giữ nguyên.
• "Đã từng được duyệt" xác định bằng items.approved_at khác null, không dựa vào trạng thái
hiện tại, để không lách được bằng cách cố tình để bản sửa bị từ chối.
• Gia hạn và đẩy bài không phải là sửa, không tính lượt.
Đã chốt (C10) — bài đang hiển thị thì khi sửa được ẩn tạm
Bài được ẩn tạm tới khi bản sửa duyệt xong (đơn giản, SLA duyệt 24 giờ, không phải lưu hai phiên bản của
một bài). Các Request đang Pending giữ nguyên. Phương án giữ bản cũ hiển thị trong lúc chờ duyệt không
Trang 18 / 83

SRS ShareLoop — phiên bản v10
làm trong phạm vi đồ án.
4.6. Đẩy bài (Boost)
• Chỉ áp dụng cho bài Approved và còn hạn, cả Cho lẫn Trao đổi.
• Mỗi gói: 5 Credit cho 3 ngày (đã chốt, C15). Mua thêm thì cộng dồn thời gian, tối đa 14 ngày tính
từ hiện tại. Thời gian đẩy không vượt quá hạn hiển thị của bài.
• Cách xếp hạng: trong đúng bộ lọc người dùng đã chọn, bài đang được đẩy đứng trước, sắp
theo thời điểm bắt đầu đẩy; sau đó mới tới bài thường. Đẩy bài không làm bài xuất hiện ở bộ lọc
không khớp.
• Bài đang được đẩy có nhãn "Được đẩy" để minh bạch với người xem.
• Tối đa 3 bài được đẩy ở đầu mỗi trang kết quả, để kết quả tìm kiếm không bị toàn bài trả tiền.
• Bài bị gỡ, bị ẩn do report hoặc hết hạn thì mất thời gian đẩy còn lại, không hoàn phí.
4.7. Sổ tài chính và các màn hình
Sổ Nguồn dữ liệu Ý nghĩa
1. Tiền mặt thực thu Đơn PaymentOrders trạng thái Success [v9] Dòng tiền thật; đối chiếu với sao kê
cổng.
2. Credit cộng vào CreditLedger Amount > 0: TopUp, Công nợ với người dùng.
AdminAdjust
3. Credit trừ ra (doanh CreditLedger Amount < 0, tách theo Type: Doanh thu đã thực hiện. Cho biết
thu) PostFee, RenewFee, EditFee, BoostFee, tính năng nào sinh tiền.
AiSearchFee
Phép kiểm tra bắt buộc: tổng tiền mặt thực thu (quy ra Credit) phải bằng tổng Credit cộng vào có Type
= TopUp. Ba màn hình giữ như v8: báo cáo tổng thể cho Admin, tra cứu lịch sử một người cho Admin,
trang ví cho Member (hiển thị số dư khả dụng, số đang giữ và danh sách bài đang giữ phí).
4.8. Lỗ hổng "đăng bài Cho để né phí Trao đổi" đã được đóng
Bản v10 đầu tiên chỉ thu phí bài Trao đổi, nên người dùng có thể đăng mọi thứ dưới dạng Cho rồi
thương lượng đổi trong chat. Khi chốt mọi bài đều 5 Credit (C01), động cơ này biến mất. Hệ quả kèm
theo: chuyển Cho → Trao đổi không còn thu phí, và luồng này chỉ còn là một thao tác đổi hình thức.
Đã chốt (C01, C02): mọi bài đăng 5 Credit = 5.000đ
Một mức phí duy nhất, dễ giải thích với người dùng và giảng viên; bỏ được một nhánh xử lý phí khi chuyển
hình thức; không cần giám sát riêng tài khoản đăng nhiều bài Cho.
4.9. Khoảng trống sau khi trao thông tin liên lạc
Sau ContactRevealedAt hai người có số điện thoại của nhau và có thể giao dịch trực tiếp ở những lần
sau. Ở v8 đây là rủi ro thất thu phải chấp nhận. Ở v10, doanh thu đã thu ở bước đăng bài nên không
còn thất thu. Hệ thống vẫn không cài cơ chế ẩn lại thông tin (BR-T12). Người giao dịch ngoài hệ thống
tự mất quyền khiếu nại, mất bằng chứng và không được cộng điểm.
Trang 19 / 83

SRS ShareLoop — phiên bản v10
5. Tài khoản & xác thực
5.1. Bỏ xác thực SMS
v8 và v9 yêu cầu xác thực cả email lẫn số điện thoại bằng OTP, trong đó kênh SMS chỉ giả lập bằng
cách in mã ra console. v10 bỏ hẳn kênh này.
Lý do
• In mã ra console không chứng minh được điều gì: người chấm vẫn thấy số điện thoại chưa thật sự được
xác thực.
• Gửi SMS thật cần đăng ký Brandname, cần pháp nhân và chi phí; ngoài khả năng của nhóm.
• Bớt một kênh gửi, một cụm yêu cầu và một điểm lỗi khi demo (rủi ro R24 của v8 biến mất).
5.2. Quy tắc định danh mới
• Email là định danh được xác thực duy nhất. Đăng ký phải nhập đúng mã OTP gửi qua email (6
chữ số, hạn 5 phút, tối đa 5 lần sai, gửi lại sau 60 giây). Chưa xác thực thì không tạo tài khoản.
• Mỗi email gắn đúng một số điện thoại. Số điện thoại là trường bắt buộc khi đăng ký, kiểm tra
định dạng di động Việt Nam (10 số, đầu 03/05/07/08/09, chấp nhận nhập dạng +84).
• Một số điện thoại không được gắn với hai tài khoản đang hoạt động. Quan hệ email ↔ số
điện thoại là một–một.
• Đổi số điện thoại phải nhập OTP gửi về email, tối đa 1 lần mỗi 30 ngày, và không được đổi khi
đang có giao dịch ở trạng thái từ LogisticsConfirmed tới trước Completed (vì số cũ đã được gửi
cho đối tác).
• Số điện thoại không được xác thực quyền sở hữu. Hệ thống ghi rõ điều này trong điều khoản
sử dụng và trên màn hình trao thông tin liên lạc.
5.3. Rủi ro khi không xác thực số điện thoại
Rủi ro Biện pháp
Khai số giả hoặc số của người khác. Khi trao Email vẫn được trao cùng lúc và đã xác thực. Đối tác có thể report lý
liên lạc, đối tác gọi không được. do "Thông tin liên lạc sai"; xác minh đúng thì trừ sao.
Chiếm số của người khác để người đó không Người bị chiếm gửi yêu cầu hỗ trợ; Admin gỡ số khỏi tài khoản đang
đăng ký được. giữ và ghi ActivityLogs. Tài khoản chiếm số bị xử lý theo chính
sách.
Tạo nhiều tài khoản dễ hơn (chỉ cần nhiều Chặn tên miền email dùng một lần (danh sách cấu hình); tối đa 3 tài
email). khoản mới mỗi IP trong 24 giờ; hạn mức AI miễn phí chỉ mở sau lần
nạp đầu tiên; giới hạn số bài và số yêu cầu đồng thời.
Đã chốt (C05)
Số điện thoại duy nhất trong các tài khoản ACTIVE (partial unique index). Đổi số bằng OTP gửi về email,
tối đa 1 lần/30 ngày, không đổi khi đang có giao dịch từ LogisticsConfirmed tới trước Completed.
Trang 20 / 83

SRS ShareLoop — phiên bản v10
6. Luồng nghiệp vụ
Chương này có một sơ đồ cho mỗi nghiệp vụ. Mỗi sơ đồ đủ chi tiết để làm đặc tả cho một nhóm API.
Màu nút cho biết ai thực hiện bước đó:
Hình 3 — Chú thích màu dùng cho mọi sơ đồ luồng
6.1. Luồng tổng thể
Luồng tổng thể ở chương 0 là xương sống của hệ thống. So với v9 có ba điểm khác: tiền chỉ đi ra ở
bước đăng bài, gia hạn, sửa bài có phí, đẩy bài và AI; giao dịch không còn chốt kiểm tra số dư; thông
tin liên lạc được trao ngay khi hai bên xác nhận lịch hẹn.
• Tài khoản số dư 0 vẫn tìm kiếm, gửi yêu cầu và giao dịch được. Muốn đăng bài thì phải nạp tối
thiểu 10 Credit (đủ cho 2 bài).
• Mọi nhánh treo đều có lối thoát tự động: Request Pending quá 3 ngày, chat im lặng quá 10
ngày, một bên không xác nhận giao nhận quá 3 ngày, đơn nạp không phản hồi quá 30 phút, tài
khoản chưa xác thực quá 24 giờ, bài hết hạn, đẩy bài hết hạn.
Trang 21 / 83

SRS ShareLoop — phiên bản v10
6.2. Tài khoản
6.2.1. Đăng ký và xác thực email
Hình 4 — Đăng ký tài khoản và xác thực email bằng OTP
• Không còn OTP số điện thoại. Số điện thoại chỉ kiểm tra định dạng và tính duy nhất (chương 5).
• Tài khoản được tạo ngay ở trạng thái PENDING_VERIFICATION để có chỗ lưu OTP; không đăng
nhập được, không giữ số điện thoại (chỉ tài khoản ACTIVE mới tính trùng) và tự xoá sau 24 giờ
nếu không xác thực.
Trang 22 / 83

SRS ShareLoop — phiên bản v10
6.2.2. Đăng nhập và quên mật khẩu
Hình 5 — Đăng nhập và đặt lại mật khẩu
Thông báo khi quên mật khẩu luôn giống nhau dù email có tồn tại hay không, để không bị dò danh
sách email.
Trang 23 / 83

SRS ShareLoop — phiên bản v10
6.2.3. Đổi số điện thoại
Hình 6 — Đổi số điện thoại (OTP gửi về email)
Trang 24 / 83

SRS ShareLoop — phiên bản v10
6.3. Nạp Credit
Hình 7 — Nạp Credit qua VNPay
• Xác thực chữ ký IPN/webhook. Sai chữ ký thì từ chối, ghi nhật ký cảnh báo.
• Chống xử lý trùng. Đơn đã SUCCESS thì bỏ qua; thêm ràng buộc duy nhất
credit_ledger.payment_order_id để cơ sở dữ liệu tự chặn cộng hai lần.
• Không tin trang "thanh toán thành công" của trình duyệt. Chỉ cộng khi IPN phía máy chủ xác
nhận.
• Ghi sổ trong một transaction: đơn SUCCESS, cộng số dư, ghi credit_ledger TOP_UP kèm
balance_after. Đơn lưu vnd_per_credit tại thời điểm nạp [v9].
Trang 25 / 83

SRS ShareLoop — phiên bản v10
6.4. Đăng bài & kiểm duyệt ba tầng
Hình 8 — Đăng bài, giữ phí 5 Credit và kiểm duyệt ba tầng
| Tầng | Ai thực hiện | Cơ chế | Khi phát hiện vi phạm |
| ---- | ------------ | ------ | --------------------- |
1 — Chặn cứng  Hệ thống (Java,  Trường bắt buộc; danh mục  Từ chối ngay tại form, không tạo
tại form đồng bộ, không AI) is_restricted; lexicon mức BLOCK;  bài, chưa giữ phí. Người dùng sửa
|     |     | regex số điện thoại, email, URL, tài khoản  | ngay, không mất phí. |
| --- | --- | ------------------------------------------- | -------------------- |
mạng xã hội; BR-A04, BR-A05; số ảnh.
2 — AI sàng lọc Dịch vụ LLM qua  Đọc tiêu đề, mô tả, mô tả món mong muốn,  Không tự quyết. Gắn cờ và lý do;
AiClient, bất  danh mục, tình trạng. Trả JSON cờ: liên hệ  bài rủi ro cao lên đầu hàng chờ. AI
đồng bộ ngầm, hàng cấm, sai danh mục, mô tả  lỗi thì nhãn "AI chưa kiểm".
kém.
3 — Admin duyệt Admin Checklist cấu hình được (10 mục khởi tạo,  Từ chối bắt buộc có lý do; nhả phí
|     |     | mục 6.17.4), thấy cờ AI và từ khoá mức  | đang giữ; người đăng sửa miễn phí  |
| --- | --- | --------------------------------------- | ---------------------------------- |
|     |     | REVIEW. SLA 24 giờ.                     | và gửi lại.                        |
Vì sao AI chỉ gắn cờ, không tự duyệt hay tự từ chối
v8 bỏ AI khỏi kiểm duyệt vì: AI từ chối nhầm thì phải xây luồng khiếu nại riêng; AI bỏ sót thì Admin vẫn phải
đọc lại; quyết định của AI khó giải thích khi bảo vệ. v10 đưa AI trở lại theo góp ý giảng viên nhưng không
Trang 26 / 83

SRS ShareLoop — phiên bản v10
phạm vào ba lý do đó: AI không từ chối ai; Admin vẫn duyệt 100%; mọi quyết định cuối có người chịu trách
nhiệm. AI giúp Admin nhìn đúng chỗ và đẩy bài rủi ro lên trước.
6.4.1. Admin duyệt bài
Hình 9 — Admin duyệt bài, đối chiếu chứng từ chính hãng
• Tầng 1 chặn phần lớn vi phạm rõ ràng; phí 5 Credit tự lọc bớt bài rác; hàng chờ có thứ tự ưu tiên;
giao diện duyệt có lý do soạn sẵn và phím tắt; nhiều tài khoản Admin. Đây là năm biện pháp
chống quá tải hàng chờ.
• Mỗi lượt duyệt lưu bản chụp bộ câu hỏi checklist cùng câu trả lời (items.review_snapshot +
activity_logs), để Admin đổi checklist sau này không làm sai lịch sử.
Trang 27 / 83

SRS ShareLoop — phiên bản v10
6.5. Sửa bài
Hình 10 — Sửa bài và cách tính phí sửa
Luật đầy đủ ở mục 4.5. Tóm tắt: bị chặn tự động hoặc chưa từng được duyệt thì sửa tự do; sau khi
được duyệt có đúng một lượt sửa miễn phí; từ lượt sau mất phí sửa. Lượt chỉ được tính khi bản sửa
được duyệt.
Trang 28 / 83

SRS ShareLoop — phiên bản v10
6.6. Gia hạn và đẩy bài
Hình 11 — Gia hạn bài và đẩy bài
• Gia hạn không duyệt lại vì nội dung không đổi. Bài EXPIRED quá 7 ngày thì phải đăng lại (C27).
• Đẩy bài chỉ đứng đầu trong đúng bộ lọc người xem đã chọn, tối đa 3 bài mỗi trang, luôn có nhãn
"Được đẩy". Bài bị gỡ, bị ẩn, hết hạn thì mất phần thời gian đẩy còn lại, không hoàn phí.
Trang 29 / 83

SRS ShareLoop — phiên bản v10
6.7. Tìm kiếm và AI trợ lý tìm đồ
Hình 12 — Tìm kiếm thủ công và AI trợ lý tìm đồ
AI trợ lý đi theo cách "tra cứu trước, diễn giải sau" (mục 8.4): LLM chỉ tách ý định và viết lời giải thích;
danh sách bài luôn đến từ cơ sở dữ liệu nên không thể có bài bịa.
Trang 30 / 83

SRS ShareLoop — phiên bản v10
6.8. Gửi yêu cầu và chọn người
Hình 13 — Gửi yêu cầu (xin đồ hoặc đề nghị trao đổi) và chọn người
Câu hỏi Quyết định Lý do
Một món đề nghị có được gửi tới Được. Khi một Request dùng món đó chuyển Một món đồ không thể đổi cho
nhiều bài cùng lúc không? Reserved, món đó cũng Reserved và các hai người.
Request Pending khác dùng cùng món tự
huỷ.
Trang 31 / 83

SRS ShareLoop — phiên bản v10
| Câu hỏi | Quyết định | Lý do |
| ------- | ---------- | ----- |
Món đề nghị có phải là bài Trao  Không bắt buộc; là bất kỳ bài Approved nào  Món đề nghị đã qua kiểm duyệt
| đổi không? | của người đề nghị. | là đủ để kiểm soát hàng cấm. |
| ---------- | ------------------ | ---------------------------- |
Điều kiện trao đổi? Cả hai đều đang có ít nhất một bài Approved  Không có gì để đổi thì đi luồng
|     | (BR-S01). | Cho. |
| --- | --------- | ---- |
Có kiểm tra số dư khi gửi yêu  Không. Giao dịch miễn phí. Chống spam bằng giới hạn 5
| cầu không? |     | Request Pending (BR-U02). |
| ---------- | --- | ------------------------- |
Thời hạn chat? 10 ngày kể từ Reserved. Dùng chung mốc với BR-T10.
6.9. Chuyển bài Cho thành Trao đổi
Hình 14 — Chuyển bài Cho thành Trao đổi giữa chừng
Vì mọi bài đã trả 5 Credit khi đăng, chuyển hình thức không thu thêm phí [v10]. Request giữ nguyên;
bên nhận phải chọn một bài Approved làm món đề nghị trước khi xác nhận lịch, không có thì Request
huỷ và bài trở về Approved.
6.10. Khung chat có kiểm soát
6.10.1. Thấy được sản phẩm của hai bên
Đầu khung chat ghim cố định thẻ sản phẩm: ảnh đại diện, tên, tình trạng, danh mục, nhãn chính hãng.
Với Trao đổi ghim cả hai món. Khối ghim không cuộn mất khi kéo tin nhắn.
Trang 32 / 83

SRS ShareLoop — phiên bản v10
6.10.2. Kiểm duyệt tin nhắn hai tầng [v9]
Hình 15 — Kiểm duyệt tin nhắn hai tầng
• Tầng 1 bắt số điện thoại (kể cả viết cách quãng, viết bằng chữ, chèn ký tự), email, URL, tài khoản
mạng xã hội và từ khoá cấm mức BLOCK.
• Tầng 2 chỉ gọi cho ca mơ hồ; độ tin cậy ≥ 0,7 thì chặn; lỗi thì chặn an toàn nhưng không tính vi
phạm.
• Đủ 3 vi phạm trong một giao dịch thì cảnh báo Admin. Không cho gửi ảnh và luôn lọc liên hệ cho
tới khi trao thông tin liên lạc.
Trang 33 / 83

SRS ShareLoop — phiên bản v10
6.10.3. Đưa bài đăng vào chat và đổi món đề nghị [v10]
Hình 16 — Chia sẻ thẻ bài đăng và đề xuất đổi món đề nghị
Mọi món đồ được đem ra đổi đều là bài đã qua kiểm duyệt. Đây là cách hệ thống vẫn kiểm soát được
đồ cấm khi trao đổi: không có đường nào để đưa một món chưa duyệt vào giao dịch.
Đã chốt (C04) — vẫn chặn liên hệ trong chat dù giao dịch đã miễn phí
Ở v8/v9 lý do chính để chặn là bảo vệ phí giao dịch. v10 không còn phí giao dịch nhưng nhóm quyết định giữ
bộ lọc, với lý do mới:
• Giữ thoả thuận trong hệ thống, để khi tranh chấp Admin có bằng chứng về lịch hẹn và điều kiện đã
thống nhất.
• Chống kéo người dùng sang kênh ngoài trước khi hai bên cam kết, là kiểu lừa đảo phổ biến trên chợ đồ
cũ.
• Chống tài khoản rác gửi yêu cầu hàng loạt chỉ để thu thập số điện thoại.
Trang 34 / 83

SRS ShareLoop — phiên bản v10
6.11. Chốt lịch và trao thông tin liên lạc
Hình 17 — Chốt lịch giao nhận và trao thông tin liên lạc
• Giao trực tiếp: chủ bài đề xuất địa điểm và thời gian (khuyến nghị nơi công cộng), bên kia xác
nhận.
• Ship qua bên thứ ba: cả hai bấm "Cam kết chịu trách nhiệm" với nội dung hiển thị đầy đủ (giữ
nguyên văn bản cam kết của v8).
• BR-T04: hệ thống không bồi thường tiền hàng và phí vận chuyển trong mọi trường hợp, kể cả
bom hàng. Report chỉ có giá trị trừ sao. Phí đăng bài không hoàn.
• Thông tin liên lạc được trao ngay khi hai bên xác nhận lịch; không còn gắn với việc thu phí.
Trang 35 / 83

SRS ShareLoop — phiên bản v10
6.12. Giao nhận và hoàn tất
Hình 18 — Xác nhận giao nhận, "đúng mô tả?" và hoàn tất
Câu hỏi "Món đồ có đúng mô tả không?" là cách hệ thống trả lời góp ý "tổng quan sản phẩm có
đúng ngoài đời không". Tỉ lệ đúng mô tả hiển thị trên hồ sơ người đăng khi có từ 3 giao dịch trở lên.
Trang 36 / 83

SRS ShareLoop — phiên bản v10
6.13. Vòng đời của Request và bài đăng
Hình 19 — Sơ đồ trạng thái của một Request (không còn Credit trong giao dịch)
Trạng thái Ý nghĩa Lối thoát tự động
Pending Đã gửi yêu cầu, chờ chủ bài xét. Quá 3 ngày → Cancelled.
Reserved Đã được chọn; bài khoá sửa, ẩn khỏi tìm kiếm; Quá 10 ngày không chốt →
Request khác của bài bị Rejected; chat mở. Cancelled, bài về Approved.
AwaitingLogistics Đã chọn hình thức giao nhận, đang thương lượng lịch. Chung mốc 10 ngày.
LogisticsConfirmed Hai bên đã xác nhận lịch; thông tin liên lạc đã trao; lịch —
bị khoá.
AwaitingHandoverConfirmation Một bên đã xác nhận giao/nhận. Quá 3 ngày bên kia im lặng,
không có report → Completed.
Completed Hoàn tất, cộng điểm, mở 7 ngày khiếu nại. Hết 7 ngày → đóng hồ sơ.
Disputed Admin đang xử lý khiếu nại. SLA 3 ngày.
Rejected / Cancelled Chủ bài chọn người khác / tự huỷ, quá hạn, Admin huỷ. —
Trang 37 / 83

SRS ShareLoop — phiên bản v10
Hình 20 — Sơ đồ trạng thái của một bài đăng
Trang 38 / 83

SRS ShareLoop — phiên bản v10
6.14. Khiếu nại và tranh chấp
Hình 21 — Báo cáo, khiếu nại và xử lý tranh chấp
• Lý do khiếu nại: NOT_AS_DESCRIBED, COUNTERFEIT, PROHIBITED_ITEM, NO_SHOW (bom hẹn),
WRONG_CONTACT_INFO, HARASSMENT, OTHER.
• Chỉ trừ sao sau khi xác minh; bên bị khiếu nại luôn có 3 ngày giải trình.
Trang 39 / 83

SRS ShareLoop — phiên bản v10
• Chế tài chỉ ở cấp tài khoản: trừ sao, gỡ bài, khoá tài khoản, thu hồi quyền gắn nhãn chính hãng.
Không hoàn tác giao dịch vật lý, không hoàn phí đăng bài.
6.15. Uy tín, điểm thưởng và hạng
Hình 22 — Trừ sao, phục hồi sao và ân hạn
| Hạng | Tổng điểm | Bài đồng thời | Quyền lợi khác                  |
| ---- | --------- | ------------- | ------------------------------- |
| Đồng | 0 – 49    | 3             | —                               |
| Bạc  | 50 – 149  | 5             | —                               |
| Vàng | 150 – 399 | 10            | Huy hiệu; bài hiển thị 45 ngày. |
Kim Cương ≥ 400 Không giới hạn Như trên; ưu tiên thấp trong hàng chờ duyệt (ít rủi
ro).
• Mỗi giao dịch Completed: người cho +10, người nhận +2; với Trao đổi mỗi bên +6.
• BR-U08: từ giao dịch thứ 3 giữa cùng một cặp trong 90 ngày không cộng điểm; vượt 5 giao dịch
thì cảnh báo Admin. BR-U11: tối đa 30 điểm mỗi ngày.
6.16. Xác thực hàng chính hãng [v9]
1. Khi đăng bài có thương hiệu, người đăng có thể bật nhãn "Hàng chính hãng" và tải chứng từ (hoá
đơn, phiếu bảo hành, ảnh tem). Chứng từ lưu private.
2. Admin đối chiếu khi duyệt bài (sơ đồ duyệt bài). Đạt thì bài có huy hiệu "Đã kiểm chứng từ"; không
đạt thì bài vẫn duyệt được nhưng không có nhãn.
Trang 40 / 83

SRS ShareLoop — phiên bản v10
3. Đối tác trong giao dịch xem được chứng từ từ lúc Reserved (đề xuất, C26 — còn chờ chốt).
4. Khiếu nại COUNTERFEIT được xác minh: trừ sao, gỡ bài, thu hồi quyền gắn nhãn (C25).
6.17. Phạm vi sản phẩm, chất lượng & hàng cấm [v10]
Mục này trả lời các câu hỏi của giảng viên: trao đổi có ràng buộc gì không, loại sản phẩm, chất lượng,
scope hạng mục là gì, và nếu là hàng fake thì sao.
6.17.1. Danh mục trong phạm vi
Nhóm danh mục (khởi tạo, Admin sửa được) Ràng buộc riêng
Sách, truyện, giáo trình, văn phòng phẩm —
Quần áo, giày dép, túi, phụ kiện thời trang Đồ lót, đồ bơi đã qua sử dụng: không nhận.
Đồ gia dụng, nhà bếp Đồ điện phải khai trạng thái hoạt động.
Điện tử & phụ kiện Bắt buộc khai trạng thái hoạt động; điện thoại, máy tính
phải cam kết đã xoá tài khoản và dữ liệu cá nhân.
Đồ chơi, đồ dùng trẻ em Không gồm sữa, đồ ăn dặm (BR-A05). Đồ chơi mô phỏng
vũ khí: vào hàng REVIEW.
Thể thao, dã ngoại —
Nội thất nhỏ, đồ trang trí Món cồng kềnh khuyến nghị giao trực tiếp.
Nhạc cụ, đồ sưu tầm, đồ sở thích —
Mỹ phẩm còn nguyên niêm phong Đã mở nắp: cấm.
Thực phẩm đóng gói Theo BR-A05: nguyên tem, hạn còn ≥ 30 ngày, ảnh chụp
rõ hạn.
Khác Admin xem kỹ; danh mục này không được đẩy bài.
6.17.2. Danh mục và vật phẩm bị cấm
• Danh mục cấm (không cho chọn): dược phẩm và thiết bị y tế; bất động sản; vũ khí, vật liệu
nguy hiểm, pháo; phương tiện có biển số; động vật sống (C09).
• BR-A04: vật phẩm cần sang tên pháp lý (xe có biển số, sổ đỏ, giấy tờ tuỳ thân, sim chính chủ, tài
khoản ngân hàng hay tài khoản game có giá trị) hoặc quy đổi trực tiếp ra tiền mặt (vàng, ngoại tệ,
thẻ cào, voucher, tiền mã hoá).
• BR-A05: thực phẩm tươi sống, đồ ăn chế biến, đồ đã mở bao bì, thực phẩm chức năng, sữa và
đồ ăn dặm cho trẻ, đồ uống có cồn, thuốc lá.
• BR-A12: hàng nhái, hàng giả mạo nhãn hiệu, kể cả khi người đăng tự ghi là "hàng rep", "like
auth".
6.17.3. Ràng buộc chất lượng khi đăng
Trường Quy tắc
Tình trạng (bắt buộc) NEW — mới, chưa dùng, còn tem; LIKE_NEW — như mới; GOOD — có dấu hiệu sử
dụng; DEFECTIVE — có lỗi.
Mô tả lỗi Bắt buộc khi tình trạng là DEFECTIVE, kèm ít nhất 1 ảnh chụp chỗ lỗi.
Ảnh Tối thiểu 3, tối đa 8 ảnh thật của chính món đồ; tối đa 5 MB/ảnh (C23).
Trang 41 / 83

SRS ShareLoop — phiên bản v10
Trường Quy tắc
Thuộc tính động theo danh Ví dụ điện tử: trạng thái hoạt động (Bình thường / Lỗi một phần / Không hoạt động),
mục [v9] thời gian đã dùng.
Nội dung Không chứa thông tin liên hệ (BR-A09), không chứa từ khoá cấm mức BLOCK.
6.17.4. Checklist duyệt bài khởi tạo (Admin cấu hình được)
# Câu hỏi Admin phải trả lời Nếu không đạt
1 Món đồ có cần sang tên, đăng ký, công chứng để đổi chủ không? Từ chối — BR-A04.
2 Món đồ có quy đổi trực tiếp ra tiền mặt được không? Từ chối — BR-A04.
3 Nếu là thực phẩm: còn nguyên tem, nhìn rõ hạn, hạn còn trên 30 Từ chối — BR-A05.
ngày?
4 Có phải thuốc, thực phẩm chức năng, thiết bị y tế, mỹ phẩm đã mở? Từ chối — danh mục cấm.
5 Ảnh là ảnh thật của đúng món đồ, đủ số lượng, không lấy trên mạng? Từ chối, yêu cầu chụp lại.
6 Ảnh hoặc mô tả có thông tin liên hệ, kể cả viết ngầm? (xem cờ AI) Từ chối — BR-A09.
7 Tình trạng khai báo có khớp với ảnh? Đồ lỗi đã mô tả lỗi? Trả về yêu cầu bổ sung.
8 Danh mục có đúng với món đồ? Trả về yêu cầu sửa danh mục.
9 Bài Trao đổi đã có mô tả món mong muốn? Trả về yêu cầu bổ sung.
10 Có dấu hiệu hàng nhái, hoặc bật nhãn chính hãng mà chứng từ Từ chối (BR-A12) hoặc duyệt không
không khớp? kèm nhãn.
Việc người đăng có TrustStars bằng 0 do hệ thống tự chặn từ lúc gửi bài, không cần Admin kiểm.
Trang 42 / 83

SRS ShareLoop — phiên bản v10
7. Kiểm soát nội dung: lexicon và AI
Chương này đặc tả ContentModerationService dùng chung cho bài đăng và tin nhắn. Câu hỏi "nên
dùng lexicon hay AI" có câu trả lời ngắn: dùng cả hai, mỗi thứ làm đúng phần nó giỏi.
7.1. So sánh lexicon và AI
Tiêu chí Lexicon + regex AI (LLM)
Chi phí mỗi lần Gần 0 Tốn tiền API, cần trần ngân sách
Tốc độ Vài mili giây, chạy đồng bộ tại form Vài giây, nên chạy bất đồng bộ với bài
đăng
Tính giải thích Rõ ràng: "khớp từ X ở vị trí Y" Có lý do bằng lời nhưng không chắc chắn
Ổn định Cùng đầu vào luôn cùng kết quả Có thể trả khác nhau giữa các lần gọi
Bắt biến thể lách luật Chỉ bắt được những gì đã lường trước Hiểu ngữ cảnh: "ib zalo mình tên Lan",
"số ở ảnh 3"
Chặn nhầm theo ngữ cảnh Dễ: "súng nước", "súng bắn keo" Ít hơn: hiểu được đồ chơi, dụng cụ
Phụ thuộc bên ngoài Không Có: mạng, nhà cung cấp, hạn mức
Quyết định: lexicon làm tầng chặn cứng, AI làm tầng gắn cờ
Lexicon + regex quyết định chặn ngay những gì chắc chắn (số điện thoại, email, từ cấm rõ ràng). AI chỉ xét
phần còn lại, nơi cần hiểu ngữ cảnh. Với bài đăng, AI gắn cờ cho Admin. Với tin nhắn, AI chặn khi đủ tin cậy
vì không có Admin duyệt từng tin.
7.2. Thiết kế lexicon
7.2.1. Cấu trúc một từ khoá
Trường Ý nghĩa Ví dụ
Term Từ hoặc cụm từ, viết có dấu súng, cầCn sa, theD cào
Category Nhóm vi phạm, dùng để chọn lý do từ chối Vũ khí, Ma tuý, Tài sản pháp lý, Quy ra tiền,
Thực phẩm cấm, Hàng giả, Liên hệ
Severity BLOCK: chặn tại form. REVIEW: cho qua nhưng cầCn sa → BLOCK; súng → REVIEW
gắn cờ cho Admin
MatchMode WORD (khớp nguyên từ), PHRASE, REGEX
Exceptions Cụm từ ngoại lệ, gặp thì bỏ qua súng nước, súng bắIn keo, súng phun
sơn, súng bắIn đinh
Scope Áp cho bài đăng, tin nhắn, hay cả hai
7.2.2. Chuẩn hoá văn bản trước khi so khớp
1. Chuẩn hoá Unicode về dạng NFC (tiếng Việt gõ bằng các bộ gõ khác nhau có thể ra mã khác
nhau).
2. Đưa về chữ thường; gộp khoảng trắng.
Trang 43 / 83

SRS ShareLoop — phiên bản v10
3. Thay ký tự lách: 0→o, 1→i, 3→e, @→a, $→s; bỏ dấu chấm, gạch, khoảng trắng chèn giữa các chữ cái
đơn ("s.ú.n.g" → "súng").
4. Rút gọn ký tự lặp ("súngggg" → "súng").
5. Riêng cho regex liên hệ: đổi số viết bằng chữ ("không chín tám…", "ko 9 8") thành chữ số, rồi bỏ
mọi ký tự không phải số để dò chuỗi 10 số.
6. Tạo thêm một bản bỏ dấu để dò các biến thể viết không dấu.
7.2.3. Luật quan trọng: khớp không dấu chỉ là REVIEW
Tiếng Việt bỏ dấu gây trùng nghĩa: "súng" bỏ dấu thành "sung" trùng với "quả sung", "sưng"; "cần sa"
thành "can sa". Vì vậy:
• Khớp trên bản có dấu → áp đúng Severity của từ khoá.
• Chỉ khớp trên bản bỏ dấu → luôn hạ xuống REVIEW, không bao giờ BLOCK.
• Khớp nằm trong một cụm ngoại lệ → bỏ qua.
7.2.4. Regex thông tin liên hệ
Loại Cách dò (sau chuẩn hoá)
Số điện thoại Chuỗi số dạng (0|84)(3|5|7|8|9) + 8 chữ số, sau khi đã bỏ ký tự chen giữa và đổi số
viết bằng chữ.
Email Mẫu email thông thường, kể cả dạng lách "a (a còng) gmail chấm com".
URL http, www., tên miền phổ biến (.com, .vn, .me…), dịch vụ rút gọn link.
Mạng xã hội Từ khoá zalo, fb, facebook, ig, insta, tiktok, telegram đi kèm tên hoặc số →
BLOCK; đứng một mình ("ib mình nhé") → REVIEW (bài) hoặc Ambiguous (chat).
7.3. Hợp đồng dữ liệu khi gọi AI sàng lọc bài đăng
Dữ liệu gửi đi chỉ gồm nội dung bài, không có email, số điện thoại, họ tên người đăng hay bất kỳ dữ
liệu ví nào. Nội dung người dùng được đặt trong một khối dữ liệu riêng, chỉ thị hệ thống dặn mô hình
bỏ qua mọi mệnh lệnh nằm trong khối đó.
ĐầCu vào:
{ "title": "...", "description": "...", "desiredItem": "...",
"category": "Điện tưD", "condition": "Good", "offerType": "Give" }
ĐầCu ra bắtI buộc (kiểmD schema trước khi dùng):
{ "contactInfo": { "found": true, "evidence": "ib zalo Lan" },
"prohibited": { "found": false, "category": null, "evidence": null },
"categoryMismatch": false,
"swapIntentInGivePost": false,
"qualityIssue": "Mô taD quá ngắnI, không nểu tình trạng pin",
"risk": "high",
"reasonVi": "Mô taD hướng người mua liển hệ qua Zalo." }
• JSON sai schema, thiếu trường hoặc risk ngoài low|medium|high → coi như lỗi.
• Timeout 20 giây, thử lại 1 lần. Vẫn lỗi thì bài vào hàng chờ với nhãn "AI chưa kiểm".
• Kết quả lưu nguyên JSON cùng bài (cột JSONB), kèm tên model và thời điểm, để truy vết.
• Mỗi lượt gọi ghi ActivityLogs loại AiUsage để tính chi phí.
Trang 44 / 83

SRS ShareLoop — phiên bản v10
7.4. Hợp đồng khi gọi AI kiểm duyệt chat [v9]
Đầu vào là một tin nhắn đã qua tầng 1 và được xếp Ambiguous, kèm tối đa 3 tin trước đó trong cùng
cuộc chat để có ngữ cảnh. Đầu ra { "containsContact": bool, "confidence": 0..1,
"reasonVi": "..." }. Ngưỡng chặn 0,7; timeout 5 giây; lỗi thì chặn an toàn, không tính vi phạm.
7.5. Đo chất lượng bộ lọc trước khi demo
• Nhóm tự soạn bộ dữ liệu thử: khoảng 100 tin nhắn và 60 bài đăng, mỗi mẫu gắn nhãn "vi phạm /
không vi phạm" và loại vi phạm. Có đủ các kiểu lách và các ca dễ chặn nhầm.
• Chạy tầng 1 riêng, rồi tầng 1 + tầng 2; đo tỉ lệ bắt đúng (recall) và tỉ lệ chặn nhầm (false positive).
• Bộ dữ liệu này đồng thời là bộ unit test cho ContentModerationService; mỗi khi sửa lexicon phải
chạy lại.
• Kết quả đo là một slide mạnh khi bảo vệ: chứng minh bằng số rằng hai tầng tốt hơn một tầng.
Trang 45 / 83

SRS ShareLoop — phiên bản v10
8. Phân tích phương án AI cho dự án
8.1. Bối cảnh và ràng buộc
• Nhóm 5 người: 4 backend Java Spring Boot, 1 frontend React; không ai có nền tảng học máy.
• Thời gian còn lại 6 tuần (tuần 4 – tuần 9); AI không phải trọng tâm chấm điểm duy nhất, nhưng
phải chạy được và giải thích được.
• Máy chủ deploy là VPS gói nhỏ, không có GPU.
• Ngân sách gần như bằng 0; chi phí AI phải có trần.
8.2. Các vai trò AI trong v10
| Vai trò | Mức ưu tiên | Khi nào gọi | Lượng gọi ước tính |
| ------- | ----------- | ----------- | ------------------ |
R1 — Trợ lý tìm đồ Must Người dùng hỏi trên trang  Tối đa 5 lượt miễn
|     |     | riêng | phí/người/ngày + lượt trả phí |
| --- | --- | ----- | ----------------------------- |
R2 — Kiểm duyệt chat tầng 2 Must Tin nhắn Ambiguous ở tầng 1 Một phần nhỏ số tin nhắn
R3 — Sàng lọc bài đăng tầng 2  Must Mỗi lần gửi hoặc sửa bài 1 lượt/bài/lần gửi
[v10]
R4 — Gợi ý danh mục khi đăng  Could Khi nhập xong tiêu đề 1 lượt/bài, có thể dùng chung
| bài [v10]           |            |     | lượt R3 |
| ------------------- | ---------- | --- | ------- |
| — AI gợi ý ghép đôi | Đã bỏ (v9) | —   | —       |
8.3. Các phương án kỹ thuật
| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
| --------- | ------- | ---------- | -------- |
A. Chỉ quy tắc (regex +  Không tốn tiền, nhanh, ổn định,  Không hiểu ngữ cảnh; không làm  Bắt buộc có, làm tầng
| lexicon) | dễ giải thích, dễ test. | được trợ lý tìm đồ. | 1.  |
| -------- | ----------------------- | ------------------- | --- |
B. Gọi LLM thương mại  Không cần kiến thức ML; hiểu  Tốn tiền theo lượt; độ trễ vài giây;  Chọn cho R1, R2, R3.
qua API tiếng Việt tốt; ra JSON có cấu  phụ thuộc mạng; dữ liệu đi ra
|     | trúc; đổi nhà cung cấp được nhờ  | ngoài. |     |
| --- | -------------------------------- | ------ | --- |
AiClient.
C. Tự chạy mô hình mở  Không tốn tiền API; dữ liệu  Cần máy nhiều RAM hoặc GPU;  Không chọn.
(vd qua Ollama) không đi ra ngoài. mô hình nhỏ yếu tiếng Việt; tốn
công vận hành; VPS gói nhỏ không
chạy nổi.
D. Tự huấn luyện bộ  Rẻ khi chạy; nhanh. Cần dữ liệu gán nhãn lớn và kiến  Không chọn.
| phân loại (vd fine-tune  |     | thức ML; ngoài năng lực và thời  |     |
| ------------------------ | --- | -------------------------------- | --- |
| PhoBERT)                 |     | gian của nhóm.                   |     |
E. Embedding +  Tìm theo nghĩa ("áo khoác mùa  Phải tạo embedding cho mọi bài;  Could — làm sau khi
pgvector cho tìm kiếm  đông" ra "áo phao"); rẻ hơn gọi  thêm extension; thêm độ phức tạp. xong Must.
| ngữ nghĩa | LLM mỗi lần; tận dụng  |     |     |
| --------- | ---------------------- | --- | --- |
PostgreSQL.
Khuyến nghị: A làm nền, B cho ba vai trò Must, E là tuỳ chọn
Nhóm không cần học ML; chỉ cần học cách gọi API, viết prompt ép ra JSON, kiểm schema, đặt timeout và ghi
log chi phí. Đây đều là kỹ năng backend thông thường.
Trang 46 / 83

SRS ShareLoop — phiên bản v10
8.4. Thiết kế trợ lý tìm đồ: tra cứu trước, diễn giải sau
v8 mô tả cách gửi cả tập bài Approved vào prompt. Cách này tốn nhiều token và có thể khiến mô hình
"bịa" ra bài không tồn tại. v10 đổi sang ba bước:
1. LLM tách ý định: câu hỏi của người dùng → JSON bộ lọc { keywords[], category, area,
condition, offerType }.
2. Backend tra cứu: chạy truy vấn PostgreSQL (full-text + unaccent + pg_trgm) trên các bài
Approved, lấy tối đa 10 bài.
3. LLM diễn giải (tuỳ chọn): gửi thông tin công khai của tối đa 5 bài đầu, nhận một câu giải thích
thân thiện cho mỗi bài. Backend bỏ mọi itemId không nằm trong danh sách đã tra.
Lợi ích: mỗi lượt chỉ tốn 1–2 lần gọi với prompt ngắn; kết quả luôn là bài có thật; khi LLM lỗi ở bước 1
vẫn có thể rơi về tìm kiếm từ khoá thường.
8.5. Kiểm soát chi phí
• Trần ngân sách theo ngày (C31) đặt trong cấu hình; chạm trần thì tạm dừng lượt miễn phí R1 và
chuyển R3 sang chế độ "AI chưa kiểm". R2 vẫn chạy vì liên quan an toàn.
• Giới hạn độ dài đầu vào và số token đầu ra cho mỗi loại lời gọi.
• Bộ nhớ đệm 10 phút cho câu hỏi R1 giống hệt nhau.
• Ghi mỗi lượt gọi vào ActivityLogs (vai trò, model, số token nếu có, miễn phí/trả phí, kết quả).
Admin xem thống kê chi phí và tỉ lệ Admin đồng ý với cờ AI (FR-116).
• Tên model, timeout, ngưỡng đều nằm trong cấu hình, không hard-code.
8.6. An toàn khi dùng AI
• Không gửi email, số điện thoại, họ tên, dữ liệu ví, nội dung chat ngoài phạm vi cần kiểm.
• Chống prompt injection: nội dung người dùng đặt trong khối dữ liệu riêng; kiểm JSON trả về; AI
không có quyền gọi bất kỳ hành động nào.
• AI không tự từ chối bài; với chat, lỗi thì chặn an toàn nhưng không tính vi phạm để không phạt oan
người dùng vì lỗi hệ thống.
• Điều khoản sử dụng ghi rõ nội dung bài đăng và tin nhắn có thể được một dịch vụ AI bên ngoài
đọc để kiểm duyệt.
8.7. Chọn nhà cung cấp LLM
Tiêu chí để chọn (C07): hỗ trợ tiếng Việt tốt; có chế độ trả về JSON hoặc structured output; có gói miễn
phí hoặc giá thấp cho model nhỏ; độ trễ thấp; có SDK hoặc REST đơn giản cho Java. Nhóm nên thử
cùng một bộ 20 mẫu trên 2–3 nhà cung cấp rồi chọn theo độ chính xác và chi phí thực tế. Giá API thay
đổi thường xuyên, vì vậy tài liệu này không ghi con số cụ thể; nhóm tra lại bảng giá khi chốt.
Trang 47 / 83

SRS ShareLoop — phiên bản v10
9. Yêu cầu chức năng
9.1. Cách đọc và quy ước ưu tiên
Mức Ý nghĩa Hệ quả nếu thiếu
Must Thiếu thì một luồng chính không chạy, sai về tiền hoặc mất Không nghiệm thu được. Xong trong
an toàn. tuần 7.
Should Hệ thống vẫn chạy nhưng trải nghiệm kém hoặc Admin phải Làm trong tuần 8 nếu còn thời gian.
làm tay.
Could Không ảnh hưởng luồng chính. Chỉ làm khi đã xong Must và Should.
Tổng cộng 125 yêu cầu chức năng: 87 Must, 30 Should, 8 Could. Mã FR đã được đánh lại hoàn toàn
trong v10.
9.2. Nhóm A — Tài khoản & xác thực
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-01 Đăng ký bằng email, mật khẩu, họ tên và một số điện Member Must UC-01 BR-U06
thoại
FR-02 Sinh OTP email 6 số, hạn 5 phút, tối đa 5 lần sai, gửi lại Hệ thống Must UC-01 BR-U06
sau 60 giây; chưa xác thực thì không tạo tài khoản
FR-03 Kiểm tra định dạng số di động Việt Nam; mỗi email một Hệ thống Must UC-01 BR-U06
số; số không trùng tài khoản đang hoạt động
FR-04 Chặn email thuộc danh sách tên miền dùng một lần Hệ thống Should UC-01 BR-U06
(cấu hình được)
FR-05 Đăng nhập, đăng xuất, cấp JWT Member, Must UC-02 —
Admin
FR-06 Xem và cập nhật hồ sơ (tên, khu vực mặc định, ảnh đại Member, Must UC-03 BR-U03
diện) Admin
FR-07 Đổi số điện thoại bằng OTP email, tối đa 1 lần/30 ngày, Member Should UC-03 BR-U10
không đổi khi đang có giao dịch đã trao liên lạc
FR-08 Đổi mật khẩu khi đã đăng nhập Member, Must UC-04 —
Admin
FR-09 Quên mật khẩu, đặt lại qua OTP email Member Should UC-04 —
FR-10 Tối đa 3 tài khoản mới mỗi IP trong 24 giờ; vượt thì Hệ thống Should UC-01 BR-U06
đánh dấu chờ Admin xem xét
9.3. Nhóm B — Ví Credit & thanh toán
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-11 Chọn gói nạp, tạo PaymentOrder, chặn dưới 10 Credit Member Must UC-10 BR-P02
hoặc không phải bội số 1.000đ
FR-12 Chuyển hướng sang cổng thanh toán Hệ thống Must UC-10 —
FR-13 Nhận webhook, xác thực chữ ký, bỏ qua đơn đã xử lý Hệ thống Must UC-10 BR-P10
Trang 48 / 83

SRS ShareLoop — phiên bản v10
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-14 Cộng Credit, cập nhật đơn và ghi CreditLedger trong Hệ thống Must UC-10 BR-P09
một transaction
FR-15 Hiển thị số dư khả dụng, số đang giữ và danh sách bài Member Must UC-11 BR-P05
đang giữ phí
FR-16 Lịch sử ví (nạp, tiêu, nhả giữ) lọc theo khoảng ngày Member Must UC-11 BR-P09
hoặc tuần/tháng/quý
FR-17 Popup xác nhận ghi rõ số Credit trước mọi thao tác có Hệ thống Must UC-13, 16, BR-P07
phí 19
FR-18 Chuyển đơn nạp sang Expired sau 30 phút không phản Hệ thống Should — —
hồi
9.4. Nhóm C — Bài đăng
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-19 Tạo bài hình thức Cho (phí 5 Credit như mọi bài) Member Must UC-12 BR-D05, BR-
P01
FR-20 Tạo bài hình thức Trao đổi, bắt buộc mô tả món mong Member Must UC-12 BR-S02
muốn
FR-21 Tải 3–8 ảnh, sắp thứ tự, tối đa 5 MB/ảnh Member Must UC-12 BR-D08
FR-22 Chọn tình trạng bắt buộc; tình trạng Có lỗi bắt buộc mô Member Must UC-12 BR-D08
tả lỗi và ít nhất 1 ảnh lỗi
FR-23 Nhập thuộc tính động theo danh mục (vd trạng thái hoạt Member Should UC-12 BR-D08
động của đồ điện tử)
FR-24 Giới hạn số bài đang hiển thị hoặc chờ duyệt theo hạng Hệ thống Must UC-12 BR-U01
FR-25 Sửa bài khi chưa có Request từ Reserved trở lên; mỗi Member Must UC-14 BR-D04, BR-
lần gửi bản sửa quay lại kiểm duyệt ba tầng, bài tạm ẩn D07
FR-26 Tính phí sửa: miễn phí khi bị chặn ở tầng 1 hoặc bài Hệ thống Must UC-14 BR-D07, BR-
chưa từng được duyệt; sau khi duyệt có 1 lượt miễn P06
phí, từ lượt sau giữ phí sửa; lượt chỉ tính khi bản sửa
được duyệt
FR-27 Gỡ bài Member Must UC-14 BR-D04
FR-28 Gia hạn bài (thu phí gia hạn, không duyệt lại) Member Should UC-15 BR-D03, BR-
P12
FR-29 Bài hết hạn 30 ngày (45 ngày từ hạng Vàng) tự chuyển Hệ thống Must — BR-D03
Expired, không còn hiển thị
FR-30 Bật nhãn hàng chính hãng, chọn thương hiệu, tải Member Should UC-17 BR-A11
chứng từ lưu private
9.5. Nhóm D — Phí đăng bài & đẩy bài
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-31 Kiểm tra số dư khả dụng ≥ 5 Credit trước khi gửi bất kỳ Hệ thống Must UC-13 BR-P05
bài nào đi duyệt
Trang 49 / 83

SRS ShareLoop — phiên bản v10
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-32 Giữ (Hold) 5 Credit phí đăng bài khi gửi duyệt Hệ thống Must UC-13 BR-P03
FR-33 Khi bài được duyệt lần đầu, chuyển Hold thành trừ thật Hệ thống Must UC-13, 40 BR-P03
(POST_FEE); phí đăng bài chỉ trừ một lần trong đời bài
FR-34 Nhả Hold khi bài bị từ chối hoặc người đăng rút bài Hệ thống Must UC-13, 40 BR-P03
trước khi duyệt
FR-35 Hiển thị chính sách hoàn phí ngay trên màn hình xác Hệ thống Must UC-13 BR-P03
nhận phí
FR-36 Mua gói đẩy bài cho bài Approved còn hạn; cộng dồn Member Should UC-16 BR-P08
tối đa 14 ngày
FR-37 Xếp bài đang đẩy lên đầu trong đúng bộ lọc (tối đa 3 Hệ thống Should UC-18 BR-P08
bài/trang), gắn nhãn "Được đẩy"
FR-38 Job kết thúc đẩy bài khi hết hạn Hệ thống Should — BR-P08
9.6. Nhóm E — Kiểm duyệt bài đăng ba tầng
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-39 Chặn tại form theo danh mục IsRestricted Hệ thống Must UC-60 BR-A01
FR-40 Chặn tại form khi khớp lexicon mức BLOCK; khớp mức Hệ thống Must UC-60 BR-A10
REVIEW thì gắn cờ
FR-41 Chặn tại form khi tiêu đề, mô tả, mô tả mong muốn Hệ thống Must UC-60 BR-A09
chứa số điện thoại, email, URL, tài khoản mạng xã hội
FR-42 Chặn tại form vật phẩm cần sang tên pháp lý hoặc quy Hệ thống Must UC-60 BR-A04, BR-
ra tiền mặt; thực phẩm không đạt điều kiện A05
FR-43 Gọi AI sàng lọc bài sau khi gửi; lưu cờ, mức rủi ro, lý do Hệ thống Must UC-60 BR-A01, BR-
và model đã dùng S04
FR-44 AI lỗi hoặc quá hạn: bài vẫn vào hàng chờ với nhãn "AI Hệ thống Must UC-60 BR-A01
chưa kiểm"
FR-45 Hàng chờ duyệt xếp theo rủi ro người đăng và cờ AI Admin Must UC-40 BR-A08
FR-46 Màn hình duyệt hiển thị checklist cấu hình được, lưu Admin Must UC-40, 42 BR-A06
bản chụp câu hỏi và câu trả lời
FR-47 Bắt buộc lý do khi từ chối (có mẫu soạn sẵn), gửi lý do Admin Must UC-40 BR-A02
cho người đăng
FR-48 Đối chiếu chứng từ hàng chính hãng, cấp hoặc không Admin Should UC-41 BR-A11
cấp huy hiệu
FR-49 Tự ẩn bài và đẩy vào hàng chờ khi đạt 3 report Hệ thống Should UC-40 BR-A03
9.7. Nhóm F — Tìm kiếm & AI trợ lý
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-50 Tìm theo từ khoá có dấu hoặc không dấu, lọc danh Member Must UC-18 —
mục, khu vực, tình trạng, hình thức — miễn phí
FR-51 Trang AI trợ lý: nhập nhu cầu bằng câu tự nhiên Member Must UC-19 BR-P04
Trang 50 / 83

SRS ShareLoop — phiên bản v10
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-52 LLM tách bộ lọc → truy vấn DB → (tuỳ chọn) LLM diễn Hệ thống Must UC-19 BR-S05
giải; chỉ trả bài có thật
FR-53 5 lượt miễn phí/ngày cho tài khoản đã từng nạp; tài Hệ thống Must UC-19 BR-P04
khoản chưa nạp 1 lượt dùng thử; sau đó 2 Credit/lượt
FR-54 Hiển thị số lượt miễn phí còn lại trước khi bấm Member Must UC-19 BR-P07
FR-55 Hoàn lượt khi LLM lỗi hoặc quá 15 giây, chuyển về tìm Hệ thống Must UC-19 BR-P11
kiếm thường
FR-56 Tách nội dung người dùng khỏi chỉ thị hệ thống, kiểm Hệ thống Must UC-19, 60, BR-S05
schema JSON trả về 61
FR-57 Trần ngân sách AI theo ngày Admin Should UC-46 —
FR-58 Ghi nhật ký mỗi lượt gọi AI (vai trò, model, miễn phí/trả Hệ thống Must — —
phí, kết quả)
FR-59 Gợi ý danh mục khi người dùng nhập tiêu đề bài Hệ thống Could UC-12 —
FR-60 Tìm kiếm ngữ nghĩa bằng embedding + pgvector Hệ thống Could UC-18, 19 —
9.8. Nhóm G — Yêu cầu & ghép giao dịch
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-61 Gửi yêu cầu xin đồ ở bài Cho Member Must UC-20 BR-R01, BR-
R02
FR-62 Gửi đề nghị trao đổi kèm chọn một bài Approved của Member Must UC-20 BR-S01, BR-
mình làm món đề nghị S03
FR-63 Giới hạn tối đa 5 Request Pending đồng thời mỗi người Hệ thống Must UC-20 BR-U02
FR-64 Tự huỷ yêu cầu khi còn Pending Member Must UC-21 BR-R04
FR-65 Xem danh sách người gửi kèm TrustStars, hạng, tỉ lệ Member Must UC-22 —
đúng mô tả và thẻ món đề nghị
FR-66 Chọn đúng một người; tự Reject yêu cầu còn lại của Member Must UC-22 BR-D01
bài; tự huỷ Request khác dùng cùng món đề nghị
FR-67 Chuyển bài Cho sang Trao đổi giữa chừng, không thu Member Should UC-23 BR-D06
thêm phí, giữ nguyên Request, bên nhận chọn món đề
nghị
9.9. Nhóm H — Khung chat có kiểm soát
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-68 Mở chat khi Request chuyển Reserved Hệ thống Must UC-24 BR-C03
FR-69 Ghim thẻ sản phẩm ở đầu chat (cả hai món nếu là Trao Member Must UC-24 BR-C02
đổi)
FR-70 Gửi thẻ bài đăng Approved vào chat Member Should UC-25 BR-C07
FR-71 Đề xuất đổi món đề nghị bằng bài Approved khác; chủ Member Could UC-25 BR-C07
bài chấp nhận thì cập nhật
Trang 51 / 83

SRS ShareLoop — phiên bản v10
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-72 Lọc liên hệ tầng 1 (chuẩn hoá + regex + lexicon) Hệ thống Must UC-61 BR-C01, BR-
C06
FR-73 Lọc tầng 2 bằng LLM cho ca mơ hồ, ngưỡng 0,7, Hệ thống Must UC-61 BR-C06
timeout 5 giây, lỗi thì chặn không tính vi phạm
FR-74 Hiển thị lý do khi tin bị chặn Hệ thống Must UC-61 BR-C01
FR-75 Chặn gửi ảnh trước LogisticsConfirmed Hệ thống Must UC-24 BR-C05
FR-76 Cảnh báo Admin sau 3 lần vi phạm trong một giao dịch Hệ thống Should UC-61 BR-C04
FR-77 Tự đóng chat sau 10 ngày kể từ Reserved nếu chưa Hệ thống Must — BR-C03
chốt
FR-78 Chặn tin chứa từ khoá cấm mức BLOCK; ghi nhận mức Hệ thống Should UC-61 BR-A10
REVIEW
9.10. Nhóm I — Chốt lịch & trao liên lạc
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-79 Nhập hình thức giao nhận, thời gian, địa điểm Member Must UC-26 BR-T01, BR-
T02
FR-80 Hiển thị đầy đủ cam kết trách nhiệm và bắt hai bên xác Member Must UC-26 BR-T03, BR-
nhận khi chọn ship T04
FR-81 Hai bên xác nhận lịch thì Request chuyển Hệ thống Must UC-26 BR-T05
LogisticsConfirmed
FR-82 Gửi email và số điện thoại của mỗi bên cho bên kia Hệ thống Must UC-62 BR-T11
kèm lịch hẹn; ghi ContactRevealedAt
FR-83 Khoá không cho sửa lịch hẹn sau khi đã xác nhận Hệ thống Must UC-26 BR-T05
9.11. Nhóm J — Xác nhận giao nhận
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-84 Xác nhận đã giao/đã nhận; một bên bấm thì vẫn chờ Member Must UC-27 BR-D02
FR-85 Khi bên nhận xác nhận, hỏi "đúng mô tả?"; chọn Không Member Must UC-27 BR-R05
đúng thì bắt buộc lý do và mở report NotAsDescribed
FR-86 Đính kèm ảnh hoặc video minh chứng Member Should UC-28 BR-T09
FR-87 Tự Completed sau 3 ngày nếu bên kia im lặng và không Hệ thống Must — BR-T06
có report
9.12. Nhóm K — Khiếu nại & uy tín
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-88 Báo cáo một bài đăng hoặc một người dùng Member Must UC-29 BR-A03
Trang 52 / 83

SRS ShareLoop — phiên bản v10
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-89 Khiếu nại giao dịch trong 7 ngày sau Completed, chọn Member Must UC-29 BR-T08
lý do (gồm NotAsDescribed, Counterfeit,
WrongContactInfo)
FR-90 Mở 3 ngày cho bên bị khiếu nại giải trình Hệ thống Must UC-47 BR-T08
FR-91 Admin xem bằng chứng, kết luận, trừ 1 sao khi có cơ Admin Must UC-47, 48 BR-U04
sở
FR-92 Vô hiệu quyền đăng bài và gửi yêu cầu khi TrustStars Hệ thống Must UC-48 BR-U05
về 0
FR-93 Bắt buộc rà soát bài khác của người vi phạm khi lý do là Admin Must UC-49 BR-A07
hàng cấm
FR-94 Tự hồi 1 sao sau 10 giao dịch sạch liên tiếp Hệ thống Should — BR-U09
FR-95 Admin cấp 1 sao ân hạn, một lần duy nhất, có lý do Admin Should UC-51 BR-U09
FR-96 Thu hồi quyền gắn nhãn chính hãng khi chứng từ giả Admin Should UC-47 BR-A11
được xác minh
9.13. Nhóm L — Điểm thưởng & hạng
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-97 Cộng điểm khi Completed: Cho +10/+2, Trao đổi +6/+6 Hệ thống Must — —
FR-98 Không cộng điểm từ giao dịch thứ 3 giữa cùng cặp Hệ thống Should — BR-U08
trong 90 ngày
FR-99 Trần 30 điểm mỗi ngày mỗi tài khoản Hệ thống Should — BR-U11
FR-100 Tính lại hạng và áp dụng quyền lợi ngay Hệ thống Must — BR-U01
FR-101 Trang xem điểm, hạng, sao, tỉ lệ đúng mô tả, lịch sử Member Must UC-05 —
thay đổi có lý do
9.14. Nhóm M — Quản trị & báo cáo
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-102 Quản lý danh mục, cờ IsRestricted và thuộc tính động Admin Must UC-43 —
FR-103 Quản lý lexicon: từ khoá, nhóm, mức BLOCK/REVIEW, Admin Must UC-44 BR-A10
ngoại lệ, phạm vi; thử nhanh một đoạn văn
FR-104 Quản lý checklist duyệt bài Admin Must UC-42 BR-A06
FR-105 Quản lý cây khu vực Admin Must UC-45 —
FR-106 Cấu hình phí, hạn mức, mốc thời gian, ngưỡng AI — Admin Must UC-46 BR-P01
không hard-code
FR-107 Khoá và mở khoá tài khoản Admin Must UC-50 —
FR-108 Gỡ số điện thoại khỏi tài khoản khi có khiếu nại chiếm Admin Should UC-50 BR-U06
số
FR-109 Báo cáo tiền mặt thu, Credit cộng vào, Credit trừ ra Admin Must UC-53 BR-P09
theo Type; lọc thời gian
Trang 53 / 83

SRS ShareLoop — phiên bản v10
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-110 Dòng đối chiếu tiền mặt với Credit TopUp Admin Must UC-53 BR-P09
FR-111 Xuất báo cáo CSV Admin Should UC-53 —
FR-112 Tra cứu toàn bộ lịch sử tài chính và giao dịch của một Admin Must UC-47 —
người
FR-113 Cảnh báo bất thường: cặp giao dịch lặp, nhiều tài Admin Should UC-52 BR-U08
khoản cùng IP, vi phạm chat lặp, tỉ lệ bài Cho cao
nhưng ít giao dịch
FR-114 Danh sách bài quá hạn và gỡ hẳn Admin Should UC-54 BR-D03
FR-115 Ghi ActivityLogs mọi hành động Admin kèm người, thời Hệ thống Must — —
điểm, lý do
FR-116 Thống kê AI: số lượt gọi, chi phí ước tính, tỉ lệ Admin Admin Could UC-55 —
đồng ý với cờ AI
9.15. Nhóm N — Tác vụ nền & thông báo
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-117 Xoá tài khoản PENDING_VERIFICATION quá 24 giờ Hệ thống Must — BR-U06
FR-118 Thông báo trong ứng dụng (chuông) cho các sự kiện Hệ thống Should — —
chính: bài được duyệt/từ chối, có yêu cầu mới, được
chọn, lịch hẹn, khiếu nại
FR-119 Huỷ Request Pending quá 3 ngày Hệ thống Must — BR-R06
FR-120 Huỷ Request im lặng quá 10 ngày kể từ Reserved, trả Hệ thống Must — BR-T10
bài về Approved
FR-121 Cảnh báo Admin khi bài chờ duyệt quá SLA 24 giờ Hệ thống Should — BR-A02
FR-122 Nhắc gia hạn bài sắp hết hạn qua email Hệ thống Could — BR-D03
9.16. Nhóm O — Có thì tốt
Mã Yêu cầu chức năng Ai thực hiện Ưu tiên Use case Business
Rule
FR-123 Trang hỏi đáp về cách dùng và chính sách phí Member Could — —
FR-124 Đánh giá và gửi lời cảm ơn sau giao dịch Member Could — —
FR-125 Thống kê số đồ đã được trao lại theo khu vực ở trang Member Could — —
chủ
Trang 54 / 83

SRS ShareLoop — phiên bản v10
10. Yêu cầu phi chức năng
| Mã Nhóm | Yêu cầu | Cách kiểm chứng |
| ------- | ------- | --------------- |
NFR-01 Bảo mật Mật khẩu băm BCrypt; JWT có hạn; không lưu thông  Xem cơ sở dữ liệu.
tin thẻ.
NFR-02 Bảo mật Webhook sai chữ ký bị từ chối và ghi cảnh báo. Gửi webhook giả, không được
cộng Credit.
NFR-03 Bảo mật Email và số điện thoại của đối phương chỉ có trong  Gọi API chi tiết giao dịch ở
|     | DTO sau ContactRevealedAt. | AwaitingLogistics, JSON không có  |
| --- | -------------------------- | --------------------------------- |
hai trường đó.
NFR-04 Toàn vẹn tiền Mọi thao tác Credit chạy trong transaction có khoá  Bấm gửi bài hai lần liên tiếp chỉ giữ
dòng; CreditBalance và HeldCredit có CHECK ≥ 0. phí một lần; test đồng thời hai
luồng.
NFR-05 Toàn vẹn tiền Mỗi dòng CreditLedger lưu BalanceAfter. Cộng dồn Amount khớp
BalanceAfter dòng cuối.
NFR-06 Hiệu năng Trang danh sách bài phản hồi dưới 2 giây với 10.000  Sinh dữ liệu mẫu, đo thời gian;
|     | bài, kể cả tìm không dấu. | kiểm tra có dùng index trigram. |
| --- | ------------------------- | ------------------------------- |
NFR-07 Hiệu năng [v10] Tầng 1 kiểm duyệt (bài và tin nhắn) chạy dưới 50  Unit test đo thời gian trên bộ dữ
|     | ms mỗi lần. | liệu thử. |
| --- | ----------- | --------- |
NFR-08 Hiệu năng AI trợ lý có màn hình chờ, tối đa 15 giây; AI sàng lọc  Giả lập LLM trả chậm.
bài chạy bất đồng bộ, không làm chậm thao tác gửi
bài.
NFR-09 Độ tin cậy Job nền idempotent. Gọi job hai lần, so sánh dữ liệu.
NFR-10 Độ tin cậy Hệ thống vẫn chạy đúng khi AI hoặc cổng thanh toán  Tắt dịch vụ ngoài và thử lại các
|     | không phản hồi: báo lỗi rõ, không treo, không mất tiền. | luồng. |
| --- | ------------------------------------------------------- | ------ |
NFR-11 Chất lượng  [v10] Trên bộ dữ liệu thử của nhóm, tầng 1 + tầng 2  Chạy bộ test mục 7.5, lưu kết quả
kiểm duyệt bắt đúng ≥ 90% tin nhắn chứa liên hệ và chặn nhầm ≤  vào báo cáo.
5% tin hợp lệ.
NFR-12 Khả dụng Giao diện tiếng Việt, dùng tốt trên điện thoại. Kiểm tra chế độ mô phỏng thiết bị
di động.
NFR-13 Khả dụng Thông báo lỗi nói rõ nguyên nhân và cách xử lý,  Rà soát trong tuần 8.
không hiện mã lỗi kỹ thuật.
NFR-14 Kiểm toán Mọi hành động Admin ghi ActivityLogs kèm người, thời  Thực hiện từng thao tác Admin và
|     | điểm, lý do. | kiểm tra log. |
| --- | ------------ | ------------- |
NFR-15 Bảo trì Business rule chỉ nằm ở Service; tham số đọc từ cấu  Review mã; đổi cấu hình thấy áp
|     | hình; schema quản lý bằng Flyway. | dụng ngay. |
| --- | --------------------------------- | ---------- |
NFR-16 Kiểm thử Unit test cho từng nhánh chuyển trạng thái Request,  Báo cáo độ phủ các service liên
|     | từng nhánh Hold/trừ/nhả phí, và bộ test kiểm duyệt nội  | quan. |
| --- | ------------------------------------------------------- | ----- |
dung. Test repository chạy trên PostgreSQL thật
(Testcontainers).
NFR-17 Riêng tư [v10] Không gửi dữ liệu định danh người dùng tới dịch  Ghi log payload gửi đi ở môi
|     | vụ AI. | trường dev và rà soát. |
| --- | ------ | ---------------------- |
Trang 55 / 83

SRS ShareLoop — phiên bản v10
11. Business Rules
Giữ hệ mã BR-U / BR-D / BR-R / BR-A / BR-T / BR-P / BR-S / BR-C. Nhãn [v10] là luật mới hoặc đã
sửa ở bản này.
11.1. Nhóm User (BR-U)
ID Nội dung Loại
BR-U01 Không giới hạn tổng số bài trong vòng đời tài khoản; giới hạn số bài đang hiển thị hoặc Constraint /
chờ duyệt theo hạng: Đồng 3, Bạc 5, Vàng 10, Kim Cương không giới hạn. Áp dụng Dynamic
cho cả Cho và Trao đổi.
BR-U02 [v10] SỬA. Được gửi song song nhiều Request tới các bài khác nhau, nhưng tối đa 5 Constraint /
Request ở trạng thái Pending cùng lúc. Dynamic
BR-U03 Chỉ được sửa, xoá dữ liệu do chính mình tạo. Constraint /
Dynamic
BR-U04 TrustStars mặc định 5 sao; trừ 1 sao mỗi lần Admin xác minh một khiếu nại có cơ sở. Fact / Dynamic
Không trừ khi mới bị report.
BR-U05 TrustStars về 0: vô hiệu đăng bài và gửi yêu cầu, ẩn bài đang hiển thị; vẫn đăng nhập Constraint /
được để hoàn tất giao dịch dở và khiếu nại. Dynamic
BR-U06 [v10] SỬA. Đăng ký bắt buộc xác thực email bằng OTP. Số điện thoại bắt buộc khai, Constraint / Static
đúng định dạng di động Việt Nam, không xác thực OTP. Mỗi email gắn đúng một số
điện thoại; một số không gắn với hai tài khoản đang hoạt động. Tài khoản chưa xác
thực ở trạng thái chờ, không đăng nhập được, tự xoá sau 24 giờ. Không gửi SMS.
BR-U07 Credit không quy đổi ngược thành tiền, không được thưởng. Nguồn Credit: nạp tiền, Fact / Static
nhả Hold, điều chỉnh của Admin.
BR-U08 Từ giao dịch Completed thứ 3 giữa cùng một cặp trong 90 ngày không cộng điểm; vượt Constraint /
5 giao dịch thì cảnh báo Admin. Dynamic
BR-U09 Hồi 1 sao sau mỗi 10 giao dịch sạch liên tiếp, tối đa 5 sao; Admin cấp 1 sao ân hạn Constraint /
đúng một lần; về 0 lần hai thì khoá vĩnh viễn. Dynamic
BR-U10 [v10] MỚI. Đổi số điện thoại phải nhập OTP email, tối đa 1 lần/30 ngày, không đổi khi Constraint /
đang có giao dịch đã trao liên lạc mà chưa Completed. Dynamic
BR-U11 [v10] MỚI. Mỗi tài khoản được cộng tối đa 30 điểm thưởng mỗi ngày. Constraint /
Dynamic
11.2. Nhóm Donor — người đăng bài (BR-D)
ID Nội dung Loại
BR-D01 Mỗi bài đăng chỉ chọn duy nhất một người giao dịch. Constraint / Static
BR-D02 Chưa đủ hai bên xác nhận đã giao/đã nhận thì giao dịch vẫn chờ. Fact / Dynamic
BR-D03 Bài hiển thị 30 ngày (45 ngày từ hạng Vàng) rồi tự chuyển Expired. Gia hạn thu phí gia Fact / Dynamic
hạn (BR-P12).
BR-D04 Không sửa nội dung bài sau khi đã có Request từ Reserved trở lên. Constraint /
Dynamic
BR-D05 [v10] SỬA. Bài Cho mất phí đăng bài như mọi bài; người cho không bị trừ Credit ở bất Fact / Dynamic
kỳ bước giao dịch nào.
BR-D06 [v10] SỬA. Chuyển Cho → Trao đổi: không thu thêm phí (bài đã trả phí đăng); chủ bài Constraint /
bổ sung mô tả món mong muốn, phần này qua tầng 1, tầng 2 và được Admin hậu kiểm. Dynamic
Request giữ nguyên; bên nhận phải chọn một bài Approved của mình làm món đề nghị
trước khi xác nhận lịch, không có thì Request huỷ.
Trang 56 / 83

SRS ShareLoop — phiên bản v10
ID Nội dung Loại
BR-D07 [v10] MỚI. Sửa bài: (1) bị tầng 1 chặn thì sửa trên form, không phí, không tính lượt; (2) Constraint /
bài chưa từng được duyệt (đang chờ hoặc bị trả về) thì sửa miễn phí; (3) bài đã từng Dynamic
được duyệt có đúng 1 lượt sửa miễn phí, từ lượt sau thu phí sửa; (4) lượt chỉ tính khi
bản sửa được duyệt, bị từ chối thì nhả phí và không tính. Mọi lần gửi bản sửa quay lại
ba tầng kiểm duyệt, bài tạm ẩn tới khi duyệt xong.
BR-D08 [v10] MỚI. Bài phải có 3–8 ảnh thật, tình trạng bắt buộc; tình trạng Có lỗi bắt buộc mô Constraint / Static
tả lỗi và ảnh lỗi; đồ điện tử bắt buộc khai trạng thái hoạt động.
11.3. Nhóm Receiver — người gửi yêu cầu (BR-R)
ID Nội dung Loại
BR-R01 Không tạo Request cho bài của chính mình. Constraint /
Dynamic
BR-R02 Mỗi người chỉ có một Request đang hoạt động cho cùng một bài. Constraint / Static
BR-R03 Request chỉ Completed khi hai bên xác nhận hoặc auto-confirm theo BR-T06. Fact / Dynamic
BR-R04 Được tự huỷ Request khi còn Pending. Constraint /
Dynamic
BR-R05 [v10] MỚI. Khi xác nhận đã nhận, bên nhận phải trả lời "đúng mô tả?"; trả lời Không Constraint /
đúng thì bắt buộc lý do và mở report NotAsDescribed. Dynamic
BR-R06 [v10] Chuyển từ BR-P08 cũ. Request Pending quá 3 ngày không được xử lý thì tự huỷ. Fact / Dynamic
11.4. Nhóm Admin & Kiểm duyệt (BR-A)
ID Nội dung Loại
BR-A01 [v10] SỬA. Bài mới hoặc vừa sửa phải qua ba tầng: chặn cứng tại form, AI gắn cờ, Constraint /
Admin duyệt. AI không tự duyệt, không tự từ chối. Dynamic
BR-A02 Admin duyệt 100% bài qua tầng 1 trong SLA 24 giờ; từ chối bắt buộc có lý do gửi cho Constraint /
người đăng. Dynamic
BR-A03 Đạt 3 report thì tự ẩn bài và đẩy vào hàng chờ Admin. Fact / Dynamic
BR-A04 Cấm vật phẩm cần sang tên pháp lý hoặc quy đổi trực tiếp ra tiền mặt. Chặn tại form. Constraint / Static
BR-A05 Thực phẩm chỉ được đăng khi nguyên tem, nguyên niêm phong, rõ hạn, hạn còn ≥ 30 Constraint / Static
ngày; cấm các nhóm thực phẩm nêu ở 6.17.2.
BR-A06 [v9] Checklist duyệt do Admin cấu hình (ít nhất 1 mục), hiển thị dạng ô tích bắt buộc; Constraint / Static
mỗi lượt duyệt lưu bản chụp câu hỏi và câu trả lời.
BR-A07 Khiếu nại hàng cấm được xác minh thì bắt buộc rà soát mọi bài đang hoạt động khác Constraint /
của người đó. Dynamic
BR-A08 [v10] SỬA. Hàng chờ xếp ưu tiên: cờ AI rủi ro cao, người từng bị trừ sao, người dùng Fact / Dynamic
mới, rồi người có lịch sử sạch.
BR-A09 [v10] MỚI. Nội dung bài (tiêu đề, mô tả, mô tả mong muốn, ảnh) không được chứa Constraint / Static
thông tin liên hệ dưới mọi hình thức.
BR-A10 [v10] MỚI. Lexicon có hai mức BLOCK và REVIEW và danh sách ngoại lệ. Khớp chỉ Constraint / Static
sau khi bỏ dấu thì luôn hạ xuống REVIEW.
BR-A11 [v9] Nhãn hàng chính hãng chỉ hiển thị khi chứng từ được Admin đối chiếu đạt. Chứng Constraint /
từ lưu private; đối tác xem được từ Reserved. Chứng từ giả được xác minh thì trừ sao Dynamic
và thu hồi quyền gắn nhãn.
Trang 57 / 83

SRS ShareLoop — phiên bản v10
ID Nội dung Loại
BR-A12 [v10] MỚI. Chỉ đăng trong danh mục thuộc phạm vi; cấm hàng nhái, hàng giả mạo nhãn Constraint / Static
hiệu.
11.5. Nhóm Giao dịch & Vận chuyển (BR-T)
ID Nội dung Loại
BR-T01 Sau Reserved, hai bên chọn giao trực tiếp hoặc ship. Constraint /
Dynamic
BR-T02 Giao trực tiếp: người đăng bài nhập địa điểm, giờ hẹn; bên kia xác nhận. Fact / Dynamic
BR-T03 Ship: hai bên bấm "Cam kết chịu trách nhiệm" với nội dung hiển thị đầy đủ. Constraint /
Dynamic
BR-T04 Hệ thống không bồi thường tiền hàng và phí vận chuyển trong mọi tình huống. Report Constraint / Static
chỉ có giá trị trừ sao. Phí đăng bài không hoàn.
BR-T05 Hai bên xác nhận lịch thì Request chuyển LogisticsConfirmed, lịch bị khoá. Fact / Dynamic
BR-T06 Quá 3 ngày kể từ khi một bên xác nhận giao/nhận mà bên kia im lặng và không có Fact / Dynamic
report thì tự Completed.
BR-T07 Admin xử lý report phát sinh trước Completed trong 3 ngày. Constraint /
Dynamic
BR-T08 Cửa sổ khiếu nại 7 ngày sau Completed; bên bị khiếu nại có 3 ngày giải trình. Constraint /
Dynamic
BR-T09 Ảnh, video minh chứng khi xác nhận chỉ hiển thị cho Admin khi có report. Constraint /
Dynamic
BR-T10 Quá 10 ngày kể từ Reserved chưa chốt được lịch thì tự Cancelled, bài về Approved. Fact / Dynamic
BR-T11 [v10] SỬA. Ngay khi hai bên xác nhận lịch, hệ thống gửi email và số điện thoại của mỗi Fact / Dynamic
bên cho bên kia kèm lịch hẹn. Không còn gắn với việc thu phí.
BR-T12 [v10] SỬA. Không ẩn lại thông tin liên lạc sau khi đã trao. Rủi ro thất thu của v8 không Fact / Static
còn vì doanh thu thu ở bước đăng bài.
11.6. Nhóm Thu phí & Credit (BR-P)
ID Nội dung Loại
BR-P01 [v10] SỬA. Mọi bài đăng (Cho và Trao đổi) mất phí đăng bài 5 Credit = 5.000đ cho 30 Fact / Dynamic
ngày hiển thị. Tìm kiếm thủ công, gửi yêu cầu, chat, chốt lịch và hoàn tất giao dịch miễn
phí.
BR-P02 Tỉ lệ 1.000 VNĐ = 1 Credit, Credit là số nguyên; nạp tối thiểu 10 Credit, số tiền là bội số Constraint / Static
1.000đ.
BR-P03 [v10] SỬA. Phí đăng bài: Hold khi gửi duyệt; trừ thật khi Approved; nhả khi Rejected Constraint /
hoặc rút trước khi duyệt. Phí đăng bài chỉ trừ một lần trong đời bài: gửi lại sau khi bị từ Dynamic
chối lần đầu thì giữ lại, bài đã trả thì không thu nữa. Không hoàn khi bài đã Approved
rồi bị gỡ, hết hạn, vi phạm hay giao dịch không thành.
BR-P04 AI trợ lý tìm đồ 2 Credit/lượt; 5 lượt miễn phí/ngày cho tài khoản đã từng nạp; tài khoản Fact / Dynamic
chưa nạp có 1 lượt dùng thử.
BR-P05 [v10] SỬA. Mọi thao tác có phí kiểm tra số dư khả dụng (CreditBalance − HeldCredit) Constraint /
ngay tại thời điểm bấm, trong transaction có khoá dòng. Dynamic
BR-P06 [v10] MỚI. Phí sửa bài 5 Credit/lần (từ lượt sửa thứ hai sau khi bài đã được duyệt): Constraint /
Hold khi gửi bản sửa, trừ khi bản sửa được duyệt, nhả khi bị từ chối. Dynamic
Trang 58 / 83

SRS ShareLoop — phiên bản v10
ID Nội dung Loại
BR-P07 Mọi thao tác trừ Credit phải có popup ghi rõ số tiền; với AI hiển thị thêm lượt miễn phí Constraint / Static
còn lại.
BR-P08 [v10] MỚI. Đẩy bài 5 Credit/3 ngày, chỉ cho bài Approved còn hạn; trừ ngay; cộng dồn Constraint /
tối đa 14 ngày và không vượt hạn bài; bài bị gỡ, ẩn, hết hạn thì mất phần còn lại, không Dynamic
hoàn.
BR-P09 Báo cáo ba sổ tách riêng; tổng tiền mặt thực thu phải khớp tổng Credit TopUp. Constraint / Static
BR-P10 Chỉ cộng Credit khi webhook đã xác thực chữ ký và đơn chưa từng xử lý. Constraint / Static
BR-P11 Gọi AI trợ lý lỗi hoặc quá 15 giây thì hoàn lượt đã trừ và chuyển về tìm kiếm thường. Constraint /
Dynamic
BR-P12 [v10] MỚI. Gia hạn bài thu phí gia hạn 5 Credit/30 ngày, áp dụng cho mọi bài; không Fact / Dynamic
duyệt lại.
11.7. Nhóm Trao đổi & AI (BR-S)
ID Nội dung Loại
BR-S01 Giao dịch Trao đổi chỉ mở khi cả hai bên đang có ít nhất một bài Approved. Constraint /
Dynamic
BR-S02 Bài Trao đổi bắt buộc có mô tả món mong muốn, hiển thị công khai. Constraint / Static
BR-S03 Người đề nghị phải chọn đúng một bài Approved của mình làm món đề nghị, không Constraint /
nhập tự do. Dynamic
BR-S04 [v10] SỬA. AI có ba vai trò: trợ lý tìm đồ, kiểm duyệt chat tầng 2, sàng lọc bài đăng tầng Constraint / Static
2. AI không đọc ảnh, không tự thực hiện hành động, không tự duyệt hay từ chối bài.
BR-S05 Nội dung người dùng phải tách khỏi chỉ thị hệ thống khi gọi AI; kết quả phải đúng Constraint / Static
schema mới được dùng.
BR-S06 [v10] MỚI. Không gửi email, số điện thoại, họ tên, dữ liệu ví của người dùng tới dịch vụ Constraint / Static
AI.
11.8. Nhóm Khung chat (BR-C)
ID Nội dung Loại
BR-C01 Chặn tin nhắn chứa thông tin liên lạc trước LogisticsConfirmed, kể cả viết cách quãng, Constraint /
bằng chữ, chèn ký tự. Dynamic
BR-C02 Ghim thẻ sản phẩm ở đầu chat; Trao đổi ghim cả hai món. Constraint / Static
BR-C03 Chat mở khi Reserved, tự đóng sau 10 ngày nếu chưa chốt. Constraint /
Dynamic
BR-C04 Sau 3 lần vi phạm trong một giao dịch thì cảnh báo Admin. Fact / Dynamic
BR-C05 Không cho gửi ảnh trước LogisticsConfirmed. Constraint /
Dynamic
BR-C06 [v9] Lọc hai tầng: tầng 1 regex + lexicon (Block/Pass/Ambiguous); tầng 2 LLM chỉ cho Constraint /
Ambiguous, ngưỡng 0,7, timeout 5 giây, lỗi thì chặn không tính vi phạm; tắt lọc sau Dynamic
ContactRevealedAt.
BR-C07 [v10] MỚI. Chỉ chia sẻ được thẻ của bài Approved; đổi món đề nghị chỉ bằng bài Constraint /
Approved của người đề nghị, chủ bài phải chấp nhận, trước LogisticsConfirmed. Dynamic
Trang 59 / 83

SRS ShareLoop — phiên bản v10
Luật đã bãi bỏ so với v9
• Ba chốt kiểm tra số dư trong giao dịch và cửa sổ ân hạn 24 giờ nạp thêm (BR-P05, BR-P06 cũ).
• Phí Swap 2 Credit/bên và phí Give 4 Credit của người nhận (BR-P03 cũ).
• Xác thực số điện thoại bằng OTP qua SMS giả lập (một phần BR-U06 cũ).
• BR-P08 cũ (hoàn Credit khi Request Pending bị huỷ) — không còn Credit trong giao dịch; phần tự huỷ
chuyển thành BR-R06.
• Luật "chỉ bài Trao đổi mất phí" và "sửa bài 2 lần miễn phí" của bản v10 đầu — thay bằng BR-P01 và BR-
D07 hiện tại.
Trang 60 / 83

SRS ShareLoop — phiên bản v10
12. Đánh giá tổng thể dự án
Chương này đánh giá toàn bộ dự án theo góc nhìn của người chấm: điều gì làm tốt, điều gì còn yếu,
rủi ro nào có thể làm dự án thất bại và nhóm xử lý thế nào.
12.1. Ưu điểm
Khía cạnh Ưu điểm
Bài toán Rõ ràng, gần đời sống sinh viên; có cả khía cạnh cộng đồng (cho tặng) và giao dịch (trao
đổi).
Mô hình doanh thu Một mức phí duy nhất 5.000đ/bài, thu ở đúng nơi phát sinh chi phí (kiểm duyệt). Không có
lỗ hổng né phí qua liên lạc riêng hay đổi hình thức bài. Credit một chiều, đối soát được với
tiền mặt.
An toàn nội dung Ba tầng kiểm duyệt bài, hai tầng kiểm duyệt chat; món đem đổi luôn là bài đã duyệt; mọi
quyết định cuối có người chịu trách nhiệm.
AI Dùng đúng chỗ: tầng lọc thứ hai và trợ lý tìm đồ. Không cần kiến thức học máy; có đường
lui khi AI lỗi; không để AI bịa kết quả.
Luồng giao dịch Đơn giản hơn v9 rất nhiều: không còn ba chốt số dư, ân hạn 24 giờ, giữ tiền hai bên. Ít
nhánh hơn thì ít lỗi hơn.
Dữ liệu 17 bảng, giữ cấu trúc mapping của giảng viên; PostgreSQL có sẵn công cụ tìm kiếm tiếng
Việt.
Kiến trúc Monolith Spring Boot tách module rõ; mọi dịch vụ ngoài nằm sau interface nên thay hoặc
giả lập được khi demo.
Khả năng bảo vệ Mỗi quyết định lớn đều có lý do và phương án đã loại, ghi trong tài liệu.
12.2. Nhược điểm
Nhược điểm Hệ quả Cách giảm nhẹ
Người cho cũng phải trả 5.000đ. Nguồn cung bài Cho có thể ít hơn Phí chỉ trừ khi được duyệt; điểm người cho cao
so với nền tảng miễn phí. nhất; cấu hình được để miễn phí vài bài Cho mỗi
tháng nếu cần.
Admin duyệt 100% bài bằng tay. Không mở rộng được khi lượng bài Phí đăng bài lọc rác; AI xếp ưu tiên. Trong đồ án
lớn. không tự duyệt bài nào (C32 đã chốt: không); tự
duyệt bài rủi ro thấp của hạng cao chỉ xem xét sau
đồ án.
Số điện thoại không được xác Đối tác có thể nhận số sai. Email đã xác thực luôn được trao cùng; report
thực. WrongContactInfo.
Phụ thuộc dịch vụ ngoài (VNPay, Demo có thể hỏng vì bên ngoài. Interface + bản giả lập cho từng dịch vụ; chuẩn bị
LLM, email, lưu ảnh). chế độ demo offline.
Không có ứng dụng di động, Trải nghiệm kém hơn app thương Giao diện responsive; nâng cấp WebSocket khi
không có chat thời gian thực ở mại. còn thời gian.
bản đầu (polling).
Không kiểm chứng được giao Tranh chấp phụ thuộc lời hai bên. Minh chứng ảnh/video, "đúng mô tả?", lịch sử uy
nhận thật ngoài đời. tín.
Nhiều cột gộp vào bảng users Hai bảng này rộng, dễ bị sửa Mỗi nhóm cột chỉ một service được ghi (Credit, uy
và items. chồng. tín, OTP, phí); review bắt buộc.
Trang 61 / 83

SRS ShareLoop — phiên bản v10
12.3. Rủi ro của toàn dự án
Khả năng (K) và Mức ảnh hưởng (Ả) chấm theo thang Thấp / Trung bình / Cao.
| Nhóm | Rủi ro | K   | Ả Phòng ngừa và phương án dự phòng |
| ---- | ------ | --- | ---------------------------------- |
Tiến độ Còn 6 tuần (tuần 4 – 9) cho 125 yêu cầu,  Cao Cao Làm theo thứ tự Must → Should → Could;
backend chưa có mã. mốc kiểm tra cuối mỗi tuần; điểm cắt giảm
cuối tuần 6; đóng băng tính năng cuối tuần
8 (chương 16, 12.6).
Tiến độ Frontend một người, khối lượng màn hình  Cao Cao API contract ngay đầu tuần 4; FE đã có
|     | lớn. |     | khung giao diện; BE hỗ trợ FE nối API từ  |
| --- | ---- | --- | ----------------------------------------- |
tuần 6.
Kỹ thuật Sai tiền: trừ trùng, số dư âm, cộng Credit  Trung  Cao Chỉ CreditService đụng tiền; khoá
|     | hai lần. | bình | dòng; CHECK ≥ 0; unique  |
| --- | -------- | ---- | ------------------------ |
payment_order_id; test đồng thời.
Kỹ thuật Nhóm dùng nhiều IDE (IntelliJ, Antigravity,  Trung Trung Định dạng mã chạy bằng Maven (Spotless)
VS Code) và nhiều trợ lý AI (Claude Code,  và kiểm trong CI; một file AGENTS.md
Codex, Gemini): định dạng mã lệch nhau,  chung cho mọi trợ lý AI; cấu trúc thư mục
mã sinh ra không theo cấu trúc chung. và người sở hữu từng package cố định (tài
liệu Lộ trình); review từ chối mã không giải
thích được.
Kỹ thuật Chuyển trạng thái sai, giao dịch treo. Trung  Cao State machine một chỗ; job dọn dẹp
|     |     | bình | idempotent; unit test mọi mũi tên. |
| --- | --- | ---- | ---------------------------------- |
Kỹ thuật Bộ lọc chặn nhầm hoặc bỏ sót. Cao Trung  Bộ dữ liệu thử 160 mẫu; đo trước demo;
bình hai mức BLOCK/REVIEW.
Kỹ thuật Nhóm chưa quen PostgreSQL, JPA,  Trung  Trung  Spike trước khi code; skeleton có ví dụ
|     | Security. | bình | bình mẫu; chia sẻ kiến thức hằng tuần. |
| --- | --------- | ---- | -------------------------------------- |
Bên ngoài VNPay sandbox chậm cấp hoặc lỗi. Trung  Cao Service giả lập giữ nguyên luồng IPN.
bình
Bên ngoài LLM hết hạn mức, đổi giá, trả chậm. Trung  Trung  Timeout, đường lui, trần ngân sách; đổi
|     |     | bình | bình nhà cung cấp qua AiClient. |
| --- | --- | ---- | ------------------------------- |
Bên ngoài Gói lưu ảnh hoặc database miễn phí hết  Trung  Cao Mở ứng dụng trước buổi demo; có bản
hạn, tự ngủ khi demo. bình chạy cục bộ bằng Docker làm dự phòng.
Bảo mật Lộ API key, lộ số điện thoại trước thời  Trung  Cao Biến môi trường; DTO riêng; test NFR-03.
|     | điểm cho phép. | bình |     |
| --- | -------------- | ---- | --- |
Pháp lý Người dùng đăng hàng cấm, hàng giả; lừa  Trung  Cao Ba tầng kiểm duyệt; điều khoản sử dụng;
đảo khi gặp mặt. bình chế tài tài khoản; khuyến nghị điểm hẹn
công cộng.
Pháp lý Credit bị hiểu là ví điện tử hoặc trung gian  Thấp Cao Không rút, không chuyển Credit giữa
|     | thanh toán. |     | người dùng; chỉ tiêu cho dịch vụ của nền  |
| --- | ----------- | --- | ----------------------------------------- |
tảng.
Sản phẩm Ít người đăng bài vì phải trả phí. Trung  Trung  Phí thấp, trừ khi duyệt; theo dõi số liệu sau
|     |     | bình | bình demo; cấu hình được. |
| --- | --- | ---- | ------------------------- |
Nhóm Một thành viên bận hoặc nghỉ giữa chừng. Trung  Cao Module có người phụ trách chính và người
|     |     | bình | review cố định (người dự phòng); tài liệu  |
| --- | --- | ---- | ------------------------------------------ |
và test đầy đủ để người khác tiếp quản.
12.4. Rủi ro nghiệp vụ giữ lại từ các bản trước
| Mã  | Rủi ro | Mức | Biện pháp |
| --- | ------ | --- | --------- |
R01 Đăng vật phẩm cấm hoặc nguy hiểm. Cao Ba tầng kiểm duyệt; món đem đổi phải là bài đã
duyệt.
R02 Lừa đảo qua "tặng" tài sản phải sang tên. Cao BR-A04 chặn tại form; checklist mục 1, 2.
Trang 62 / 83

SRS ShareLoop — phiên bản v10
| Mã Rủi ro                          | Mức | Biện pháp |
| ---------------------------------- | --- | --------- |
| R03 Ngộ độc từ thực phẩm được cho. | Cao | BR-A05.   |
R05 Một khoản Credit bị trừ hai lần. Cao Transaction có khoá dòng; phí chỉ còn ở bài đăng,
đẩy bài, AI.
R06 Liên lạc riêng trong chat rồi giao dịch ngoài  Trung bình Lọc hai tầng, chặn ảnh; không còn là rủi ro doanh
| hệ thống trước khi cam kết. |     | thu. |
| --------------------------- | --- | ---- |
R07 Bị bom hàng khi ship. Trung bình BR-T04; cam kết hiển thị đầy đủ.
| R11 Webhook giả mạo. | Cao | BR-P10. |
| -------------------- | --- | ------- |
R12 Giao dịch treo vì một bên không xác nhận. Cao BR-T06 auto-confirm.
R13 Trừ sao oan do report trả đũa. Trung bình Chỉ trừ sau xác minh; quyền giải trình.
R14 An toàn cá nhân khi gặp người lạ. Cao Khuyến nghị nơi công cộng; lựa chọn ship.
R19 Chi phí gọi LLM vượt dự toán. Trung bình Trần ngân sách ngày; hạn mức miễn phí chỉ sau
lần nạp đầu.
R20 Prompt injection qua nội dung người dùng. Trung bình BR-S05; AI không có quyền hành động; AI không
tự từ chối bài.
12.5. Rủi ro mới phát sinh từ v10
| Mã Rủi ro mới | Sinh ra từ | Biện pháp |
| ------------- | ---------- | --------- |
R27 Tài khoản rác gửi yêu cầu hàng  Bỏ phí giao dịch Tối đa 5 Request Pending; xác thực email; giới hạn
loạt vì gửi yêu cầu đã miễn phí. IP; chặn email dùng một lần; TrustStars.
R28 Cày điểm bằng giao dịch giả không  Bỏ phí giao dịch Mỗi bài vẫn tốn 5 Credit; BR-U08, BR-U11; điểm
| tốn tiền ở bước giao dịch. |     | không quy ra tiền. |
| -------------------------- | --- | ------------------ |
R29 Số điện thoại khai sai hoặc chiếm  Bỏ xác thực SMS Email luôn được trao cùng; report WrongContactInfo;
số người khác. Admin gỡ số khi có khiếu nại; đổi số có OTP email.
R30 Lexicon chặn nhầm nội dung hợp  Kiểm duyệt bằng  Hai mức BLOCK/REVIEW; ngoại lệ; khớp không dấu
| lệ. | lexicon | chỉ REVIEW; chức năng thử nhanh cho Admin; bộ  |
| --- | ------- | ---------------------------------------------- |
test hồi quy.
R31 AI gắn cờ sai làm Admin nghi oan  AI sàng lọc bài Cờ chỉ là gợi ý; hiển thị bằng chứng; thống kê tỉ lệ
| hoặc mất thời gian. |     | Admin đồng ý để chỉnh prompt. |
| ------------------- | --- | ----------------------------- |
R32 Chi phí AI tăng vì mọi bài đều qua  AI sàng lọc bài Phí 5 Credit mỗi bài bù chi phí; prompt ngắn; chạm
| sàng lọc. |     | trần ngân sách thì chuyển sang "AI chưa kiểm". |
| --------- | --- | ---------------------------------------------- |
R33 Người dùng ít đăng bài vì mất phí,  Thu phí đăng bài Phí thấp; chỉ trừ khi duyệt; giao dịch miễn phí; đẩy
| doanh thu thấp. |     | bài là nguồn thu bổ sung. |
| --------------- | --- | ------------------------- |
R34 Kết quả tìm kiếm bị bài trả tiền lấn  Đẩy bài Chỉ đẩy trong đúng bộ lọc; tối đa 3 bài/trang; nhãn
| át. |     | "Được đẩy". |
| --- | --- | ----------- |
R35 Chậm tiến độ do chuyển sang  Đổi cơ sở dữ liệu Chuyển trước khi có mã backend; Flyway; Docker
| PostgreSQL. |     | Compose chung. |
| ----------- | --- | -------------- |
R36 Người dùng cố tình để bản sửa bị  Luật sửa bài "Đã từng được duyệt" xét bằng approved_at,
từ chối để lách phí sửa. không bằng trạng thái hiện tại; bản sửa bị từ chối
không đổi lượt đã dùng.
12.6. Phương án cắt giảm nếu chậm tiến độ
Điểm kiểm tra là cuối tuần 6: nếu một giao dịch chưa chạy được tới Completed, cắt ngay mục 1–3; nếu
cuối tuần 7 vẫn chưa xong Must, cắt tiếp mục 4–6. Mỗi mục cắt đi không làm hỏng luồng chính.
Trang 63 / 83

SRS ShareLoop — phiên bản v10
Thứ tự cắt Phần cắt / đơn giản hoá Ảnh hưởng
1 Mọi yêu cầu Could (tìm kiếm ngữ nghĩa, thống kê AI, gợi ý Không ảnh hưởng luồng chính.
danh mục, đánh giá sau giao dịch…).
2 Đề xuất đổi món trong chat; chuyển Cho → Trao đổi. Người dùng huỷ và gửi lại yêu cầu mới.
3 AI diễn giải kết quả tìm đồ (giữ bước tách bộ lọc). Kết quả vẫn đúng, chỉ thiếu câu giải thích.
4 Đẩy bài. Mất một nguồn thu phụ.
5 Tầng 2 AI của chat (giữ tầng 1 regex). Bắt kém hơn với cách viết lách tinh vi.
6 Xác thực hàng chính hãng. Mất huy hiệu; vẫn có lý do khiếu nại
Counterfeit.
Không cắt Đăng bài + phí + kiểm duyệt tầng 1 và 3; giao dịch tới Đây là phần tối thiểu để bảo vệ.
Completed; nạp Credit; khiếu nại; báo cáo tài chính.
Trang 64 / 83

SRS ShareLoop — phiên bản v10
13. Thiết kế dữ liệu (17 bảng)
Thiết kế dữ liệu v10 được làm lại với ba mục tiêu: ít bảng nhất có thể (giới hạn 20, thực tế 17), giữ
cấu trúc mapping theo hình minh hoạ của giảng viên, và để code đơn giản: mỗi bảng là một entity
JPA với ít quan hệ, không có bảng chỉ phục vụ một tính năng nhỏ. Hình ERD chi tiết theo từng module,
từ điển dữ liệu đầy đủ và bảng ánh xạ luồng ↔ bảng nằm trong tài liệu riêng "Luồng hoạt động & ERD
v10". ERD 17 bảng đã được nhóm review và chốt ngày 28/09/2026 (C33); mọi thay đổi schema sau
mốc này đi bằng một migration Flyway mới và phải được người sở hữu bảng duyệt. Ngày 29/09/2026,
theo yêu cầu của giảng viên, các bảng chính được bổ sung đủ bảy cột chuẩn (mục 13.5, C34); số bảng
vẫn là 17.
13.1. Cấu trúc mapping giữ theo giảng viên
Mẫu trong hình minh hoạ Bảng trong ShareLoop v10 Dùng cho
Pictures + media_files + Ảnh bài, ảnh lỗi, chứng từ chính hãng (cột
ItemPictureMappings item_media_mappings role), thứ tự ảnh.
(cùng mẫu, mở rộng) media_files + Minh chứng khi giao nhận và khi tranh
request_media_mappings chấp.
ItemAttributes + item_attributes + Thuộc tính động theo danh mục (vd trạng
ItemAttributeMappings item_attribute_mappings thái hoạt động của đồ điện tử).
WebsiteAttributes website_attributes Mọi cấu hình khoá–giá trị: phí, hạn mức,
mốc thời gian, lexicon, checklist, lý do từ
chối mẫu, tên miền email dùng một lần.
13.2. Danh sách bảng
Nhóm Bảng Vai trò
Người dùng & hệ users Tài khoản Member/Admin; gộp luôn OTP, ví Credit, hạn mức AI, uy tín
thống và điểm.
Người dùng & hệ areas Cây khu vực (tỉnh → quận/huyện).
thống
Người dùng & hệ website_attributes Cấu hình dạng khoá–giá trị: phí, hạn mức, mốc thời gian, lexicon,
thống checklist, lý do từ chối mẫu, tên miền email dùng một lần.
Người dùng & hệ activity_logs Nhật ký chung: hành động Admin, lượt gọi AI, thay đổi điểm và sao,
thống quyết định kiểm duyệt, sự kiện bảo mật.
Người dùng & hệ notifications Thông báo trong ứng dụng (chuông thông báo).
thống
Bài đăng item_categories Danh mục (có danh mục cha).
Bài đăng item_attributes Định nghĩa thuộc tính động theo danh mục (vd: trạng thái hoạt động).
Bài đăng items Bài đăng; gộp trạng thái phí đang giữ, kết quả AI sàng lọc, bản chụp
checklist lần duyệt gần nhất, đẩy bài.
Bài đăng item_attribute_mappi Giá trị thuộc tính động của từng bài.
ngs
Bài đăng media_files Mọi tệp đã tải lên (ảnh bài, ảnh lỗi, chứng từ, ảnh chat, minh chứng,
ảnh đại diện).
Bài đăng item_media_mappings Gắn tệp vào bài đăng theo vai trò và thứ tự.
Trang 65 / 83

SRS ShareLoop — phiên bản v10
Nhóm Bảng Vai trò
Giao dịch requests Một yêu cầu xin đồ hoặc đề nghị trao đổi; toàn bộ vòng đời giao dịch,
lịch hẹn, xác nhận hai chiều.
Giao dịch request_media_mappin Ảnh/video minh chứng gắn với một giao dịch (khi giao nhận hoặc khi
gs tranh chấp).
Giao dịch messages Tin nhắn trong khung chat, gồm thẻ bài đăng và đề xuất đổi món; lưu
cả kết quả kiểm duyệt.
Giao dịch reports Báo cáo bài/người và khiếu nại giao dịch; kết quả xử lý và số sao bị
trừ.
Tài chính payment_orders Đơn nạp Credit; đơn SUCCESS chính là sổ tiền mặt thực thu.
Tài chính credit_ledger Sổ cái Credit: mọi thay đổi thật của số dư.
Trang 66 / 83

SRS ShareLoop — phiên bản v10
Hình 23 — ERD tổng quan v10 (chỉ hiện khoá chính, khoá ngoại)
Trang 67 / 83

SRS ShareLoop — phiên bản v10
13.3. Những gì đã gộp để giảm số bảng
Trước (v8/v9 hoặc cách làm  Trong v10 Vì sao giảm được độ phức tạp
thông thường)
Bảng OTP riêng Cột otp_* trong users (một mã hiệu  Không cần bảng, không cần job dọn OTP;
|     | lực tại một thời điểm) | một câu UPDATE. |
| --- | ---------------------- | --------------- |
CashLedger Đơn payment_orders trạng thái  Một nguồn dữ liệu cho sổ tiền mặt, không
|     | SUCCESS | đồng bộ hai bảng. |
| --- | ------- | ----------------- |
CreditHolds (giữ tiền) users.held_credit +  Chỉ còn giữ phí cho bài chờ duyệt; một bài
|     | items.fee_state, pending_fee | giữ tối đa một khoản. |
| --- | ---------------------------- | --------------------- |
PointsLedger, TrustStarLedger,  activity_logs (cột type + data  Một service ghi nhật ký cho mọi loại sự
| AuditLogs, AiUsageLogs | JSONB) | kiện. |
| ---------------------- | ------ | ----- |
Bảng Checklist,  website_attributes theo  Một màn hình và một service cấu hình cho
| RestrictedKeywords, SystemConfig | attr_group | Admin. |
| -------------------------------- | ---------- | ------ |
Bảng AuthProof (chứng từ) item_media_mappings.role =  Cùng cơ chế tải ảnh; khác quyền xem nhờ
|     | AUTH_PROOF | media_files.visibility. |
| --- | ---------- | ----------------------- |
Bảng kết quả kiểm duyệt chat Cột moderation* ngay trong  Không join khi đếm vi phạm.
messages
Bảng Boost items.boosted_until Xếp hạng chỉ cần một điều kiện thời gian.
Bảng SwapProposal requests.offered_item_id + tin  Đề nghị trao đổi là một Request bình
|     | OFFER_CHANGE | thường. |
| --- | ------------ | ------- |
13.4. Nguyên tắc để code đơn giản
• Mỗi nhóm cột trong bảng rộng (users, items) chỉ do một service ghi: tiền → CreditService; uy
tín, điểm → ReputationService; OTP → AuthService; phí bài → PostFeeService.
• Không dùng khoá ngoại đa hình, trừ activity_logs.target_* (bảng nhật ký chỉ ghi, không join
nghiệp vụ).
• Enum lưu VARCHAR + CHECK, ánh xạ @Enumerated(STRING); thêm giá trị chỉ cần một migration.
• Ràng buộc quan trọng đặt ở database: số dư ≥ 0, số điện thoại duy nhất trong tài khoản ACTIVE,
một Request đang hoạt động cho mỗi cặp (bài, người nhận), payment_order_id duy nhất trong
sổ Credit.
• Không cần bảng thống kê: báo cáo tài chính và thống kê AI đọc thẳng từ credit_ledger,
payment_orders, activity_logs.
13.5. Cột chuẩn của bảng chính (C34)
Giảng viên yêu cầu mỗi bảng chính có đủ: ngày tạo, ngày sửa, người tạo, người sửa, isActive, isDelete
và status. Trong ShareLoop là các cột created_at, updated_at, created_by, updated_by,
is_active, is_deleted, status. Yêu cầu dành cho bảng chính, nên nhóm chỉ thêm cột ở nơi cột có
nghĩa:
| Nhóm bảng  | Bảng                              | Cột chuẩn |
| ---------- | --------------------------------- | --------- |
| Bảng chính | users, items, requests, reports,  | Đủ 7 cột  |
media_files
Danh mục, cấu hình item_categories, item_attributes, areas,  6 cột, không có status (không có
|     | website_attributes | vòng đời) |
| --- | ------------------ | --------- |
Trang 68 / 83

SRS ShareLoop — phiên bản v10
Nhóm bảng Bảng Cột chuẩn
Tài chính payment_orders, credit_ledger created_at; payment_orders thêm
updated_at và có status;
credit_ledger có created_by.
Không xoá, không tắt
Sự kiện, nhật ký; messages, notifications, activity_logs; ba Giữ nguyên: chỉ ghi thêm, hoặc sống
mapping bảng mapping theo bản ghi cha
• status là bước trong vòng đời nghiệp vụ; is_active là công tắc tạm tắt / ẩn; is_deleted là xoá
mềm. Ba cột không chồng nghĩa, nên bỏ giá trị LOCKED của tài khoản (thay bằng is_active),
HIDDEN và REMOVED của bài đăng (thay bằng is_active, is_deleted). Bài bị ẩn rồi bật lại tự về
đúng trạng thái cũ.
• Người dùng thường chỉ thấy bản ghi đúng trạng thái, đang bật và chưa xoá. Không có câu
DELETE trong nghiệp vụ; dữ liệu tiền và nhật ký không bao giờ bị xoá.
• Trong mã, một lớp BaseEntity chứa sáu cột chuẩn, được Spring Data JPA Auditing tự điền thời
gian và người thao tác (lấy từ JWT). Chi tiết cột, trigger và ràng buộc ở mục 7.11 tài liệu Luồng
hoạt động & ERD.
Trang 69 / 83

SRS ShareLoop — phiên bản v10
14. Góp ý của giảng viên và cách xử lý
Tổng hợp từ ghi chú buổi góp ý ngày 26/09/2026. Mỗi dòng trỏ tới mục đã sửa trong tài liệu.
# Góp ý Cách xử lý trong v10 Mục
1 Trao đổi sản phẩm có ràng buộc gì Danh sách danh mục trong phạm vi, danh mục cấm, BR-A12; 6.17, 11
không; loại sản phẩm; scope hạng điều kiện trao đổi BR-S01, BR-S03.
mục.
2 Chất lượng sản phẩm. Tình trạng bắt buộc, mô tả lỗi và ảnh lỗi, 3–8 ảnh thật, thuộc 6.17.3
tính động (BR-D08).
3 Nếu trao đổi sản phẩm fake thì Cấm hàng nhái (BR-A12); nhãn chính hãng có chứng từ [v9]; lý 6.16, 6.17
sao. do khiếu nại Counterfeit; chế tài trừ sao, gỡ bài, thu hồi quyền
gắn nhãn.
4 Tổng quan sản phẩm có đúng Câu hỏi "đúng mô tả?" khi nhận hàng, report NotAsDescribed, tỉ 6.12
ngoài đời không. lệ đúng mô tả trên hồ sơ.
5 Box chat có thấy được sản phẩm Ghim thẻ cả hai món ở đầu chat. 6.10.1
hai người muốn trao đổi không.
6 Đưa bài đăng sản phẩm vào box Gửi thẻ bài đăng; đề xuất đổi món bằng bài đã duyệt. 6.10.3
chat.
7 Vẫn kiểm soát khi trao đổi những Món đem đổi luôn là bài đã qua ba tầng kiểm duyệt; lexicon áp 6.10.3, 6.10.3
món đồ cấm. cả trong chat.
8 Đăng bài có duyệt tự động xem có Regex chặn tại form; AI gắn cờ liên hệ viết ngầm; checklist mục 6.4, 7.2.4
để email và số điện thoại trên bài 6 cho ảnh.
không (Admin).
9 AI phải kiểm duyệt bài đăng có AI sàng lọc bài tầng 2, gắn cờ cho Admin. 6.4, 7.3
thông tin liên hệ.
10 Từ khoá cấm (vd: súng) là có từ Không phải lúc nào cũng cấm: lexicon hai mức, ngoại lệ, khớp 7.2
khoá trong bài thì cấm bài đúng không dấu chỉ REVIEW.
không.
11 Đăng bài có tốn tiền không (đăng Mọi bài đăng 5 Credit (5.000đ); giao dịch hoàn toàn miễn phí. 4
bài tốn tiền, trao đổi không tốn
tiền).
12 Thêm tính năng sales để bài lên Đẩy bài có phí, nhãn "Được đẩy". 4.6
tốt.
13 Sửa bài đăng không tốn tiền (giới Bị chặn tự động hoặc chưa được duyệt: sửa miễn phí; sau khi 4.5
hạn khoảng 2 lần miễn phí). duyệt được sửa miễn phí 1 lần, từ lần sau tính phí; mọi lần sửa
đều duyệt lại.
14 Dòng đầu tiên của ghi chú bị cắt Nhóm quyết định không cần làm rõ thêm; nội dung này không C13
("…stack vs end"). ảnh hưởng thiết kế (C13 đóng).
15 [29/09] Mỗi bảng chính phải có 5 bảng chính đủ 7 cột; 4 bảng danh mục, cấu hình có 6 cột; 13.5
ngày tạo, ngày sửa, người tạo, bảng tài chính, nhật ký, mapping giữ gọn; bỏ các trạng thái
người sửa, isActive, isDelete, trùng nghĩa (C34).
status.
Trang 70 / 83

SRS ShareLoop — phiên bản v10
15. Tổng kết những điểm cần chốt
Toàn bộ điểm cần quyết định gom về một chỗ. Nhóm 1 ảnh hưởng tới dữ liệu, API hoặc luồng tiền nên
phải chốt trước khi code. Nhóm 2 chỉ là giá trị cấu hình trong website_attributes, Admin đổi được bất
cứ lúc nào. Cột "Trạng thái" cập nhật theo buổi chốt ngày 28/09/2026; phần thân tài liệu đã được sửa
theo đúng các quyết định này.
15.1. Nhóm 1 — chốt trước khi code
| # Điểm cần chốt | Quyết định / đề xuất | Trạng thái | Mục |
| --------------- | -------------------- | ---------- | --- |
C01 Bài nào mất phí đăng? Mọi bài (Cho và Trao đổi) Đã chốt 4.8
C02 Mức phí và cách thu 5 Credit = 5.000đ / bài / 30 ngày; giữ khi gửi,  Đã chốt 4.2, 4.4
trừ khi duyệt lần đầu
C03 Giao dịch miễn phí cho cả hai bên? Có, theo góp ý giảng viên Đã chốt 4.1
C04 Còn chặn liên hệ trong chat khi không  Giữ, với lý do an toàn và bằng chứng Đã chốt 6.10.3
còn phí giao dịch?
C05 Số điện thoại duy nhất; quy tắc đổi số Duy nhất trong tài khoản ACTIVE; OTP email,  Đã chốt 5.2
1 lần/30 ngày
C06 Nơi chạy PostgreSQL khi deploy Dịch vụ managed có gói miễn phí hoặc cùng  Đã chốt  2.3
|     | nhà cung cấp với nơi lưu ảnh; chọn dịch vụ cụ  | (nguyên tắc) |     |
| --- | ---------------------------------------------- | ------------ | --- |
thể khi deploy thử (tuần 7)
C07 Nhà cung cấp LLM và model Thử 20 mẫu trên 2–3 nhà cung cấp (spike S5,  Chờ thử  8.7
|                                 | tuần 5)    | nghiệm  |     |
| ------------------------------- | ---------- | ------- | --- |
| C08 AI sàng lọc bài chỉ gắn cờ? | Chỉ gắn cờ | Đã chốt | 6.4 |
C09 Danh mục trong phạm vi và danh  Theo 6.17.1, 6.17.2; cấm động vật sống Đã chốt 6.17
mục cấm
C10 Bài đang hiển thị khi sửa: ẩn tạm hay  Ẩn tạm tới khi duyệt xong Đã chốt 4.5
giữ bản cũ?
C11 Cổng thanh toán khi demo VNPay sandbox; dự phòng cổng giả lập giữ  Đã chốt 6.3
nguyên kiến trúc webhook
C12 Nơi lưu ảnh khi deploy Dev: Local (thử S3 với MinIO). Deploy: chọn  Chờ chốt —  2.6
|     | theo phân tích trong tài liệu Lộ trình, sau khi  | sau khi web  |     |
| --- | ------------------------------------------------ | ------------ | --- |
|     | web chạy ổn định                                 | chạy ổn định |     |
C13 Nội dung dòng góp ý đầu bị cắt trong  Không cần hỏi lại giảng viên Đóng —  14
| ảnh ghi chú |     | không cần  |     |
| ----------- | --- | ---------- | --- |
thiết
C33 Thiết kế dữ liệu 17 bảng 17 bảng theo chương 13 và tài liệu Luồng hoạt  Đã chốt  13
|     | động & ERD | (28/09) |     |
| --- | ---------- | ------- | --- |
C34 Cột chuẩn cho bảng chính 7 cột cho users, items, requests, reports,  Đã chốt  13.5
|     | media_files; 6 cột cho bảng danh mục, cấu  | (29/09) |     |
| --- | ------------------------------------------ | ------- | --- |
hình; bỏ LOCKED, HIDDEN, REMOVED
15.2. Nhóm 2 — giá trị cấu hình
| # Tham số | Giá trị | Trạng thái | Mục |
| --------- | ------- | ---------- | --- |
C14 Luật sửa bài Miễn phí khi bị chặn tự động hoặc chưa từng duyệt; 1  Đã chốt 4.5
lần miễn phí sau duyệt; từ lần sau mất phí
C14b Mức phí sửa từ lần thứ hai 5 Credit/lần (bằng phí đăng bài) Đã chốt 4.5
C15 Phí đẩy bài 5 Credit/3 ngày; cộng dồn tối đa 14 ngày; ≤ 3 bài  Đã chốt 4.6
đẩy/trang
Trang 71 / 83

SRS ShareLoop — phiên bản v10
| # Tham số | Giá trị | Trạng thái | Mục |
| --------- | ------- | ---------- | --- |
C16 Phí gia hạn 5 Credit/30 ngày (bằng phí đăng bài) Đã chốt 4.2
C17 Phí AI trợ lý 2 Credit/lượt; 5 lượt miễn phí/ngày Đã chốt 4.2
C18 Điều kiện mở hạn mức AI miễn  Đã nạp ít nhất 1 lần; chưa nạp có 1 lượt thử Đã chốt 5.3
phí
| C19 Số Request Pending đồng thời | 5       | Đã chốt | 11.1 |
| -------------------------------- | ------- | ------- | ---- |
| C20 Trần điểm mỗi ngày           | 30 điểm | Đã chốt | 6.15 |
C21 Ngưỡng và timeout AI chat 0,7; 5 giây; lỗi thì chặn không tính vi phạm Đã chốt (v9) 7.4
C22 Timeout AI sàng lọc bài 20 giây, thử lại 1 lần Đã chốt 7.3
| C23 Ảnh bài đăng          | 3–8 ảnh, tối đa 5 MB/ảnh | Đã chốt | 6.17.3 |
| ------------------------- | ------------------------ | ------- | ------ |
| C24 Bộ checklist khởi tạo | 10 mục ở 6.17.4          | Đã chốt | 6.17.4 |
C25 Chế tài chứng từ giả Lần 1: trừ 1 sao, thu hồi quyền gắn nhãn 90 ngày; lần 2:  Đã chốt 6.16
vĩnh viễn
C26 Thời điểm đối tác xem chứng từ Từ Reserved Chờ chốt  6.16
(trước tuần
8)
C27 Các mốc thời gian Pending 3 ngày; chat 10 ngày; auto-confirm 3 ngày;  Đã chốt;  6.13, 6.14
|     | khiếu nại 7 ngày; giải trình 3 ngày; SLA duyệt 24 giờ; 3    | Admin cấu     |     |
| --- | ----------------------------------------------------------- | ------------- | --- |
|     | report thì ẩn bài; tài khoản chưa xác thực 24 giờ; gia hạn  | hình lại được |     |
bài EXPIRED trong 7 ngày
C28 Hạng, số bài đồng thời, mốc điểm Giữ như v8 Đã chốt (v8) 6.15
| C29 Phục hồi sao | Giữ như v8 | Đã chốt (v8) | 6.15 |
| ---------------- | ---------- | ------------ | ---- |
C30 Giới hạn tài khoản mới theo IP 3 tài khoản/IP/24 giờ Đã chốt 5.3
C31 Trần ngân sách AI theo ngày Đặt khi đã biết giá thật (sau C07) Chờ tìm hiểu 8.5
| C32 Tự động duyệt bài hạng Kim  | Không | Đã chốt | 6.4 |
| ------------------------------- | ----- | ------- | --- |
Cương 5 sao?
Những điểm còn mở sau buổi chốt 28/09
C07 (nhà cung cấp LLM) và C31 (trần ngân sách AI) chốt trong tuần 5 theo kết quả spike S5. C12 (nơi lưu
ảnh khi deploy) chốt khi web chạy ổn định, trước lần deploy thử ở tuần 7. C26 (thời điểm đối tác xem chứng
từ) chốt trước khi làm xác thực hàng chính hãng ở tuần 8. Mọi điểm còn lại đã chốt.
Trang 72 / 83

SRS ShareLoop — phiên bản v10
16. Kế hoạch từ tuần 4 đến tuần 9
Dự án đang ở cuối tuần 3 và kết thúc ở tuần 9, nên còn 6 tuần, trong đó khoảng 5 tuần để viết code.
Kế hoạch dưới đây thay cho kế hoạch 8 tuần của các bản trước. Nguyên tắc: làm phần rủi ro nhất
(tiền, trạng thái, kiểm duyệt) sớm nhất; mỗi tuần có một mốc chạy được đầu–cuối; tuần 9 chỉ sửa lỗi và
chuẩn bị demo.
16.1. Đã xong đến hết tuần 3
• Tài liệu: SRS từ v8 lên v10, bốn sơ đồ use case, 17 sơ đồ luồng, thiết kế dữ liệu 17 bảng.
• Repo GitHub (monorepo, nhánh main + develop, CODEOWNERS, mẫu PR).
• Frontend: khung giao diện các trang chính với dữ liệu giả.
• Backend: chưa có mã — đây là rủi ro tiến độ lớn nhất, nên tuần 4 vừa chuẩn bị vừa phải có tính
năng đầu tiên chạy được.
16.2. Lịch theo tuần
Tuần Công việc Mốc phải đạt cuối tuần
4 T2: chốt các điểm nhóm 1 và ERD 17 bảng (đã xong 28/9). T2–T4: API Đăng ký → đăng nhập → tạo
28/9 – 4/10 contract phần tài khoản và bài đăng; T3 bổ sung cột chuẩn cho bảng bài chạy đầu–cuối trên
chính (C34). T3–T5: BE1 dựng skeleton Java 21 + Spring Boot 4.1.1 theo develop; CI xanh.
cấu trúc thư mục trong tài liệu Lộ trình (Docker Compose, Flyway
V1__init.sql đủ 17 bảng và cột chuẩn, JWT, định dạng lỗi, CI). T5–CN:
BE1 đăng ký + OTP email + đăng nhập; BE2 tải ảnh (Local) + danh mục,
khu vực + tạo bài (chưa kiểm duyệt); BE3 enum trạng thái Request + gửi
yêu cầu; BE4 website_attributes (cấu hình, lexicon, checklist) + khung
trang Admin. Spike song song: VNPay, LLM (nơi lưu ảnh chỉ làm bản
Local, chốt sau). FE: nối đăng ký, đăng nhập.
5 BE1: ví, nạp Credit VNPay sandbox, phí đăng bài 5 Credit (giữ / trừ / Mốc 1: đăng bài có phí + kiểm
5/10 – 11/10 nhả). BE2: ContentModerationService tầng 1 + bộ test. BE4: hàng duyệt tầng 1 và tầng 3 hoàn
chờ duyệt, checklist, duyệt / từ chối. BE3: chọn người, mở chat (polling). chỉnh; nạp tiền sandbox chạy.
FE: đăng bài, ví, trang duyệt bài.
6 BE3: lọc liên hệ trong chat (tầng 1), chốt lịch, trao liên lạc, xác nhận giao Mốc 2 (quan trọng nhất): một
12/10 – 18/10 nhận, "đúng mô tả?", job nền. BE2: tìm kiếm không dấu, AI sàng lọc bài giao dịch chạy từ gửi yêu cầu
(tầng 2). BE1: sửa bài theo luật mới, gia hạn. BE4: khiếu nại, TrustStars, đến Completed. Điểm kiểm tra
điểm, hạng. FE: chat, tìm kiếm, luồng giao dịch. cắt giảm (12.6).
7 BE2: AI trợ lý tìm đồ, AI tầng 2 cho chat. BE4: báo cáo tài chính, nhật ký Mốc 3: toàn bộ yêu cầu Must
19/10 – 25/10 Admin, khoá tài khoản. BE1: đẩy bài; deploy thử lần 1 (VPS + chạy; hệ thống chạy online.
PostgreSQL managed + nơi lưu ảnh chốt ở C12). BE3: thẻ bài trong chat,
test đủ mọi mũi tên trạng thái. FE: trang Admin, báo cáo.
8 Kiểm thử tích hợp các nhánh tiền và trạng thái; đo chất lượng bộ lọc (mục Đóng băng tính năng cuối tuần
26/10 – 1/11 7.5); làm Should theo thứ tự ưu tiên (xác thực chính hãng, đổi số điện 8; bản deploy ổn định.
thoại, chuyển Cho → Trao đổi, giám sát bất thường, CSV); sửa lỗi;
deploy bản ổn định.
9 Chỉ sửa lỗi và hoàn thiện giao diện; dữ liệu mẫu cho demo; tài liệu cuối Nộp sản phẩm và tài liệu; bảo
2/11 – 8/11 (SRS, hướng dẫn cài đặt, báo cáo kiểm thử); kịch bản demo nhấn vào: vệ.
kiểm duyệt ba tầng, phí đăng bài, chat hai tầng, state machine, báo cáo
tài chính; tập dượt demo 2 lần; chuẩn bị chế độ dự phòng.
16.3. Quy tắc giữ tiến độ
• Mỗi tuần là một sprint: họp lập kế hoạch sáng thứ Hai, demo nội bộ tối Chủ nhật trên nhánh
develop.
Trang 73 / 83

SRS ShareLoop — phiên bản v10
• Tính năng chưa xong ở mốc thì không kéo dài âm thầm: báo ngay ở buổi họp, chọn cắt hoặc dời
theo danh sách 12.6.
• Cuối tuần 6 nếu Mốc 2 chưa đạt: cắt ngay mục 1–3 của danh sách 12.6 và dồn người vào luồng
giao dịch.
• Không nhận thêm yêu cầu mới sau tuần 7, trừ khi giảng viên yêu cầu.
Trang 74 / 83

SRS ShareLoop — phiên bản v10
17. Đặc tả Use Case
17.1. Quy ước
Use case là một mục tiêu hoàn chỉnh mà người dùng đạt được, viết theo góc nhìn người dùng.
«include»: bắt buộc gọi; «extend»: có thể mở rộng trong một số điều kiện. Mẹo phân biệt: bỏ use
case con đi mà luồng chính vẫn chạy được thì là extend.
Mã use case được đánh lại trong v10
Mã UC dưới đây thay cho mã trong sơ đồ v9 (ShareLoop_UseCase-v3.drawio). Bốn sơ đồ ngay sau đây đã
được vẽ lại theo v10: thêm "Thanh toán phí bài đăng", "Đẩy bài", "Chia sẻ thẻ bài / đổi món đề nghị", "Quản lý
từ khoá cấm", "Sàng lọc nội dung bài đăng", "Xem thống kê AI"; bỏ "Kiểm tra số dư Credit" và "AI gợi ý ghép
đôi"; đổi actor "Email/SMS Service" thành "Email Service". Nếu nhóm vẫn dùng file drawio thì sửa theo đúng
các hình này.
Hình 24 — Sơ đồ use case v10 — Actor và use case chung của User
Trang 75 / 83

SRS ShareLoop — phiên bản v10
Hình 25 — Sơ đồ use case v10 — Member: ví Credit, bài đăng, tìm đồ
Trang 76 / 83

SRS ShareLoop — phiên bản v10
Hình 26 — Sơ đồ use case v10 — Member: giao dịch
Trang 77 / 83

SRS ShareLoop — phiên bản v10
Hình 27 — Sơ đồ use case v10 — Admin
17.2. Use case của User (cả Member và Admin)
| Mã Tên use case | Tên trên sơ đồ | Quan hệ |
| --------------- | -------------- | ------- |
UC-01 Đăng ký & xác thực email Register account Email Service
| UC-02 Đăng nhập / Đăng xuất | Log in / Log out | —   |
| --------------------------- | ---------------- | --- |
UC-03 Quản lý hồ sơ (gồm đổi số điện thoại) Manage profile Email Service
UC-04 Đổi / quên mật khẩu Change / reset password Email Service
| UC-05 Xem điểm, hạng & sao uy tín | View reputation | —   |
| --------------------------------- | --------------- | --- |
17.3. Use case của Member
| Mã Tên use case               | Tên trên sơ đồ | Quan hệ         |
| ----------------------------- | -------------- | --------------- |
| UC-10 Nạp Credit              | Top up Credit  | Cổng thanh toán |
| UC-11 Xem ví & lịch sử Credit | View wallet    | —               |
UC-12 Đăng bài (Cho / Trao đổi) Create post include UC-60; include UC-
13 (mọi bài)
UC-13 Thanh toán phí bài đăng [v10] Pay post fee được include bởi UC-12,
UC-14, UC-15, UC-16
UC-14 Sửa / gỡ bài Edit / remove post include UC-60; include UC-
13 từ lần sửa thứ hai sau
duyệt
| UC-15 Gia hạn bài | Renew post | include UC-13 |
| ----------------- | ---------- | ------------- |
Trang 78 / 83

SRS ShareLoop — phiên bản v10
| Mã Tên use case     | Tên trên sơ đồ | Quan hệ       |
| ------------------- | -------------- | ------------- |
| UC-16 Đẩy bài [v10] | Boost post     | include UC-13 |
UC-17 Tải chứng từ hàng chính hãng [v9] Upload authenticity proof extend UC-12
| UC-18 Tìm kiếm & lọc                  | Search items         | —            |
| ------------------------------------- | -------------------- | ------------ |
| UC-19 AI trợ lý tìm đồ                | AI search assistant  | Dịch vụ AI   |
| UC-20 Gửi yêu cầu (xin / đề nghị đổi) | Send request         | —            |
| UC-21 Tự huỷ yêu cầu                  | Cancel request       | extend UC-20 |
| UC-22 Chọn người giao dịch            | Select trade partner | —            |
UC-23 Chuyển Cho → Trao đổi (không phí) Convert Give to Swap extend UC-22
UC-24 Nhắn tin trong giao dịch Chat in transaction include UC-61
UC-25 Chia sẻ thẻ bài / đề xuất đổi món [v10] Share post card extend UC-24
UC-26 Chốt lịch giao nhận Confirm meetup schedule include UC-62
| UC-27 Xác nhận đã giao / đã nhận | Confirm handover   | —            |
| -------------------------------- | ------------------ | ------------ |
| UC-28 Đính kèm minh chứng        | Attach proof media | extend UC-27 |
| UC-29 Gửi báo cáo / khiếu nại    | Submit report      | —            |
17.4. Use case của Admin
| Mã Tên use case | Tên trên sơ đồ | Quan hệ |
| --------------- | -------------- | ------- |
UC-40 Kiểm duyệt bài đăng Moderate posts include UC-13 (trừ hoặc
nhả phí)
UC-41 Đối chiếu chứng từ chính hãng [v9] Verify authenticity proof extend UC-40
UC-42 Quản lý checklist duyệt bài [v9] Manage moderation checklist —
| UC-43 Quản lý danh mục & thuộc tính | Manage categories       | —   |
| ----------------------------------- | ----------------------- | --- |
| UC-44 Quản lý từ khoá cấm [v10]     | Manage banned keywords  | —   |
| UC-45 Quản lý khu vực               | Manage areas            | —   |
| UC-46 Cấu hình phí & hạn mức        | Configure fees & limits | —   |
UC-47 Xử lý khiếu nại & tranh chấp Resolve reports & disputes include UC-48
UC-48 Trừ sao uy tín Deduct trust star được include bởi UC-47
UC-49 Rà soát bài của người vi phạm Review violator posts extend UC-47
| UC-50 Khoá / mở khoá tài khoản | Lock / unlock account       | —   |
| ------------------------------ | --------------------------- | --- |
| UC-51 Cấp sao ân hạn           | Restore grace star          | —   |
| UC-52 Giám sát bất thường      | Monitor suspicious activity | —   |
| UC-53 Xem báo cáo tài chính    | View financial reports      | —   |
| UC-54 Xử lý bài quá hạn        | Handle expired posts        | —   |
| UC-55 Xem thống kê AI (Could)  | View AI statistics          | —   |
Trang 79 / 83

SRS ShareLoop — phiên bản v10
17.5. Use case dùng lại (không có actor gọi trực tiếp)
Mã Tên use case Tên trên sơ đồ Được dùng bởi
UC-60 Sàng lọc nội dung bài đăng [v10] Screen post content UC-12, UC-14; nối Dịch
vụ AI
UC-61 Phát hiện thông tin liên hệ trong tin nhắn Detect contact info in message UC-24; nối Dịch vụ AI
[v9]
UC-62 Trao thông tin liên lạc Reveal contact info UC-26; nối Email Service
17.6. Đặc tả chi tiết các use case then chốt
UC-12 + UC-13 — Đăng bài và thanh toán phí bài đăng
Mục Nội dung
Actor Member (chính); Dịch vụ AI (phụ, qua UC-60)
Tiền điều kiện Đã đăng nhập; TrustStars > 0; chưa vượt số bài đồng thời theo hạng.
Hậu điều kiện Bài ở PENDING_REVIEW; held_credit tăng đúng 5; fee_state = HELD,
pending_fee_type = POST. Có kết quả AI sàng lọc hoặc nhãn "AI chưa kiểm".
Luồng chính 1. Chọn hình thức, danh mục, tình trạng, nhập tiêu đề, mô tả, (Trao đổi) mô tả món mong muốn, tải
3–8 ảnh. 2. Hệ thống chạy tầng 1 (UC-60). 3. Hiển thị popup phí 5 Credit và chính sách hoàn phí.
4. Member xác nhận; hệ thống kiểm tra số dư khả dụng trong transaction có khoá dòng và giữ 5
Credit. 5. Tạo bài PENDING_REVIEW. 6. Gửi nội dung sang AI sàng lọc bất đồng bộ; lưu cờ. 7.
Bài vào hàng chờ Admin theo mức ưu tiên.
Luồng thay thế 2a. Vi phạm tầng 1 → hiện lý do tại trường tương ứng, không tạo bài, không giữ phí; người dùng
sửa ngay miễn phí. 4a. Số dư không đủ → chặn, gợi ý nạp. 6a. AI lỗi hoặc quá 20 giây sau 1 lần
thử lại → nhãn "AI chưa kiểm".
Luật liên quan BR-U01, BR-U05, BR-D08, BR-A01, BR-A09, BR-A10, BR-P01, BR-P03, BR-P05, BR-P07, BR-
S02
UC-14 — Sửa bài
Mục Nội dung
Actor Member (chủ bài); Dịch vụ AI (phụ, qua UC-60)
Tiền điều kiện Bài của chính mình; chưa có Request ở trạng thái Reserved trở lên.
Hậu điều kiện Bài quay lại PENDING_REVIEW, tạm ẩn; nếu là lần sửa có phí thì đang giữ phí sửa
(pending_fee_type = EDIT).
Luồng chính 1. Bấm "Sửa bài". 2. Hệ thống xác định loại lần sửa: bài chưa từng được duyệt (approved_at
null) → miễn phí; đã từng được duyệt và free_edit_used = false → lượt miễn phí; còn lại →
lượt có phí. 3. Lượt có phí: popup phí sửa 5 Credit, kiểm tra số dư, giữ phí. 4. Member sửa, bấm
gửi; tầng 1 kiểm tra. 5. Bài về PENDING_REVIEW; AI sàng lọc lại; vào hàng chờ. 6. Admin duyệt
(UC-40): trừ khoản đang giữ; nếu là lượt miễn phí thì đặt free_edit_used = true.
Luồng thay thế 4a. Tầng 1 chặn → sửa tiếp trên form, không phí, không tính lượt. 6a. Admin từ chối bản sửa →
nhả khoản đang giữ, không tính lượt, bài ở REJECTED; lần sửa tiếp theo vẫn theo loại ở bước 2.
2a. Bài đang chờ duyệt một bản sửa chưa được xét → sửa đè, không thêm phí, không thêm lượt.
Luật liên quan BR-D04, BR-D07, BR-P05, BR-P06, BR-P07, BR-A01
Trang 80 / 83

SRS ShareLoop — phiên bản v10
UC-40 — Kiểm duyệt bài đăng
Mục Nội dung
Actor Admin
Tiền điều kiện Có bài PendingReview đã qua tầng 1.
Hậu điều kiện APPROVED: hiển thị công khai, đặt expire_at (lần duyệt đầu), khoản đang giữ (POST hoặc
EDIT) chuyển sang trừ thật. REJECTED: nhả khoản đang giữ, lý do gửi người đăng.
Luồng chính 1. Mở hàng chờ đã xếp ưu tiên. 2. Mở một bài, xem nội dung, ảnh, cờ AI (đoạn bị gắn cờ được tô
sáng), từ khoá mức REVIEW. 3. Trả lời checklist. 4. Đạt hết → Duyệt. 5. Hệ thống trừ phí (nếu có),
lưu bản chụp checklist, ghi ActivityLogs, thông báo người đăng.
Luồng thay thế 4a. Có mục không đạt → Từ chối, chọn hoặc nhập lý do; hệ thống nhả Hold. 4b. Chỉ cần bổ sung
(thiếu mô tả lỗi, sai danh mục) → trả về yêu cầu bổ sung; lần sửa này không tính lượt. 2a. Bài có
nhãn chính hãng → UC-41.
SLA 24 giờ kể từ khi bài vào hàng chờ.
Luật liên quan BR-A01, BR-A02, BR-A06, BR-A08, BR-A11, BR-P03, BR-D07
UC-16 — Đẩy bài
Mục Nội dung
Actor Member (chủ bài)
Tiền điều kiện Bài Approved, còn hạn, không bị ẩn, không thuộc danh mục "Khác".
Luồng chính 1. Chọn "Đẩy bài" trên bài của mình. 2. Chọn số gói; hệ thống hiển thị phí, thời gian kết thúc dự
kiến và chính sách không hoàn. 3. Xác nhận; hệ thống kiểm tra số dư khả dụng, trừ phí, ghi
CreditLedger BoostFee, cập nhật thời điểm hết đẩy. 4. Bài xuất hiện ở đầu kết quả khớp bộ lọc, có
nhãn "Được đẩy".
Luồng thay thế 2a. Tổng thời gian vượt 14 ngày hoặc vượt hạn bài → giới hạn số gói được chọn. 3a. Không đủ số
dư → gợi ý nạp.
Luật liên quan BR-P05, BR-P07, BR-P08
UC-20 — Gửi yêu cầu
Mục Nội dung
Actor Member (vai người nhận)
Tiền điều kiện Bài Approved; chưa có Request đang hoạt động cho bài này; TrustStars > 0; đang có ít hơn 5
Request Pending.
Luồng chính 1. Mở bài, bấm gửi yêu cầu. 2. Nếu bài là Trao đổi: chọn một bài Approved của mình làm món đề
nghị. 3. Tạo Request Pending. 4. Thông báo chủ bài.
Luồng thay thế 2a. Chưa có bài Approved nào → chặn, giải thích BR-S01, gợi ý đăng bài. 1a. Đã có 5 Request
Pending → chặn, gợi ý huỷ bớt. 1b. Bài của chính mình → chặn (BR-R01).
Ghi chú Không kiểm tra số dư, không giữ tiền [v10].
Luật liên quan BR-R01, BR-R02, BR-U02, BR-U05, BR-S01, BR-S03
UC-26 — Chốt lịch giao nhận (gồm UC-62 Trao thông tin liên lạc)
Mục Nội dung
Actor Member (cả hai bên); Email Service (phụ)
Trang 81 / 83

SRS ShareLoop — phiên bản v10
Mục Nội dung
Tiền điều kiện Request ở AwaitingLogistics, chưa quá 10 ngày kể từ Reserved. Nếu là Trao đổi: đã có món đề
nghị hợp lệ.
Hậu điều kiện Request LogisticsConfirmed; lịch hẹn bị khoá; ContactRevealedAt có giá trị; hai bên nhận email
chứa email, số điện thoại của nhau và lịch hẹn; lọc liên hệ trong chat tắt; được gửi ảnh.
Luồng chính 1. Người đăng bài nhập hình thức, thời gian, địa điểm. 2. Nếu Ship: cả hai xác nhận cam kết trách
nhiệm. 3. Bên kia xem và xác nhận. 4. Hệ thống chuyển trạng thái, ghi ContactRevealedAt. 5. Gửi
thông tin liên lạc (UC-62).
Luồng thay thế 3a. Bên kia đề xuất sửa lịch → quay lại bước 1. 5a. Gửi email lỗi → thông tin vẫn hiển thị trong
trang giao dịch; job gửi lại.
Ghi chú Không thu phí, không kiểm tra số dư [v10]. Sau bước 5 hai bên có liên lạc của nhau vĩnh viễn (BR-
T12).
Luật liên quan BR-T01 → BR-T05, BR-T10, BR-T11, BR-T12, BR-C06
UC-19 — AI trợ lý tìm đồ
Mục Nội dung
Actor Member; Dịch vụ AI (phụ)
Tiền điều kiện Đã đăng nhập, tài khoản không bị khoá. Không cần có bài đăng.
Luồng chính 1. Mở trang Trợ lý; thấy số lượt miễn phí còn lại. 2. Nhập nhu cầu. 3. Còn lượt miễn phí thì dùng;
hết thì popup 2 Credit, xác nhận, trừ phí. 4. LLM tách bộ lọc. 5. Backend truy vấn bài Approved. 6.
(Tuỳ chọn) LLM viết câu giải thích cho tối đa 5 bài. 7. Hiển thị kết quả; chỉ gồm bài có thật trong kết
quả bước 5.
Luồng thay thế 3a. Tài khoản chưa từng nạp đã dùng lượt thử → yêu cầu nạp để mở hạn mức. 4a/6a. LLM lỗi
hoặc quá 15 giây → hoàn lượt, chạy tìm kiếm từ khoá thường với câu người dùng nhập. 5a. Không
có kết quả → gợi ý nới bộ lọc.
Luật liên quan BR-P04, BR-P07, BR-P11, BR-S04, BR-S05, BR-S06
Trang 82 / 83

SRS ShareLoop — phiên bản v10
Phụ lục A. Lịch sử phiên bản
Phiên bản Ngày Nội dung chính
v8 19/09/2026 Bỏ AI khỏi kiểm duyệt bài; chuyển hẳn sang thu phí giao dịch; bỏ quỹ cộng đồng;
SMS giả lập; 99 FR.
v9 25/09/2026 1.000đ = 1 Credit; bỏ AI ghép đôi; kiểm duyệt chat hai tầng; checklist cấu hình
được; xác thực hàng chính hãng; StorageService; ERD tối ưu 15 bảng; 122 FR.
v10 26/09/2026 PostgreSQL; mọi bài 5 Credit thay phí giao dịch; đẩy bài; sửa bài miễn phí trước
khi duyệt và 1 lần sau khi duyệt; bỏ SMS, 1 email ↔ 1 số điện thoại; kiểm duyệt
bài ba tầng (lexicon + AI gắn cờ + Admin); phạm vi danh mục và chất lượng;
"đúng mô tả?"; thẻ bài trong chat; phân tích phương án AI; đánh giá tổng thể; dữ
liệu 17 bảng; đủ sơ đồ luồng và use case; 125 FR.
v10 (cập 27/09/2026 Kế hoạch làm lại cho thời gian còn lại: tuần 4 – tuần 9 (chương 16); điểm cắt
nhật) giảm cuối tuần 6; đóng băng tính năng cuối tuần 8.
v10 (cập nhật 28/09/2026 Đồng bộ các quyết định chương 15: phí sửa từ lần thứ hai 5 Credit; AI trợ lý 2
28/09) Credit/lượt; gia hạn 5 Credit/30 ngày; đẩy bài 5 Credit/3 ngày; C04, C05, C06,
C08–C11 chốt; ERD 17 bảng chốt (C33); C13 đóng; C12 chốt sau khi web ổn
định. Backend Java 21 + Spring Boot 3.5.16 (trùng bài thi thực hành tháng 11); bổ
sung bộ công cụ IDE và trợ lý AI.
v10 (cập nhật 29/09/2026 Theo yêu cầu giảng viên: cột chuẩn created_at, updated_at, created_by,
29/09) updated_by, is_active, is_deleted, status cho bảng chính (C34); tài khoản bỏ
trạng thái LOCKED, bài đăng bỏ HIDDEN, REMOVED; media_files thêm status;
ERD vẫn 17 bảng. Backend chuyển sang Spring Boot 4.1.1 (nhánh 3.5 đã hết hỗ
trợ; bài thi thực hành cũng dùng 4.1.1).
Phụ lục B. Vì sao chọn mô hình P2P thay vì kho vật lý
Giữ nguyên lập luận từ v8: mô hình hai bên tự giao nhận có chi phí vận hành gần bằng 0 và vừa sức
thời gian đồ án; đổi lại, kiểm soát hàng cấm yếu hơn kho vật lý nên được bù bằng kiểm duyệt ba tầng,
bằng chứng ảnh và chế tài uy tín. Mô hình kho cần mặt bằng, nhân sự, thêm actor nhân viên kho và
toàn bộ nghiệp vụ nhập xuất tồn, không phù hợp phạm vi đồ án.
Trang 83 / 83