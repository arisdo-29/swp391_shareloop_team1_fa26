# ShareLoop – Business Rule v12 (bản tổng hợp)

Nov 2, 2026 · Khuyên

ShareLoop là nền tảng cho tặng và trao đổi đồ cũ, tìm theo quận; hai người dùng không trả tiền cho nhau, Credit chỉ để trả phí dịch vụ cho nền tảng. Bản này gộp BR v11.2, 18 quyết định D-01 … D-18, các ghi chú đỏ và các thay đổi đã merge tới 02/11; mỗi luật có ví dụ và lý do chọn con số. Cập nhật 09/11/2026: mục G theo luật đẩy bài chốt ngày 06/11 (đợt 6 ngày, nghỉ 3 ngày, giới hạn suất, xoay vòng); TK-01 thêm câu hồ sơ công khai cần đăng nhập (chốt 09/11).

## Cách đọc tài liệu này

Mỗi hạng mục có bảng luật (giữ mã TK, KC, UT… như bản v11.2 để tra chéo), rồi ba phần: **Ví dụ** kể bằng ba tài khoản demo An, Bình, Chi; **Vì sao là con số này** giải thích tại sao không lớn hơn, không nhỏ hơn; **Nếu bị hỏi** chuẩn bị câu trả lời cho câu hỏi lắt léo. Trạng thái viết tiếng Việt, tên trong hệ thống để trong ngoặc.

### Bốn cặp khái niệm hay bị hỏi nhầm

| Khái niệm | Nghĩa là | Khác với |
| --- | --- | --- |
| Credit | Đơn vị trả phí dịch vụ cho nền tảng (1 Credit = 1.000 đ), nạp bằng tiền thật qua VNPay. | Điểm: không mua được, không đổi ra Credit, chỉ có khi giao dịch hoàn tất. |
| Sao uy tín (0–5) | Mức đáng tin. Chỉ giảm khi Admin kết luận vi phạm; về 0 là bị cấm. | Điểm và hạng: đo mức hoạt động, quyết định quyền lợi (số bài, số ngày hiển thị). |
| Yêu cầu | Bản ghi trong bảng requests khi còn Chờ chủ bài chọn. | Giao dịch: cùng bản ghi đó, từ lúc chủ bài chọn người trở đi. |
| Ẩn / Xoá mềm / Khoá / Cấm | Ẩn: is\_active = false, trạng thái giữ nguyên. Xoá mềm: is\_deleted = true, không xoá khỏi DB. Khoá: Admin chủ động, mở lại được. Cấm (BANNED): hệ thống tự làm khi sao về 0, không mở lại. | Bài bị ẩn vẫn còn và hiện lại được; bài xoá mềm thì chỉ còn để đối soát. |

### Ba nguyên tắc khi chọn một con số

Khi giảng viên hỏi "vì sao là X mà không phải lớn hơn hay nhỏ hơn", hầu hết câu trả lời dựa trên ba nguyên tắc này:

1. **Người dùng thật không chạm tới, kẻ lạm dụng thì chạm sớm.** Ví dụ 5 bài Cho đang hiển thị: người dọn phòng hiếm khi cần hơn, kẻ đăng bài rác thì bị chặn ngay.
2. **Gắn với nhịp sinh hoạt.** 24 giờ là một ngày, 48 giờ đủ cho người đi học hoặc đi làm trả lời, 10 ngày bao trọn hai cuối tuần, 30 ngày là một tháng.
3. **Chỉnh được mà không sửa code.** Mọi con số nghiệp vụ nằm trong bảng cấu hình website\_attributes; Admin đổi trên màn Cấu hình và có hiệu lực ngay. Nếu bị hỏi "lỡ con số này sai thì sao": nhóm chọn giá trị ban đầu có lý do, và vận hành thật sẽ điều chỉnh bằng cấu hình. Riêng thời hạn mã đăng nhập nằm trong application.yml.

## A. Tài khoản và đăng nhập

Ai cũng xem được bài; muốn đăng, xin hay đổi đồ thì phải có tài khoản đã xác thực email.

| Mã | Luật | Con số |
| --- | --- | --- |
| TK-01 | Khách (chưa đăng nhập) xem trang chủ, tìm và lọc bài, xem chi tiết bài, danh mục, khu vực. Mọi thao tác khác phải đăng nhập. Hồ sơ công khai của người dùng chỉ xem được khi đã đăng nhập. | — |
| TK-02 | Đăng ký cần email, mật khẩu, họ tên, số điện thoại. Email thuộc danh sách tên miền dùng một lần thì bị từ chối. | — |
| TK-03 | SĐT bắt buộc, là số di động Việt Nam đầu 03 / 05 / 07 / 08 / 09 (nhận +84). Không xác thực SĐT bằng SMS. | 10 chữ số |
| TK-04 | Email và SĐT không trùng tài khoản đang hoạt động khác. Đăng ký lại một email đang chờ xác thực thì ghi đè thông tin cũ và gửi mã mới. | — |
| TK-05 | Một địa chỉ IP tạo tối đa 3 tài khoản mới trong 24 giờ gần nhất; vượt thì báo lỗi 429. | accounts\_per\_ip\_24h = 3 |
| TK-06 | Tài khoản chỉ dùng được sau khi nhập đúng OTP gửi qua email: 6 số, sống 5 phút, sai 5 lần thì mã bị huỷ, gửi lại sau ít nhất 60 giây. Mã lưu dạng băm BCrypt. | otp\_ttl\_minutes = 5, otp\_max\_failed = 5, otp\_resend\_cooldown\_seconds = 60 |
| TK-07 | Tài khoản chưa xác thực quá 24 giờ bị xoá mềm; email đó đăng ký lại được. | pending\_account\_ttl\_hours = 24 |
| TK-08 | Đăng nhập bằng email + mật khẩu. Sai email hay sai mật khẩu đều cùng một câu báo. Đăng nhập đúng được cấp mã đăng nhập (JWT) sống 24 giờ; hết hạn thì đăng nhập lại. | app.jwt.ttl-minutes = 1440 |
| TK-09 | Quên mật khẩu: hệ thống luôn trả lời giống nhau; chỉ gửi mã nếu email thuộc tài khoản đang hoạt động. | — |
| TK-10 | Đổi mật khẩu: phải đúng mật khẩu hiện tại, mật khẩu mới khác mật khẩu cũ; đổi xong gửi email báo. | — |
| TK-11 | Đổi SĐT: nhập OTP gửi về email; tối đa 1 lần / 30 ngày; số mới chưa thuộc tài khoản khác; không đổi khi đang có giao dịch Đã chốt lịch hoặc Chờ xác nhận giao nhận. | phone\_change\_interval\_days = 30 |
| TK-12 | Chỉ xem, sửa dữ liệu do chính mình tạo (hồ sơ, bài, yêu cầu, tin nhắn). | — |
| TK-13 | Admin là tài khoản tạo sẵn trong cơ sở dữ liệu, không đăng ký qua web, không đăng bài và không gửi yêu cầu. | — |
| TK-14 | Email chỉ dùng để gửi OTP và báo đổi mật khẩu; không gửi thông tin giao dịch qua email. | — |
| TK-15 | Mã đăng nhập chỉ chứa mã người dùng và vai trò. Tài khoản bị khoá hoặc bị cấm bị chặn ở mọi request (kể cả mã còn hạn). Đổi mật khẩu không làm mã cũ mất hiệu lực; mã cũ tự hết sau 24 giờ. | — |

Mã OTP có ba mục đích: Đăng ký (REGISTER), Quên mật khẩu (RESET\_PASSWORD), Đổi số điện thoại (CHANGE\_PHONE). Mã của mục đích này không dùng cho mục đích khác.

**Ví dụ**

- Bình đăng ký bằng một email tên miền dùng một lần: bị từ chối ngay, chưa gửi mã. Bình đăng ký bằng Gmail, nhận mã, gõ sai 5 lần: mã bị huỷ, phải bấm Gửi lại (sau 60 giây kể từ lần gửi trước).
- Phòng ký túc xá dùng chung một wifi: 3 bạn đăng ký được, bạn thứ 4 trong 24 giờ bị lỗi 429; khi tài khoản đầu tiên đã quá 24 giờ thì bạn thứ 4 đăng ký được.
- An đang có giao dịch Đã chốt lịch với Bình thì không đổi SĐT được, vì Bình đang thấy và sẽ gọi số cũ.

**Vì sao là con số này**

- **OTP 6 số, 5 phút, sai tối đa 5 lần.** 6 số có 1.000.000 khả năng; đoán mò 5 lần chỉ có 5 / 1.000.000 cơ hội trúng, sau đó phải xin mã mới. 5 phút đủ để mở hộp thư và chép mã; ngắn hơn (1–2 phút) thì email đến chậm là hỏng, dài hơn (30 phút) thì mã bị lộ còn dùng được lâu. Ít hơn 5 lần thì người gõ nhầm 2–3 lần đã bị huỷ mã; nhiều hơn thì tăng cơ hội dò.
- **Gửi lại sau 60 giây.** Chặn bấm liên tục làm tốn hạn mức gửi mail và bị nhà cung cấp email coi là spam. 60 giây vừa đủ để mail đến; chờ lâu hơn thì người dùng mất kiên nhẫn khi mail lạc.
- **3 tài khoản / IP / 24 giờ.** Người thật chỉ cần 1 tài khoản; số 3 chừa chỗ cho bạn cùng phòng, người nhà dùng chung wifi. Bằng 1 thì bạn cùng phòng không đăng ký được; lớn hơn (10) thì dễ tạo hàng loạt tài khoản để lách hạn mức 5 bài Cho, lấy lượt AI dùng thử miễn phí hoặc cày điểm giữa các tài khoản của mình.
- **24 giờ chờ xác thực.** Ai gõ nhầm email của người khác thì email và SĐT đó chỉ bị giữ chỗ tối đa một ngày; một ngày đủ để người thật mở mail.
- **Mã đăng nhập 24 giờ.** Hệ thống chưa có cơ chế gia hạn mã tự động (refresh token). Trước đây để 120 phút thì người dùng bị đá ra giữa lúc chat hay đang điền form đăng bài. 24 giờ phủ một ngày dùng; dài hơn (7 ngày) thì mã bị lộ sống lâu. Rủi ro mã sống lâu đã giảm vì tài khoản bị khoá / cấm bị chặn ở mọi request.
- **Đổi SĐT 1 lần / 30 ngày.** SĐT là thứ người kia dùng để liên lạc khi giao nhận và là căn cứ của báo cáo Sai thông tin liên lạc. Đổi liên tục giúp kẻ xấu né trách nhiệm; một tháng vẫn đủ cho người thay số thật.
- **Không xác thực SMS.** SMS OTP tốn tiền trên từng tin và cần đăng ký thương hiệu gửi tin; đồ án không có ngân sách, nên dùng OTP email miễn phí.

**Nếu bị hỏi**

- *"Sao đăng nhập sai không báo rõ là sai email?"* Để không ai dò được email nào đã có tài khoản. Quên mật khẩu cũng luôn trả cùng một câu vì lý do này.
- *"OTP chỉ sống 5 phút, băm làm gì?"* Ai đọc được cơ sở dữ liệu cũng không lấy được mã để đăng nhập thay người khác.
- *"Đổi mật khẩu mà mã đăng nhập cũ vẫn dùng được?"* Nhóm đã chốt chấp nhận: mã tự hết sau tối đa 24 giờ. Làm mất hiệu lực ngay cần thêm cột lưu thời điểm đổi mật khẩu; nếu tài khoản bị chiếm, Admin khoá và mọi request bị chặn ngay.
- *"Người ta đổi IP bằng 4G thì lách được giới hạn 3 tài khoản?"* Đúng, đây chỉ là rào rẻ tiền nhất. Phía sau còn OTP email, email và SĐT không được trùng, luật không cộng điểm khi cùng một cặp giao dịch lặp lại (UT-08) và màn Giám sát bất thường (cặp giao dịch lặp, nhiều tài khoản đăng ký cùng một IP).
- *"Sao Admin không đăng bài được?"* Admin là người phân xử khiếu nại và duyệt bài; nếu được giao dịch thì sẽ vừa đá bóng vừa thổi còi. Backend chặn cả đăng bài lẫn gửi yêu cầu, không chỉ ẩn nút trên giao diện.

## B. Khoá và cấm tài khoản

Có hai cách chặn một tài khoản (D-09): Admin chủ động khoá có lý do và mở lại được, hoặc hệ thống tự cấm khi sao về 0 và không mở lại.

|  | Khoá | Cấm |
| --- | --- | --- |
| Ai làm | Admin, bắt buộc lý do ≤ 500 ký tự, không tự khoá mình | Hệ thống, ngay khi sao về 0 |
| Lưu ở đâu | users.is\_active = false; trạng thái gốc giữ nguyên; Admin thấy nhãn LOCKED | users.status = BANNED |
| Màn đăng nhập báo | "Tài khoản đã bị khoá. Lý do: …" | "Tài khoản đã bị cấm" |
| Mở lại | Admin mở khoá, bắt buộc lý do | Không có (đã bỏ sao ân hạn) |

Trạng thái tài khoản: Chờ xác thực email (PENDING\_VERIFICATION) → Đang hoạt động (ACTIVE) khi nhập đúng OTP, hoặc bị xoá mềm sau 24 giờ; Đang hoạt động → Bị cấm (BANNED) khi sao về 0. Khoá là cờ riêng, đặt chồng lên trạng thái.

| Mã | Luật |
| --- | --- |
| KC-01 | Admin khoá phải nhập lý do ≤ 500 ký tự, không tự khoá mình. Người bị khoá thấy lý do khi đăng nhập (ví dụ "spam bài", "nghi ngờ lừa đảo"). |
| KC-02 | Tài khoản bị khoá hoặc bị cấm bị chặn ở mọi request, kể cả mã đăng nhập cấp trước đó (lỗi 403). Giao diện nhận lỗi này thì đăng xuất và đưa về màn đăng nhập kèm câu báo. |
| KC-03 | Khi khoá, trong cùng một lần xử lý: huỷ mọi giao dịch đang mở của người đó (trừ giao dịch Đang tranh chấp) và nhả bài; từ chối mọi bài Chờ duyệt, bài đang giữ phí thì nhả phí; ẩn mọi bài Đang hiển thị. Một bước lỗi thì cả lần khoá không xảy ra. |
| KC-04 | Mở khoá: hiện lại đúng các bài đã bị ẩn lúc khoá, trừ bài đã bị kết luận vi phạm hoặc đang đủ ngưỡng báo cáo. Giao dịch đã huỷ và bài đã bị từ chối không khôi phục. |
| KC-05 | Cấm: khi sao về 0, tài khoản chuyển Bị cấm và dọn dẹp giống khoá (KC-03), ẩn thêm mọi bài còn lại; người dùng nhận thông báo; màn đăng nhập báo "Tài khoản đã bị cấm". |
| KC-06 | Tài khoản bị khoá / cấm không gửi yêu cầu được, và người khác không gửi yêu cầu tới bài của họ được. |

**Ví dụ**

- Admin khoá An với lý do "spam bài". An đang cho Bình bộ ly (Đã chốt lịch): giao dịch bị huỷ với lý do "\[Hệ thống\] …", Bình nhận thông báo. Bài bộ ly về Đang hiển thị nhưng bị ẩn cùng các bài khác của An. Bài vợt đang Chờ duyệt giữ 5 Credit bị từ chối, 5 Credit được nhả. An đăng nhập thấy "Tài khoản đã bị khoá. Lý do: spam bài".
- Một tuần sau Admin mở khoá: các bài ẩn lúc khoá hiện lại, trừ bài mà trong tuần đó Admin đã chấp thuận báo cáo. Giao dịch với Bình không tự mở lại.
- Chi còn 1 sao, bị Admin chấp thuận một báo cáo: sao về 0, tài khoản Bị cấm ngay, giao dịch đang mở bị huỷ, mã đăng nhập còn hạn của Chi bị chặn 403 ở request tiếp theo.

**Vì sao thiết kế như vậy**

- **Hai mức thay vì một.** Khoá là biện pháp tạm thời của con người (đang điều tra, nghi ngờ), cần lý do và gỡ được. Cấm là kết quả tự động của 5 lần vi phạm đã được Admin xác nhận, nên không cần ai quyết thêm.
- **Lý do tối đa 500 ký tự.** Đủ cho một đoạn giải thích, không thành bản tường trình; cùng giới hạn với lý do huỷ giao dịch.
- **Không huỷ giao dịch Đang tranh chấp.** Nếu huỷ, người đang bị khiếu nại sẽ thoát được kết luận chỉ nhờ bị khoá. Giữ lại để Admin vẫn kết luận và trừ sao đúng người.
- **Tất cả trong một transaction.** Không có trường hợp "đã khoá nhưng giao dịch còn mở" hay "đã từ chối bài nhưng chưa nhả phí".

**Nếu bị hỏi**

- *"Cấm vĩnh viễn có nặng quá không?"* Phải bị Admin chấp thuận vi phạm 5 lần; mỗi lần đều có thông báo "Bạn còn N sao trước khi tài khoản bị cấm"; giữa các lần còn hồi được sao bằng 10 giao dịch sạch. Nhóm đã bỏ sao ân hạn vì cảnh báo đã đủ rõ.
- *"Khoá xong tại sao không xoá luôn tài khoản?"* Cần giữ lịch sử giao dịch, báo cáo, sổ cái Credit để đối soát; cả hệ thống chỉ xoá mềm.
- *"Mã đăng nhập còn hạn thì sao chặn được?"* Mỗi request có mã đều qua một bộ lọc kiểm lại trạng thái tài khoản trong DB (LockedAccountFilter), không chỉ tin vào mã.

## C. Uy tín: sao, điểm, hạng

Sao đo mức đáng tin và chỉ giảm khi Admin kết luận vi phạm; điểm đo mức hoạt động và quyết định hạng. Sao, điểm, hạng đều không đổi ra tiền hay Credit.

| Hạng | Điểm từ | Bài Trao đổi được phí thường | Ngày hiển thị | Nhanh nhất sau |
| --- | --- | --- | --- | --- |
| Đồng (BRONZE) | 0 | 5 | 30 | — |
| Bạc (SILVER) | 50 | 8 | 30 | 2 ngày |
| Vàng (GOLD) | 150 | 15 | 45 | 5 ngày |
| Kim cương (DIAMOND) | 400 | Không giới hạn | 45 | 14 ngày |

Cột "Nhanh nhất sau" tính theo trần 30 điểm / ngày (UT-07).

| Mã | Luật | Con số |
| --- | --- | --- |
| UT-01 | Mỗi tài khoản bắt đầu với 5 sao, thang 0–5. | 5 |
| UT-02 | Chỉ trừ sao khi Admin chấp thuận một báo cáo / khiếu nại; mỗi lần 1 sao, không âm. Mới bị báo cáo thì chưa trừ. | 1 sao / lần |
| UT-03 | Mỗi lần bị trừ sao, người dùng nhận thông báo "Bạn bị trừ 1 sao. Bạn còn N sao trước khi tài khoản bị cấm." | — |
| UT-04 | Sao về 0 → tài khoản Bị cấm (KC-05). | 0 |
| UT-05 | Hồi 1 sao sau mỗi 10 giao dịch hoàn tất liên tiếp không bị trừ sao, tối đa 5 sao. Bị trừ sao thì chuỗi đếm về 0. | reputation\_clean\_streak\_for\_star = 10 |
| UT-06 | Cộng điểm khi giao dịch Hoàn tất: bài Cho +10 người cho, +2 người nhận; bài Trao đổi +6 mỗi bên. Hạng tính lại ngay. | 10 / 2 / 6 |
| UT-07 | Mỗi tài khoản được cộng tối đa 30 điểm / ngày theo giờ Việt Nam; chạm trần thì chỉ cộng phần còn lại. | daily\_points\_cap = 30 |
| UT-08 | Chống cày điểm: từ giao dịch hoàn tất thứ 3 giữa cùng một cặp người trong 90 ngày thì không cộng điểm (vẫn ghi lịch sử); cặp có hơn 5 giao dịch hiện ở màn Giám sát bất thường. | 90 ngày / thứ 3 / hơn 5 |
| UT-09 | (Đã bỏ) Không có sao ân hạn. | — |
| UT-10 | Mọi thay đổi sao, điểm ghi nhật ký kèm lý do. | — |

**Ví dụ**

- An cho Bình nồi cơm: An +10, Bình +2. An đổi vợt cầu lông với Chi: mỗi người +6.
- Trong một ngày An hoàn tất 4 giao dịch Cho (40 điểm): chỉ nhận 30, giao dịch thứ 4 được 0 điểm nhưng vẫn tính vào chuỗi giao dịch sạch.
- An và Bình hoàn tất giao dịch thứ 3 với nhau trong vòng 90 ngày: không ai được điểm. Giao dịch thứ 6: cặp này hiện ở Giám sát bất thường cho Admin xem.
- Chi đang 5 sao, bị Admin chấp thuận 4 báo cáo qua thời gian, còn 1 sao và nhận "Bạn còn 1 sao trước khi tài khoản bị cấm". Nếu Chi hoàn tất 10 giao dịch không bị trừ sao thì lên lại 2 sao.

**Vì sao là con số này**

- **5 sao, mỗi vi phạm trừ 1.** Thang 5 sao ai cũng quen. Năm lần vi phạm được xác nhận mới cấm: đủ khoan dung cho 1–2 lần hiểu lầm, đủ chặt với người lừa nhiều lần. Thang 3 sao thì hai lần hiểu lầm đã sát cấm; thang 10 sao thì kẻ lừa được 9 lần.
- **Hồi 1 sao sau 10 giao dịch sạch.** Một lần vi phạm phải "chuộc" bằng mười lần làm tốt. Ít hơn (3) thì vi phạm gần như không để lại dấu; nhiều hơn (30) thì người dùng bình thường gần như không bao giờ hồi được.
- **Cho 10 + 2, Trao đổi 6 + 6.** Cả hai loại giao dịch đều tạo tổng 12 điểm, chỉ khác cách chia. Người cho mất món mà không nhận lại gì nên được nhiều nhất; người nhận chỉ được 2 để "xin đồ" không trở thành cách cày điểm. Trao đổi hai bên cùng có lợi nên chia đều.
- **Ngưỡng 50 / 150 / 400.** Quy ra số lần cho đồ: Bạc = 5 lần, Vàng = 15 lần, Kim cương = 40 lần. Khoảng cách tăng dần (gấp 3, rồi gấp gần 3) để hạng cao hiếm và có giá trị.
- **Trần 30 điểm / ngày.** Bằng 3 lần cho đồ trong một ngày, hơn mức người thật thường làm. Nhờ trần này không ai lên Kim cương trong một buổi: tối thiểu 14 ngày.
- **Cặp người 90 ngày, từ lần 3 không điểm, hơn 5 thì cảnh báo.** Hai bạn cùng phòng giao dịch 1–2 lần trong một quý là bình thường nên vẫn được điểm; lần thứ 3 trở đi nhiều khả năng là đổi qua đổi lại để cày. 90 ngày đủ dài để không lách được bằng cách giãn ra vài tuần.

**Nếu bị hỏi**

- *"Sao bị báo cáo mà chưa trừ sao?"* Nếu trừ ngay, ai cũng có thể báo cáo bừa để hạ uy tín người khác. Chỉ trừ khi Admin đã nghe hai phía và kết luận.
- *"Sao không cho hai bên chấm sao cho nhau như Shopee?"* Chấm sao hai chiều dễ bị trả đũa và cày chéo. ShareLoop thay bằng câu hỏi bắt buộc "Món đồ có đúng mô tả không?" khi nhận đồ và hiển thị tỉ lệ đúng mô tả cho chủ bài khi chọn người.
- *"Điểm có đổi ra Credit không?"* Không. Nếu đổi được, điểm trở thành tiền và động cơ cày điểm tăng vọt; điểm chỉ mở quyền lợi hiển thị.

## D. Ví Credit và nạp tiền

Credit là tiền trả phí dịch vụ cho nền tảng, nạp duy nhất qua VNPay; hai người dùng không chuyển Credit hay trả tiền cho nhau qua hệ thống.

| Mã | Luật | Con số |
| --- | --- | --- |
| VI-01 | 1 Credit = 1.000 đ, số nguyên. Credit không đổi ngược ra tiền, không tặng, không chuyển cho người khác. | vnd\_per\_credit = 1000 |
| VI-02 | Số dư khả dụng = số dư − số đang bị giữ. Mọi thao tác có phí kiểm số dư khả dụng ngay lúc bấm. | — |
| VI-03 | Nạp tối thiểu 10 Credit (10.000 đ), số tiền là bội số của 1.000 đ. Tỉ lệ quy đổi chốt lúc tạo đơn. | topup\_min\_credits = 10 |
| VI-04 | Chỉ cộng Credit khi VNPay gọi IPN có chữ ký đúng, số tiền khớp và đơn còn Chờ thanh toán. IPN gửi lại cho đơn đã xử lý thì trả "đã xác nhận", không cộng lần hai. IPN báo thành công nhưng đơn đã quá hạn thì đơn chuyển Hết hạn, không cộng Credit, ghi cảnh báo để đối soát. | — |
| VI-05 | (Đã bỏ) Không có nạp thủ công. Trên máy dev dùng cổng giả lập (MOCK). | — |
| VI-06 | Đơn Chờ thanh toán quá 30 phút thì Hết hạn. Cùng mốc này được gửi sang VNPay làm hạn của trang thanh toán. | payment\_order\_expire\_minutes = 30 |
| VI-07 | Mỗi lần cộng / trừ Credit nằm trong một giao dịch cơ sở dữ liệu có khoá dòng ví và ghi một dòng sổ cái; mỗi đơn nạp chỉ sinh một dòng Nạp. | — |
| VI-08 | Không có chức năng Admin cộng / trừ Credit tay. | — |
| VI-09 | Báo cáo tài chính tách ba sổ: tiền mặt thực thu (đơn thành công), Credit cộng vào, Credit trừ ra theo loại phí; tiền mặt thu phải bằng Credit nạp × 1.000 đ. | — |

Đơn nạp có bốn trạng thái: Chờ thanh toán (PENDING), Thành công (SUCCESS), Thất bại (FAILED, VNPay báo lỗi), Hết hạn (EXPIRED). Sổ cái có các loại dòng: Nạp (TOP\_UP), Phí đăng (POST\_FEE), Phí sửa (EDIT\_FEE), Phí gia hạn (RENEW\_FEE), Phí đẩy bài (BOOST\_FEE), Phí AI (AI\_SEARCH\_FEE, dòng dương là hoàn phí); loại Điều chỉnh (ADMIN\_ADJUST) có trong danh sách nhưng không dùng.

**Ví dụ**

- Bình nạp 50.000 đ: hệ thống tạo đơn Chờ thanh toán 50 Credit, chuyển sang trang VNPay. Bình trả bằng thẻ test NCB, VNPay gọi IPN có chữ ký → đơn Thành công, ví +50, sổ cái thêm một dòng Nạp. VNPay gọi lại IPN lần hai: trả "đã xác nhận", ví không đổi.
- Bình tạo đơn rồi bỏ dở, 30 phút sau đơn thành Hết hạn.
- Bình có 50 Credit, đang giữ 5 Credit cho một bài Trao đổi chờ duyệt: số dư 50, khả dụng 45. Bình muốn đẩy bài 5 Credit thì hệ thống so với 45.

**Vì sao là con số này**

- **1 Credit = 1.000 đ, số nguyên.** Dễ tính nhẩm (5 Credit = 5.000 đ), không có số lẻ, mọi mức phí đều tròn.
- **Nạp tối thiểu 10 Credit.** 10.000 đ đủ cho 2 bài Trao đổi hoặc 5 lượt AI, là một lần nạp có ích. Nhỏ hơn thì sinh nhiều đơn lẻ tẻ, mỗi đơn tốn công đối soát và phí cổng; lớn hơn (50.000 đ) thì người mới ngại nạp lần đầu.
- **Đơn hết hạn sau 30 phút.** Đủ thời gian nhập thẻ, chờ OTP ngân hàng, chuyển ứng dụng; ngắn để không có đơn treo mãi. Hạn này cũng gửi sang VNPay (vnp\_ExpireDate) nên hai bên cùng một mốc; backend cộng thêm 2 phút để bù lệch đồng hồ giữa hai máy chủ.
- **Không đổi ngược, không chuyển.** Nếu Credit chuyển được giữa người dùng, ShareLoop thành ví điện tử (cần giấy phép trung gian thanh toán) và mở đường cho mua bán trá hình, trái với nguyên tắc chỉ cho tặng và trao đổi.

**Nếu bị hỏi**

- *"Sao bỏ nạp thủ công?"* Admin duyệt tay là chỗ cộng Credit không có tiền thật đi kèm, sổ tiền mặt sẽ lệch (VI-09). VNPay sandbox đã chạy trọn vòng trên bản deploy.
- *"Tiền đã trừ bên VNPay mà IPN về sau 30 phút thì sao?"* Đơn chuyển Hết hạn, không cộng Credit, hệ thống ghi log cảnh báo kèm mã giao dịch VNPay để đối soát và hoàn tiền ngoài hệ thống. Đây là trường hợp hiếm, nhóm chọn an toàn sổ sách thay vì cộng cho đơn đã hết hạn.
- *"Hai người cùng bấm trừ phí một lúc có bị âm ví không?"* Không. Mỗi lần đổi Credit đều khoá dòng ví (SELECT … FOR UPDATE) rồi mới kiểm số dư khả dụng, nên hai thao tác chạy lần lượt.
- *"Sao không cho Admin tặng Credit khi demo?"* Mỗi Credit trong hệ thống phải có tiền thật ứng với nó, nếu không báo cáo tài chính không khớp.

## E. Phí dịch vụ

Nền tảng thu phí cho việc hiển thị và dùng AI, không thu phí trên món đồ. Mọi khoản trừ đều có hộp xác nhận ghi rõ số Credit trước khi bấm.

| Mã | Thao tác | Phí | Khi nào trừ | Có hoàn không |
| --- | --- | --- | --- | --- |
| PH-01 | Đăng bài Cho | 0 | — | — |
| PH-02 | Đăng bài Trao đổi trong hạn mức theo hạng | 5 Credit | Giữ khi gửi duyệt, trừ khi Admin duyệt lần đầu | Nhả nếu bị từ chối hoặc gỡ trước khi duyệt |
| PH-02 | Đăng bài Trao đổi vượt hạn mức (không giới hạn trên) | 10 Credit | Như trên | Như trên |
| PH-04 | Sửa bài: lượt đầu sau khi đã được duyệt / từ lượt hai | 0 / 5 Credit | Giữ khi gửi bản sửa, trừ khi bản sửa được duyệt | Nhả nếu bản sửa bị từ chối (không tính lượt) |
| PH-05 | Gia hạn bài (cả bài Cho), +30 ngày | 5 Credit | Ngay | Không |
| PH-06 | Đẩy bài, +3 ngày | 5 Credit | Ngay | Không, kể cả khi bài bị gỡ, ẩn, hết hạn |
| PH-07 | AI trợ lý tìm đồ sau khi hết lượt miễn phí | 2 Credit / lượt | Khi AI chạy xong và có kết quả | AI lỗi thì không trừ |
| PH-08 | Chuyển bài Cho → Trao đổi lúc chọn người (mục K) | 5 Credit | Ngay | Không; Trao đổi → Cho cũng không hoàn phí đã trả |
| PH-09 | Tìm kiếm thường, gửi yêu cầu, chat, chốt lịch, giao nhận, báo cáo, khiếu nại | 0 | — | — |

PH-03: mỗi bài chỉ bị trừ phí đăng một lần. Bài đã được duyệt rồi bị gỡ, hết hạn, vi phạm hay giao dịch không thành thì không hoàn. Lượt AI miễn phí: tài khoản đã từng nạp có 5 lượt / ngày; tài khoản chưa nạp có 1 lượt dùng thử.

**Ví dụ**

- An hạng Đồng đang có 5 bài Trao đổi hiển thị hoặc chờ duyệt. An đăng bài thứ 6: hộp xác nhận ghi 10 Credit. Admin từ chối: 10 Credit được nhả. An sửa rồi nộp lại: giữ lại 10 Credit.
- Bình dùng AI lần đầu khi chưa nạp: miễn phí (lượt dùng thử). Lần hai: hệ thống báo cần 2 Credit. Bình nạp 10 Credit thì từ hôm đó có 5 lượt miễn phí mỗi ngày.

**Vì sao là con số này**

- **Bài Cho miễn phí.** Cho tặng là hành vi nền tảng muốn khuyến khích nhất; thay vì thu phí, chống spam bằng hạn mức 5 bài (BD-05).
- **Bài Trao đổi 5 Credit.** Người đăng Trao đổi nhận lại một món nên trả một khoản nhỏ. 5.000 đ rất nhỏ so với giá trị món đồ đem đổi, nhưng đăng 100 bài rác thì mất 500.000 đ.
- **Vượt hạn mức 10 Credit, không giới hạn trên.** Gấp đôi để người dùng cân nhắc, nhưng không chặn người thật sự cần đăng nhiều (ví dụ dọn nhà). Không cần trần vì mỗi bài đã trả gấp đôi và vẫn qua Admin duyệt.
- **Giữ phí, chưa trừ ngay.** Bài bị từ chối nghĩa là người dùng chưa nhận được dịch vụ hiển thị, nên không mất tiền. Sau lần duyệt đầu thì dịch vụ đã diễn ra, nên gỡ hay hết hạn cũng không hoàn.
- **Sửa lượt đầu miễn phí, từ lượt hai 5 Credit.** Sửa một lần sau khi đăng (sai chính tả, thêm ảnh) là bình thường; mỗi bản sửa đều phải qua Admin duyệt lại nên sửa liên tục thì trả phí bằng phí đăng.
- **Gia hạn 5 Credit cả bài Cho (đã chốt).** Bài Cho đã được hiển thị miễn phí 30 ngày; giữ chỗ lâu hơn là chiếm chỗ hiển thị của người khác. Muốn miễn phí thì đăng bài mới (Admin duyệt lại, nội dung được cập nhật).
- **Đẩy 5 Credit / 3 ngày.** 3 ngày đủ một đợt xem nhiều (ví dụ trọn một cuối tuần); người cần lâu hơn mua thêm gói thứ hai, mỗi đợt tối đa 6 ngày (HT-04).
- **AI 2 Credit, 5 lượt miễn phí cho người đã nạp, 1 lượt thử.** Mỗi lượt AI tốn tiền gọi model thật; 2 Credit rẻ hơn đăng một bài. Lượt miễn phí gắn với "đã từng nạp" để người tạo hàng loạt tài khoản ảo không dùng AI miễn phí mãi; 1 lượt thử để người mới biết tính năng có gì trước khi nạp.

**Nếu bị hỏi**

- *"Sao không thu hoa hồng trên giao dịch như sàn thương mại?"* Hai bên không trả tiền cho nhau nên không có giá trị giao dịch để tính phần trăm. Nền tảng chỉ bán dịch vụ hiển thị và AI.
- *"Giao dịch bị huỷ thì phí đăng có hoàn không?"* Không. Phí đăng trả cho việc bài được duyệt và hiển thị, không phải cho việc đổi thành công; bài về lại Đang hiển thị và vẫn còn thời gian hiển thị.
- *"Nếu người dùng bấm đăng bài hai lần một lúc?"* Mỗi lần giữ phí đều khoá dòng ví nên hai lần bấm chạy lần lượt, không giữ vượt số dư; bài Cho còn có khoá theo người đăng (advisory lock) nên hai bài gửi cùng lúc không vượt được 5 bài.

## F. Bài đăng

Mỗi bài phải qua Admin duyệt trước khi ai đó nhìn thấy; từ lúc chủ bài chọn người thì bài bị khoá nội dung đến khi giao dịch kết thúc.

| Trạng thái | Ý nghĩa | Đi tiếp sang |
| --- | --- | --- |
| Chờ duyệt (PENDING\_REVIEW) | Vừa đăng hoặc vừa gửi bản sửa; không hiện khi tìm kiếm | Đang hiển thị (Admin duyệt) · Bị từ chối (Admin từ chối, hoặc chủ bài bị khoá / cấm) |
| Bị từ chối (REJECTED) | Admin từ chối kèm lý do | Chờ duyệt (chủ bài sửa và nộp lại) |
| Đang hiển thị (APPROVED) | Mọi người thấy và gửi yêu cầu được | Chờ duyệt (gửi bản sửa) · Đang giao dịch (chọn người) · Hết hạn (tác vụ tự động) |
| Đang giao dịch (RESERVED) | Chủ bài đã chọn người; cũng dùng cho món đem đổi của người nhận | Đang hiển thị (giao dịch huỷ) · Đã trao (hoàn tất) |
| Đã trao (TRADED) | Giao dịch hoàn tất | Trạng thái cuối |
| Hết hạn (EXPIRED) | Quá thời gian hiển thị | Trạng thái cuối, không gia hạn (D-06) |

Thông tin trên bài: hình thức Cho tặng (GIVE) / Trao đổi (SWAP); tình trạng Mới / Như mới / Đã dùng, còn tốt / Có lỗi; trạng thái hoạt động (chỉ đồ điện tử) Bình thường / Một phần / Không hoạt động; phí bài Không có / Đang giữ / Đã trừ / Đã nhả; cờ Ẩn và cờ Xoá mềm.

| Mã | Luật | Con số |
| --- | --- | --- |
| BD-01 | Bài có 3–8 ảnh, mỗi ảnh ≤ 5 MB, ảnh do chính người đăng tải lên. | item\_photos\_min = 3, item\_photos\_max = 8, image\_max\_mb = 5 |
| BD-02 | Bắt buộc chọn hình thức, danh mục đang mở, quận đang dùng, tình trạng; nhập tiêu đề (≤ 150 ký tự) và mô tả. Bài Trao đổi bắt buộc ghi món mong muốn (hiển thị công khai). | — |
| BD-03 | Tình trạng Có lỗi: bắt buộc mô tả lỗi và ít nhất 1 ảnh lỗi. Tình trạng khác không được gửi ảnh lỗi. | — |
| BD-04 | Danh mục điện tử bắt buộc khai trạng thái hoạt động; danh mục khác không khai. | — |
| BD-05 | Hạn mức bài Cho: tối đa 5 bài Đang hiển thị hoặc Chờ duyệt, mọi hạng. | max\_active\_give\_items = 5 |
| BD-06 | Bài Trao đổi được phí thường theo hạng (Đồng 5, Bạc 8, Vàng 15, Kim cương không giới hạn); vượt thì vẫn đăng được với phí 10 Credit (PH-02). | max\_active\_items\_bronze / silver / gold / diamond = 5 / 8 / 15 / −1 |
| BD-07 | Tài khoản 0 sao, bị khoá, bị cấm hoặc là Admin thì không đăng bài được. | — |
| BD-08 | Phí sửa theo bảng dưới; hệ thống báo trước số phí trước khi gửi. | edit\_fee = 5 |
| BD-09 | Mọi bản sửa quay lại Chờ duyệt, bài ẩn khỏi tìm kiếm tới khi duyệt xong; các yêu cầu đang chờ của bài giữ nguyên. | — |
| BD-10 | Chỉ sửa / gỡ được khi là chủ bài, còn sao, bài không ở Đang giao dịch / Đã trao / Hết hạn và chưa có yêu cầu nào được chọn. Không đổi hình thức Cho ↔ Trao đổi khi sửa (đổi theo mục K hoặc đăng bài mới). | — |
| BD-11 | Gỡ bài = xoá mềm, nhả phí đang giữ; các yêu cầu Chờ chủ bài chọn bị huỷ ngay với lý do "\[Hệ thống\] Bài đã bị gỡ" và người gửi được báo (D-13). | — |
| BD-12 | Thời gian hiển thị tính từ lần duyệt đầu: 30 ngày; hạng Vàng trở lên 45 ngày. | post\_display\_days = 30, post\_display\_days\_gold = 45 |

| Tình huống khi sửa (BD-08) | Phí |
| --- | --- |
| Bị kiểm tra tự động chặn ngay lúc gửi | Sửa trên form, 0 |
| Chưa duyệt lần nào và đang giữ phí | 0 (phí đang giữ vẫn giữ) |
| Bị từ chối khi chưa từng được duyệt, nộp lại | Giữ lại phí đăng (Trao đổi 5 hoặc 10, Cho 0) |
| Đang có bản sửa chờ duyệt, sửa đè | 0 |
| Đã duyệt, lượt sửa đầu | 0 |
| Đã duyệt, từ lượt sửa thứ hai | 5 |

**Ví dụ**

- An đăng vợt cầu lông (Trao đổi, giữ 5) → Admin duyệt (trừ 5) → An sửa món mong muốn lần 1 (0) → duyệt → sửa lần 2 (giữ 5) → Admin từ chối (nhả 5, không tính lượt) → An sửa lại (giữ 5) → duyệt (trừ 5). Tổng An trả 10 Credit.
- Bình đã gửi yêu cầu xin nồi cơm của An (Chờ chủ bài chọn). An sửa mô tả: bài về Chờ duyệt, yêu cầu của Bình vẫn giữ. An gỡ bài: yêu cầu của Bình bị huỷ ngay và Bình nhận thông báo.
- An muốn đổi bài nồi cơm từ Cho sang Trao đổi qua màn sửa: bị chặn, hệ thống gợi ý đăng bài mới.

**Vì sao là con số này**

- **Tối thiểu 3 ảnh.** Đủ ba góc: tổng thể, mặt sau hoặc cạnh, chi tiết (tem, nhãn, vết dùng). Một ảnh dễ là ảnh mạng và giấu lỗi, dẫn tới khiếu nại Không đúng mô tả. 3 ảnh cũng là đầu vào để AI gợi ý mô tả (mục I).
- **Tối đa 8 ảnh, 5 MB mỗi ảnh.** 8 ảnh đủ cho món phức tạp (laptop, xe đạp) mà trang chi tiết không nặng; 5 MB chứa được ảnh chụp điện thoại thông thường và giới hạn dung lượng lưu trữ.
- **5 bài Cho đang hoạt động.** Bài Cho miễn phí nên cần trần chống spam. 5 bài đủ cho một đợt dọn phòng; tặng xong bài nào thì trống một suất. Nhỏ hơn (2) thì người dọn nhà phải chờ lâu; lớn hơn (20) thì một người có thể lấp trang chủ.
- **5 / 8 / 15 / không giới hạn bài Trao đổi phí thường.** Thưởng người hoạt động tốt: hạng càng cao càng nhiều suất giá thường. Đồng = 5 bằng hạn mức bài Cho để người mới dễ nhớ.
- **30 ngày, Vàng trở lên 45 ngày.** Một tháng đủ cho một món đồ cũ được nhiều người trong quận thấy; lâu hơn thì trang đầy bài đã cho xong mà quên gỡ. 45 ngày là ưu đãi cho người đã chứng minh được tin cậy (ít nhất 150 điểm).
- **Không đổi hình thức khi sửa.** Phí đăng, hạn mức và các yêu cầu đang chờ đều phụ thuộc hình thức. Người đã gửi yêu cầu xin đồ sẽ thấy bài bỗng thành "phải đổi". Đổi hình thức chỉ làm lúc chọn người, có hộp xác nhận riêng (mục K).

**Nếu bị hỏi**

- *"Bài đang có người xin mà chủ bài sửa thành món khác thì sao?"* Mọi bản sửa đều qua Admin duyệt lại theo checklist "danh mục có đúng món", "ảnh là ảnh thật của đúng món". Từ lúc đã chọn người thì không sửa được nữa.
- *"Sao không xoá hẳn bài khi gỡ?"* Bài cần còn để đối chiếu khi có báo cáo, khiếu nại hoặc đối soát phí.

## G. Gia hạn và đẩy bài

Hai cách trả phí để bài được thấy nhiều hơn; cả hai không qua duyệt lại vì nội dung không đổi.

| Mã | Luật | Con số |
| --- | --- | --- |
| HT-01 | Gia hạn: bài của mình, Đang hiển thị, đủ 5 Credit khả dụng. Hạn mới = (hạn cũ nếu còn, không thì bây giờ) + 30 ngày, mọi hạng. Gia hạn lúc nào cũng được. | renew\_fee = 5, +30 ngày |
| HT-02 | Hộp xác nhận báo trước phí và hạn mới (gia hạn: hạn hiển thị mới; đẩy: đẩy tới ngày nào). | — |
| HT-03 | Bài Hết hạn không gia hạn được; muốn hiện lại thì đăng bài mới (D-06). | — |
| HT-04 | Đẩy bài: bài Đang hiển thị, không bị ẩn, thuộc danh mục cho phép đẩy (Admin bật / tắt theo danh mục). Mỗi lần mua là một gói 3 ngày, trừ ngay. Một đợt đẩy liên tục tối đa 6 ngày tính từ lúc bắt đầu đợt; hết đợt phải nghỉ 3 ngày mới mở đợt mới; không vượt hạn hiển thị của bài. | boost\_fee = 5, boost\_days = 3, boost\_max\_days = 6, boost\_cooldown\_days = 3 |
| HT-05 | Khi tìm / duyệt bài: tối đa 3 bài đang đẩy ở đầu mỗi trang, chọn theo vòng xoay (thứ tự cố định theo id, đổi nhóm mỗi 3 phút, mỗi người xem bắt đầu ở một vị trí khác); bài đẩy không lên đầu vẫn có trong phần bài thường, mới nhất trước. Nhãn "Được đẩy" chỉ có trong dữ liệu, không hiện trên giao diện. | boost\_max\_per\_page = 3, boost\_rotate\_seconds = 180 |
| HT-06 | Hết thời gian đẩy thì bài tự về vị trí thường, không cần tác vụ nền (truy vấn so boosted\_until với giờ hiện tại). | — |
| HT-07 | Mỗi người tối đa 3 bài đang đẩy cùng lúc; toàn hệ thống tối đa 30 bài đang đẩy. Mua thêm gói cho bài đang đẩy không chiếm suất mới. Hết suất thì báo thời điểm sớm nhất có suất. | boost\_max\_per\_user = 3, boost\_max\_active = 30 |
| HT-08 | Banner trang chủ hiển thị một nhóm bài đang đẩy (mặc định 5 bài), đổi nhóm mỗi 3 phút theo cùng vòng xoay. | boost\_rotate\_seconds = 180 |
| HT-09 | 24 giờ trước khi bài hết đẩy, chủ bài nhận thông báo kèm mốc còn mua thêm gói được. | boost\_ending\_notice\_hours = 24 |

**Ví dụ**

- Hôm nay 01/11, bài của An hết hạn 20/11. An gia hạn: hạn mới 20/12 (cộng nối tiếp, không mất 19 ngày còn lại).
- Cùng bài đó, An mua đẩy: đẩy tới 04/11; mua thêm một gói thành 07/11 (đợt đủ 6 ngày). Gói tiếp theo sẽ thành 10/11, quá 6 ngày của đợt nên bị chặn; sau 3 ngày nghỉ, từ 10/11 An mở đợt mới được. Nếu An đã có 3 bài đang đẩy, hoặc toàn hệ thống đã đủ 30 bài, hệ thống báo thời điểm sớm nhất có suất.
- Bài của Bình hết hạn 05/11 đã đẩy tới 04/11; mua thêm sẽ thành 07/11, vượt hạn hiển thị nên bị chặn. Bình gia hạn trước rồi đẩy tiếp thì được.

**Vì sao là con số này**

- **Gia hạn +30 ngày cho mọi hạng.** Ưu đãi hạng (45 ngày) đã nằm ở lần duyệt đầu; gia hạn là dịch vụ trả tiền nên mọi người mua cùng một giá, cùng một lượng thời gian.
- **Không gia hạn bài Hết hạn.** Bài đã hết hạn thường có thông tin cũ (món đã cho ngoài đời, ảnh cũ); đăng mới để Admin duyệt lại với nội dung hiện tại.
- **Đợt 6 ngày, nghỉ 3 ngày.** Không ai giữ đầu trang liên tục: tối đa hai gói liền nhau (gần một tuần, phủ một cuối tuần), rồi nghỉ 3 ngày để nhường chỗ cho bài khác. **3 bài / người, 30 bài toàn hệ thống, xoay vòng 3 phút.** Mỗi trang chỉ có 3 chỗ đầu; giới hạn suất giữ cho số bài đẩy vừa với số chỗ mà vòng xoay chia đều được, nên người mua trước không bị người mua sau đẩy xuống và trang không ngập bài đẩy.
- **Không quá hạn hiển thị.** Tránh trả tiền đẩy cho những ngày mà bài đã hết hạn và không còn hiện.

**Nếu bị hỏi**

- *"Trả tiền để lên đầu có bất công với người không trả không?"* Đẩy chỉ đổi thứ tự, không ẩn bài ai; mỗi trang tối đa 3 bài đẩy và các bài đẩy xoay vòng 3 phút một lần nên ai cũng có lượt lên đầu, người mua sau không chen người mua trước; mỗi đợt tối đa 6 ngày rồi nghỉ 3 ngày; Admin tắt đẩy theo danh mục được.
- *"Sao nhãn 'Được đẩy' không hiện?"* Nhóm chọn để nhãn trong dữ liệu (để sắp xếp) nhưng không hiện, tránh trang chủ trông như quảng cáo.

## H. Kiểm duyệt nội dung và danh mục

Bài mới hoặc vừa sửa đi qua hai lớp: kiểm tra tự động (tầng 1) chặn ngay lúc gửi, rồi Admin duyệt 100% theo checklist. Không dùng AI trong duyệt bài (D-03).

| Mã | Luật |
| --- | --- |
| KD-01 | Hai lớp: tầng 1 tự động, rồi Admin duyệt mọi bài qua được tầng 1. Không có bước AI. |
| KD-02 | Tầng 1 chặn: thiếu trường bắt buộc; danh mục bị hạn chế, đang ẩn hoặc quận không hợp lệ; từ khoá cấm mức Chặn; SĐT, email, URL trong tiêu đề, mô tả, món mong muốn (kể cả viết cách quãng bằng dấu cách, chấm, gạch). Bị chặn thì sửa ngay trên form, không mất phí. |
| KD-03 | Từ khoá cấm có hai mức: Chặn (BLOCK) chặn ngay; Gắn cờ (REVIEW) vẫn gửi duyệt và Admin thấy từ bị gắn cờ. Từ chỉ khớp sau khi bỏ dấu thì hạ xuống Gắn cờ. Mỗi từ có danh sách ngoại lệ. |
| KD-04 | Admin duyệt theo checklist do Admin cấu hình (ít nhất 1 câu đang bật); phải tích đạt hết mới duyệt được. Mỗi lần duyệt lưu lại câu hỏi và câu trả lời lúc đó; checklist vừa đổi thì phải tích lại. |
| KD-05 | Từ chối bắt buộc có lý do (chọn lý do mẫu hoặc tự nhập), gửi cho người đăng. |
| KD-06 | Hàng chờ duyệt xếp: người đăng ít sao trước → tài khoản mới hơn trước → bài chờ lâu hơn trước. Không có thông báo nhắc Admin khi bài chờ quá 24 giờ (đã bỏ). |
| KD-07 | Không có danh mục thực phẩm. Các từ khoá cấm về thực phẩm tươi sống, đồ ăn chế biến, thực phẩm chức năng, sữa và đồ ăn dặm cho trẻ, rượu bia, thuốc lá giữ nguyên để chặn người đăng đồ ăn. |
| KD-08 | Chỉ đăng trong danh mục đang mở. Danh mục bị hạn chế (Dược phẩm và thiết bị y tế) không hiện ở bất kỳ chỗ nào người dùng chọn danh mục; Admin vẫn thấy để quản lý. |
| KD-09 | Cấm: vật phẩm cần sang tên pháp lý (xe có biển số, giấy tờ, sim chính chủ, tài khoản…), vật quy đổi ngay ra tiền (vàng, ngoại tệ, thẻ cào, voucher, tiền mã hoá), hàng nhái, hàng giả, vũ khí, chất cấm. |
| KD-10 | Không có chức năng hàng chính hãng (tải chứng từ, nhãn "đã kiểm chứng từ") (D-08). |
| KD-11 | Hai Admin cùng xử lý một bài thì chỉ một người thành công; phí không bị trừ hai lần. |

**Ví dụ**

- An viết mô tả "liên hệ 090.123.4567": tầng 1 gộp dấu chấm giữa các chữ số thành 0901234567 và chặn ngay trên form. Mã sản phẩm, năm 2024 hay giá 1.000.000 không bị nhận nhầm là SĐT.
- Từ "súng" mức Chặn. Bài ghi "quả sung" chỉ khớp sau khi bỏ dấu nên hạ xuống Gắn cờ: bài vẫn vào hàng chờ, Admin thấy từ "súng" được tô và tự quyết.
- Bài ghi "ib zalo": "zalo" là từ Gắn cờ, Admin từ chối theo câu checklist về thông tin liên hệ.

**Vì sao thiết kế như vậy**

- **Admin duyệt 100%.** Đồ cũ có nhiều kiểu lách (ảnh mạng, hàng cấm viết tránh); quy mô đồ án cho phép người xem từng bài. Tầng 1 lọc lỗi rõ ràng trước để Admin không phí công.
- **Bỏ AI duyệt bài.** AI trả kết quả không ổn định, có lúc chậm hoặc lỗi, mà quyết định cuối vẫn do Admin; thêm AI chỉ tăng chi phí và điểm lỗi.
- **Hai mức từ khoá, hạ mức khi chỉ khớp không dấu.** Tiếng Việt bỏ dấu rất dễ trùng nghĩa vô hại (súng / sung). Chặn cứng sẽ làm người dùng thật bực mình; Gắn cờ để người quyết.
- **Thứ tự hàng chờ.** Bài của người ít sao rủi ro cao hơn nên Admin xem trước khi còn tập trung; tài khoản mới được duyệt sớm để có trải nghiệm đầu tiên tốt; còn lại theo thứ tự vào trước.
- **Bỏ nhắc quá 24 giờ.** Nếu hàng chờ dài, thông báo nhắc sẽ ngập chuông và đẩy trôi thông báo quan trọng (khiếu nại, bài bị ẩn do báo cáo). Admin vốn mở hàng chờ hằng ngày.
- **Bỏ thực phẩm và ẩn danh mục hạn chế.** Thực phẩm và thuốc ảnh hưởng sức khoẻ, hạn dùng khó kiểm qua ảnh; nền tảng không chịu được trách nhiệm nên bỏ hẳn. Danh mục hạn chế không đăng được thì cũng không nên cho người dùng thấy và bấm vào.

**Nếu bị hỏi**

- *"Viết SĐT bằng chữ ('không chín một…') vào bài đăng thì sao?"* Tầng 1 của bài đăng bắt SĐT viết cách quãng nhưng chưa đổi chữ thành số (chat thì có). Lớp 2 bắt buộc có câu checklist về thông tin liên hệ, kể cả viết ngầm như "o9o", "không chín" hoặc chèn trên ảnh, nên Admin từ chối. Đây là chỗ có thể làm tốt hơn bằng cách dùng chung bộ chuẩn hoá của chat.
- *"Admin duyệt sai thì sao?"* Còn lớp sau: người dùng báo cáo bài, đủ 3 báo cáo thì bài tự ẩn (BC-02); mọi lần duyệt lưu lại checklist và người duyệt để truy lại.
- *"Nếu nhiều Admin cùng bấm duyệt?"* Trạng thái bài đổi trước rồi mới trừ phí; người thứ hai gặp lỗi chuyển trạng thái không hợp lệ, phí không trừ hai lần.
