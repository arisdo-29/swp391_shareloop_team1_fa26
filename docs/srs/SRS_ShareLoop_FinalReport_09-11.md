**TRƯỜNG ĐẠI HỌC FPT – CƠ SỞ TP. HỒ CHÍ MINH**

Môn SWP391 · Nhóm 1 · Học kỳ Fall 2026

**SOFTWARE REQUIREMENT SPECIFICATION**

**ĐẶC TẢ YÊU CẦU PHẦN MỀM**

**SHARELOOP**

*Nền tảng cho tặng và trao đổi đồ cũ cộng đồng*

| **Mục**       | **Nội dung**                                                                                                                                                                                     |
|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Dự án         | ShareLoop – SWP391, Nhóm 1, học kỳ Fall 2026                                                                                                                                                     |
| Loại phần mềm | Ứng dụng web (React + Spring Boot)                                                                                                                                                               |
| Tài liệu      | Phần III – Software Requirement Specification của Final Report                                                                                                                                   |
| Căn cứ        | Business Rule v12 (02/11/2026, mục A–R) và các quyết định chốt ngày 06/11 và 09/11/2026 (mục 5.5); code nhánh develop, commit b82737d (08/11/2026); các file sơ đồ đính kèm; file mô tả use case |
| Ghi chú nhánh | Nhánh main của repo mới có commit khởi tạo (21/09/2026); mọi đối chiếu code trong tài liệu dùng nhánh develop                                                                                    |
| Ngày          | 07/11/2026, cập nhật 09/11/2026                                                                                                                                                                  |

*TP. Hồ Chí Minh, tháng 11 năm 2026*

**Cách đọc tài liệu**

- **Mã luật (vd TK-06) và mã use case (vd UC-12) đều là liên kết**: bấm vào để nhảy tới dòng luật ở mục 5.1 hoặc dòng use case ở mục 2.2.2. Trong Word giữ Ctrl rồi bấm; muốn quay lại vị trí cũ dùng Alt + ←.

- **Không cần nhảy đi nhảy lại**: mỗi chức năng ở mục 3 có bảng "Luật nghiệp vụ áp dụng" chép sẵn nội dung luật ngay tại chỗ; liên kết chỉ dùng khi cần xem cả nhóm luật hoặc ví dụ.

- **Bảng luật ở mục 5.1 có cột "Dùng ở"** dẫn ngược về các chức năng áp dụng luật đó.

- **Mục lục và danh sách hình bấm được**; số trang cập nhật lại trong Word bằng Ctrl + A rồi F9.

- **Ô tô vàng nhạt** là ghi chú rà soát: chỗ sơ đồ, mô tả use case hoặc BR sai hay lệch với code. Phần còn lại chỉ dùng trắng đen.

- **Khung viền đứt "\[Ảnh chụp màn hình cần chèn\]"** là chỗ giữ cho ảnh giao diện thực tế; danh sách đầy đủ ở Phụ lục B.

> **Ví dụ ghi chú:** Đây là mẫu ô ghi chú. Mọi chỗ tô vàng nhạt trong tài liệu là chỗ file gốc (sơ đồ, mô tả use case, BR) sai hoặc lệch với code và cần nhóm xem lại; phần còn lại của tài liệu chỉ dùng trắng đen.
>
> **\[Ảnh chụp màn hình cần chèn\]** *Đây là mẫu khung giữ chỗ cho ảnh chụp màn hình cần chèn sau.*

**MỤC LỤC**

[**1. Tổng quan sản phẩm**](#tổng-quan-sản-phẩm)

[**2. Yêu cầu người dùng**](#yêu-cầu-người-dùng)

> [2.1 Tác nhân](#tác-nhân)
>
> [2.2 Use case](#use-case)
>
> [2.2.1 Sơ đồ use case](#sơ-đồ-use-case)
>
> [2.2.2 Mô tả use case](#mô-tả-use-case)

[**3. Yêu cầu chức năng**](#yêu-cầu-chức-năng)

> [3.1 Tổng quan chức năng hệ thống](#tổng-quan-chức-năng-hệ-thống)
>
> [3.1.1 Luồng màn hình](#luồng-màn-hình)
>
> [3.1.2 Mô tả màn hình](#mô-tả-màn-hình)
>
> [3.1.3 Phân quyền màn hình](#phân-quyền-màn-hình)
>
> [3.1.4 Chức năng không có màn hình](#chức-năng-không-có-màn-hình)
>
> [3.1.5 Mô hình dữ liệu khái niệm](#mô-hình-dữ-liệu-khái-niệm)
>
> [3.2 Tài khoản và truy cập](#tài-khoản-và-truy-cập)
>
> [3.2.1 Đăng ký tài khoản và xác thực email](#đăng-ký-tài-khoản-và-xác-thực-email)
>
> [3.2.2 Đăng nhập và đăng xuất](#đăng-nhập-và-đăng-xuất)
>
> [3.2.3 Quên mật khẩu và đổi mật khẩu](#quên-mật-khẩu-và-đổi-mật-khẩu)
>
> [3.2.4 Quản lý hồ sơ và đổi số điện thoại](#quản-lý-hồ-sơ-và-đổi-số-điện-thoại)
>
> [3.2.5 Xem sao uy tín, điểm và hạng](#xem-sao-uy-tín-điểm-và-hạng)
>
> [3.3 Ví Credit](#ví-credit)
>
> [3.3.1 Nạp Credit qua VNPay](#nạp-credit-qua-vnpay)
>
> [3.3.2 Xem ví và lịch sử Credit](#xem-ví-và-lịch-sử-credit)
>
> [3.4 Quản lý bài đăng](#quản-lý-bài-đăng)
>
> [3.4.1 Đăng bài Cho hoặc Trao đổi](#đăng-bài-cho-hoặc-trao-đổi)
>
> [3.4.2 Gợi ý mô tả bằng AI](#gợi-ý-mô-tả-bằng-ai)
>
> [3.4.3 Sửa bài và gỡ bài](#sửa-bài-và-gỡ-bài)
>
> [3.4.4 Gia hạn bài](#gia-hạn-bài)
>
> [3.4.5 Đẩy bài](#đẩy-bài)
>
> [3.5 Tìm kiếm và AI trợ lý](#tìm-kiếm-và-ai-trợ-lý)
>
> [3.5.1 Tìm kiếm, lọc và xem chi tiết bài](#tìm-kiếm-lọc-và-xem-chi-tiết-bài)
>
> [3.5.2 AI trợ lý tìm đồ](#ai-trợ-lý-tìm-đồ)
>
> [3.6 Yêu cầu và giao dịch](#yêu-cầu-và-giao-dịch)
>
> [3.6.1 Gửi yêu cầu xin hoặc đề nghị đổi](#gửi-yêu-cầu-xin-hoặc-đề-nghị-đổi)
>
> [3.6.2 Rút yêu cầu](#rút-yêu-cầu)
>
> [3.6.3 Chọn người giao dịch và chuyển hình thức Cho ↔ Trao đổi](#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi)
>
> [3.6.4 Nhắn tin trong giao dịch](#nhắn-tin-trong-giao-dịch)
>
> [3.6.5 Chốt lịch giao nhận và xem thông tin liên lạc](#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc)
>
> [3.6.6 Xác nhận đã giao, đã nhận](#xác-nhận-đã-giao-đã-nhận)
>
> [3.6.7 Huỷ giao dịch và báo chưa nhận / chưa giao](#huỷ-giao-dịch-và-báo-chưa-nhận-chưa-giao)
>
> [3.7 Báo cáo, khiếu nại và thông báo](#báo-cáo-khiếu-nại-và-thông-báo)
>
> [3.7.1 Báo cáo bài, người dùng và khiếu nại giao dịch](#báo-cáo-bài-người-dùng-và-khiếu-nại-giao-dịch)
>
> [3.7.2 Giải trình báo cáo, khiếu nại](#giải-trình-báo-cáo-khiếu-nại)
>
> [3.7.3 Xem thông báo](#xem-thông-báo)
>
> [3.8 Quản trị hệ thống](#quản-trị-hệ-thống)
>
> [3.8.1 Tổng quan quản trị](#tổng-quan-quản-trị)
>
> [3.8.2 Duyệt bài theo checklist](#duyệt-bài-theo-checklist)
>
> [3.8.3 Quản lý danh mục, thuộc tính, khu vực](#quản-lý-danh-mục-thuộc-tính-khu-vực)
>
> [3.8.4 Quản lý từ khoá cấm, checklist, lý do từ chối mẫu](#quản-lý-từ-khoá-cấm-checklist-lý-do-từ-chối-mẫu)
>
> [3.8.5 Cấu hình phí, hạn mức, uy tín và hạng](#cấu-hình-phí-hạn-mức-uy-tín-và-hạng)
>
> [3.8.6 Xử lý báo cáo và khiếu nại](#xử-lý-báo-cáo-và-khiếu-nại)
>
> [3.8.7 Quản lý tài khoản: khoá và mở khoá](#quản-lý-tài-khoản-khoá-và-mở-khoá)
>
> [3.8.8 Quản lý giao dịch](#quản-lý-giao-dịch)
>
> [3.8.9 Giám sát bất thường và nhật ký hoạt động](#giám-sát-bất-thường-và-nhật-ký-hoạt-động)
>
> [3.8.10 Báo cáo tài chính, đơn nạp và tra cứu ví](#báo-cáo-tài-chính-đơn-nạp-và-tra-cứu-ví)
>
> [3.9 Sơ đồ trạng thái](#sơ-đồ-trạng-thái)
>
> [3.10 Tác vụ nền](#tác-vụ-nền)

[**4. Yêu cầu phi chức năng**](#yêu-cầu-phi-chức-năng)

> [4.1 Giao tiếp với hệ thống ngoài](#giao-tiếp-với-hệ-thống-ngoài)
>
> [4.2 Thuộc tính chất lượng](#thuộc-tính-chất-lượng)
>
> [4.2.1 Khả dụng](#khả-dụng)
>
> [4.2.2 Độ tin cậy](#độ-tin-cậy)
>
> [4.2.3 Hiệu năng](#hiệu-năng)
>
> [4.2.4 Bảo mật](#bảo-mật)

[**5. Phụ lục yêu cầu**](#phụ-lục-yêu-cầu)

> [5.1 Luật nghiệp vụ (Business Rules)](#luật-nghiệp-vụ-business-rules)
>
> [5.1.A Tài khoản và đăng nhập](#a-tài-khoản-và-đăng-nhập)
>
> [5.1.B Khoá và cấm tài khoản](#b-khoá-và-cấm-tài-khoản)
>
> [5.1.C Uy tín: sao, điểm, hạng](#c-uy-tín-sao-điểm-hạng)
>
> [5.1.D Ví Credit và nạp tiền](#d-ví-credit-và-nạp-tiền)
>
> [5.1.E Phí dịch vụ](#e-phí-dịch-vụ)
>
> [5.1.F Bài đăng](#f-bài-đăng)
>
> [5.1.G Gia hạn và đẩy bài](#g-gia-hạn-và-đẩy-bài)
>
> [5.1.H Kiểm duyệt nội dung và danh mục](#h-kiểm-duyệt-nội-dung-và-danh-mục)
>
> [5.1.I Tìm kiếm và AI](#i-tìm-kiếm-và-ai)
>
> [5.1.J Yêu cầu và chọn người](#j-yêu-cầu-và-chọn-người)
>
> [5.1.K Chuyển đổi Cho ↔ Trao đổi](#k-chuyển-đổi-cho-trao-đổi)
>
> [5.1.L Chat trong giao dịch](#l-chat-trong-giao-dịch)
>
> [5.1.M Lịch hẹn và giao nhận](#m-lịch-hẹn-và-giao-nhận)
>
> [5.1.N Huỷ giao dịch và sự cố giao nhận](#n-huỷ-giao-dịch-và-sự-cố-giao-nhận)
>
> [5.1.O Báo cáo và khiếu nại](#o-báo-cáo-và-khiếu-nại)
>
> [5.1.P Quản trị](#p-quản-trị)
>
> [5.1.Q Tác vụ tự động](#q-tác-vụ-tự-động)
>
> [5.1.R Thông báo và nhật ký](#r-thông-báo-và-nhật-ký)
>
> [5.2 Bảng cấu hình nghiệp vụ](#bảng-cấu-hình-nghiệp-vụ)
>
> [5.3 Yêu cầu chung](#yêu-cầu-chung)
>
> [5.3.1 Bảo mật](#bảo-mật-1)
>
> [5.3.2 Hiệu năng](#hiệu-năng-1)
>
> [5.3.3 Khả dụng](#khả-dụng-1)
>
> [5.3.4 Tương thích](#tương-thích)
>
> [5.4 Danh sách thông báo của ứng dụng](#danh-sách-thông-báo-của-ứng-dụng)
>
> [5.5 Các quyết định đã chốt](#các-quyết-định-đã-chốt)

[**Phụ lục A. Rà soát các sơ đồ đính kèm**](#phụ-lục-a.-rà-soát-các-sơ-đồ-đính-kèm)

> [A.1 Sơ đồ ngữ cảnh (ShareLoop_Context_Diagram.drawio)](#a.1-sơ-đồ-ngữ-cảnh-shareloop_context_diagram.drawio)
>
> [A.2 Sơ đồ use case (ShareLoop_UseCase.drawio)](#a.2-sơ-đồ-use-case-shareloop_usecase.drawio)
>
> [A.3 Sơ đồ dữ liệu khái niệm cũ (conceptual.drawio) – đã vẽ lại](#a.3-sơ-đồ-dữ-liệu-khái-niệm-cũ-conceptual.drawio-đã-vẽ-lại)
>
> [A.4 Sơ đồ luồng swimlane v12 (bản EN và VI)](#a.4-sơ-đồ-luồng-swimlane-v12-bản-en-và-vi)
>
> [A.5 Sơ đồ kiến trúc (system.drawio)](#a.5-sơ-đồ-kiến-trúc-system.drawio)

[**Phụ lục B. Danh sách ảnh màn hình cần chụp**](#phụ-lục-b.-danh-sách-ảnh-màn-hình-cần-chụp)

[**Phụ lục C. Đối chiếu BR v12 với code**](#phụ-lục-c.-đối-chiếu-br-v12-với-code)

**DANH SÁCH HÌNH**

> [Hình 1. Sơ đồ ngữ cảnh (Context Diagram) – file ShareLoop_Context_Diagram.drawio, chuyển sang trắng đen khi chèn](#FIG_context)
>
> [Hình 2. Sơ đồ use case – file ShareLoop_UseCase.drawio](#FIG_usecase)
>
> [Hình 3. Luồng màn hình Khách và Member](#FIG_flow_member)
>
> [Hình 4. Luồng màn hình Admin](#FIG_flow_admin)
>
> [Hình 5. Mô hình dữ liệu khái niệm (vẽ lại) – file ShareLoop_Conceptual_ERD.drawio](#FIG_erd)
>
> [Hình 6. Luồng xử lý F01 Đăng ký – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw2)
>
> [Hình 7. Luồng xử lý F02 Đăng nhập, quên và đổi mật khẩu – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw3)
>
> [Hình 8. Luồng xử lý F03 Đổi số điện thoại – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw4)
>
> [Hình 9. Luồng xử lý F17 Uy tín – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw18)
>
> [Hình 10. Luồng xử lý F04 Nạp Credit – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw5)
>
> [Hình 11. Luồng xử lý F05 Đăng bài – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw6)
>
> [Hình 12. Luồng xử lý F07 Sửa, gỡ bài – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw8)
>
> [Hình 13. Luồng xử lý F08 Gia hạn, đẩy bài – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw9)
>
> [Hình 14. Luồng xử lý F09 Tìm kiếm, AI – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw10)
>
> [Hình 15. Luồng xử lý F10 Gửi yêu cầu, chọn người – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw11)
>
> [Hình 16. Luồng xử lý F20 Chuyển Cho ↔ Trao đổi – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw21)
>
> [Hình 17. Luồng xử lý F11 Chat – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw12)
>
> [Hình 18. Luồng xử lý F14 Chốt lịch – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw15)
>
> [Hình 19. Luồng xử lý F15 Giao nhận – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw16)
>
> [Hình 20. Luồng xử lý F12 Huỷ giao dịch – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw13)
>
> [Hình 21. Luồng xử lý F16 Báo cáo, khiếu nại – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw17)
>
> [Hình 22. Luồng xử lý F06 Admin duyệt bài – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw7)
>
> [Hình 23. Luồng xử lý F19 Khoá tài khoản – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw20)
>
> [Hình 24. S1 Trạng thái giao dịch (requests.status) – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw22)
>
> [Hình 25. S2 Trạng thái bài đăng (items.status) – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw23)
>
> [Hình 26. S3 Trạng thái báo cáo và đơn nạp – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw24)
>
> [Hình 27. F18 Tác vụ nền – file ShareLoop_Flows_Swimlane_v12_VI.drawio](#FIG_sw19)

**THUẬT NGỮ VÀ VIẾT TẮT**

| **Thuật ngữ**                         | **Nghĩa**                                                                                                                  |
|---------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| **Member**                            | Thành viên đã xác thực email; trong một giao dịch là chủ bài (người cho / người đăng) hoặc người nhận (người gửi yêu cầu). |
| **Cho tặng (GIVE) / Trao đổi (SWAP)** | Hai hình thức bài đăng: cho không, hoặc đổi lấy một bài đã duyệt của người nhận.                                           |
| **Yêu cầu / Giao dịch**               | Cùng một bản ghi requests: là yêu cầu khi còn Chờ chủ bài chọn, là giao dịch từ lúc được chọn.                             |
| **Credit**                            | Đơn vị trả phí dịch vụ cho nền tảng, 1 Credit = 1.000 đ, nạp qua VNPay, không đổi ngược, không chuyển cho người khác.      |
| **Giữ phí (Hold)**                    | Khoá tạm Credit khi gửi bài / bản sửa đi duyệt; trừ thật khi duyệt, nhả khi bị từ chối.                                    |
| **Đẩy bài (Boost)**                   | Trả Credit để bài vào nhóm hiển thị ở đầu trang theo vòng xoay.                                                            |
| **Sao uy tín / Điểm / Hạng**          | Sao (0–5) đo mức đáng tin; điểm đo mức hoạt động; hạng Đồng, Bạc, Vàng, Kim cương quyết định quyền lợi.                    |
| **Khoá / Cấm**                        | Khoá: Admin chủ động, có lý do, mở lại được. Cấm (BANNED): hệ thống tự làm khi sao về 0, không mở lại.                     |
| **Tầng 1 (kiểm tra tự động)**         | Bộ kiểm tra bằng quy tắc: trường bắt buộc, từ khoá cấm, regex thông tin liên hệ.                                           |
| **Từ khoá Chặn / Gắn cờ**             | BLOCK: chặn ngay. REVIEW: cho qua nhưng Admin thấy từ bị gắn cờ.                                                           |
| **Checklist**                         | Bộ câu hỏi Admin phải tích đạt hết để duyệt một bài.                                                                       |
| **IPN**                               | Instant Payment Notification – lời gọi máy chủ – máy chủ của VNPay báo kết quả thanh toán.                                 |
| **OTP**                               | Mã xác thực một lần 6 số gửi qua email.                                                                                    |
| **JWT**                               | Mã đăng nhập do backend cấp, sống 24 giờ.                                                                                  |
| **BR / UC / FR**                      | Business Rule (luật nghiệp vụ) / Use Case / Functional Requirement.                                                        |
| **SRS / SDD / ERD**                   | Software Requirement Specification / Software Design Description / Entity Relationship Diagram.                            |

# 1. Tổng quan sản phẩm

ShareLoop là nền tảng web giúp cộng đồng cho tặng và trao đổi đồ cũ, tìm theo quận. Người dùng đăng bài về món đồ không còn dùng (Cho tặng hoặc Trao đổi); người khác gửi yêu cầu xin hoặc đề nghị đổi bằng một bài đã duyệt của mình; hai bên thống nhất qua khung chat có kiểm soát, chốt lịch rồi tự giao nhận. Hai người dùng không trả tiền cho nhau; Credit (1 Credit = 1.000 đ, nạp qua VNPay) chỉ dùng để trả phí dịch vụ cho nền tảng: đăng bài Trao đổi, sửa bài từ lần thứ hai, gia hạn, đẩy bài và AI trợ lý tìm đồ.

Hệ thống phục vụ ba nhóm người: Khách (xem, tìm bài), Member (đăng bài, gửi yêu cầu, giao dịch, báo cáo) và Admin (duyệt 100% bài đăng theo checklist, xử lý báo cáo và khiếu nại, quản lý danh mục, từ khoá cấm, cấu hình, tài khoản và theo dõi tài chính). An toàn được giữ bằng kiểm tra nội dung tự động (từ khoá cấm, chặn số điện thoại, email, URL), uy tín bằng sao (0–5) và chế tài ở cấp tài khoản.

Sơ đồ ngữ cảnh dưới đây cho thấy ranh giới hệ thống và năm hệ thống ngoài: cổng thanh toán VNPay, dịch vụ AI Gemini (AI trợ lý tìm đồ, gợi ý mô tả), dịch vụ email Brevo (OTP, báo đổi mật khẩu) và kho ảnh Cloudflare R2.

<span id="FIG_context" class="anchor"></span>***Hình 1. Sơ đồ ngữ cảnh (Context Diagram) – file ShareLoop_Context_Diagram.drawio, chuyển sang trắng đen khi chèn***

> **Ghi chú rà soát:** Sơ đồ ngữ cảnh còn 4 chỗ chưa khớp hệ thống (Khách thiếu luồng đăng ký, Brevo không trả "trạng thái gửi", Gemini không nhận ảnh, sơ đồ gốc có màu). Chi tiết ở [Phụ lục A.1](#a.1-sơ-đồ-ngữ-cảnh-shareloop_context_diagram.drawio).

# 2. Yêu cầu người dùng

## 2.1 Tác nhân

| **\#** | **Tác nhân**                     | **Mô tả**                                                                                                                                                                                                                                                               |
|--------|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1      | **Khách**                        | Người chưa đăng nhập. Xem trang chủ, tìm và lọc bài, xem chi tiết bài, đăng ký tài khoản, quên mật khẩu ([TK-01](#BR_TK_01)).                                                                                                                                           |
| 2      | **Member**                       | Thành viên đã xác thực email. Cùng một tài khoản vừa đăng bài (chủ bài) vừa gửi yêu cầu (người nhận); vai trò suy ra theo từng giao dịch. Nạp Credit, đăng / sửa / gia hạn / đẩy bài, gửi yêu cầu, chat, chốt lịch, xác nhận giao nhận, báo cáo, khiếu nại, giải trình. |
| 3      | **Admin**                        | Tài khoản quản trị tạo sẵn, không đăng bài và không gửi yêu cầu ([TK-13](#BR_TK_13)). Duyệt bài, xử lý báo cáo / khiếu nại, khoá / mở khoá tài khoản, quản lý danh mục, khu vực, từ khoá cấm, checklist, cấu hình, xem báo cáo tài chính, nhật ký và cảnh báo.          |
| 4      | **Cổng thanh toán VNPay**        | Nhận tiền thật khi nạp Credit; gọi IPN có chữ ký về backend để báo kết quả. ShareLoop không giữ tiền, không xử lý thẻ.                                                                                                                                                  |
| 5      | **Dịch vụ AI (Gemini)**          | Chọn bài phù hợp nhu cầu từ danh sách ứng viên hệ thống gửi (AI trợ lý tìm đồ) và tạo bản nháp mô tả từ tên món. Không đọc ảnh, không duyệt bài, không kiểm tin nhắn.                                                                                                   |
| 6      | **Dịch vụ email (Brevo)**        | Gửi OTP (đăng ký, quên mật khẩu, đổi số điện thoại) và email báo đổi mật khẩu. Không gửi thông tin giao dịch ([TK-14](#BR_TK_14)).                                                                                                                                      |
| 7      | **Kho ảnh (Cloudflare R2)**      | Lưu ảnh bài đăng, ảnh đại diện, ảnh chat và ảnh bằng chứng qua giao thức tương thích S3.                                                                                                                                                                                |
| 8      | **Bộ hẹn giờ (Cloud Scheduler)** | Gọi định kỳ các tác vụ nền: hết hạn bài, huỷ yêu cầu treo, tự hoàn tất, xoá tài khoản chưa xác thực, hết hạn đơn nạp ([TD-01](#BR_TD_01) … [TD-05](#BR_TD_05)).                                                                                                         |

## 2.2 Use case

### 2.2.1 Sơ đồ use case

<span id="FIG_usecase" class="anchor"></span>***Hình 2. Sơ đồ use case – file ShareLoop_UseCase.drawio***

> **Ghi chú rà soát:** Sơ đồ use case còn sai so với BR v12 và code ở 12 điểm, quan trọng nhất: còn "Check Credit balance", "Restore grace star", "Handle expired posts" (đã bỏ); "Detect contact info" còn nối AI Service (chat không dùng AI); Khách chưa nối "Register account", "Search & filter items"; thiếu các use case Đẩy bài, Huỷ giao dịch, Giải trình, Xem thông báo, Quản lý từ khoá, checklist, giao dịch, nhật ký. Chi tiết ở [Phụ lục A.2](#a.2-sơ-đồ-use-case-shareloop_usecase.drawio).

### 2.2.2 Mô tả use case

Bảng dưới lấy từ file ShareLoop_Use_Case_Descriptions.docx, đã đối chiếu với BR v12 và code. Dòng tô vàng là chỗ file gốc sai hoặc thiếu; cột "Đặc tả" bấm để tới chức năng tương ứng ở mục 3.

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 17%" />
<col style="width: 15%" />
<col style="width: 41%" />
<col style="width: 16%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>ID</strong></th>
<th><strong>Use case</strong></th>
<th><strong>Tác nhân</strong></th>
<th><strong>Mô tả</strong></th>
<th><strong>Đặc tả</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="UC_01" class="anchor"></span><strong>UC-01</strong></td>
<td>Đăng ký và xác thực email</td>
<td>Khách, Dịch vụ email</td>
<td>Khách tạo tài khoản và nhập OTP gửi qua email để kích hoạt tài khoản.</td>
<td><a href="#đăng-ký-tài-khoản-và-xác-thực-email">Đăng ký tài khoản và xác thực email</a></td>
</tr>
<tr class="even">
<td><span id="UC_02" class="anchor"></span><strong>UC-02</strong></td>
<td>Đăng nhập và đăng xuất</td>
<td>Member, Admin</td>
<td><p>Người dùng đăng nhập để dùng chức năng theo quyền và đăng xuất khi kết thúc phiên.</p>
<p>Ghi chú rà soát: File gốc ghi tác nhân "Khách, Admin". Người đăng nhập là người đã có tài khoản (Member hoặc Admin, tác nhân User trên sơ đồ).</p></td>
<td><a href="#đăng-nhập-và-đăng-xuất">Đăng nhập và đăng xuất</a></td>
</tr>
<tr class="odd">
<td><span id="UC_03" class="anchor"></span><strong>UC-03</strong></td>
<td>Quản lý hồ sơ</td>
<td>Member, Admin</td>
<td><p>Xem, cập nhật thông tin cá nhân và đổi số điện thoại sau khi xác thực qua email.</p>
<p>Ghi chú rà soát: File gốc chỉ ghi Member; code cho cả Admin mở trang Hồ sơ (ProfileRoute).</p></td>
<td><a href="#quản-lý-hồ-sơ-và-đổi-số-điện-thoại">Quản lý hồ sơ và đổi số điện thoại</a></td>
</tr>
<tr class="even">
<td><span id="UC_04" class="anchor"></span><strong>UC-04</strong></td>
<td>Quên hoặc đổi mật khẩu</td>
<td>Khách, Member, Admin</td>
<td><p>Đặt lại mật khẩu bằng OTP email hoặc đổi mật khẩu khi đang đăng nhập.</p>
<p>Ghi chú rà soát: File gốc thiếu Admin (Admin cũng đổi mật khẩu được).</p></td>
<td><a href="#quên-mật-khẩu-và-đổi-mật-khẩu">Quên mật khẩu và đổi mật khẩu</a></td>
</tr>
<tr class="odd">
<td><span id="UC_05" class="anchor"></span><strong>UC-05</strong></td>
<td>Xem sao, điểm và hạng</td>
<td>Member</td>
<td>Member xem số sao uy tín, điểm tích lũy và hạng hiện tại của mình.</td>
<td><a href="#xem-sao-uy-tín-điểm-và-hạng">Xem sao uy tín, điểm và hạng</a></td>
</tr>
<tr class="even">
<td><span id="UC_07" class="anchor"></span><strong>UC-07</strong></td>
<td>Xem bài và hồ sơ công khai</td>
<td>Khách, Member</td>
<td>Khách và Member xem các bài đang hiển thị; hồ sơ công khai của người đăng chỉ xem được khi đã đăng nhập.</td>
<td><a href="#tìm-kiếm-lọc-và-xem-chi-tiết-bài">Tìm kiếm, lọc và xem chi tiết bài</a></td>
</tr>
<tr class="odd">
<td><span id="UC_10" class="anchor"></span><strong>UC-10</strong></td>
<td>Nạp Credit</td>
<td>Member, Cổng thanh toán</td>
<td>Member tạo yêu cầu nạp; hệ thống cộng Credit khi thanh toán được xác nhận hợp lệ.</td>
<td><a href="#nạp-credit-qua-vnpay">Nạp Credit qua VNPay</a></td>
</tr>
<tr class="even">
<td><span id="UC_11" class="anchor"></span><strong>UC-11</strong></td>
<td>Xem ví và lịch sử Credit</td>
<td>Member</td>
<td>Xem số dư khả dụng, số đang giữ và lịch sử giao dịch trong ví.</td>
<td><a href="#xem-ví-và-lịch-sử-credit">Xem ví và lịch sử Credit</a></td>
</tr>
<tr class="odd">
<td><span id="UC_12" class="anchor"></span><strong>UC-12</strong></td>
<td>Đăng bài Cho hoặc Trao đổi</td>
<td>Member</td>
<td>Nhập thông tin, tải ảnh và gửi bài để Admin duyệt. Bài Trao đổi bị giữ phí đăng bài theo cấu hình.</td>
<td><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></td>
</tr>
<tr class="even">
<td><span id="UC_13" class="anchor"></span><strong>UC-13</strong></td>
<td>Thanh toán phí bằng Credit</td>
<td>Hệ thống</td>
<td>Kiểm tra số dư và giữ hoặc trừ Credit khi một chức năng có phí được dùng.</td>
<td><p><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></p>
<p><a href="#sửa-bài-và-gỡ-bài">Sửa bài và gỡ bài</a></p>
<p><a href="#gia-hạn-bài">Gia hạn bài</a></p>
<p><a href="#đẩy-bài">Đẩy bài</a></p>
<p><a href="#duyệt-bài-theo-checklist">Duyệt bài theo checklist</a></p></td>
</tr>
<tr class="odd">
<td><span id="UC_14" class="anchor"></span><strong>UC-14</strong></td>
<td>Sửa hoặc gỡ bài</td>
<td>Member</td>
<td>Chủ bài chỉnh sửa nội dung hoặc gỡ bài khi bài và các yêu cầu liên quan còn ở trạng thái cho phép.</td>
<td><a href="#sửa-bài-và-gỡ-bài">Sửa bài và gỡ bài</a></td>
</tr>
<tr class="even">
<td><span id="UC_15" class="anchor"></span><strong>UC-15</strong></td>
<td>Gia hạn bài</td>
<td>Member</td>
<td>Chủ bài trả Credit để kéo dài thời gian hiển thị của bài đủ điều kiện.</td>
<td><a href="#gia-hạn-bài">Gia hạn bài</a></td>
</tr>
<tr class="odd">
<td><span id="UC_16" class="anchor"></span><strong>UC-16</strong></td>
<td>Đẩy bài</td>
<td>Member</td>
<td><p>Chủ bài trả Credit để bài được ưu tiên ở đầu kết quả trong thời gian quy định.</p>
<p>Ghi chú rà soát: Nên bổ sung: bài đẩy được xếp theo vòng xoay đổi nhóm mỗi 3 phút, có giới hạn suất (3 bài / người, 30 bài toàn hệ thống) và thời gian nghỉ giữa hai đợt (code 06/11).</p></td>
<td><a href="#đẩy-bài">Đẩy bài</a></td>
</tr>
<tr class="even">
<td><span id="UC_18" class="anchor"></span><strong>UC-18</strong></td>
<td>Tìm kiếm và lọc bài</td>
<td>Khách, Member</td>
<td><p>Tìm bài đang hiển thị theo từ khóa, danh mục, khu vực, tình trạng và hình thức.</p>
<p>Ghi chú rà soát: File gốc chỉ ghi Khách; Member cũng dùng chức năng này.</p></td>
<td><a href="#tìm-kiếm-lọc-và-xem-chi-tiết-bài">Tìm kiếm, lọc và xem chi tiết bài</a></td>
</tr>
<tr class="odd">
<td><span id="UC_19" class="anchor"></span><strong>UC-19</strong></td>
<td>AI trợ lý tìm đồ</td>
<td>Member, Dịch vụ AI</td>
<td>Member mô tả món cần tìm bằng ngôn ngữ tự nhiên; AI chọn các bài phù hợp trong dữ liệu ShareLoop.</td>
<td><a href="#ai-trợ-lý-tìm-đồ">AI trợ lý tìm đồ</a></td>
</tr>
<tr class="even">
<td><span id="UC_19A" class="anchor"></span><strong>UC-19A</strong></td>
<td>AI gợi ý mô tả bài đăng</td>
<td>Member, Dịch vụ AI</td>
<td><p>Member nhập tên món để nhận bản nháp mô tả và câu hỏi cần bổ sung; kiểm tra, sửa rồi mới dùng.</p>
<p>Ghi chú rà soát: Chưa có trên sơ đồ use case (sơ đồ chỉ có ô "provide suggestions" nối AI Service, không nối Member).</p></td>
<td><a href="#gợi-ý-mô-tả-bằng-ai">Gợi ý mô tả bằng AI</a></td>
</tr>
<tr class="odd">
<td><span id="UC_20" class="anchor"></span><strong>UC-20</strong></td>
<td>Gửi yêu cầu xin hoặc đề nghị đổi</td>
<td>Member</td>
<td>Gửi yêu cầu tới một bài đang nhận yêu cầu; có thể chọn một bài của mình làm món đổi.</td>
<td><a href="#gửi-yêu-cầu-xin-hoặc-đề-nghị-đổi">Gửi yêu cầu xin hoặc đề nghị đổi</a></td>
</tr>
<tr class="even">
<td><span id="UC_21" class="anchor"></span><strong>UC-21</strong></td>
<td>Rút yêu cầu</td>
<td>Member</td>
<td>Người gửi rút yêu cầu trước khi chủ bài chọn người.</td>
<td><a href="#rút-yêu-cầu">Rút yêu cầu</a></td>
</tr>
<tr class="odd">
<td><span id="UC_22" class="anchor"></span><strong>UC-22</strong></td>
<td>Chọn người giao dịch</td>
<td>Member (chủ bài)</td>
<td>Chủ bài chọn một yêu cầu để bắt đầu giao dịch; hệ thống xử lý các yêu cầu còn lại.</td>
<td><a href="#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi">Chọn người giao dịch và chuyển hình thức Cho ↔︎ Trao đổi</a></td>
</tr>
<tr class="even">
<td><span id="UC_23" class="anchor"></span><strong>UC-23</strong></td>
<td>Chuyển hình thức Cho và Trao đổi</td>
<td>Member (chủ bài)</td>
<td>Chủ bài xác nhận chuyển hình thức khi chọn người, hoặc khi người nhận đề nghị đưa một món vào đổi trong chat (bài Cho, trước khi chốt lịch). Cho → Trao đổi trừ 5 Credit; Trao đổi → Cho không hoàn phí.</td>
<td><a href="#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi">Chọn người giao dịch và chuyển hình thức Cho ↔︎ Trao đổi</a></td>
</tr>
<tr class="odd">
<td><span id="UC_24" class="anchor"></span><strong>UC-24</strong></td>
<td>Nhắn tin trong giao dịch</td>
<td>Member</td>
<td>Hai bên trao đổi trong cuộc trò chuyện gắn với giao dịch; tin chứa liên hệ bị chặn trước khi chốt lịch.</td>
<td><a href="#nhắn-tin-trong-giao-dịch">Nhắn tin trong giao dịch</a></td>
</tr>
<tr class="even">
<td><span id="UC_25" class="anchor"></span><strong>UC-25</strong></td>
<td>Chia sẻ thẻ bài vào chat</td>
<td>Member</td>
<td>Chia sẻ một bài Đang hiển thị dưới dạng thẻ trong cuộc trò chuyện.</td>
<td><a href="#nhắn-tin-trong-giao-dịch">Nhắn tin trong giao dịch</a></td>
</tr>
<tr class="odd">
<td><span id="UC_26" class="anchor"></span><strong>UC-26</strong></td>
<td>Chốt lịch giao nhận</td>
<td>Member</td>
<td>Một bên đề xuất cách thức, thời gian, địa điểm; lịch được chốt khi cả hai xác nhận.</td>
<td><a href="#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc">Chốt lịch giao nhận và xem thông tin liên lạc</a></td>
</tr>
<tr class="even">
<td><span id="UC_27" class="anchor"></span><strong>UC-27</strong></td>
<td>Xác nhận đã giao hoặc đã nhận</td>
<td>Member</td>
<td>Mỗi bên xác nhận việc giao nhận; đủ điều kiện thì giao dịch hoàn tất.</td>
<td><a href="#xác-nhận-đã-giao-đã-nhận">Xác nhận đã giao, đã nhận</a></td>
</tr>
<tr class="odd">
<td><span id="UC_28" class="anchor"></span><strong>UC-28</strong></td>
<td>Đính kèm ảnh minh chứng</td>
<td>Member</td>
<td>Tải ảnh hoặc video làm minh chứng giao nhận hoặc khiếu nại.</td>
<td><p><a href="#xác-nhận-đã-giao-đã-nhận">Xác nhận đã giao, đã nhận</a></p>
<p><a href="#báo-cáo-bài-người-dùng-và-khiếu-nại-giao-dịch">Báo cáo bài, người dùng và khiếu nại giao dịch</a></p></td>
</tr>
<tr class="even">
<td><span id="UC_29" class="anchor"></span><strong>UC-29</strong></td>
<td>Báo cáo bài, người hoặc khiếu nại giao dịch</td>
<td>Member</td>
<td>Gửi lý do và minh chứng để báo cáo vi phạm hoặc khiếu nại một giao dịch.</td>
<td><a href="#báo-cáo-bài-người-dùng-và-khiếu-nại-giao-dịch">Báo cáo bài, người dùng và khiếu nại giao dịch</a></td>
</tr>
<tr class="odd">
<td><span id="UC_30" class="anchor"></span><strong>UC-30</strong></td>
<td>Huỷ giao dịch</td>
<td>Member</td>
<td><p>Người tham gia huỷ giao dịch ở trạng thái được phép, có lý do.</p>
<p>Ghi chú rà soát: Chưa có trên sơ đồ use case.</p></td>
<td><a href="#huỷ-giao-dịch-và-báo-chưa-nhận-chưa-giao">Huỷ giao dịch và báo chưa nhận / chưa giao</a></td>
</tr>
<tr class="even">
<td><span id="UC_31" class="anchor"></span><strong>UC-31</strong></td>
<td>Báo chưa nhận hoặc chưa giao được</td>
<td>Member</td>
<td><p>Báo sự cố giao nhận khi bên còn lại đã xác nhận; giao dịch chuyển tranh chấp.</p>
<p>Ghi chú rà soát: Chưa có trên sơ đồ use case.</p></td>
<td><a href="#huỷ-giao-dịch-và-báo-chưa-nhận-chưa-giao">Huỷ giao dịch và báo chưa nhận / chưa giao</a></td>
</tr>
<tr class="odd">
<td><span id="UC_32" class="anchor"></span><strong>UC-32</strong></td>
<td>Giải trình khiếu nại</td>
<td>Member</td>
<td><p>Bên bị báo cáo / khiếu nại gửi giải trình và minh chứng để Admin xem xét.</p>
<p>Ghi chú rà soát: Chưa có trên sơ đồ use case.</p></td>
<td><a href="#giải-trình-báo-cáo-khiếu-nại">Giải trình báo cáo, khiếu nại</a></td>
</tr>
<tr class="even">
<td><span id="UC_33" class="anchor"></span><strong>UC-33</strong></td>
<td>Xem thông báo</td>
<td>Member, Admin</td>
<td><p>Xem thông báo về bài đăng, yêu cầu, giao dịch, ví và kết quả xử lý.</p>
<p>Ghi chú rà soát: File gốc chỉ ghi Member; Admin cũng nhận thông báo (bài tự ẩn, cảnh báo vi phạm chat). Chưa có trên sơ đồ use case.</p></td>
<td><a href="#xem-thông-báo">Xem thông báo</a></td>
</tr>
<tr class="odd">
<td><span id="UC_40" class="anchor"></span><strong>UC-40</strong></td>
<td>Kiểm duyệt bài đăng</td>
<td>Admin</td>
<td>Kiểm tra bài trong hàng chờ theo checklist; duyệt hoặc từ chối kèm lý do.</td>
<td><a href="#duyệt-bài-theo-checklist">Duyệt bài theo checklist</a></td>
</tr>
<tr class="even">
<td><span id="UC_42" class="anchor"></span><strong>UC-42</strong></td>
<td>Quản lý checklist duyệt bài</td>
<td>Admin</td>
<td><p>Cập nhật tiêu chí dùng khi kiểm duyệt bài.</p>
<p>Ghi chú rà soát: Chưa có trên sơ đồ use case.</p></td>
<td><a href="#quản-lý-từ-khoá-cấm-checklist-lý-do-từ-chối-mẫu">Quản lý từ khoá cấm, checklist, lý do từ chối mẫu</a></td>
</tr>
<tr class="odd">
<td><span id="UC_43" class="anchor"></span><strong>UC-43</strong></td>
<td>Quản lý danh mục và thuộc tính</td>
<td>Admin</td>
<td>Quản lý danh mục và thuộc tính cần nhập cho từng danh mục.</td>
<td><a href="#quản-lý-danh-mục-thuộc-tính-khu-vực">Quản lý danh mục, thuộc tính, khu vực</a></td>
</tr>
<tr class="even">
<td><span id="UC_44" class="anchor"></span><strong>UC-44</strong></td>
<td>Quản lý từ khóa cấm</td>
<td>Admin</td>
<td><p>Cập nhật danh sách từ khoá và quy tắc phát hiện nội dung vi phạm.</p>
<p>Ghi chú rà soát: Chưa có trên sơ đồ use case.</p></td>
<td><a href="#quản-lý-từ-khoá-cấm-checklist-lý-do-từ-chối-mẫu">Quản lý từ khoá cấm, checklist, lý do từ chối mẫu</a></td>
</tr>
<tr class="odd">
<td><span id="UC_45" class="anchor"></span><strong>UC-45</strong></td>
<td>Quản lý khu vực</td>
<td>Admin</td>
<td>Quản lý khu vực người dùng chọn khi đăng và tìm bài.</td>
<td><a href="#quản-lý-danh-mục-thuộc-tính-khu-vực">Quản lý danh mục, thuộc tính, khu vực</a></td>
</tr>
<tr class="even">
<td><span id="UC_46" class="anchor"></span><strong>UC-46</strong></td>
<td>Cấu hình phí, hạn mức và thời hạn</td>
<td>Admin</td>
<td>Cập nhật giá trị nghiệp vụ như phí Credit, hạn mức đăng bài, thời gian hiển thị.</td>
<td><a href="#cấu-hình-phí-hạn-mức-uy-tín-và-hạng">Cấu hình phí, hạn mức, uy tín và hạng</a></td>
</tr>
<tr class="odd">
<td><span id="UC_47" class="anchor"></span><strong>UC-47</strong></td>
<td>Xử lý báo cáo và khiếu nại</td>
<td>Admin</td>
<td>Xem nội dung, minh chứng và giải trình để kết luận và áp dụng biện pháp xử lý.</td>
<td><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></td>
</tr>
<tr class="even">
<td><span id="UC_48" class="anchor"></span><strong>UC-48</strong></td>
<td>Trừ sao uy tín</td>
<td>Hệ thống</td>
<td><p>Trừ sao khi kết luận chấp thuận vi phạm.</p>
<p>Ghi chú rà soát: Chỉ trừ khi Admin chấp thuận, nên trên sơ đồ phải là «extend» của Resolve disputes, không phải «include».</p></td>
<td><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></td>
</tr>
<tr class="odd">
<td><span id="UC_49" class="anchor"></span><strong>UC-49</strong></td>
<td>Ẩn bài của người vi phạm</td>
<td>Hệ thống</td>
<td>Ẩn bài theo quy tắc xử lý vi phạm hoặc khi tài khoản không còn đủ điều kiện hoạt động.</td>
<td><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></td>
</tr>
<tr class="even">
<td><span id="UC_50" class="anchor"></span><strong>UC-50</strong></td>
<td>Khóa hoặc mở khóa tài khoản</td>
<td>Admin</td>
<td>Khoá tài khoản vi phạm hoặc mở khoá sau khi xem xét; xử lý bài và giao dịch liên quan.</td>
<td><a href="#quản-lý-tài-khoản-khoá-và-mở-khoá">Quản lý tài khoản: khoá và mở khoá</a></td>
</tr>
<tr class="odd">
<td><strong>UC-51</strong></td>
<td>Cấp sao ân hạn</td>
<td>Admin</td>
<td><p>Admin cấp sao ân hạn cho tài khoản đủ điều kiện và ghi lý do.</p>
<p>Ghi chú rà soát: Đã bỏ theo D-09 và UT-09; API /admin/users/{id}/grace-star đã gỡ. Không đưa vào phạm vi.</p></td>
<td>—</td>
</tr>
<tr class="even">
<td><span id="UC_52" class="anchor"></span><strong>UC-52</strong></td>
<td>Giám sát bất thường</td>
<td>Admin</td>
<td>Xem các dấu hiệu hoạt động bất thường (cặp giao dịch lặp) để quyết định có kiểm tra thêm.</td>
<td><p><a href="#tổng-quan-quản-trị">Tổng quan quản trị</a></p>
<p><a href="#giám-sát-bất-thường-và-nhật-ký-hoạt-động">Giám sát bất thường và nhật ký hoạt động</a></p></td>
</tr>
<tr class="odd">
<td><span id="UC_53" class="anchor"></span><strong>UC-53</strong></td>
<td>Xem báo cáo tài chính</td>
<td>Admin</td>
<td>Xem số liệu nạp Credit, sử dụng Credit và các khoản phí.</td>
<td><p><a href="#tổng-quan-quản-trị">Tổng quan quản trị</a></p>
<p><a href="#báo-cáo-tài-chính-đơn-nạp-và-tra-cứu-ví">Báo cáo tài chính, đơn nạp và tra cứu ví</a></p></td>
</tr>
<tr class="even">
<td><strong>UC-56</strong></td>
<td>Duyệt yêu cầu nạp thủ công</td>
<td>Admin</td>
<td><p>Admin đối chiếu yêu cầu nạp thủ công và duyệt hoặc từ chối.</p>
<p>Đã bỏ theo D-10 và VI-05 (chỉ nạp qua VNPay); code luồng nạp thủ công được gỡ theo quyết định ngày 09/11/2026 (D-25). Không đưa vào phạm vi.</p></td>
<td>—</td>
</tr>
<tr class="odd">
<td><span id="UC_57" class="anchor"></span><strong>UC-57</strong></td>
<td>Quản lý giao dịch</td>
<td>Admin</td>
<td><p>Tra cứu giao dịch và huỷ giao dịch bị kẹt hoặc cần can thiệp.</p>
<p>Ghi chú rà soát: Chưa có trên sơ đồ use case.</p></td>
<td><a href="#quản-lý-giao-dịch">Quản lý giao dịch</a></td>
</tr>
<tr class="even">
<td><span id="UC_58" class="anchor"></span><strong>UC-58</strong></td>
<td>Xem nhật ký hoạt động</td>
<td>Admin</td>
<td><p>Tra cứu hoạt động quản trị và sự kiện quan trọng đã ghi nhận.</p>
<p>Ghi chú rà soát: Chưa có trên sơ đồ use case.</p></td>
<td><a href="#giám-sát-bất-thường-và-nhật-ký-hoạt-động">Giám sát bất thường và nhật ký hoạt động</a></td>
</tr>
<tr class="odd">
<td><span id="UC_59" class="anchor"></span><strong>UC-59</strong></td>
<td>Tra cứu tài khoản và lịch sử Credit</td>
<td>Admin</td>
<td><p>Xem thông tin tài khoản và lịch sử Credit để kiểm tra, hỗ trợ, xử lý vụ việc.</p>
<p>Ghi chú rà soát: Chưa có trên sơ đồ use case.</p></td>
<td><a href="#báo-cáo-tài-chính-đơn-nạp-và-tra-cứu-ví">Báo cáo tài chính, đơn nạp và tra cứu ví</a></td>
</tr>
<tr class="even">
<td><span id="UC_60" class="anchor"></span><strong>UC-60</strong></td>
<td>Sàng lọc nội dung bài</td>
<td>Hệ thống</td>
<td>Kiểm tra nội dung bài trước khi nhận đăng / sửa, chặn hoặc gắn cờ để Admin xem.</td>
<td><p><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></p>
<p><a href="#sửa-bài-và-gỡ-bài">Sửa bài và gỡ bài</a></p></td>
</tr>
<tr class="odd">
<td><span id="UC_61" class="anchor"></span><strong>UC-61</strong></td>
<td>Lọc thông tin liên hệ trong tin nhắn</td>
<td>Hệ thống</td>
<td>Chặn chia sẻ thông tin liên hệ trong tin nhắn trước khi chốt lịch, chỉ bằng quy tắc (không AI).</td>
<td><a href="#nhắn-tin-trong-giao-dịch">Nhắn tin trong giao dịch</a></td>
</tr>
<tr class="even">
<td><span id="UC_62" class="anchor"></span><strong>UC-62</strong></td>
<td>Hiện thông tin liên lạc</td>
<td>Hệ thống</td>
<td>Cho hai bên xem thông tin liên lạc trong ứng dụng sau khi xác nhận lịch giao nhận.</td>
<td><a href="#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc">Chốt lịch giao nhận và xem thông tin liên lạc</a></td>
</tr>
<tr class="odd">
<td><strong>UC-63</strong></td>
<td>Xử lý tự động theo lịch</td>
<td>Bộ hẹn giờ</td>
<td><p>Tự xử lý bài, yêu cầu, giao dịch, tài khoản đến hạn theo các quy tắc thời gian.</p>
<p>Ghi chú rà soát: Thực tế tác vụ được Cloud Scheduler gọi qua endpoint /internal/jobs (Cloud Run Jobs), nên tác nhân nên ghi rõ "Cloud Scheduler".</p></td>
<td>—</td>
</tr>
<tr class="even">
<td><span id="UC_64" class="anchor"></span><strong>UC-64</strong></td>
<td>Xác thực OTP qua email</td>
<td>Hệ thống, Dịch vụ email</td>
<td>Gửi và kiểm tra OTP khi đăng ký, đặt lại mật khẩu hoặc đổi số điện thoại.</td>
<td><p><a href="#đăng-ký-tài-khoản-và-xác-thực-email">Đăng ký tài khoản và xác thực email</a></p>
<p><a href="#quên-mật-khẩu-và-đổi-mật-khẩu">Quên mật khẩu và đổi mật khẩu</a></p>
<p><a href="#quản-lý-hồ-sơ-và-đổi-số-điện-thoại">Quản lý hồ sơ và đổi số điện thoại</a></p></td>
</tr>
</tbody>
</table>

# 3. Yêu cầu chức năng

## 3.1 Tổng quan chức năng hệ thống

### 3.1.1 Luồng màn hình

Hai sơ đồ dưới được vẽ mới theo các đường dẫn trong frontend/src/app/router.tsx và menu quản trị (AdminLayout.tsx). File nguồn: ShareLoop_ScreenFlow_Member.drawio, ShareLoop_ScreenFlow_Admin.drawio.

<span id="FIG_flow_member" class="anchor"></span>***Hình 3. Luồng màn hình Khách và Member***

<span id="FIG_flow_admin" class="anchor"></span>***Hình 4. Luồng màn hình Admin***

### 3.1.2 Mô tả màn hình

| **\#** | **Nhóm chức năng** | **Màn hình**                                              | **Mô tả**                                                                                  |
|--------|--------------------|-----------------------------------------------------------|--------------------------------------------------------------------------------------------|
| 1      | Công khai          | **Trang chủ**                                             | Banner bài đang đẩy (đổi nhóm mỗi 3 phút), bài mới, lối vào tìm đồ.                        |
| 2      | Công khai          | **Tìm đồ**                                                | Danh sách bài có bộ lọc danh mục, khu vực, tình trạng, hình thức; tìm không phân biệt dấu. |
| 3      | Công khai          | **Chi tiết bài đăng**                                     | Ảnh, mô tả, tình trạng, món mong muốn, người đăng; nút Gửi yêu cầu, Báo cáo.               |
| 4      | Công khai          | **Giới thiệu, Hướng dẫn, An toàn**                        | Trang nội dung tĩnh về cách dùng và quy tắc an toàn.                                       |
| 5      | Tài khoản          | **Đăng ký**                                               | Form email, mật khẩu, họ tên, số điện thoại.                                               |
| 6      | Tài khoản          | **Xác thực email**                                        | Nhập OTP 6 số, gửi lại mã.                                                                 |
| 7      | Tài khoản          | **Đăng nhập**                                             | Form email, mật khẩu; lối vào Quên mật khẩu, Đăng ký.                                      |
| 8      | Tài khoản          | **Quên / đặt lại mật khẩu**                               | Nhập email, sau đó OTP và mật khẩu mới.                                                    |
| 9      | Tài khoản          | **Hồ sơ cá nhân**                                         | Họ tên, khu vực, ảnh đại diện, uy tín; đổi số điện thoại (hộp thoại OTP).                  |
| 10     | Tài khoản          | **Đổi mật khẩu**                                          | Mật khẩu hiện tại và mật khẩu mới.                                                         |
| 11     | Tài khoản          | **Hồ sơ công khai**                                       | Sao, hạng, tỉ lệ đúng mô tả và bài đang hiển thị của một người.                            |
| 12     | Bài đăng           | **Đăng đồ**                                               | Form tạo / sửa bài, tải ảnh, gợi ý mô tả bằng AI, hộp thoại xác nhận phí.                  |
| 13     | Bài đăng           | **Hoạt động của tôi**                                     | Bài của tôi (sửa, gỡ, gia hạn, đẩy, xem người xin, chọn người) và yêu cầu đã gửi (rút).    |
| 14     | Tìm kiếm           | **Trợ lý AI**                                             | Nhập nhu cầu bằng câu tự nhiên, xem lượt miễn phí còn lại và kết quả.                      |
| 15     | Giao dịch          | **Tin nhắn**                                              | Danh sách cuộc trò chuyện theo giao dịch và khung chat có thẻ bài ghim.                    |
| 16     | Giao dịch          | **Tiến trình giao dịch**                                  | Lịch hẹn, xác nhận lịch, liên lạc đối tác, xác nhận giao nhận, huỷ, báo sự cố, khiếu nại.  |
| 17     | Báo cáo            | **Gửi giải trình**                                        | Nội dung, hạn và form giải trình cho báo cáo / khiếu nại.                                  |
| 18     | Ví                 | **Ví Credit**                                             | Số dư, số đang giữ, lịch sử; nạp Credit (chuyển sang VNPay).                               |
| 19     | Chung              | **Thông báo**                                             | Menu chuông: danh sách thông báo, số chưa đọc.                                             |
| 20     | Quản trị           | **Tổng quan**                                             | Việc cần làm và số liệu chính.                                                             |
| 21     | Quản trị           | **Duyệt bài**                                             | Hàng chờ bài và màn chi tiết duyệt theo checklist.                                         |
| 22     | Quản trị           | **Danh mục; Khu vực**                                     | Quản lý danh mục, thuộc tính động, cây khu vực.                                            |
| 23     | Quản trị           | **Checklist duyệt bài; Từ khóa cấm**                      | Quản lý câu hỏi checklist, lý do từ chối mẫu, từ khoá cấm (thử nhanh).                     |
| 24     | Quản trị           | **Tài khoản; Uy tín và hạng**                             | Khoá / mở khoá tài khoản; cấu hình điểm và ngưỡng hạng.                                    |
| 25     | Quản trị           | **Giao dịch; Khiếu nại và báo cáo**                       | Tra cứu, huỷ giao dịch kẹt; yêu cầu giải trình và kết luận hồ sơ.                          |
| 26     | Quản trị           | **Báo cáo tài chính; Đơn nạp Credit; Tra cứu người dùng** | Ba sổ tài chính, xuất CSV; danh sách đơn nạp; ví và lịch sử Credit từng người.             |
| 27     | Quản trị           | **Cấu hình phí; Nhật ký hoạt động; Cảnh báo bất thường**  | Sửa cấu hình nghiệp vụ; tra cứu nhật ký; xem cặp giao dịch lặp.                            |

### 3.1.3 Phân quyền màn hình

X: được dùng. Admin đăng nhập bị chuyển thẳng vào khu quản trị nên không dùng các màn của Member (trừ Hồ sơ và Đổi mật khẩu).

| **Màn hình**                                                      | **Khách** | **Member** | **Admin** |
|-------------------------------------------------------------------|-----------|------------|-----------|
| Trang chủ, Tìm đồ, Chi tiết bài, Giới thiệu / Hướng dẫn / An toàn | X         | X          |           |
| Đăng ký, Xác thực email, Quên / đặt lại mật khẩu                  | X         |            |           |
| Đăng nhập                                                         | X         |            |           |
| Hồ sơ cá nhân, Đổi mật khẩu                                       |           | X          | X         |
| Hồ sơ công khai                                                   |           | X          |           |
| Đăng đồ, Hoạt động của tôi, Trợ lý AI                             |           | X          |           |
| Tin nhắn, Tiến trình giao dịch, Gửi giải trình                    |           | X          |           |
| Ví Credit                                                         |           | X          |           |
| Thông báo (chuông)                                                |           | X          | X         |
| Mọi màn /admin/\*                                                 |           |            | X         |

### 3.1.4 Chức năng không có màn hình

| **\#** | **Nhóm**   | **Chức năng hệ thống**            | **Mô tả**                                                                                                                  |
|--------|------------|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| 1      | Thanh toán | **Nhận IPN VNPay**                | Kiểm chữ ký, số tiền, hạn đơn; cộng Credit và ghi sổ trong một transaction; chống cộng hai lần.                            |
| 2      | Nội dung   | **Kiểm tra tầng 1 bài đăng**      | Chuẩn hoá văn bản, so từ khoá cấm (Chặn / Gắn cờ, ngoại lệ, khớp không dấu chỉ Gắn cờ), regex số điện thoại / email / URL. |
| 3      | Nội dung   | **Lọc liên hệ trong chat**        | Đổi số viết bằng chữ thành số, dò SĐT, email, URL, mạng xã hội trước khi chốt lịch; đếm vi phạm, cảnh báo Admin khi đủ 3.  |
| 4      | Tài khoản  | **Gửi OTP qua email**             | Sinh OTP, lưu băm, gửi qua Brevo sau khi lưu dữ liệu.                                                                      |
| 5      | Tài khoản  | **Chặn tài khoản bị khoá / cấm**  | Bộ lọc kiểm trạng thái tài khoản ở mỗi request có mã đăng nhập (LockedAccountFilter).                                      |
| 6      | Tác vụ nền | **Hết hạn bài**                   | Mỗi giờ: bài Đang hiển thị quá hạn → Hết hạn ([TD-01](#BR_TD_01)).                                                         |
| 7      | Tác vụ nền | **Xử lý yêu cầu, giao dịch treo** | Huỷ yêu cầu chờ quá 3 ngày, giao dịch chưa chốt lịch quá 10 ngày; tự hoàn tất sau 3 ngày ([TD-02](#BR_TD_02)).             |
| 8      | Tác vụ nền | **Xoá tài khoản chưa xác thực**   | Xoá mềm tài khoản chờ xác thực quá 24 giờ ([TD-03](#BR_TD_03)).                                                            |
| 9      | Tác vụ nền | **Hết hạn đơn nạp**               | 5 phút / lần: đơn chờ thanh toán quá 30 phút → Hết hạn ([TD-05](#BR_TD_05)).                                               |
| 10     | Tác vụ nền | **Báo sắp hết đẩy bài**           | Gửi thông báo cho chủ bài 24 giờ trước khi hết đẩy ([HT-09](#BR_HT_09)).                                                   |
| 11     | Thông báo  | **Tạo thông báo trong app**       | Tạo thông báo cho các sự kiện ở [TB-02](#BR_TB_02); ghi nhật ký hoạt động ở [TB-03](#BR_TB_03).                            |

### 3.1.5 Mô hình dữ liệu khái niệm

Sơ đồ được vẽ lại theo đúng 17 bảng trong cơ sở dữ liệu hiện tại (migration V1\_\_init.sql và các migration sau). Mỗi hộp là một thực thể; ký hiệu chân chim: một vạch = đúng một, vòng tròn = không hoặc một, chân chim = nhiều.

<span id="FIG_erd" class="anchor"></span>***Hình 5. Mô hình dữ liệu khái niệm (vẽ lại) – file ShareLoop_Conceptual_ERD.drawio***

> **Ghi chú rà soát:** File conceptual.drawio đính kèm chỉ có 13 thực thể và thiếu nhiều quan hệ so với cơ sở dữ liệu nên không dùng; danh sách chỗ sai ở [Phụ lục A.3](#a.3-sơ-đồ-dữ-liệu-khái-niệm-cũ-conceptual.drawio-đã-vẽ-lại).

**Mô tả thực thể**

| **\#** | **Thực thể (bảng)**                                | **Mô tả**                                                                                                                                           |
|--------|----------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| 1      | **User (users)**                                   | Tài khoản Member hoặc Admin; gộp OTP, ví Credit (số dư, số đang giữ), hạn mức AI, sao uy tín, điểm, hạng.                                           |
| 2      | **Area (areas)**                                   | Cây khu vực thành phố → quận, tự tham chiếu cha – con.                                                                                              |
| 3      | **ItemCategory (item_categories)**                 | Danh mục bài đăng, có danh mục cha, cờ hạn chế và cờ cho phép đẩy bài.                                                                              |
| 4      | **ItemAttribute (item_attributes)**                | Định nghĩa thuộc tính động theo danh mục (vd trạng thái hoạt động của đồ điện tử).                                                                  |
| 5      | **Item (items)**                                   | Bài đăng Cho / Trao đổi; gộp trạng thái phí đang giữ, bản chụp checklist lần duyệt, hạn hiển thị, mốc đẩy bài.                                      |
| 6      | **ItemAttributeMapping (item_attribute_mappings)** | Giá trị thuộc tính động của từng bài (bảng nối Item – ItemAttribute).                                                                               |
| 7      | **MediaFile (media_files)**                        | Mọi tệp đã tải lên: ảnh bài, ảnh lỗi, ảnh đại diện, ảnh chat, bằng chứng; công khai hoặc riêng tư.                                                  |
| 8      | **ItemMediaMapping (item_media_mappings)**         | Gắn tệp vào bài theo vai trò (ảnh thường, ảnh lỗi) và thứ tự.                                                                                       |
| 9      | **Request (requests)**                             | Một yêu cầu xin / đề nghị đổi và toàn bộ vòng đời giao dịch: món đổi, lịch hẹn, xác nhận hai chiều, mốc hiện liên lạc.                              |
| 10     | **RequestMediaMapping (request_media_mappings)**   | Ảnh / video minh chứng gắn với một giao dịch (khi giao nhận hoặc tranh chấp).                                                                       |
| 11     | **Message (messages)**                             | Tin nhắn trong giao dịch: chữ, ảnh, thẻ bài, tin hệ thống; lưu kết quả kiểm duyệt.                                                                  |
| 12     | **Report (reports)**                               | Báo cáo bài / người và khiếu nại giao dịch; giải trình, hạn giải trình, kết luận, số sao bị trừ.                                                    |
| 13     | **Notification (notifications)**                   | Thông báo trong app của từng người dùng.                                                                                                            |
| 14     | **ActivityLog (activity_logs)**                    | Nhật ký chung: thao tác Admin, lượt gọi AI, đổi sao / điểm, quyết định duyệt, sự kiện bảo mật.                                                      |
| 15     | **PaymentOrder (payment_orders)**                  | Đơn nạp Credit; đơn Thành công là sổ tiền mặt thực thu.                                                                                             |
| 16     | **CreditLedger (credit_ledger)**                   | Sổ cái Credit: mọi thay đổi thật của số dư (nạp, các loại phí), kèm số dư sau giao dịch.                                                            |
| 17     | **WebsiteAttribute (website_attributes)**          | Bảng khoá – giá trị: cấu hình nghiệp vụ, từ khoá cấm, checklist, lý do từ chối mẫu, tên miền email dùng một lần. Không có khoá ngoại tới bảng khác. |

## 3.2 Tài khoản và truy cập

Nhóm chức năng giúp Khách tạo tài khoản và giúp Member, Admin đăng nhập, quản lý mật khẩu, hồ sơ.

### 3.2.1 Đăng ký tài khoản và xác thực email

**Use case:** [UC-01](#UC_01), [UC-64](#UC_64) **· Tác nhân:** Khách, Dịch vụ email (Brevo)

**Kích hoạt:** Khách bấm "Đăng ký" ở trang Đăng nhập hoặc thanh điều hướng.

**Mô tả:** Khách tạo tài khoản Member bằng email, mật khẩu, họ tên và số điện thoại. Tài khoản ở trạng thái Chờ xác thực (PENDING_VERIFICATION) cho tới khi nhập đúng mã OTP 6 số gửi qua email.

**Chi tiết chức năng**

**Đầu vào:**

- Email, mật khẩu (8–64 ký tự), họ tên (≤ 100 ký tự), số điện thoại di động.

- Mã OTP 6 số nhận qua email; nút "Gửi lại mã".

**Kiểm tra:**

- Email đúng định dạng, không thuộc danh sách tên miền email dùng một lần ([TK-02](#BR_TK_02)).

- Số điện thoại di động Việt Nam 10 số, đầu 03 / 05 / 07 / 08 / 09, nhận dạng +84 ([TK-03](#BR_TK_03)).

- Email và số điện thoại chưa thuộc tài khoản đang hoạt động khác; email đang chờ xác thực thì ghi đè và gửi mã mới ([TK-04](#BR_TK_04)).

- Một địa chỉ IP tạo tối đa 3 tài khoản mới trong 24 giờ ([TK-05](#BR_TK_05)).

- OTP đúng, còn hạn 5 phút, sai tối đa 5 lần; gửi lại sau ít nhất 60 giây ([TK-06](#BR_TK_06)).

**Nếu hợp lệ:**

- Tạo tài khoản PENDING_VERIFICATION, mật khẩu băm BCrypt, gửi OTP qua email.

- Nhập đúng OTP: tài khoản chuyển Đang hoạt động (ACTIVE), cấp mã đăng nhập (JWT 24 giờ), về Trang chủ.

- Tài khoản không xác thực trong 24 giờ bị xoá mềm ([TK-07](#BR_TK_07)).

**Nếu không hợp lệ (thông báo):**

- "Email đã được đăng ký."

- "Số điện thoại đã được sử dụng."

- "Không thể đăng ký bằng email dùng một lần. Vui lòng dùng email cá nhân của bạn."

- "Mạng của bạn đã tạo quá nhiều tài khoản trong 24 giờ qua. Vui lòng thử lại sau."

- "Mã OTP không đúng." / "Mã OTP đã hết hạn." / "Bạn đã nhập sai quá nhiều lần, vui lòng gửi lại mã."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                             |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [TK-02](#BR_TK_02) | Đăng ký cần email, mật khẩu, họ tên, số điện thoại. Email thuộc danh sách tên miền dùng một lần thì bị từ chối.                                               |
| [TK-03](#BR_TK_03) | SĐT bắt buộc, là số di động Việt Nam đầu 03 / 05 / 07 / 08 / 09 (nhận +84). Không xác thực SĐT bằng SMS.                                                      |
| [TK-04](#BR_TK_04) | Email và SĐT không trùng tài khoản đang hoạt động khác. Đăng ký lại một email đang chờ xác thực thì ghi đè thông tin cũ và gửi mã mới.                        |
| [TK-05](#BR_TK_05) | Một địa chỉ IP tạo tối đa 3 tài khoản mới trong 24 giờ gần nhất; vượt thì báo lỗi 429.                                                                        |
| [TK-06](#BR_TK_06) | Tài khoản chỉ dùng được sau khi nhập đúng OTP gửi qua email: 6 số, sống 5 phút, sai 5 lần thì mã bị huỷ, gửi lại sau ít nhất 60 giây. Mã lưu dạng băm BCrypt. |
| [TK-07](#BR_TK_07) | Tài khoản chưa xác thực quá 24 giờ bị xoá mềm; email đó đăng ký lại được.                                                                                     |

<span id="FIG_sw2" class="anchor"></span>***Hình 6. Luồng xử lý F01 Đăng ký – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Đăng ký (/register): form trống và form có lỗi ở từng ô.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Xác thực email (/verify-email): ô nhập 6 số, nút Gửi lại mã đang đếm ngược 60 giây.*

### 3.2.2 Đăng nhập và đăng xuất

**Use case:** [UC-02](#UC_02) **· Tác nhân:** Member, Admin

**Kích hoạt:** Người dùng bấm "Đăng nhập" trên thanh điều hướng; bấm "Đăng xuất" trong menu tài khoản.

**Mô tả:** Xác thực bằng email và mật khẩu, cấp mã đăng nhập theo vai trò. Admin được đưa vào trang quản trị, Member về Trang chủ.

**Chi tiết chức năng**

**Đầu vào:**

- Email, mật khẩu.

**Kiểm tra:**

- Email và mật khẩu khớp; sai email hay sai mật khẩu đều trả cùng một câu báo ([TK-08](#BR_TK_08)).

- Tài khoản phải Đang hoạt động; tài khoản chưa xác thực được đưa sang màn nhập OTP.

- Tài khoản bị khoá hoặc bị cấm bị chặn ở đăng nhập và ở mọi request sau đó ([TK-15](#BR_TK_15), [KC-02](#BR_KC_02)).

**Nếu hợp lệ:**

- Cấp JWT sống 24 giờ (chứa mã người dùng và vai trò).

- Member về Trang chủ, Admin về Tổng quan (/admin).

- Đăng xuất: xoá phiên ở trình duyệt, quay về Trang chủ.

**Nếu không hợp lệ (thông báo):**

- "Email hoặc mật khẩu không đúng."

- "Email chưa được xác thực."

- "Tài khoản đã bị khoá. Lý do: …" / "Tài khoản đã bị cấm."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                           |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [TK-08](#BR_TK_08) | Đăng nhập bằng email + mật khẩu. Sai email hay sai mật khẩu đều cùng một câu báo. Đăng nhập đúng được cấp mã đăng nhập (JWT) sống 24 giờ; hết hạn thì đăng nhập lại.                        |
| [TK-13](#BR_TK_13) | Admin là tài khoản tạo sẵn trong cơ sở dữ liệu, không đăng ký qua web, không đăng bài và không gửi yêu cầu.                                                                                 |
| [TK-15](#BR_TK_15) | Mã đăng nhập chỉ chứa mã người dùng và vai trò. Tài khoản bị khoá hoặc bị cấm bị chặn ở mọi request (kể cả mã còn hạn). Đổi mật khẩu không làm mã cũ mất hiệu lực; mã cũ tự hết sau 24 giờ. |
| [KC-01](#BR_KC_01) | Admin khoá phải nhập lý do ≤ 500 ký tự, không tự khoá mình. Người bị khoá thấy lý do khi đăng nhập (ví dụ "spam bài", "nghi ngờ lừa đảo").                                                  |
| [KC-02](#BR_KC_02) | Tài khoản bị khoá hoặc bị cấm bị chặn ở mọi request, kể cả mã đăng nhập cấp trước đó (lỗi 403). Giao diện nhận lỗi này thì đăng xuất và đưa về màn đăng nhập kèm câu báo.                   |

<span id="FIG_sw3" class="anchor"></span>***Hình 7. Luồng xử lý F02 Đăng nhập, quên và đổi mật khẩu – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Đăng nhập (/login).*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Thông báo khi đăng nhập bằng tài khoản bị khoá (có lý do).*

### 3.2.3 Quên mật khẩu và đổi mật khẩu

**Use case:** [UC-04](#UC_04), [UC-64](#UC_64) **· Tác nhân:** Khách (quên mật khẩu); Member, Admin (đổi mật khẩu)

**Kích hoạt:** Bấm "Quên mật khẩu?" ở trang Đăng nhập; hoặc bấm "Đổi mật khẩu" ở trang Hồ sơ.

**Mô tả:** Đặt lại mật khẩu bằng OTP email khi quên; đổi mật khẩu khi đang đăng nhập bằng mật khẩu hiện tại.

**Chi tiết chức năng**

**Đầu vào:**

- Quên mật khẩu: email, sau đó OTP và mật khẩu mới.

- Đổi mật khẩu: mật khẩu hiện tại, mật khẩu mới.

**Kiểm tra:**

- Hệ thống luôn trả cùng một câu "nếu email tồn tại, mã đã được gửi"; chỉ gửi mã khi email thuộc tài khoản đang hoạt động ([TK-09](#BR_TK_09)).

- OTP mục đích RESET_PASSWORD, cùng luật hạn và số lần sai như đăng ký ([TK-06](#BR_TK_06)).

- Đổi mật khẩu: đúng mật khẩu hiện tại, mật khẩu mới khác mật khẩu cũ ([TK-10](#BR_TK_10)).

**Nếu hợp lệ:**

- Lưu mật khẩu mới (BCrypt), xoá OTP; đổi mật khẩu thì gửi email báo.

- Mã đăng nhập cũ vẫn dùng được tới khi hết 24 giờ ([TK-15](#BR_TK_15)).

**Nếu không hợp lệ (thông báo):**

- "Mã OTP không đúng." / "Mã OTP đã hết hạn."

- "Mật khẩu hiện tại không đúng."

- "Mật khẩu mới phải khác mật khẩu hiện tại."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                           |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [TK-06](#BR_TK_06) | Tài khoản chỉ dùng được sau khi nhập đúng OTP gửi qua email: 6 số, sống 5 phút, sai 5 lần thì mã bị huỷ, gửi lại sau ít nhất 60 giây. Mã lưu dạng băm BCrypt.                               |
| [TK-09](#BR_TK_09) | Quên mật khẩu: hệ thống luôn trả lời giống nhau; chỉ gửi mã nếu email thuộc tài khoản đang hoạt động.                                                                                       |
| [TK-10](#BR_TK_10) | Đổi mật khẩu: phải đúng mật khẩu hiện tại, mật khẩu mới khác mật khẩu cũ; đổi xong gửi email báo.                                                                                           |
| [TK-14](#BR_TK_14) | Email chỉ dùng để gửi OTP và báo đổi mật khẩu; không gửi thông tin giao dịch qua email.                                                                                                     |
| [TK-15](#BR_TK_15) | Mã đăng nhập chỉ chứa mã người dùng và vai trò. Tài khoản bị khoá hoặc bị cấm bị chặn ở mọi request (kể cả mã còn hạn). Đổi mật khẩu không làm mã cũ mất hiệu lực; mã cũ tự hết sau 24 giờ. |

Luồng xử lý: xem [Hình 7](#FIG_sw3) (F02 Đăng nhập, quên và đổi mật khẩu).

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Quên mật khẩu: bước nhập email và bước nhập OTP + mật khẩu mới.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Đổi mật khẩu (/change-password).*

### 3.2.4 Quản lý hồ sơ và đổi số điện thoại

**Use case:** [UC-03](#UC_03), [UC-64](#UC_64) **· Tác nhân:** Member (Admin xem, sửa hồ sơ của chính mình)

**Kích hoạt:** Người dùng mở "Hồ sơ" trong menu tài khoản.

**Mô tả:** Xem và sửa họ tên, khu vực mặc định, ảnh đại diện. Đổi số điện thoại phải nhập OTP gửi về email hiện tại.

**Chi tiết chức năng**

**Đầu vào:**

- Họ tên (≤ 100 ký tự), khu vực, ảnh đại diện (đã tải lên qua API ảnh).

- Đổi số: số điện thoại mới, sau đó mã OTP.

**Kiểm tra:**

- Chỉ sửa dữ liệu của chính mình ([TK-12](#BR_TK_12)).

- Khu vực phải đang dùng.

- Số mới đúng định dạng, khác số hiện tại, chưa thuộc tài khoản khác; tối đa 1 lần / 30 ngày; không đổi khi có giao dịch Đã chốt lịch hoặc Chờ xác nhận giao nhận ([TK-11](#BR_TK_11)).

**Nếu hợp lệ:**

- Lưu hồ sơ ngay; đổi số thì lưu số chờ, gửi OTP CHANGE_PHONE; nhập đúng thì cập nhật số và mốc đổi.

**Nếu không hợp lệ (thông báo):**

- "Khu vực không tồn tại hoặc đã ngừng hoạt động."

- "Chỉ được đổi số điện thoại tối đa 1 lần trong 30 ngày."

- "Không thể đổi số điện thoại khi đang có giao dịch chưa hoàn tất."

- "Số điện thoại đã được sử dụng bởi tài khoản khác."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                   |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [TK-03](#BR_TK_03) | SĐT bắt buộc, là số di động Việt Nam đầu 03 / 05 / 07 / 08 / 09 (nhận +84). Không xác thực SĐT bằng SMS.                                                            |
| [TK-11](#BR_TK_11) | Đổi SĐT: nhập OTP gửi về email; tối đa 1 lần / 30 ngày; số mới chưa thuộc tài khoản khác; không đổi khi đang có giao dịch Đã chốt lịch hoặc Chờ xác nhận giao nhận. |
| [TK-12](#BR_TK_12) | Chỉ xem, sửa dữ liệu do chính mình tạo (hồ sơ, bài, yêu cầu, tin nhắn).                                                                                             |

<span id="FIG_sw4" class="anchor"></span>***Hình 8. Luồng xử lý F03 Đổi số điện thoại – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Hồ sơ (/profile).*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại Đổi số điện thoại ở bước nhập OTP.*

### 3.2.5 Xem sao uy tín, điểm và hạng

**Use case:** [UC-05](#UC_05) **· Tác nhân:** Member

**Kích hoạt:** Member mở trang Hồ sơ hoặc bấm huy hiệu uy tín.

**Mô tả:** Hiển thị số sao (0–5), tổng điểm, hạng hiện tại, quyền lợi theo hạng và lịch sử thay đổi kèm lý do.

**Chi tiết chức năng**

**Đầu vào:**

- Không có (lấy theo người đang đăng nhập).

**Kiểm tra:**

- Chỉ xem của chính mình; người khác chỉ thấy sao, hạng, tỉ lệ đúng mô tả trên hồ sơ công khai.

**Nếu hợp lệ:**

- Hiện sao, điểm, hạng (Đồng 0, Bạc 50, Vàng 150, Kim cương 400), số bài Trao đổi phí thường và số ngày hiển thị theo hạng.

**Nếu không hợp lệ (thông báo):**

- Chưa đăng nhập: chuyển về trang Đăng nhập.

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                               |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [UT-01](#BR_UT_01) | Mỗi tài khoản bắt đầu với 5 sao, thang 0–5.                                                                                                                                     |
| [UT-02](#BR_UT_02) | Chỉ trừ sao khi Admin chấp thuận một báo cáo / khiếu nại; mỗi lần 1 sao, không âm. Mới bị báo cáo thì chưa trừ.                                                                 |
| [UT-05](#BR_UT_05) | Hồi 1 sao sau mỗi 10 giao dịch hoàn tất liên tiếp không bị trừ sao, tối đa 5 sao. Bị trừ sao thì chuỗi đếm về 0.                                                                |
| [UT-06](#BR_UT_06) | Cộng điểm khi giao dịch Hoàn tất: bài Cho +10 người cho, +2 người nhận; bài Trao đổi +6 mỗi bên. Hạng tính lại ngay.                                                            |
| [UT-07](#BR_UT_07) | Mỗi tài khoản được cộng tối đa 30 điểm / ngày theo giờ Việt Nam; chạm trần thì chỉ cộng phần còn lại.                                                                           |
| [UT-08](#BR_UT_08) | Chống cày điểm: từ giao dịch hoàn tất thứ 3 giữa cùng một cặp người trong 90 ngày thì không cộng điểm (vẫn ghi lịch sử); cặp có hơn 5 giao dịch hiện ở màn Giám sát bất thường. |

<span id="FIG_sw18" class="anchor"></span>***Hình 9. Luồng xử lý F17 Uy tín – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Khối uy tín trên trang Hồ sơ (sao, điểm, hạng, lịch sử).*

## 3.3 Ví Credit

Credit chỉ dùng để trả phí dịch vụ cho nền tảng (1 Credit = 1.000 đ), nạp duy nhất qua VNPay.

### 3.3.1 Nạp Credit qua VNPay

**Use case:** [UC-10](#UC_10) **· Tác nhân:** Member, Cổng thanh toán VNPay

**Kích hoạt:** Member bấm "Nạp Credit" ở trang Ví Credit, hoặc bấm gợi ý nạp khi thiếu Credit.

**Mô tả:** Tạo đơn nạp, chuyển sang trang thanh toán VNPay; Credit chỉ được cộng khi VNPay gọi IPN hợp lệ về máy chủ.

**Chi tiết chức năng**

**Đầu vào:**

- Số tiền nạp (VND).

**Kiểm tra:**

- Tối thiểu 10.000 đ, là bội số 1.000 đ; tỉ lệ chốt lúc tạo đơn ([VI-01](#BR_VI_01), [VI-03](#BR_VI_03)).

- IPN: chữ ký đúng, số tiền khớp, đơn còn Chờ thanh toán và chưa hết hạn 30 phút ([VI-04](#BR_VI_04), [VI-06](#BR_VI_06)).

**Nếu hợp lệ:**

- Trong một transaction có khoá dòng ví: đơn → Thành công, cộng Credit, ghi một dòng sổ cái TOP_UP, đánh dấu đã từng nạp ([VI-07](#BR_VI_07)).

- IPN gửi lại cho đơn đã xử lý: trả "đã xác nhận", không cộng lần hai.

**Nếu không hợp lệ (thông báo):**

- "Số tiền nạp phải đạt mức tối thiểu và là bội số của giá một Credit."

- Chữ ký sai: từ chối, ghi nhật ký SECURITY.

- VNPay báo lỗi: đơn Thất bại. IPN tới sau hạn: đơn Hết hạn, ghi cảnh báo để đối soát.

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                                              |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [VI-01](#BR_VI_01) | 1 Credit = 1.000 đ, số nguyên. Credit không đổi ngược ra tiền, không tặng, không chuyển cho người khác.                                                                                                                                                                        |
| [VI-03](#BR_VI_03) | Nạp tối thiểu 10 Credit (10.000 đ), số tiền là bội số của 1.000 đ. Tỉ lệ quy đổi chốt lúc tạo đơn.                                                                                                                                                                             |
| [VI-04](#BR_VI_04) | Chỉ cộng Credit khi VNPay gọi IPN có chữ ký đúng, số tiền khớp và đơn còn Chờ thanh toán. IPN gửi lại cho đơn đã xử lý thì trả "đã xác nhận", không cộng lần hai. IPN báo thành công nhưng đơn đã quá hạn thì đơn chuyển Hết hạn, không cộng Credit, ghi cảnh báo để đối soát. |
| [VI-05](#BR_VI_05) | (Đã bỏ) Không có nạp thủ công. Trên máy dev dùng cổng giả lập (MOCK).                                                                                                                                                                                                          |
| [VI-06](#BR_VI_06) | Đơn Chờ thanh toán quá 30 phút thì Hết hạn. Cùng mốc này được gửi sang VNPay làm hạn của trang thanh toán.                                                                                                                                                                     |
| [VI-07](#BR_VI_07) | Mỗi lần cộng / trừ Credit nằm trong một giao dịch cơ sở dữ liệu có khoá dòng ví và ghi một dòng sổ cái; mỗi đơn nạp chỉ sinh một dòng Nạp.                                                                                                                                     |
| [VI-08](#BR_VI_08) | Không có chức năng Admin cộng / trừ Credit tay.                                                                                                                                                                                                                                |

<span id="FIG_sw5" class="anchor"></span>***Hình 10. Luồng xử lý F04 Nạp Credit – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Ví Credit: hộp thoại nhập số tiền nạp.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang thanh toán VNPay sandbox (thẻ test NCB).*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Ví sau khi nạp thành công (số dư mới).*

### 3.3.2 Xem ví và lịch sử Credit

**Use case:** [UC-11](#UC_11) **· Tác nhân:** Member

**Kích hoạt:** Member mở "Ví Credit" trong menu tài khoản.

**Mô tả:** Xem số dư, số đang giữ, số khả dụng, danh sách bài đang giữ phí và lịch sử giao dịch Credit có lọc theo thời gian, loại.

**Chi tiết chức năng**

**Đầu vào:**

- Bộ lọc khoảng ngày, loại giao dịch; trang.

**Kiểm tra:**

- Khoảng ngày hợp lệ (từ ngày ≤ đến ngày).

**Nếu hợp lệ:**

- Số dư khả dụng = số dư − số đang giữ ([VI-02](#BR_VI_02)). Lịch sử hiện các loại TOP_UP, POST_FEE, EDIT_FEE, RENEW_FEE, BOOST_FEE, AI_SEARCH_FEE.

**Nếu không hợp lệ (thông báo):**

- "Thời điểm bắt đầu phải trước thời điểm kết thúc."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                            |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [VI-01](#BR_VI_01) | 1 Credit = 1.000 đ, số nguyên. Credit không đổi ngược ra tiền, không tặng, không chuyển cho người khác.                                                      |
| [VI-02](#BR_VI_02) | Số dư khả dụng = số dư − số đang bị giữ. Mọi thao tác có phí kiểm số dư khả dụng ngay lúc bấm.                                                               |
| [VI-09](#BR_VI_09) | Báo cáo tài chính tách ba sổ: tiền mặt thực thu (đơn thành công), Credit cộng vào, Credit trừ ra theo loại phí; tiền mặt thu phải bằng Credit nạp × 1.000 đ. |

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Ví Credit (/credit): thẻ số dư, số đang giữ, bảng lịch sử có bộ lọc.*

## 3.4 Quản lý bài đăng

Bài Cho miễn phí (tối đa 5 bài), bài Trao đổi giữ 5 Credit khi gửi duyệt và trừ khi Admin duyệt lần đầu.

### 3.4.1 Đăng bài Cho hoặc Trao đổi

**Use case:** [UC-12](#UC_12), [UC-13](#UC_13), [UC-60](#UC_60) **· Tác nhân:** Member

**Kích hoạt:** Member bấm "Đăng đồ" trên thanh điều hướng.

**Mô tả:** Member nhập thông tin món đồ, tải ảnh và gửi bài vào hàng chờ duyệt. Hệ thống kiểm tra tự động (tầng 1) ngay lúc gửi; bài Trao đổi bị giữ phí đăng.

**Chi tiết chức năng**

**Đầu vào:**

- Hình thức (Cho / Trao đổi), danh mục, khu vực, tình trạng, tiêu đề (≤ 150 ký tự), mô tả, thương hiệu (≤ 80 ký tự).

- 3–8 ảnh (mỗi ảnh ≤ 5 MB); bài Trao đổi thêm "món mong muốn"; đồ Có lỗi thêm mô tả lỗi và ít nhất 1 ảnh lỗi; đồ điện tử thêm trạng thái hoạt động.

**Kiểm tra:**

- Người đăng còn sao, không bị khoá / cấm, không phải Admin ([BD-07](#BR_BD_07)).

- Đủ trường bắt buộc, đúng số ảnh, đúng ràng buộc tình trạng và thuộc tính ([BD-01](#BR_BD_01) … [BD-04](#BR_BD_04)).

- Bài Cho: tối đa 5 bài Đang hiển thị hoặc Chờ duyệt ([BD-05](#BR_BD_05)).

- Tầng 1: danh mục hợp lệ, từ khoá cấm mức Chặn, SĐT / email / URL trong nội dung ([KD-02](#BR_KD_02), [KD-03](#BR_KD_03)).

- Bài Trao đổi: số dư khả dụng ≥ phí (5 Credit; vượt ngưỡng theo hạng: 10 Credit) ([PH-02](#BR_PH_02), [BD-06](#BR_BD_06)).

**Nếu hợp lệ:**

- Giữ phí (bài Trao đổi), tạo bài Chờ duyệt (PENDING_REVIEW), gắn ảnh, lưu từ khoá bị gắn cờ (REVIEW) để Admin xem.

- Bài vào hàng chờ duyệt ([Duyệt bài](#duyệt-bài-theo-checklist)).

**Nếu không hợp lệ (thông báo):**

- "Bài đăng cần từ 3 đến 8 ảnh."

- "Bạn đã đạt giới hạn bài Cho tặng đang hoạt động."

- "Nội dung bài đăng không qua kiểm duyệt tầng 1." (kèm từ bị chặn)

- "Số dư Credit khả dụng không đủ."

- "Tài khoản có 0 sao uy tín, không thể đăng bài mới."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                                    |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [BD-01](#BR_BD_01) | Bài có 3–8 ảnh, mỗi ảnh ≤ 5 MB, ảnh do chính người đăng tải lên.                                                                                                                                                                                                     |
| [BD-02](#BR_BD_02) | Bắt buộc chọn hình thức, danh mục đang mở, quận đang dùng, tình trạng; nhập tiêu đề (≤ 150 ký tự) và mô tả. Bài Trao đổi bắt buộc ghi món mong muốn (hiển thị công khai).                                                                                            |
| [BD-03](#BR_BD_03) | Tình trạng Có lỗi: bắt buộc mô tả lỗi và ít nhất 1 ảnh lỗi. Tình trạng khác không được gửi ảnh lỗi.                                                                                                                                                                  |
| [BD-04](#BR_BD_04) | Danh mục điện tử bắt buộc khai trạng thái hoạt động; danh mục khác không khai.                                                                                                                                                                                       |
| [BD-05](#BR_BD_05) | Hạn mức bài Cho: tối đa 5 bài Đang hiển thị hoặc Chờ duyệt, mọi hạng.                                                                                                                                                                                                |
| [BD-06](#BR_BD_06) | Bài Trao đổi được phí thường theo hạng (Đồng 5, Bạc 8, Vàng 15, Kim cương không giới hạn); vượt thì vẫn đăng được với phí 10 Credit (PH-02).                                                                                                                         |
| [BD-07](#BR_BD_07) | Tài khoản 0 sao, bị khoá, bị cấm hoặc là Admin thì không đăng bài được.                                                                                                                                                                                              |
| [PH-01](#BR_PH_01) | Đăng bài Cho: 0; —; hoàn: —                                                                                                                                                                                                                                          |
| [PH-02](#BR_PH_02) | Đăng bài Trao đổi trong hạn mức theo hạng: 5 Credit; Giữ khi gửi duyệt, trừ khi Admin duyệt lần đầu; hoàn: Nhả nếu bị từ chối hoặc gỡ trước khi duyệt                                                                                                                |
| [PH-03](#BR_PH_03) | Phí đăng của mỗi bài chỉ bị trừ một lần: Theo PH-02; Lần duyệt đầu tiên; hoàn: Không hoàn khi bài đã duyệt rồi bị gỡ, hết hạn, vi phạm hay giao dịch không thành                                                                                                     |
| [KD-02](#BR_KD_02) | Tầng 1 chặn: thiếu trường bắt buộc; danh mục bị hạn chế, đang ẩn hoặc quận không hợp lệ; từ khoá cấm mức Chặn; SĐT, email, URL trong tiêu đề, mô tả, món mong muốn (kể cả viết cách quãng bằng dấu cách, chấm, gạch). Bị chặn thì sửa ngay trên form, không mất phí. |
| [KD-03](#BR_KD_03) | Từ khoá cấm có hai mức: Chặn (BLOCK) chặn ngay; Gắn cờ (REVIEW) vẫn gửi duyệt và Admin thấy từ bị gắn cờ. Từ chỉ khớp sau khi bỏ dấu thì hạ xuống Gắn cờ. Mỗi từ có danh sách ngoại lệ.                                                                              |
| [KD-09](#BR_KD_09) | Cấm: vật phẩm cần sang tên pháp lý (xe có biển số, giấy tờ, sim chính chủ, tài khoản…), vật quy đổi ngay ra tiền (vàng, ngoại tệ, thẻ cào, voucher, tiền mã hoá), hàng nhái, hàng giả, vũ khí, chất cấm.                                                             |

<span id="FIG_sw6" class="anchor"></span>***Hình 11. Luồng xử lý F05 Đăng bài – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Đăng đồ (/post): form Cho tặng.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Đăng đồ: form Trao đổi có ô Món mong muốn và hộp thoại xác nhận giữ phí.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Lỗi tầng 1 khi mô tả có số điện thoại.*

### 3.4.2 Gợi ý mô tả bằng AI

**Use case:** [UC-19A](#UC_19A) **· Tác nhân:** Member, Dịch vụ AI (Gemini)

**Kích hoạt:** Trên trang Đăng đồ, Member nhập tên món rồi bấm "Gợi ý mô tả bằng AI".

**Mô tả:** AI tạo bản nháp mô tả chỉ từ tên món đồ (không đọc ảnh), kèm 2–4 câu hỏi để người đăng tự bổ sung. Bản nháp chỉ được điền vào ô Mô tả khi người đăng chọn dùng.

**Chi tiết chức năng**

**Đầu vào:**

- Tên món đồ (≤ 150 ký tự).

**Kiểm tra:**

- Tên không chứa thông tin liên hệ hay từ bị chặn.

- Tối đa 5 lượt thành công / người / ngày theo giờ Việt Nam; chờ tối đa 20 giây ([TT-09](#BR_TT_09)).

**Nếu hợp lệ:**

- Trả bản nháp mô tả, danh sách thuộc tính, câu hỏi cần người đăng điền và số lượt còn lại. Bài vẫn đi qua tầng 1 và Admin như bình thường.

**Nếu không hợp lệ (thông báo):**

- "Bạn đã dùng hết lượt gợi ý mô tả hôm nay."

- "Tên món đồ không hợp lệ để tạo gợi ý mô tả."

- AI lỗi / quá giờ: báo người đăng tự viết mô tả, không chặn đăng bài.

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                                                             |
|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [TT-06](#BR_TT_06) | Không gửi email, SĐT, họ tên, dữ liệu ví tới dịch vụ AI; nội dung người dùng được tách khỏi chỉ thị hệ thống.                                                                                                                                                                                 |
| [TT-07](#BR_TT_07) | Mỗi lượt gọi AI ghi nhật ký (model, kết quả, thời gian, có tính phí không), không ghi câu người dùng hỏi.                                                                                                                                                                                     |
| [TT-08](#BR_TT_08) | Gợi ý mô tả khi đăng bài: người đăng nhập tên món rồi bấm "Gợi ý mô tả bằng AI". AI tạo bản nháp mô tả chỉ từ tên món và 2–4 câu hỏi để người đăng tự điền; chỉ điền vào ô Mô tả khi người đăng chọn dùng. Kết quả được lọc thông tin liên hệ và từ khoá cấm; AI lỗi thì không chặn đăng bài. |
| [TT-09](#BR_TT_09) | Gợi ý mô tả miễn phí, tối đa 5 lượt thành công / người / ngày (giờ Việt Nam), chờ tối đa 20 giây.                                                                                                                                                                                             |

> **Ghi chú rà soát:** BR v12 ([TT-08](#BR_TT_08)) mô tả AI đọc 3 ảnh để gợi ý; code hiện tại chỉ dùng tên món, không gửi ảnh. Tài liệu này đặc tả theo code.

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại Gợi ý mô tả trên trang Đăng đồ (bản nháp và các câu hỏi).*

### 3.4.3 Sửa bài và gỡ bài

**Use case:** [UC-14](#UC_14), [UC-13](#UC_13), [UC-60](#UC_60) **· Tác nhân:** Member (chủ bài)

**Kích hoạt:** Chủ bài bấm "Sửa" hoặc "Gỡ" ở trang Hoạt động của tôi.

**Mô tả:** Sửa nội dung bài (đưa bài về Chờ duyệt) hoặc gỡ bài (xoá mềm). Trước khi sửa, hệ thống báo trước loại lượt sửa và số Credit sẽ giữ.

**Chi tiết chức năng**

**Đầu vào:**

- Nội dung mới như khi đăng bài (không đổi được hình thức Cho ↔ Trao đổi).

**Kiểm tra:**

- Là chủ bài, còn sao; bài không ở Đang giao dịch / Đã trao / Hết hạn và chưa có yêu cầu nào được chọn ([BD-10](#BR_BD_10)).

- Tầng 1 kiểm lại tiêu đề, mô tả, món mong muốn.

- Phí sửa theo bảng: lượt sửa đầu sau khi duyệt miễn phí, từ lượt hai 5 Credit ([BD-08](#BR_BD_08), [PH-04](#BR_PH_04)).

**Nếu hợp lệ:**

- Sửa: lưu nội dung, bài về Chờ duyệt và ẩn khỏi tìm kiếm; các yêu cầu đang chờ giữ nguyên ([BD-09](#BR_BD_09)).

- Gỡ: xoá mềm, nhả phí đang giữ, huỷ ngay các yêu cầu đang chờ và báo người gửi ([BD-11](#BR_BD_11)).

**Nếu không hợp lệ (thông báo):**

- "Bài đăng đã có yêu cầu được giữ chỗ hoặc không thể sửa."

- "Không đổi hình thức Cho tặng / Trao đổi khi sửa bài."

- "Bài đăng đã được giữ chỗ hoặc không thể gỡ."

- "Số dư Credit khả dụng không đủ."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                          |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [BD-08](#BR_BD_08) | Phí sửa theo bảng dưới; hệ thống báo trước số phí trước khi gửi.                                                                                                                                           |
| [BD-09](#BR_BD_09) | Mọi bản sửa quay lại Chờ duyệt, bài ẩn khỏi tìm kiếm tới khi duyệt xong; các yêu cầu đang chờ của bài giữ nguyên.                                                                                          |
| [BD-10](#BR_BD_10) | Chỉ sửa / gỡ được khi là chủ bài, còn sao, bài không ở Đang giao dịch / Đã trao / Hết hạn và chưa có yêu cầu nào được chọn. Không đổi hình thức Cho ↔ Trao đổi khi sửa (đổi theo mục K hoặc đăng bài mới). |
| [BD-11](#BR_BD_11) | Gỡ bài = xoá mềm, nhả phí đang giữ; các yêu cầu Chờ chủ bài chọn bị huỷ ngay với lý do "\[Hệ thống\] Bài đã bị gỡ" và người gửi được báo (D-13).                                                           |
| [PH-03](#BR_PH_03) | Phí đăng của mỗi bài chỉ bị trừ một lần: Theo PH-02; Lần duyệt đầu tiên; hoàn: Không hoàn khi bài đã duyệt rồi bị gỡ, hết hạn, vi phạm hay giao dịch không thành                                           |
| [PH-04](#BR_PH_04) | Sửa bài: lượt đầu sau khi đã được duyệt / từ lượt hai: 0 / 5 Credit; Giữ khi gửi bản sửa, trừ khi bản sửa được duyệt; hoàn: Nhả nếu bản sửa bị từ chối (không tính lượt)                                   |

<span id="FIG_sw8" class="anchor"></span>***Hình 12. Luồng xử lý F07 Sửa, gỡ bài – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Hoạt động của tôi (/activities): danh sách bài với các nút Sửa, Gỡ, Gia hạn, Đẩy.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại báo phí trước khi sửa (miễn phí / giữ 5 Credit).*

### 3.4.4 Gia hạn bài

**Use case:** [UC-15](#UC_15), [UC-13](#UC_13) **· Tác nhân:** Member (chủ bài)

**Kích hoạt:** Chủ bài bấm "Gia hạn" ở trang Hoạt động của tôi.

**Mô tả:** Trả 5 Credit để cộng thêm 30 ngày hiển thị, không qua duyệt lại.

**Chi tiết chức năng**

**Đầu vào:**

- Không có (hệ thống báo trước phí và hạn mới).

**Kiểm tra:**

- Bài của mình, Đang hiển thị, không bị ẩn; đủ 5 Credit khả dụng ([HT-01](#BR_HT_01)).

- Bài Hết hạn không gia hạn được ([HT-03](#BR_HT_03)).

**Nếu hợp lệ:**

- Trừ RENEW_FEE ngay; hạn mới = max(bây giờ, hạn cũ) + 30 ngày ([PH-05](#BR_PH_05)).

**Nếu không hợp lệ (thông báo):**

- "Chỉ gia hạn được bài đang hiển thị."

- "Số dư Credit khả dụng không đủ."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                        |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [HT-01](#BR_HT_01) | Gia hạn: bài của mình, Đang hiển thị và không bị ẩn, đủ 5 Credit khả dụng. Hạn mới = (hạn cũ nếu còn, không thì bây giờ) + 30 ngày, mọi hạng. Gia hạn lúc nào cũng được. |
| [HT-02](#BR_HT_02) | Hộp xác nhận báo trước phí và hạn mới (gia hạn: hạn hiển thị mới; đẩy: đẩy tới ngày nào).                                                                                |
| [HT-03](#BR_HT_03) | Bài Hết hạn không gia hạn được; muốn hiện lại thì đăng bài mới (D-06).                                                                                                   |
| [PH-05](#BR_PH_05) | Gia hạn bài (cả bài Cho), +30 ngày: 5 Credit; Ngay; hoàn: Không                                                                                                          |

<span id="FIG_sw9" class="anchor"></span>***Hình 13. Luồng xử lý F08 Gia hạn, đẩy bài – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

> **Ghi chú rà soát:** Nhánh B (đẩy bài) của F08 sai so với code 06/11: còn "≤ now + 14 ngày" và "đẩy gần nhất lên đầu". Đúng: đợt đẩy ≤ 6 ngày tính từ lúc bắt đầu đợt, nghỉ 3 ngày giữa hai đợt, tối đa 3 bài / người và 30 bài toàn hệ thống, xếp theo vòng xoay 3 phút, tối đa 3 bài đẩy / trang ([HT-04](#BR_HT_04), [HT-05](#BR_HT_05), [HT-07](#BR_HT_07)).

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại Gia hạn bài (phí và hạn hiển thị mới).*

### 3.4.5 Đẩy bài

**Use case:** [UC-16](#UC_16), [UC-13](#UC_13) **· Tác nhân:** Member (chủ bài)

**Kích hoạt:** Chủ bài bấm "Đẩy bài" ở trang Hoạt động của tôi.

**Mô tả:** Mỗi lần mua là một gói 3 ngày, 5 Credit. Bài đang đẩy được xếp vào nhóm đầu trang theo vòng xoay đổi nhóm mỗi 3 phút và vào banner trang chủ.

**Chi tiết chức năng**

**Đầu vào:**

- Không có; hệ thống báo trước phí, mốc kết thúc, thời gian nghỉ, số suất còn trống.

**Kiểm tra:**

- Bài Đang hiển thị, không bị ẩn, danh mục cho phép đẩy ([HT-04](#BR_HT_04)).

- Không đang trong 3 ngày nghỉ sau đợt trước; đợt đẩy liên tục ≤ 6 ngày; không vượt hạn hiển thị ([HT-04](#BR_HT_04)).

- Bài chưa đang đẩy: chủ bài đang đẩy \< 3 bài và toàn hệ thống \< 30 bài ([HT-07](#BR_HT_07)).

- Đủ 5 Credit khả dụng.

**Nếu hợp lệ:**

- Trừ BOOST_FEE ngay, kéo dài mốc hết đẩy. Bài vào vòng xoay ([HT-05](#BR_HT_05)) và banner trang chủ ([HT-08](#BR_HT_08)); 24 giờ trước khi hết đẩy chủ bài nhận thông báo ([HT-09](#BR_HT_09)).

**Nếu không hợp lệ (thông báo):**

- "Bài vừa hết đợt đẩy, cần chờ hết thời gian nghỉ mới đẩy lại được."

- "Đợt đẩy này đã đạt số ngày đẩy liên tục tối đa."

- "Bạn đã có đủ số bài đang đẩy cùng lúc cho phép."

- "Đã hết suất đẩy bài, vui lòng thử lại sau."

- "Thời gian đẩy không được vượt quá hạn hiển thị của bài."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                                                    |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [PH-06](#BR_PH_06) | Đẩy bài, +3 ngày: 5 Credit; Ngay; hoàn: Không, kể cả khi bài bị gỡ, ẩn, hết hạn                                                                                                                                                                                                      |
| [HT-02](#BR_HT_02) | Hộp xác nhận báo trước phí và hạn mới (gia hạn: hạn hiển thị mới; đẩy: đẩy tới ngày nào).                                                                                                                                                                                            |
| [HT-04](#BR_HT_04) | Đẩy bài: bài Đang hiển thị, không bị ẩn, thuộc danh mục cho phép đẩy (Admin bật / tắt theo danh mục). Mỗi lần mua là một gói 3 ngày, trừ ngay. Một đợt đẩy liên tục tối đa 6 ngày tính từ lúc bắt đầu đợt; hết đợt phải nghỉ 3 ngày mới mở đợt mới; không vượt hạn hiển thị của bài. |
| [HT-05](#BR_HT_05) | Khi tìm / duyệt bài: tối đa 3 bài đang đẩy ở đầu mỗi trang, chọn theo vòng xoay (thứ tự cố định theo id, đổi nhóm mỗi 3 phút, mỗi người xem bắt đầu ở một vị trí khác); bài đẩy không lên đầu vẫn có trong phần bài thường. Nhãn "Được đẩy" không hiện trên giao diện.               |
| [HT-06](#BR_HT_06) | Hết thời gian đẩy thì bài tự về vị trí thường, không cần tác vụ nền (truy vấn so boosted_until với giờ hiện tại).                                                                                                                                                                    |
| [HT-07](#BR_HT_07) | Mỗi người tối đa 3 bài đang đẩy cùng lúc; toàn hệ thống tối đa 30 bài đang đẩy. Mua thêm gói cho bài đang đẩy không chiếm suất mới. Hết suất thì báo thời điểm sớm nhất có suất.                                                                                                     |
| [HT-08](#BR_HT_08) | Banner trang chủ hiển thị một nhóm bài đang đẩy (mặc định 5 bài), đổi nhóm mỗi 3 phút theo cùng vòng xoay.                                                                                                                                                                           |
| [HT-09](#BR_HT_09) | 24 giờ trước khi bài hết đẩy, chủ bài nhận thông báo kèm mốc còn mua thêm gói được.                                                                                                                                                                                                  |

> **Ghi chú rà soát:** Sơ đồ F08 (Hình bên dưới) vẫn vẽ luật cũ của BR v12: cộng dồn tối đa 14 ngày, "đẩy gần nhất lên đầu". Code từ 06/11/2026 đã đổi sang đợt 6 ngày + nghỉ 3 ngày, giới hạn suất và vòng xoay. Xem [Phụ lục A](#phụ-lục-a.-rà-soát-các-sơ-đồ-đính-kèm).

Luồng xử lý: xem [Hình 13](#FIG_sw9) (F08 Gia hạn, đẩy bài).

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại Đẩy bài: phí, mốc kết thúc, số suất còn trống.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang chủ: banner bài đang đẩy.*

## 3.5 Tìm kiếm và AI trợ lý

Khách và Member tìm đồ miễn phí; AI trợ lý chỉ dành cho Member đã đăng nhập.

### 3.5.1 Tìm kiếm, lọc và xem chi tiết bài

**Use case:** [UC-18](#UC_18), [UC-07](#UC_07) **· Tác nhân:** Khách, Member

**Kích hoạt:** Người dùng nhập từ khoá hoặc chọn bộ lọc ở trang Tìm đồ; bấm vào một bài để xem chi tiết.

**Mô tả:** Liệt kê bài Đang hiển thị, không phân biệt dấu và hoa thường; lọc theo danh mục, khu vực, tình trạng, hình thức; sắp xếp mới nhất hoặc sắp hết hạn.

**Chi tiết chức năng**

**Đầu vào:**

- Từ khoá (tìm trong tiêu đề), danh mục, khu vực, tình trạng, hình thức, cách sắp xếp, trang.

**Kiểm tra:**

- Chỉ bài APPROVED, đang hiện, chưa xoá, chưa hết hạn ([TT-01](#BR_TT_01)).

- Danh mục bị hạn chế không hiện ở bộ lọc ([KD-08](#BR_KD_08)).

**Nếu hợp lệ:**

- Trả trang kết quả: tối đa 3 bài đang đẩy ở đầu trang theo vòng xoay, sau đó là bài thường ([HT-05](#BR_HT_05)). Chi tiết bài hiện ảnh, mô tả, tình trạng, món mong muốn, người đăng.

**Nếu không hợp lệ (thông báo):**

- Không có kết quả: hiện thông báo và gợi ý bỏ bớt bộ lọc.

- "Không tìm thấy bài đăng." khi mở bài đã gỡ / ẩn.

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                                      |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [TK-01](#BR_TK_01) | Khách (chưa đăng nhập) xem trang chủ, tìm và lọc bài, xem chi tiết bài, danh mục, khu vực. Mọi thao tác khác phải đăng nhập. Hồ sơ công khai của người dùng (trang /users/:id) chỉ xem được khi đã đăng nhập.                                                          |
| [TT-01](#BR_TT_01) | Tìm thường: chỉ bài Đang hiển thị, không bị ẩn; không phân biệt dấu, hoa thường; lọc theo danh mục, quận, tình trạng, hình thức. Bài đang được đẩy đứng trước (HT-05). Từ khoá chỉ tìm trong tiêu đề bài, không tìm trong mô tả.                                       |
| [HT-05](#BR_HT_05) | Khi tìm / duyệt bài: tối đa 3 bài đang đẩy ở đầu mỗi trang, chọn theo vòng xoay (thứ tự cố định theo id, đổi nhóm mỗi 3 phút, mỗi người xem bắt đầu ở một vị trí khác); bài đẩy không lên đầu vẫn có trong phần bài thường. Nhãn "Được đẩy" không hiện trên giao diện. |
| [KD-08](#BR_KD_08) | Chỉ đăng trong danh mục đang mở. Danh mục bị hạn chế (Dược phẩm và thiết bị y tế) không hiện ở bất kỳ chỗ nào người dùng chọn danh mục; Admin vẫn thấy để quản lý.                                                                                                     |

<span id="FIG_sw10" class="anchor"></span>***Hình 14. Luồng xử lý F09 Tìm kiếm, AI – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

> **Ghi chú rà soát:** F09 nhánh B khác code: làn AI ghi "OpenRouter" (thực tế Gemini); bước 11 trừ phí trước khi gọi AI và bước 14 hoàn lại (code chỉ trừ sau khi AI thành công và có kết quả); bước 12 "tách câu thành bộ lọc JSON" và bước 15 "nới bộ lọc" (code gửi danh sách bài ứng viên cho AI chọn); AI lỗi thì code trả danh sách rỗng, không tự tìm thường. Nhánh A bước 3 còn ghi "mọi bài đẩy đứng trước, đẩy gần nhất lên đầu" (đúng: vòng xoay, tối đa 3 bài / trang).

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Tìm đồ (/browse) có bộ lọc và bài đẩy ở đầu trang.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Chi tiết bài (/items/:id) với nút Gửi yêu cầu, Báo cáo.*

### 3.5.2 AI trợ lý tìm đồ

**Use case:** [UC-19](#UC_19) **· Tác nhân:** Member, Dịch vụ AI (Gemini)

**Kích hoạt:** Member mở trang "Trợ lý AI" và nhập nhu cầu bằng câu tự nhiên.

**Mô tả:** AI đọc nhu cầu và chọn các bài phù hợp trong danh sách bài đang hiển thị do hệ thống cung cấp; kết quả luôn là bài có thật.

**Chi tiết chức năng**

**Đầu vào:**

- Câu mô tả nhu cầu (1–500 ký tự).

**Kiểm tra:**

- Câu không chứa SĐT, email, URL ([TT-02](#BR_TT_02)).

- Còn lượt miễn phí (5 lượt / ngày nếu đã từng nạp; 1 lượt thử nếu chưa nạp) hoặc đủ 2 Credit khả dụng ([TT-03](#BR_TT_03)).

**Nếu hợp lệ:**

- Trả lời ngắn, danh sách bài phù hợp, số lượt còn lại. Chỉ trừ lượt / Credit khi AI chạy xong và có ít nhất một bài hợp lệ ([PH-07](#BR_PH_07)). Mỗi câu hỏi được xử lý riêng, không nhớ các câu trước (TT-04).

- Câu ngoài phạm vi tìm đồ: từ chối ngắn, không tính lượt.

**Nếu không hợp lệ (thông báo):**

- "Số dư Credit khả dụng không đủ." (hết lượt miễn phí)

- "Trợ lý tìm đồ hiện chưa sẵn sàng." / "Trợ lý tìm đồ đã quá thời gian chờ." – không tính lượt.

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                                                                                                                                                    |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [TT-02](#BR_TT_02) | AI trợ lý chỉ dành cho thành viên đã đăng nhập (không phải Admin); câu hỏi ≤ 500 ký tự, không chứa SĐT, email, URL.                                                                                                                                                                                                                                                                  |
| [TT-03](#BR_TT_03) | Trước khi gọi AI, hệ thống kiểm còn lượt miễn phí hoặc đủ 2 Credit khả dụng; thiếu thì báo nạp Credit.                                                                                                                                                                                                                                                                               |
| [TT-04](#BR_TT_04) | Hệ thống gửi danh sách bài ứng viên (bài Đang hiển thị, không bị ẩn, còn hạn) theo lô cho AI; AI chỉ chọn ID trong danh sách đó (tối đa 6 bài) và viết một câu trả lời ngắn. ID lạ bị loại và mỗi bài được kiểm lại trước khi trả, nên kết quả luôn là bài có thật. Mỗi câu hỏi được xử lý riêng, không nhớ các câu trước. Câu hỏi ngoài phạm vi tìm đồ bị từ chối, không tính lượt. |
| [TT-05](#BR_TT_05) | AI lỗi, quá 15 giây hoặc trả sai định dạng: hệ thống báo trợ lý chưa trả lời được và trả danh sách rỗng, không tính lượt, không trừ Credit; người dùng có thể dùng tìm kiếm thường.                                                                                                                                                                                                  |
| [TT-06](#BR_TT_06) | Không gửi email, SĐT, họ tên, dữ liệu ví tới dịch vụ AI; nội dung người dùng được tách khỏi chỉ thị hệ thống.                                                                                                                                                                                                                                                                        |
| [TT-07](#BR_TT_07) | Mỗi lượt gọi AI ghi nhật ký (model, kết quả, thời gian, có tính phí không), không ghi câu người dùng hỏi.                                                                                                                                                                                                                                                                            |
| [PH-07](#BR_PH_07) | AI trợ lý tìm đồ sau khi hết lượt miễn phí: 2 Credit / lượt; Khi AI chạy xong và có kết quả; hoàn: AI lỗi thì không trừ                                                                                                                                                                                                                                                              |

> **Ghi chú rà soát:** Sơ đồ F09 còn vẽ cách cũ (AI tách câu thành bộ lọc, nới bộ lọc, AI lỗi thì tự tìm thường) và ghi nhà cung cấp là OpenRouter. Tài liệu này đặc tả theo code (Gemini, AI chọn bài trong danh sách ứng viên); nhóm đã chốt giữ như code ngày 09/11/2026 (D-21).

Luồng xử lý: xem [Hình 14](#FIG_sw10) (F09 Tìm kiếm, AI).

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Trợ lý AI (/ai): ô nhập, số lượt miễn phí còn lại, kết quả có thẻ bài.*

## 3.6 Yêu cầu và giao dịch

Gửi yêu cầu, chat, chốt lịch và giao nhận đều miễn phí. Một bản ghi requests là "yêu cầu" khi còn chờ và là "giao dịch" từ lúc chủ bài chọn người.

### 3.6.1 Gửi yêu cầu xin hoặc đề nghị đổi

**Use case:** [UC-20](#UC_20) **· Tác nhân:** Member (người nhận)

**Kích hoạt:** Member bấm "Gửi yêu cầu" ở trang Chi tiết bài.

**Mô tả:** Gửi yêu cầu tới một bài đang hiển thị; có thể kèm một bài của mình làm món đem đổi.

**Chi tiết chức năng**

**Đầu vào:**

- Món đem đổi (tuỳ chọn): chọn từ danh sách bài Đang hiển thị của mình.

**Kiểm tra:**

- Không gửi cho bài của mình; người gửi còn sao, không bị khoá / cấm, không phải Admin ([YC-01](#BR_YC_01), [YC-02](#BR_YC_02)).

- Một yêu cầu đang mở cho mỗi bài; tối đa 5 yêu cầu Chờ cùng lúc ([YC-03](#BR_YC_03)).

- Món đem đổi là bài của mình, Đang hiển thị, không bị ẩn ([YC-04](#BR_YC_04), [CD-01](#BR_CD_01)).

**Nếu hợp lệ:**

- Tạo yêu cầu Chờ chủ bài chọn (PENDING), báo chủ bài.

**Nếu không hợp lệ (thông báo):**

- "Không thể gửi yêu cầu cho bài đăng của chính mình."

- "Bạn đã có một yêu cầu đang hoạt động cho bài đăng này."

- "Bạn đã đạt giới hạn tối đa 5 yêu cầu đang chờ xử lý."

- "Bài đăng không ở trạng thái sẵn sàng để gửi yêu cầu."

- "Quản trị viên không được gửi yêu cầu giao dịch."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                           |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [YC-01](#BR_YC_01) | Không gửi yêu cầu cho bài của chính mình.                                                                                                                                                                   |
| [YC-02](#BR_YC_02) | Chỉ gửi tới bài Đang hiển thị, không bị ẩn, chủ bài không bị khoá / cấm. Người gửi phải còn sao, không bị khoá / cấm, không phải Admin.                                                                     |
| [YC-03](#BR_YC_03) | Mỗi người chỉ có một yêu cầu đang mở với cùng một bài; tối đa 5 yêu cầu Chờ chủ bài chọn cùng lúc (gửi song song tới nhiều bài khác nhau).                                                                  |
| [YC-04](#BR_YC_04) | Món đem đổi phải là bài của chính người gửi, Đang hiển thị, không bị ẩn; chọn từ danh sách bài, không nhập tự do. Bài Trao đổi bắt buộc kèm món đổi; bài Cho được kèm món đổi nhưng không bắt buộc (CD-01). |
| [CD-01](#BR_CD_01) | Ở bài Cho, người xin được kèm một bài Đang hiển thị của mình làm món đổi (không bắt buộc). Ở bài Trao đổi, người xin được gửi yêu cầu không kèm món đổi.                                                    |

<span id="FIG_sw11" class="anchor"></span>***Hình 15. Luồng xử lý F10 Gửi yêu cầu, chọn người – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại Gửi yêu cầu (có chọn món đem đổi).*

### 3.6.2 Rút yêu cầu

**Use case:** [UC-21](#UC_21) **· Tác nhân:** Member (người gửi)

**Kích hoạt:** Người gửi bấm "Rút yêu cầu" ở trang Hoạt động của tôi.

**Mô tả:** Huỷ yêu cầu khi chủ bài chưa chọn người.

**Chi tiết chức năng**

**Đầu vào:**

- Không có.

**Kiểm tra:**

- Là người gửi; yêu cầu còn Chờ chủ bài chọn ([YC-05](#BR_YC_05)).

**Nếu hợp lệ:**

- Yêu cầu → Đã huỷ. Quá 3 ngày chưa được chọn thì hệ thống tự huỷ ([TD-02](#BR_TD_02)).

**Nếu không hợp lệ (thông báo):**

- "Chuyển trạng thái yêu cầu không hợp lệ." khi yêu cầu không còn Chờ.

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                       |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [YC-05](#BR_YC_05) | Người nhận tự rút yêu cầu khi còn Chờ chủ bài chọn. Quá 3 ngày chưa được chọn thì tự huỷ.                                                                                                                               |
| [TD-02](#BR_TD_02) | Mỗi giờ, phút 15: Yêu cầu Chờ chủ bài chọn quá 3 ngày → Đã huỷ; giao dịch Đã được chọn / Đang hẹn lịch quá 10 ngày → Đã huỷ, nhả bài; Chờ xác nhận giao nhận quá 3 ngày từ xác nhận đầu, không có khiếu nại → Hoàn tất. |

Luồng xử lý: xem [Hình 15](#FIG_sw11) (F10 Gửi yêu cầu, chọn người).

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Tab Yêu cầu đã gửi trên trang Hoạt động, có nút Rút yêu cầu.*

### 3.6.3 Chọn người giao dịch và chuyển hình thức Cho ↔ Trao đổi

**Use case:** [UC-22](#UC_22), [UC-23](#UC_23) **· Tác nhân:** Member (chủ bài)

**Kích hoạt:** Chủ bài mở danh sách người xin của một bài và bấm "Chọn". Hoặc, trong chat của giao dịch bài Cho chưa chốt lịch, người nhận gửi đề nghị đưa một bài của mình vào đổi.

**Mô tả:** Chủ bài xem sao, hạng, tỉ lệ đúng mô tả và món đem đổi của từng người, chọn đúng một người. Nếu hình thức bài khác loại yêu cầu, hệ thống hỏi xác nhận chuyển hình thức. Với bài Cho, người nhận còn có thể đề nghị đổi món ngay trong chat trước khi chốt lịch; chủ bài đồng ý thì bài chuyển sang Trao đổi.

**Chi tiết chức năng**

**Đầu vào:**

- Yêu cầu được chọn; xác nhận chuyển hình thức (nếu có). Đề nghị đổi món trong chat: bài đem đổi của người nhận, lời nhắn (≤ 500 ký tự).

**Kiểm tra:**

- Yêu cầu còn Chờ, bài còn Đang hiển thị, hai bên không bị khoá ([YC-06](#BR_YC_06)).

- Bài Cho + yêu cầu có món đổi: chuyển sang Trao đổi, trừ 5 Credit ngay ([CD-02](#BR_CD_02), [CD-06](#BR_CD_06)).

- Bài Trao đổi + yêu cầu không kèm món: chuyển sang Cho, không hoàn phí ([CD-04](#BR_CD_04)).

- Đề nghị đổi món trong chat: giao dịch bài Cho đang Đã được chọn hoặc Đang hẹn lịch; món đề nghị là bài Đang hiển thị, không bị ẩn, của người nhận. Chủ bài đồng ý thì trừ 5 Credit, bài thành Trao đổi, món đề nghị → Đang giao dịch, các yêu cầu khác dùng cùng món đó → Đã huỷ; chủ bài từ chối thì giao dịch giữ nguyên (CD-03).

**Nếu hợp lệ:**

- Yêu cầu → Đã được chọn; bài (và món đổi) → Đang giao dịch; yêu cầu khác của bài → Không được chọn; yêu cầu khác dùng cùng món đổi → Đã huỷ; mở chat, ghim thẻ bài ([YC-07](#BR_YC_07)).

**Nếu không hợp lệ (thông báo):**

- "Số dư Credit khả dụng không đủ." (chuyển Cho → Trao đổi)

- "Chuyển trạng thái yêu cầu không hợp lệ."

- "Không thể chuyển trạng thái bài đăng."

- "Chỉ giao dịch Cho đang chờ chốt lịch mới được chuyển sang Trao đổi bằng món đề nghị."

- "Đề nghị đổi món này đã được trả lời hoặc đã bị huỷ."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                           |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [YC-06](#BR_YC_06) | Chủ bài xem danh sách người xin (sao, hạng, tỉ lệ đúng mô tả, món đem đổi) và chọn đúng một người.                                                                                                                                          |
| [YC-07](#BR_YC_07) | Khi chọn: yêu cầu → Đã được chọn, bài và món đem đổi → Đang giao dịch; các yêu cầu khác của bài → Không được chọn; các yêu cầu khác đang dùng cùng món đem đổi ở bài khác → Đã huỷ. Mở chat, ghim thẻ bài (bài Trao đổi ghim cả hai món).   |
| [CD-02](#BR_CD_02) | Cho → Trao đổi lúc chọn người: chủ bài chọn một người có kèm món đổi → hộp xác nhận "Chuyển bài sang Trao đổi, trừ 5 Credit". Đồng ý và đủ khả dụng → trừ 5 Credit, bài thành Trao đổi, chat mở. Không đồng ý thì không chọn được người đó. |
| [CD-03](#BR_CD_03) | Cho → Trao đổi trong chat: giao dịch bài Cho chưa chốt lịch, người nhận muốn đưa một bài của mình vào đổi → chủ bài được hỏi có chuyển sang Trao đổi (5 Credit) không; đồng ý và trả phí thì món đó thành món đổi và chuyển Đang giao dịch. |
| [CD-04](#BR_CD_04) | Trao đổi → Cho: chủ bài chọn một người không kèm món đổi → hộp xác nhận "Chuyển bài sang Cho, không hoàn phí đã trả". Đồng ý thì bài thành Cho và mở chat.                                                                                  |
| [CD-06](#BR_CD_06) | Phí chuyển ghi sổ cái loại Phí đăng (POST_FEE), trừ ngay; bài không phải duyệt lại vì nội dung không đổi. Không kiểm hạn mức bài Trao đổi khi chuyển.                                                                                       |
| [CD-07](#BR_CD_07) | Điểm uy tín tính theo hình thức cuối cùng khi hoàn tất (Cho: +10 / +2; Trao đổi: +6 / +6).                                                                                                                                                  |

<span id="FIG_sw21" class="anchor"></span>***Hình 16. Luồng xử lý F20 Chuyển Cho ↔ Trao đổi – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

> **Ghi chú rà soát:** Ghi chú đầu trang F20 ("đang làm trong code, 02/11") đã cũ: cả ba nhánh A, B, C đã có trong code (nhánh C, đổi món trong chat, có từ 08/11/2026).

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Danh sách người xin của một bài (sao, hạng, tỉ lệ đúng mô tả, món đổi).*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại xác nhận "Chuyển bài sang Trao đổi, trừ 5 Credit".*

### 3.6.4 Nhắn tin trong giao dịch

**Use case:** [UC-24](#UC_24), [UC-25](#UC_25), [UC-61](#UC_61) **· Tác nhân:** Member (hai bên giao dịch)

**Kích hoạt:** Một bên mở "Tin nhắn" và chọn cuộc trò chuyện của giao dịch.

**Mô tả:** Hai bên trao đổi bằng tin chữ, thẻ bài và ảnh (sau khi chốt lịch). Tin nhắn được lọc thông tin liên hệ bằng quy tắc, không dùng AI.

**Chi tiết chức năng**

**Đầu vào:**

- Nội dung tin, ảnh (đã tải lên), hoặc thẻ bài muốn chia sẻ.

**Kiểm tra:**

- Giao dịch từ Đã được chọn tới Đang tranh chấp ([CH-01](#BR_CH_01)).

- Trước khi chốt lịch: chặn SĐT, email, URL, mạng xã hội, lời mời liên hệ ngoài ([CH-02](#BR_CH_02)); từ khoá mức Chặn bị chặn mọi lúc ([CH-03](#BR_CH_03)).

- Ảnh chỉ gửi từ khi Đã chốt lịch; người nhận của bài Cho không gửi ảnh ([CH-07](#BR_CH_07)).

- Thẻ bài chỉ của bài Đang hiển thị ([CH-06](#BR_CH_06)).

**Nếu hợp lệ:**

- Lưu tin PASS, hai bên thấy sau tối đa khoảng 5 giây ([CH-08](#BR_CH_08)).

- Tin bị chặn: người gửi thấy lý do, tính 1 vi phạm; đủ 3 vi phạm thì cảnh báo Admin ([CH-05](#BR_CH_05)).

**Nếu không hợp lệ (thông báo):**

- "Tin nhắn bị chặn do chứa thông tin liên hệ."

- "Chỉ được gửi ảnh sau khi hai bên đã chốt lịch hẹn (LOGISTICS_CONFIRMED)."

- "Người nhận trong giao dịch Cho tặng không được gửi ảnh trong cuộc trò chuyện."

- "Chỉ được chia sẻ thẻ của bài đăng đã được duyệt (APPROVED)."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                  |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [CH-01](#BR_CH_01) | Gửi tin được khi giao dịch từ Đã được chọn tới Đang tranh chấp; sau khi kết thúc (hoàn tất / huỷ) chỉ xem lại.                                                                     |
| [CH-02](#BR_CH_02) | Trước khi Đã chốt lịch: chặn tin chứa SĐT, email, URL, tên mạng xã hội và lời mời liên hệ ngoài (zalo, facebook, ib, inbox…), kể cả viết cách quãng, viết số bằng chữ, chèn ký tự. |
| [CH-03](#BR_CH_03) | Từ khoá cấm mức Chặn bị chặn ở mọi thời điểm.                                                                                                                                      |
| [CH-04](#BR_CH_04) | Chỉ kiểm tin bằng tầng 1 (quy tắc và từ khoá); không gọi AI kiểm tin (D-17).                                                                                                       |
| [CH-05](#BR_CH_05) | Tin bị chặn: người gửi thấy lý do; tính một lần vi phạm. Đủ 3 lần vi phạm trong một giao dịch thì cảnh báo Admin.                                                                  |
| [CH-06](#BR_CH_06) | Chỉ chia sẻ thẻ của bài Đang hiển thị. Đầu chat ghim thẻ bài; giao dịch Trao đổi ghim cả hai món.                                                                                  |
| [CH-07](#BR_CH_07) | Ảnh trong chat chỉ gửi từ khi Đã chốt lịch; người nhận của bài Cho không gửi ảnh (CD-05).                                                                                          |
| [CH-08](#BR_CH_08) | Tin mới hiện sau tối đa khoảng 5 giây (trình duyệt hỏi định kỳ, không dùng kết nối thời gian thực).                                                                                |
| [CD-05](#BR_CD_05) | Trong giao dịch bài Cho, người nhận không gửi ảnh trong chat; chủ bài gửi ảnh từ khi Đã chốt lịch.                                                                                 |

<span id="FIG_sw12" class="anchor"></span>***Hình 17. Luồng xử lý F11 Chat – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Tin nhắn (/messages): thẻ bài ghim ở đầu, một tin bị chặn kèm lý do.*

### 3.6.5 Chốt lịch giao nhận và xem thông tin liên lạc

**Use case:** [UC-26](#UC_26), [UC-62](#UC_62) **· Tác nhân:** Member (hai bên giao dịch)

**Kích hoạt:** Một bên bấm "Đề xuất lịch" ở trang Tiến trình giao dịch.

**Mô tả:** Một bên đề xuất hình thức, thời gian, địa điểm; bên kia xác nhận. Khi đủ hai xác nhận, lịch bị khoá và họ tên, email, SĐT hai bên hiện trong trang giao dịch.

**Chi tiết chức năng**

**Đầu vào:**

- Hình thức (Gặp trực tiếp / Gửi vận chuyển), thời gian hẹn, địa điểm (≤ 255 ký tự); cam kết chịu trách nhiệm khi gửi vận chuyển.

**Kiểm tra:**

- Thời gian ở tương lai; lịch chưa bị khoá ([GN-01](#BR_GN_01)).

- Gửi vận chuyển: cả hai bên bấm "Cam kết chịu trách nhiệm" ([GN-02](#BR_GN_02)).

- Quá 10 ngày từ lúc chọn người mà chưa chốt thì giao dịch tự huỷ ([GN-05](#BR_GN_05)).

**Nếu hợp lệ:**

- Đề xuất: giao dịch → Đang hẹn lịch; bên đề xuất tự đồng ý, đồng ý cũ của bên kia bị xoá.

- Đủ hai xác nhận: → Đã chốt lịch, khoá lịch, hiện liên lạc trong app (không gửi email), tắt lọc liên hệ, cho gửi ảnh ([GN-03](#BR_GN_03), [GN-04](#BR_GN_04)).

**Nếu không hợp lệ (thông báo):**

- "Thời gian hẹn giao nhận phải ở tương lai."

- "Lịch hẹn đã được xác nhận và bị khoá, không thể chỉnh sửa."

- "Hình thức giao hàng (Shipping) bắt buộc phải xác nhận cam kết chịu trách nhiệm."

- "Thông tin liên lạc chỉ được hiển thị sau khi hai bên đã chốt lịch hẹn."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [GN-01](#BR_GN_01) | Bên nào cũng đề xuất lịch được: hình thức, giờ hẹn (phải ở tương lai), địa điểm (≤ 255 ký tự). Bên đề xuất tự được tính là đồng ý; đề xuất mới xoá đồng ý cũ của bên kia (D-12). |
| [GN-02](#BR_GN_02) | Gửi qua đơn vị vận chuyển: cả hai bên phải bấm "Cam kết chịu trách nhiệm" với nội dung hiển thị đầy đủ.                                                                          |
| [GN-03](#BR_GN_03) | Hai bên cùng đồng ý → Đã chốt lịch; lịch bị khoá, không sửa được nữa.                                                                                                            |
| [GN-04](#BR_GN_04) | Khi Đã chốt lịch: họ tên, email, SĐT của hai bên hiện trong trang giao dịch (không gửi qua email); đã hiện thì không ẩn lại (D-11).                                              |
| [GN-05](#BR_GN_05) | Quá 10 ngày từ lúc chọn người mà chưa chốt lịch → tự huỷ, bài về Đang hiển thị.                                                                                                  |
| [GN-11](#BR_GN_11) | Hệ thống không bồi thường tiền hàng, phí gửi trong mọi trường hợp; khiếu nại chỉ dẫn tới trừ sao.                                                                                |
| [TK-14](#BR_TK_14) | Email chỉ dùng để gửi OTP và báo đổi mật khẩu; không gửi thông tin giao dịch qua email.                                                                                          |

<span id="FIG_sw15" class="anchor"></span>***Hình 18. Luồng xử lý F14 Chốt lịch – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Tiến trình giao dịch: thẻ lịch hẹn đang chờ bên kia xác nhận.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Tiến trình giao dịch sau khi chốt: khối thông tin liên lạc của đối tác.*

### 3.6.6 Xác nhận đã giao, đã nhận

**Use case:** [UC-27](#UC_27), [UC-28](#UC_28) **· Tác nhân:** Member (hai bên giao dịch)

**Kích hoạt:** Chủ bài bấm "Đã giao"; người nhận bấm "Đã nhận" ở trang Tiến trình giao dịch.

**Mô tả:** Mỗi bên xác nhận một lần, có thể kèm ảnh / video minh chứng. Người nhận bắt buộc trả lời "Món đồ có đúng mô tả không?".

**Chi tiết chức năng**

**Đầu vào:**

- Câu trả lời đúng mô tả (người nhận); lý do nếu không đúng (≤ 1000 ký tự); ảnh / video minh chứng (tuỳ chọn).

**Kiểm tra:**

- Giao dịch Đã chốt lịch hoặc Chờ xác nhận giao nhận; người gọi chưa xác nhận ([GN-06](#BR_GN_06)).

- Chọn "Không đúng" thì bắt buộc lý do ([GN-07](#BR_GN_07)).

**Nếu hợp lệ:**

- Xác nhận đầu tiên → Chờ xác nhận giao nhận, báo bên kia ([GN-08](#BR_GN_08)).

- Đủ hai xác nhận và đúng mô tả → Hoàn tất; bài (và món đổi) → Đã trao; cộng điểm; báo hạn khiếu nại 24 giờ.

- "Không đúng mô tả" → Đang tranh chấp ngay, mở khiếu nại NOT_AS_DESCRIBED, chủ bài có 48 giờ giải trình ([GN-07](#BR_GN_07)).

- Một bên im lặng 3 ngày, không có khiếu nại → tự Hoàn tất ([GN-09](#BR_GN_09)).

**Nếu không hợp lệ (thông báo):**

- "Người nhận bắt buộc phải trả lời câu hỏi món đồ có đúng mô tả hay không."

- "Bắt buộc nhập lý do khi chọn món đồ không đúng mô tả."

- "Bạn đã xác nhận giao nhận cho yêu cầu này rồi."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                         |
|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [GN-06](#BR_GN_06) | Xác nhận giao nhận chỉ sau khi Đã chốt lịch, mỗi bên một lần. Chủ bài bấm "Đã giao"; người nhận bấm "Đã nhận" và bắt buộc trả lời "Món đồ có đúng mô tả không?".                                                                                          |
| [GN-07](#BR_GN_07) | Người nhận trả lời Không đúng: bắt buộc nhập lý do → giao dịch Đang tranh chấp ngay, mở khiếu nại Không đúng mô tả (chủ bài có 48 giờ giải trình) trong cùng một lần xử lý; bài chưa Đã trao, chưa cộng điểm (D-04). Trang giao dịch hiện hạn giải trình. |
| [GN-08](#BR_GN_08) | Xác nhận đầu tiên → Chờ xác nhận giao nhận, báo bên kia. Đủ hai xác nhận (và đúng mô tả) → Hoàn tất; bài (và món đổi) → Đã trao; cộng điểm; báo hai bên hạn khiếu nại kèm giờ cụ thể.                                                                     |
| [GN-09](#BR_GN_09) | Một bên đã xác nhận, bên kia im lặng 3 ngày và giao dịch không có khiếu nại → tự Hoàn tất.                                                                                                                                                                |
| [GN-10](#BR_GN_10) | Ảnh / video minh chứng đính kèm được từ Đã chốt lịch trở đi (cả khi Hoàn tất, Đang tranh chấp); hai bên xem được; Admin xem khi xử lý khiếu nại.                                                                                                          |
| [UT-06](#BR_UT_06) | Cộng điểm khi giao dịch Hoàn tất: bài Cho +10 người cho, +2 người nhận; bài Trao đổi +6 mỗi bên. Hạng tính lại ngay.                                                                                                                                      |

<span id="FIG_sw16" class="anchor"></span>***Hình 19. Luồng xử lý F15 Giao nhận – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại Xác nhận đã nhận với câu hỏi "Món đồ có đúng mô tả không?".*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Tiến trình giao dịch ở trạng thái Hoàn tất (hạn khiếu nại).*

### 3.6.7 Huỷ giao dịch và báo chưa nhận / chưa giao

**Use case:** [UC-30](#UC_30), [UC-31](#UC_31) **· Tác nhân:** Member (hai bên giao dịch)

**Kích hoạt:** Một bên bấm "Huỷ giao dịch" hoặc "Tôi chưa nhận được đồ" / "Tôi chưa giao được" ở trang Tiến trình giao dịch.

**Mô tả:** Huỷ giao dịch đang mở kèm lý do; hoặc báo sự cố khi bên kia đã xác nhận còn mình chưa, giao dịch chuyển tranh chấp.

**Chi tiết chức năng**

**Đầu vào:**

- Huỷ: lý do 5–500 ký tự.

- Báo sự cố: ghi chú 5–1000 ký tự, ảnh bằng chứng (tuỳ chọn).

**Kiểm tra:**

- Huỷ khi Đã được chọn, Đang hẹn lịch, Đã chốt lịch hoặc Chờ xác nhận giao nhận; Đang tranh chấp thì chờ Admin ([HG-01](#BR_HG_01), [HG-02](#BR_HG_02)).

- Báo sự cố chỉ khi Chờ xác nhận giao nhận, bên kia đã xác nhận, mình chưa ([HG-03](#BR_HG_03)).

**Nếu hợp lệ:**

- Huỷ: giao dịch → Đã huỷ (lý do kèm "\[Chủ bài\]" / "\[Người nhận\]"), bài và món đổi về Đang hiển thị, bên kia được báo; không hoàn, không thu phí.

- Báo sự cố: → Đang tranh chấp, mở khiếu nại NO_SHOW, bên kia có 48 giờ giải trình.

**Nếu không hợp lệ (thông báo):**

- "Dữ liệu không hợp lệ." (lý do / ghi chú sai độ dài)

- "Chuyển trạng thái yêu cầu không hợp lệ."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                                     |
|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [HG-01](#BR_HG_01) | Một bên huỷ được giao dịch khi đang Đã được chọn, Đang hẹn lịch, Đã chốt lịch hoặc Chờ xác nhận giao nhận; bắt buộc lý do 5–500 ký tự (ghi kèm "\[Chủ bài\]" / "\[Người nhận\]"). Bài và món đổi về Đang hiển thị, bên kia nhận thông báo. Không hoàn, không thu phí. |
| [HG-02](#BR_HG_02) | Giao dịch Đang tranh chấp: người dùng không tự huỷ, phải chờ Admin.                                                                                                                                                                                                   |
| [HG-03](#BR_HG_03) | Báo "Tôi chưa nhận được đồ" / "Tôi chưa giao được": chỉ khi Chờ xác nhận giao nhận, bên kia đã xác nhận còn mình chưa; ghi chú 5–1000 ký tự, ảnh tuỳ chọn. Kết quả: Đang tranh chấp + khiếu nại Không đến / không giao (NO_SHOW), hạn giải trình 48 giờ.              |
| [BC-09](#BR_BC_09) | Khiếu nại tự mở từ: báo chưa nhận / chưa giao (HG-03), báo không đúng mô tả (GN-07).                                                                                                                                                                                  |

<span id="FIG_sw13" class="anchor"></span>***Hình 20. Luồng xử lý F12 Huỷ giao dịch – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại Huỷ giao dịch có ô lý do.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại "Tôi chưa nhận được đồ".*

## 3.7 Báo cáo, khiếu nại và thông báo

Mọi chế tài chỉ ở cấp tài khoản (trừ sao, ẩn bài, cấm); hệ thống không bồi thường tiền hàng.

### 3.7.1 Báo cáo bài, người dùng và khiếu nại giao dịch

**Use case:** [UC-29](#UC_29), [UC-28](#UC_28) **· Tác nhân:** Member

**Kích hoạt:** Member bấm "Báo cáo" ở chi tiết bài / hồ sơ công khai, hoặc "Khiếu nại" ở giao dịch đã Hoàn tất.

**Mô tả:** Gửi lý do, mô tả và ảnh bằng chứng. Bài có 3 báo cáo đang chờ tự ẩn tạm; khiếu nại giao dịch mở ngay thời hạn giải trình 48 giờ cho bên kia.

**Chi tiết chức năng**

**Đầu vào:**

- Lý do: Không đúng mô tả, Hàng giả, Hàng cấm, Không đến / không giao, Sai thông tin liên lạc, Quấy rối, Khác.

- Mô tả (≤ 1000 ký tự), ảnh bằng chứng.

**Kiểm tra:**

- Không tự báo cáo mình; không báo cáo trùng một đối tượng ([BC-01](#BR_BC_01)).

- Khiếu nại giao dịch: là một bên, giao dịch Hoàn tất, trong 24 giờ, chưa khiếu nại ([BC-07](#BR_BC_07)).

**Nếu hợp lệ:**

- Báo cáo bài / người: tạo hồ sơ Chờ xử lý; đủ 3 báo cáo đang chờ thì ẩn bài, báo chủ bài và Admin ([BC-02](#BR_BC_02)).

- Khiếu nại: hồ sơ Chờ giải trình, hạn 48 giờ, báo bên bị khiếu nại ([BC-08](#BR_BC_08)).

**Nếu không hợp lệ (thông báo):**

- "Không thể tự báo cáo chính mình hoặc bài đăng của chính mình."

- "Bạn đã báo cáo đối tượng này trước đó."

- "Đã quá thời hạn 24 giờ để khiếu nại giao dịch."

- "Chỉ có thể khiếu nại giao dịch đã hoàn thành."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                      |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [BC-01](#BR_BC_01) | Báo cáo bài hoặc người: chọn lý do, mô tả ≤ 1000 ký tự, ảnh bằng chứng. Không tự báo cáo mình, không báo cáo trùng một đối tượng.                                      |
| [BC-02](#BR_BC_02) | Bài có 3 báo cáo đang chờ (Chờ xử lý / Chờ giải trình) → tạm ẩn bài, trạng thái bài giữ nguyên; báo chủ bài và Admin.                                                  |
| [BC-07](#BR_BC_07) | Khiếu nại giao dịch Hoàn tất: là một bên của giao dịch, trong 24 giờ sau hoàn tất, chưa khiếu nại giao dịch này (D-05).                                                |
| [BC-08](#BR_BC_08) | Bên bị khiếu nại giải trình 1 lần kèm bằng chứng, trong 48 giờ; thông báo ghi rõ giờ hết hạn. Nội dung giải trình tối đa 2000 ký tự, giống giải trình báo cáo (BC-03). |
| [BC-09](#BR_BC_09) | Khiếu nại tự mở từ: báo chưa nhận / chưa giao (HG-03), báo không đúng mô tả (GN-07).                                                                                   |

<span id="FIG_sw17" class="anchor"></span>***Hình 21. Luồng xử lý F16 Báo cáo, khiếu nại – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại Báo cáo bài (chọn lý do, mô tả, ảnh).*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Hộp thoại Khiếu nại giao dịch.*

### 3.7.2 Giải trình báo cáo, khiếu nại

**Use case:** [UC-32](#UC_32) **· Tác nhân:** Member (bên bị báo cáo)

**Kích hoạt:** Bên bị báo cáo bấm thông báo "Bạn có 48 giờ … để giải trình".

**Mô tả:** Gửi nội dung giải trình kèm ảnh một lần, trước hạn. Một bài nhiều báo cáo chỉ cần giải trình một lần cho cả bài.

**Chi tiết chức năng**

**Đầu vào:**

- Nội dung giải trình (≤ 2000 ký tự, cho cả báo cáo và khiếu nại), ảnh bằng chứng.

**Kiểm tra:**

- Là bên bị báo cáo; hồ sơ đang Chờ giải trình, còn hạn; chưa giải trình lần nào ([BC-08](#BR_BC_08)).

**Nếu hợp lệ:**

- Lưu giải trình, hồ sơ về Chờ xử lý để Admin kết luận ([BC-03](#BR_BC_03), [BC-10](#BR_BC_10)).

**Nếu không hợp lệ (thông báo):**

- "Chỉ bên bị khiếu nại mới có quyền giải trình."

- "Đã quá thời hạn 48 giờ để gửi giải trình."

- "Bạn đã gửi bản giải trình cho khiếu nại này rồi."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                               |
|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [BC-03](#BR_BC_03) | Admin bấm "Yêu cầu giải trình" trên báo cáo bài hoặc người: bên bị báo cáo có 48 giờ, giải trình kèm ảnh. Một bài nhiều báo cáo thì mọi báo cáo chờ của bài dùng chung một hạn và chủ bài giải trình một lần cho cả bài. Nội dung giải trình tối đa 2000 ký tự. |
| [BC-08](#BR_BC_08) | Bên bị khiếu nại giải trình 1 lần kèm bằng chứng, trong 48 giờ; thông báo ghi rõ giờ hết hạn. Nội dung giải trình tối đa 2000 ký tự, giống giải trình báo cáo (BC-03).                                                                                          |
| [BC-10](#BR_BC_10) | Admin chỉ kết luận khi đã có giải trình hoặc đã quá hạn giải trình (D-15); màn Admin khoá nút Kết luận và hiện hạn.                                                                                                                                             |

Luồng xử lý: xem [Hình 21](#FIG_sw17) (F16 Báo cáo, khiếu nại).

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Gửi giải trình (/me/reports/:id hoặc /me/disputes/:id) có hạn giải trình.*

### 3.7.3 Xem thông báo

**Use case:** [UC-33](#UC_33) **· Tác nhân:** Member, Admin

**Kích hoạt:** Người dùng bấm biểu tượng chuông trên thanh điều hướng.

**Mô tả:** Danh sách thông báo trong app, số chưa đọc, đánh dấu đã đọc; bấm thông báo để mở đúng trang liên quan.

**Chi tiết chức năng**

**Đầu vào:**

- Không có; bấm từng thông báo để đánh dấu đã đọc.

**Kiểm tra:**

- Chỉ xem thông báo của chính mình.

**Nếu hợp lệ:**

- Số chưa đọc cập nhật mỗi 30 giây, tạm dừng khi tab bị ẩn ([TB-01](#BR_TB_01)).

**Nếu không hợp lệ (thông báo):**

- "Không tìm thấy thông báo."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                                                                                   |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [TB-01](#BR_TB_01) | Thông báo hiện trong app (chuông) với số chưa đọc; trình duyệt hỏi số chưa đọc mỗi 30 giây và tạm dừng khi tab bị ẩn.                                                                                                                                                                                               |
| [TB-02](#BR_TB_02) | Có thông báo khi: có yêu cầu mới; được / không được chọn; yêu cầu, giao dịch bị huỷ; lịch thay đổi; bên kia xác nhận giao nhận; hoàn tất (kèm hạn khiếu nại); bị khiếu nại hoặc được yêu cầu giải trình (kèm hạn); có kết luận; bài được duyệt / bị từ chối / bị ẩn / được mở lại; bị trừ sao (kèm số sao còn lại). |
| [TK-14](#BR_TK_14) | Email chỉ dùng để gửi OTP và báo đổi mật khẩu; không gửi thông tin giao dịch qua email.                                                                                                                                                                                                                             |

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Menu chuông thông báo đang mở (có thông báo chưa đọc).*

## 3.8 Quản trị hệ thống

Các chức năng Admin. Admin là tài khoản tạo sẵn, không đăng bài và không gửi yêu cầu ([TK-13](#BR_TK_13)). Mọi thao tác Admin đều ghi nhật ký ([AD-05](#BR_AD_05)).

### 3.8.1 Tổng quan quản trị

**Use case:** [UC-52](#UC_52), [UC-53](#UC_53) **· Tác nhân:** Admin

**Kích hoạt:** Admin đăng nhập hoặc bấm "Tổng quan" ở menu.

**Mô tả:** Hiển thị việc cần làm (bài chờ duyệt, khiếu nại chờ, giao dịch kẹt, cảnh báo) và số liệu chính.

**Chi tiết chức năng**

**Đầu vào:**

- Không có.

**Kiểm tra:**

- Chỉ vai trò Admin.

**Nếu hợp lệ:**

- Hiện thẻ việc cần làm, mỗi thẻ dẫn tới màn xử lý.

**Nếu không hợp lệ (thông báo):**

- Không phải Admin: chuyển về Trang chủ.

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                    |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [AD-05](#BR_AD_05) | Mọi thao tác Admin (duyệt, từ chối, kết luận, yêu cầu giải trình, khoá, mở khoá, huỷ giao dịch, đổi cấu hình, sửa danh mục, từ khoá, checklist, khu vực) đều ghi nhật ký có người làm; thao tác nặng bắt buộc lý do. |

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Tổng quan (/admin).*

### 3.8.2 Duyệt bài theo checklist

**Use case:** [UC-40](#UC_40), [UC-13](#UC_13) **· Tác nhân:** Admin

**Kích hoạt:** Admin mở "Duyệt bài" và chọn một bài trong hàng chờ.

**Mô tả:** Admin xem nội dung, ảnh, thuộc tính, từ bị gắn cờ, tích đủ checklist để duyệt, hoặc từ chối kèm lý do.

**Chi tiết chức năng**

**Đầu vào:**

- Câu trả lời checklist; quyết định Duyệt / Từ chối; lý do từ chối (chọn mẫu hoặc tự nhập).

**Kiểm tra:**

- Hàng chờ xếp: người ít sao → tài khoản mới → bài chờ lâu ([KD-06](#BR_KD_06)).

- Phải tích đạt mọi câu checklist đang bật, đúng phiên bản checklist hiện tại ([KD-04](#BR_KD_04)).

- Từ chối bắt buộc có lý do ([KD-05](#BR_KD_05)); hai Admin cùng xử lý thì chỉ một người thành công ([KD-11](#BR_KD_11)).

**Nếu hợp lệ:**

- Duyệt: bài → Đang hiển thị, trừ phí đang giữ, đặt hạn 30 / 45 ngày ở lần duyệt đầu, lưu bản chụp checklist, báo người đăng.

- Từ chối: bài → Bị từ chối, nhả phí, gửi lý do.

**Nếu không hợp lệ (thông báo):**

- "Cần trả lời đạt tất cả mục checklist để duyệt bài."

- "Checklist vừa được cập nhật, vui lòng tải lại trang."

- "Lý do từ chối là bắt buộc."

- "Chủ bài đang bị khoá hoặc bị cấm nên không duyệt được bài."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                  |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [KD-01](#BR_KD_01) | Hai lớp: tầng 1 tự động, rồi Admin duyệt mọi bài qua được tầng 1. Không có bước AI.                                                                                                                |
| [KD-04](#BR_KD_04) | Admin duyệt theo checklist do Admin cấu hình (ít nhất 1 câu đang bật); phải tích đạt hết mới duyệt được. Mỗi lần duyệt lưu lại câu hỏi và câu trả lời lúc đó; checklist vừa đổi thì phải tích lại. |
| [KD-05](#BR_KD_05) | Từ chối bắt buộc có lý do (chọn lý do mẫu hoặc tự nhập), gửi cho người đăng.                                                                                                                       |
| [KD-06](#BR_KD_06) | Hàng chờ duyệt xếp: người đăng ít sao trước → tài khoản mới hơn trước → bài chờ lâu hơn trước. Không có thông báo nhắc Admin khi bài chờ quá 24 giờ (đã bỏ).                                       |
| [KD-11](#BR_KD_11) | Hai Admin cùng xử lý một bài thì chỉ một người thành công; phí không bị trừ hai lần.                                                                                                               |
| [PH-03](#BR_PH_03) | Phí đăng của mỗi bài chỉ bị trừ một lần: Theo PH-02; Lần duyệt đầu tiên; hoàn: Không hoàn khi bài đã duyệt rồi bị gỡ, hết hạn, vi phạm hay giao dịch không thành                                   |
| [BD-12](#BR_BD_12) | Thời gian hiển thị tính từ lần duyệt đầu: 30 ngày; hạng Vàng trở lên 45 ngày.                                                                                                                      |

> **Ghi chú rà soát:** Sơ đồ F06 ghi "10 câu checklist khởi tạo"; dữ liệu hiện tại còn 9 câu đang bật (câu về an toàn thực phẩm đã gỡ theo [KD-07](#BR_KD_07)).

<span id="FIG_sw7" class="anchor"></span>***Hình 22. Luồng xử lý F06 Admin duyệt bài – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

> **Ghi chú rà soát:** Sơ đồ F06 bước 3 ghi "10 mục khởi tạo"; dữ liệu hiện tại có 9 câu checklist đang bật.

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Duyệt bài (/admin/moderation): hàng chờ.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Chi tiết duyệt bài: checklist, từ bị gắn cờ, nút Duyệt / Từ chối.*

### 3.8.3 Quản lý danh mục, thuộc tính, khu vực

**Use case:** [UC-43](#UC_43), [UC-45](#UC_45) **· Tác nhân:** Admin

**Kích hoạt:** Admin mở "Danh mục" hoặc "Khu vực" ở menu Nội dung.

**Mô tả:** Thêm, sửa, ẩn, sắp xếp danh mục; bật / tắt hạn chế và cho phép đẩy bài; quản lý thuộc tính động theo danh mục; quản lý cây khu vực thành phố → quận.

**Chi tiết chức năng**

**Đầu vào:**

- Danh mục: tên, danh mục cha, hạn chế, cho phép đẩy, thứ tự.

- Thuộc tính: tên, kiểu (TEXT / NUMBER / SELECT / BOOLEAN), lựa chọn, bắt buộc.

- Khu vực: tên, khu vực cha.

**Kiểm tra:**

- Danh mục / khu vực cha hợp lệ; danh sách sắp xếp đủ mọi danh mục.

- Không tắt thuộc tính bắt buộc của đồ điện tử ([BD-04](#BR_BD_04)).

**Nếu hợp lệ:**

- Lưu ngay; danh mục ẩn không hiện ở form đăng bài, bài cũ giữ nguyên ([AD-01](#BR_AD_01)).

**Nếu không hợp lệ (thông báo):**

- "Danh mục cha không hợp lệ."

- "Tùy chọn thuộc tính không hợp lệ."

- "Không thể chuyển khu vực cha thành khu vực con."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                                                    |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [AD-01](#BR_AD_01) | Admin quản lý danh mục (thêm, sửa, ẩn, sắp xếp; bật / tắt hạn chế và cho phép đẩy bài), thuộc tính động theo danh mục, khu vực (thành phố → quận), từ khoá cấm, checklist, lý do từ chối mẫu.                                                                                        |
| [KD-08](#BR_KD_08) | Chỉ đăng trong danh mục đang mở. Danh mục bị hạn chế (Dược phẩm và thiết bị y tế) không hiện ở bất kỳ chỗ nào người dùng chọn danh mục; Admin vẫn thấy để quản lý.                                                                                                                   |
| [BD-04](#BR_BD_04) | Danh mục điện tử bắt buộc khai trạng thái hoạt động; danh mục khác không khai.                                                                                                                                                                                                       |
| [HT-04](#BR_HT_04) | Đẩy bài: bài Đang hiển thị, không bị ẩn, thuộc danh mục cho phép đẩy (Admin bật / tắt theo danh mục). Mỗi lần mua là một gói 3 ngày, trừ ngay. Một đợt đẩy liên tục tối đa 6 ngày tính từ lúc bắt đầu đợt; hết đợt phải nghỉ 3 ngày mới mở đợt mới; không vượt hạn hiển thị của bài. |

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Danh mục (/admin/categories) có hộp thoại thêm / sửa.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Khu vực (/admin/districts).*

### 3.8.4 Quản lý từ khoá cấm, checklist, lý do từ chối mẫu

**Use case:** [UC-44](#UC_44), [UC-42](#UC_42) **· Tác nhân:** Admin

**Kích hoạt:** Admin mở "Từ khóa cấm" hoặc "Checklist duyệt bài".

**Mô tả:** Quản lý từ khoá cấm (mức Chặn / Gắn cờ, ngoại lệ, phạm vi) có ô thử nhanh một đoạn văn; quản lý câu hỏi checklist và lý do từ chối mẫu.

**Chi tiết chức năng**

**Đầu vào:**

- Từ khoá: từ, nhóm, mức, ngoại lệ, phạm vi (bài / chat / cả hai).

- Checklist: nội dung câu hỏi, điều kiện đạt, thứ tự, bật / tắt.

**Kiểm tra:**

- Không trùng từ trong cùng nhóm và phạm vi ([KD-03](#BR_KD_03)).

- Luôn có ít nhất 1 câu checklist đang bật ([KD-04](#BR_KD_04)).

**Nếu hợp lệ:**

- Có hiệu lực ngay cho lần kiểm tra tiếp theo; ghi nhật ký.

**Nếu không hợp lệ (thông báo):**

- "Từ khóa đã tồn tại trong nhóm và phạm vi này."

- "Phải luôn có ít nhất 1 mục checklist đang dùng (BR-A06)."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                                    |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [KD-02](#BR_KD_02) | Tầng 1 chặn: thiếu trường bắt buộc; danh mục bị hạn chế, đang ẩn hoặc quận không hợp lệ; từ khoá cấm mức Chặn; SĐT, email, URL trong tiêu đề, mô tả, món mong muốn (kể cả viết cách quãng bằng dấu cách, chấm, gạch). Bị chặn thì sửa ngay trên form, không mất phí. |
| [KD-03](#BR_KD_03) | Từ khoá cấm có hai mức: Chặn (BLOCK) chặn ngay; Gắn cờ (REVIEW) vẫn gửi duyệt và Admin thấy từ bị gắn cờ. Từ chỉ khớp sau khi bỏ dấu thì hạ xuống Gắn cờ. Mỗi từ có danh sách ngoại lệ.                                                                              |
| [KD-04](#BR_KD_04) | Admin duyệt theo checklist do Admin cấu hình (ít nhất 1 câu đang bật); phải tích đạt hết mới duyệt được. Mỗi lần duyệt lưu lại câu hỏi và câu trả lời lúc đó; checklist vừa đổi thì phải tích lại.                                                                   |
| [KD-05](#BR_KD_05) | Từ chối bắt buộc có lý do (chọn lý do mẫu hoặc tự nhập), gửi cho người đăng.                                                                                                                                                                                         |
| [KD-07](#BR_KD_07) | Không có danh mục thực phẩm. Các từ khoá cấm về thực phẩm tươi sống, đồ ăn chế biến, thực phẩm chức năng, sữa và đồ ăn dặm cho trẻ, rượu bia, thuốc lá giữ nguyên để chặn người đăng đồ ăn.                                                                          |
| [AD-01](#BR_AD_01) | Admin quản lý danh mục (thêm, sửa, ẩn, sắp xếp; bật / tắt hạn chế và cho phép đẩy bài), thuộc tính động theo danh mục, khu vực (thành phố → quận), từ khoá cấm, checklist, lý do từ chối mẫu.                                                                        |

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Từ khóa cấm (/admin/keywords) với ô thử nhanh.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Checklist duyệt bài (/admin/checklist).*

### 3.8.5 Cấu hình phí, hạn mức, uy tín và hạng

**Use case:** [UC-46](#UC_46) **· Tác nhân:** Admin

**Kích hoạt:** Admin mở "Cấu hình phí" hoặc "Uy tín và hạng".

**Mô tả:** Sửa các con số nghiệp vụ lưu trong bảng cấu hình (phí, hạn mức, mốc thời gian, điểm, ngưỡng hạng).

**Chi tiết chức năng**

**Đầu vào:**

- Giá trị mới của từng khoá cấu hình.

**Kiểm tra:**

- Đúng kiểu, trong khoảng cho phép; ngưỡng hạng tăng dần ([AD-02](#BR_AD_02)).

- Khoá cố định (hạn khiếu nại 24 giờ, hạn giải trình 48 giờ) không sửa được qua giao diện.

**Nếu hợp lệ:**

- Có hiệu lực ngay, ghi nhật ký đổi cấu hình.

**Nếu không hợp lệ (thông báo):**

- "Giá trị cấu hình nằm ngoài khoảng cho phép."

- "Điểm tối thiểu các hạng phải tăng dần: Bạc nhỏ hơn Vàng, Vàng nhỏ hơn Kim cương."

- "Cấu hình này cố định, Admin không sửa được."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                    |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [AD-02](#BR_AD_02) | Mọi con số nghiệp vụ nằm ở bảng cấu hình; Admin đổi trên màn Cấu hình, có hiệu lực ngay. Giá trị phải đúng kiểu và trong khoảng cho phép; ngưỡng hạng phải tăng dần. |

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Cấu hình phí (/admin/settings/fees).*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Uy tín và hạng (/admin/reputation).*

### 3.8.6 Xử lý báo cáo và khiếu nại

**Use case:** [UC-47](#UC_47), [UC-48](#UC_48), [UC-49](#UC_49) **· Tác nhân:** Admin

**Kích hoạt:** Admin mở "Khiếu nại và báo cáo" và chọn một hồ sơ.

**Mô tả:** Admin xem lý do, bằng chứng hai bên, chat, lịch sử; yêu cầu giải trình (báo cáo bài / người) và kết luận Chấp thuận hoặc Bác bỏ.

**Chi tiết chức năng**

**Đầu vào:**

- Yêu cầu giải trình; quyết định UPHOLD / DISMISS; ghi chú (≤ 500 ký tự).

**Kiểm tra:**

- Chỉ kết luận khi đã có giải trình hoặc đã quá hạn ([BC-10](#BR_BC_10)).

- Yêu cầu giải trình chỉ với báo cáo bài / người đang Chờ xử lý, chưa có giải trình ([BC-03](#BR_BC_03)).

**Nếu hợp lệ:**

- Bác bỏ: hồ sơ Bác bỏ; mở lại bài đang tạm ẩn khi đủ điều kiện ([BC-04](#BR_BC_04)); giao dịch tranh chấp → Hoàn tất ([BC-11](#BR_BC_11)).

- Chấp thuận: trừ 1 sao người vi phạm; báo cáo bài thì ẩn bài vĩnh viễn và đóng các báo cáo khác của bài ([BC-05](#BR_BC_05)); lý do nghiêm trọng thì ẩn mọi bài của người vi phạm ([BC-06](#BR_BC_06)); giao dịch tranh chấp → Đã huỷ ([BC-12](#BR_BC_12)).

- Sao về 0: tài khoản bị cấm ([UT-04](#BR_UT_04), [KC-05](#BR_KC_05)).

**Nếu không hợp lệ (thông báo):**

- "Bên bị báo cáo vẫn đang trong hạn giải trình, chưa thể kết luận."

- "Báo cáo này đã được kết luận trước đó."

- "Chỉ yêu cầu giải trình được với báo cáo đang chờ xử lý và chưa có giải trình."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                               |
|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [BC-03](#BR_BC_03) | Admin bấm "Yêu cầu giải trình" trên báo cáo bài hoặc người: bên bị báo cáo có 48 giờ, giải trình kèm ảnh. Một bài nhiều báo cáo thì mọi báo cáo chờ của bài dùng chung một hạn và chủ bài giải trình một lần cho cả bài. Nội dung giải trình tối đa 2000 ký tự. |
| [BC-04](#BR_BC_04) | Báo cáo bài bị bác bỏ: mở lại bài nếu không còn báo cáo chờ, bài chưa từng có báo cáo bị chấp thuận, chủ bài không bị khoá / cấm và còn sao.                                                                                                                    |
| [BC-05](#BR_BC_05) | Báo cáo bài được chấp thuận: ẩn bài vĩnh viễn (không tự hiện lại), trừ 1 sao chủ bài; các báo cáo khác đang chờ của bài đóng theo, chỉ trừ 1 sao.                                                                                                               |
| [BC-06](#BR_BC_06) | Lý do nghiêm trọng (Hàng cấm, Hàng giả) được chấp thuận → ẩn mọi bài đang hiển thị của người vi phạm để Admin rà soát. Admin sửa được danh sách lý do nghiêm trọng.                                                                                             |
| [BC-10](#BR_BC_10) | Admin chỉ kết luận khi đã có giải trình hoặc đã quá hạn giải trình (D-15); màn Admin khoá nút Kết luận và hiện hạn.                                                                                                                                             |
| [BC-11](#BR_BC_11) | Kết luận Bác bỏ: báo cáo Bác bỏ; giao dịch Đang tranh chấp → Hoàn tất (bài Đã trao, cộng điểm).                                                                                                                                                                 |
| [BC-12](#BR_BC_12) | Kết luận Chấp thuận: báo cáo Có vi phạm; trừ 1 sao người vi phạm (UT-02); giao dịch Đang tranh chấp → Đã huỷ, bài và món đổi về Đang hiển thị.                                                                                                                  |
| [BC-13](#BR_BC_13) | Mọi kết luận ghi nhật ký kèm ghi chú của Admin; các bên nhận thông báo.                                                                                                                                                                                         |
| [UT-02](#BR_UT_02) | Chỉ trừ sao khi Admin chấp thuận một báo cáo / khiếu nại; mỗi lần 1 sao, không âm. Mới bị báo cáo thì chưa trừ.                                                                                                                                                 |
| [UT-03](#BR_UT_03) | Mỗi lần bị trừ sao, người dùng nhận thông báo "Bạn bị trừ 1 sao. Bạn còn N sao trước khi tài khoản bị cấm."                                                                                                                                                     |
| [UT-04](#BR_UT_04) | Sao về 0 → tài khoản Bị cấm (KC-05).                                                                                                                                                                                                                            |

Luồng xử lý: xem [Hình 21](#FIG_sw17) (F16 Báo cáo, khiếu nại).

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Khiếu nại và báo cáo (/admin/complaints): chi tiết hồ sơ, nút Kết luận bị khoá khi còn hạn giải trình.*

### 3.8.7 Quản lý tài khoản: khoá và mở khoá

**Use case:** [UC-50](#UC_50) **· Tác nhân:** Admin

**Kích hoạt:** Admin mở "Tài khoản", chọn một tài khoản và bấm Khoá / Mở khoá.

**Mô tả:** Khoá tài khoản có lý do (dọn giao dịch, bài liên quan trong cùng một transaction) và mở khoá khi xem xét xong.

**Chi tiết chức năng**

**Đầu vào:**

- Lý do (≤ 500 ký tự).

**Kiểm tra:**

- Không tự khoá mình; tài khoản chưa bị khoá ([KC-01](#BR_KC_01)).

**Nếu hợp lệ:**

- Khoá: huỷ giao dịch đang mở (trừ Đang tranh chấp), từ chối bài chờ duyệt và nhả phí, ẩn bài đang hiển thị; chặn mọi request ([KC-02](#BR_KC_02), [KC-03](#BR_KC_03)).

- Mở khoá: hiện lại bài đã ẩn lúc khoá, trừ bài vi phạm hoặc đang đủ ngưỡng báo cáo ([KC-04](#BR_KC_04)).

**Nếu không hợp lệ (thông báo):**

- "Không thể tự khoá tài khoản của chính mình."

- "Tài khoản đã bị khoá."

- "Tài khoản đang hoạt động, không cần mở khoá."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                    |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [KC-01](#BR_KC_01) | Admin khoá phải nhập lý do ≤ 500 ký tự, không tự khoá mình. Người bị khoá thấy lý do khi đăng nhập (ví dụ "spam bài", "nghi ngờ lừa đảo").                                                                                                           |
| [KC-02](#BR_KC_02) | Tài khoản bị khoá hoặc bị cấm bị chặn ở mọi request, kể cả mã đăng nhập cấp trước đó (lỗi 403). Giao diện nhận lỗi này thì đăng xuất và đưa về màn đăng nhập kèm câu báo.                                                                            |
| [KC-03](#BR_KC_03) | Khi khoá, trong cùng một lần xử lý: huỷ mọi giao dịch đang mở của người đó (trừ giao dịch Đang tranh chấp) và nhả bài; từ chối mọi bài Chờ duyệt, bài đang giữ phí thì nhả phí; ẩn mọi bài Đang hiển thị. Một bước lỗi thì cả lần khoá không xảy ra. |
| [KC-04](#BR_KC_04) | Mở khoá: hiện lại đúng các bài đã bị ẩn lúc khoá, trừ bài đã bị kết luận vi phạm hoặc đang đủ ngưỡng báo cáo. Giao dịch đã huỷ và bài đã bị từ chối không khôi phục.                                                                                 |
| [KC-05](#BR_KC_05) | Cấm: khi sao về 0, tài khoản chuyển Bị cấm và dọn dẹp giống khoá (KC-03), ẩn thêm mọi bài còn lại; người dùng nhận thông báo; màn đăng nhập báo "Tài khoản đã bị cấm".                                                                               |
| [KC-06](#BR_KC_06) | Tài khoản bị khoá / cấm không gửi yêu cầu được, và người khác không gửi yêu cầu tới bài của họ được.                                                                                                                                                 |

<span id="FIG_sw20" class="anchor"></span>***Hình 23. Luồng xử lý F19 Khoá tài khoản – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Tài khoản (/admin/users) với hộp thoại Khoá tài khoản.*

### 3.8.8 Quản lý giao dịch

**Use case:** [UC-57](#UC_57) **· Tác nhân:** Admin

**Kích hoạt:** Admin mở "Giao dịch".

**Mô tả:** Tra cứu giao dịch theo trạng thái, lọc giao dịch bị kẹt và huỷ giao dịch cần can thiệp.

**Chi tiết chức năng**

**Đầu vào:**

- Bộ lọc trạng thái / "bị kẹt"; lý do huỷ.

**Kiểm tra:**

- Giao dịch chưa kết thúc (kể cả Đang tranh chấp); bắt buộc lý do ([HG-04](#BR_HG_04)).

**Nếu hợp lệ:**

- Giao dịch → Đã huỷ "\[Admin\] …", nhả bài, báo hai bên. Bị kẹt: quá giờ hẹn 48 giờ chưa ai xác nhận, hoặc một bên đang bị khoá ([HG-05](#BR_HG_05)).

**Nếu không hợp lệ (thông báo):**

- "Chuyển trạng thái yêu cầu không hợp lệ."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                 |
|--------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| [HG-04](#BR_HG_04) | Admin huỷ được giao dịch ở mọi trạng thái chưa kết thúc (kể cả Đang tranh chấp), bắt buộc lý do. Khiếu nại đang mở không tự đóng. |
| [HG-05](#BR_HG_05) | Giao dịch "bị kẹt" (Admin lọc được): quá giờ hẹn 48 giờ mà chưa bên nào xác nhận giao nhận, hoặc một bên đang bị khoá.            |

Luồng xử lý: xem [Hình 20](#FIG_sw13) (F12 Huỷ giao dịch).

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Giao dịch (/admin/transactions) với bộ lọc Bị kẹt.*

### 3.8.9 Giám sát bất thường và nhật ký hoạt động

**Use case:** [UC-52](#UC_52), [UC-58](#UC_58) **· Tác nhân:** Admin

**Kích hoạt:** Admin mở "Cảnh báo bất thường" hoặc "Nhật ký hoạt động".

Mô tả: Xem các cặp người giao dịch lặp lại nhiều lần và các địa chỉ IP có nhiều tài khoản đăng ký; tra cứu nhật ký thao tác quan trọng theo loại và thời gian.

**Chi tiết chức năng**

**Đầu vào:**

- Bộ lọc loại, khoảng ngày.

**Kiểm tra:**

- Khoảng ngày hợp lệ.

**Nếu hợp lệ:**

- Liệt kê cặp có hơn 5 giao dịch trong 90 ngày ([AD-04](#BR_AD_04), [UT-08](#BR_UT_08)); nhật ký có người làm, đối tượng, lý do ([TB-03](#BR_TB_03)).

- Liệt kê địa chỉ IP có từ 3 tài khoản đã xác thực email trở lên đăng ký trong 30 ngày gần nhất, nhóm nhiều tài khoản nhất lên trước; mỗi nhóm kèm họ tên, email, ngày đăng ký, sao, trạng thái của từng tài khoản. Không tính tài khoản chờ xác thực hoặc đã xoá mềm (AD-04).

**Nếu không hợp lệ (thông báo):**

- "Khoảng ngày không hợp lệ (từ ngày phải trước hoặc bằng đến ngày)."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                                                                                                                                                                                                  |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [AD-04](#BR_AD_04) | Giám sát bất thường: (1) liệt kê các cặp người giao dịch lặp lại nhiều lần (UT-08); (2) liệt kê các địa chỉ IP có từ 3 tài khoản đã xác thực email trở lên đăng ký trong 30 ngày gần nhất, kèm danh sách tài khoản (họ tên, email, ngày đăng ký, sao, trạng thái). Chỉ để Admin xem và tự quyết, hệ thống không tự khoá tài khoản. |
| [AD-05](#BR_AD_05) | Mọi thao tác Admin (duyệt, từ chối, kết luận, yêu cầu giải trình, khoá, mở khoá, huỷ giao dịch, đổi cấu hình, sửa danh mục, từ khoá, checklist, khu vực) đều ghi nhật ký có người làm; thao tác nặng bắt buộc lý do.                                                                                                               |
| [TB-03](#BR_TB_03) | Nhật ký hoạt động lưu thao tác quan trọng (duyệt, từ chối, khoá, mở khoá, cấm, huỷ giao dịch, kết luận, đổi sao / điểm, đổi cấu hình, danh mục, từ khoá, checklist, khu vực, lượt gọi AI). Admin lọc theo loại và thời gian.                                                                                                       |
| [UT-08](#BR_UT_08) | Chống cày điểm: từ giao dịch hoàn tất thứ 3 giữa cùng một cặp người trong 90 ngày thì không cộng điểm (vẫn ghi lịch sử); cặp có hơn 5 giao dịch hiện ở màn Giám sát bất thường.                                                                                                                                                    |

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Cảnh báo bất thường (/admin/alerts): khối cặp giao dịch lặp và khối nhiều tài khoản cùng IP.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Nhật ký hoạt động (/admin/audit-logs).*

### 3.8.10 Báo cáo tài chính, đơn nạp và tra cứu ví

**Use case:** [UC-53](#UC_53), [UC-59](#UC_59) **· Tác nhân:** Admin

**Kích hoạt:** Admin mở "Báo cáo tài chính", "Đơn nạp Credit" hoặc "Tra cứu người dùng".

**Mô tả:** Xem ba sổ: tiền mặt thực thu, Credit cộng vào, Credit trừ ra theo loại phí; xuất CSV; xem danh sách đơn nạp; tra cứu ví và lịch sử Credit của từng tài khoản (chỉ xem).

**Chi tiết chức năng**

**Đầu vào:**

- Khoảng thời gian; tài khoản cần tra cứu.

**Kiểm tra:**

- Khoảng ngày hợp lệ.

**Nếu hợp lệ:**

- Tiền mặt thực thu phải bằng Credit nạp × 1.000 đ ([VI-09](#BR_VI_09)); không có chức năng cộng / trừ Credit tay ([VI-08](#BR_VI_08), [AD-03](#BR_AD_03)).

**Nếu không hợp lệ (thông báo):**

- "Khoảng ngày không hợp lệ (từ ngày phải trước hoặc bằng đến ngày)."

**Luật nghiệp vụ áp dụng (tóm tắt tại chỗ; bấm mã để xem đầy đủ ở mục 5.1)**

| **Mã**             | **Nội dung luật**                                                                                                                                            |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [VI-08](#BR_VI_08) | Không có chức năng Admin cộng / trừ Credit tay.                                                                                                              |
| [VI-09](#BR_VI_09) | Báo cáo tài chính tách ba sổ: tiền mặt thực thu (đơn thành công), Credit cộng vào, Credit trừ ra theo loại phí; tiền mặt thu phải bằng Credit nạp × 1.000 đ. |
| [AD-03](#BR_AD_03) | Admin xem báo cáo tài chính, xuất CSV, tra cứu ví và lịch sử Credit của từng tài khoản (chỉ xem, không sửa).                                                 |

**Giao diện**

> **\[Ảnh chụp màn hình cần chèn\]** *Trang Báo cáo tài chính (/admin/finance) có biểu đồ và nút xuất CSV.*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Đơn nạp Credit (/admin/finance/topup-orders).*
>
> **\[Ảnh chụp màn hình cần chèn\]** *Trang Chi tiết ví người dùng (/admin/finance/users/:userId).*

## 3.9 Sơ đồ trạng thái

Ba sơ đồ trạng thái lấy từ file swimlane v12 (trang S1, S2, S3).

<span id="FIG_sw22" class="anchor"></span>***Hình 24. S1 Trạng thái giao dịch (requests.status) – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

> **Ghi chú rà soát:** S1 vẽ cạnh LOGISTICS_CONFIRMED → DISPUTED; RequestStatus.canTransition không có cạnh này. Code đi qua AWAITING_HANDOVER rồi sang DISPUTED trong cùng một lần xử lý khi người nhận chọn "Không đúng mô tả".

<span id="FIG_sw23" class="anchor"></span>***Hình 25. S2 Trạng thái bài đăng (items.status) – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

<span id="FIG_sw24" class="anchor"></span>***Hình 26. S3 Trạng thái báo cáo và đơn nạp – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

## 3.10 Tác vụ nền

Tác vụ nền chạy theo lịch, mỗi bản ghi xử lý riêng, chạy lại không gây tác dụng kép (xem [TD-01](#BR_TD_01) … [TD-06](#BR_TD_06)).

<span id="FIG_sw19" class="anchor"></span>***Hình 27. F18 Tác vụ nền – file ShareLoop_Flows_Swimlane_v12_VI.drawio***

# 4. Yêu cầu phi chức năng

## 4.1 Giao tiếp với hệ thống ngoài

| **Hệ thống ngoài**                | **Dùng để**                              | **Cách giao tiếp**                                                                                                   |
|-----------------------------------|------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| VNPay (sandbox)                   | Nạp Credit                               | Tạo URL thanh toán có chữ ký; VNPay gọi IPN (GET /payments/vnpay/ipn) và trang trả về chỉ để hiển thị.               |
| Gemini API                        | AI trợ lý tìm đồ, gợi ý mô tả từ tên món | Gọi HTTPS từ backend (GEMINI_API_KEY), store = false; không gửi email, SĐT, họ tên, dữ liệu ví ([TT-06](#BR_TT_06)). |
| Brevo                             | Gửi email OTP và báo đổi mật khẩu        | Gửi sau khi lưu dữ liệu (sau commit); email không chứa thông tin giao dịch.                                          |
| Cloudflare R2                     | Lưu ảnh                                  | Giao thức S3 (STORAGE_TYPE = s3); DB chỉ lưu storage_key; ảnh trả qua GET /api/v1/media/{id}.                        |
| Neon PostgreSQL 16                | Cơ sở dữ liệu                            | Dịch vụ managed; schema quản lý bằng Flyway.                                                                         |
| Google Cloud Run, Cloud Scheduler | Chạy backend và tác vụ nền               | Cloud Scheduler gọi /internal/jobs theo lịch ([TD-01](#BR_TD_01) … [TD-05](#BR_TD_05)).                              |

## 4.2 Thuộc tính chất lượng

### 4.2.1 Khả dụng

- Giao diện tiếng Việt, responsive, dùng tốt trên điện thoại.

- Mọi thao tác có phí đều có hộp xác nhận ghi rõ số Credit; hệ thống báo trước phí sửa, gia hạn, đẩy bài.

- Thông báo lỗi nói rõ nguyên nhân, không hiện mã kỹ thuật (mục 5.4).

### 4.2.2 Độ tin cậy

- Mọi thay đổi Credit chạy trong transaction có khoá dòng ví; số dư và số đang giữ không âm (ràng buộc CHECK).

- Mỗi đơn nạp chỉ sinh một dòng sổ cái (unique payment_order_id); IPN gửi lại không cộng hai lần.

- Tác vụ nền chạy lại không gây tác dụng kép; lỗi một bản ghi không làm hỏng cả lượt.

- AI hoặc dịch vụ ngoài lỗi không làm mất tiền và không chặn luồng chính (AI lỗi không tính phí).

### 4.2.3 Hiệu năng

- Danh sách bài phản hồi trong vài giây với dữ liệu demo; tìm không dấu dùng index trigram.

- AI trợ lý chờ tối đa 15 giây, gợi ý mô tả tối đa 20 giây.

- Chat cập nhật khoảng 5 giây, thông báo 30 giây (polling, chưa dùng WebSocket).

### 4.2.4 Bảo mật

- Mật khẩu và OTP lưu dạng băm BCrypt; JWT 24 giờ chỉ chứa mã người dùng và vai trò.

- Phân quyền theo vai trò Member / Admin và theo quyền sở hữu bản ghi; tài khoản bị khoá / cấm bị chặn ở mọi request.

- Email, SĐT đối tác chỉ trả về sau khi hai bên chốt lịch; khoá API và mật khẩu dịch vụ nằm ở biến môi trường / Secret Manager.

- Chứng từ, bằng chứng lưu riêng tư; chỉ hai bên giao dịch và Admin xem được.

# 5. Phụ lục yêu cầu

## 5.1 Luật nghiệp vụ (Business Rules)

Nguồn: "ShareLoop – Business Rule v12" (02/11/2026, gồm mục A–R). Mỗi mã luật có liên kết; cột "Dùng ở" dẫn ngược về chức năng áp dụng luật. Dòng tô vàng là luật đã được cập nhật theo code (sau 02/11) hoặc có ghi chú lệch với code.

### 5.1.A Tài khoản và đăng nhập

Ai cũng xem được bài; muốn đăng, xin hay đổi đồ thì phải có tài khoản đã xác thực email.

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 54%" />
<col style="width: 17%" />
<col style="width: 19%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Luật</strong></th>
<th><strong>Con số</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_TK_01" class="anchor"></span><strong>TK-01</strong></td>
<td><p>Khách (chưa đăng nhập) xem trang chủ, tìm và lọc bài, xem chi tiết bài, danh mục, khu vực. Mọi thao tác khác phải đăng nhập. Hồ sơ công khai của người dùng (trang /users/:id) chỉ xem được khi đã đăng nhập.</p>
<p>Ghi chú: Bổ sung theo quyết định ngày 09/11/2026 (D-23), giữ như code.</p></td>
<td>—</td>
<td><a href="#tìm-kiếm-lọc-và-xem-chi-tiết-bài">Tìm kiếm, lọc và xem chi tiết bài</a></td>
</tr>
<tr class="even">
<td><span id="BR_TK_02" class="anchor"></span><strong>TK-02</strong></td>
<td>Đăng ký cần email, mật khẩu, họ tên, số điện thoại. Email thuộc danh sách tên miền dùng một lần thì bị từ chối.</td>
<td>—</td>
<td><a href="#đăng-ký-tài-khoản-và-xác-thực-email">Đăng ký tài khoản và xác thực email</a></td>
</tr>
<tr class="odd">
<td><span id="BR_TK_03" class="anchor"></span><strong>TK-03</strong></td>
<td>SĐT bắt buộc, là số di động Việt Nam đầu 03 / 05 / 07 / 08 / 09 (nhận +84). Không xác thực SĐT bằng SMS.</td>
<td>10 chữ số</td>
<td><p><a href="#đăng-ký-tài-khoản-và-xác-thực-email">Đăng ký tài khoản và xác thực email</a></p>
<p><a href="#quản-lý-hồ-sơ-và-đổi-số-điện-thoại">Quản lý hồ sơ và đổi số điện thoại</a></p></td>
</tr>
<tr class="even">
<td><span id="BR_TK_04" class="anchor"></span><strong>TK-04</strong></td>
<td>Email và SĐT không trùng tài khoản đang hoạt động khác. Đăng ký lại một email đang chờ xác thực thì ghi đè thông tin cũ và gửi mã mới.</td>
<td>—</td>
<td><a href="#đăng-ký-tài-khoản-và-xác-thực-email">Đăng ký tài khoản và xác thực email</a></td>
</tr>
<tr class="odd">
<td><span id="BR_TK_05" class="anchor"></span><strong>TK-05</strong></td>
<td>Một địa chỉ IP tạo tối đa 3 tài khoản mới trong 24 giờ gần nhất; vượt thì báo lỗi 429.</td>
<td>accounts_per_ip_24h = 3</td>
<td><a href="#đăng-ký-tài-khoản-và-xác-thực-email">Đăng ký tài khoản và xác thực email</a></td>
</tr>
<tr class="even">
<td><span id="BR_TK_06" class="anchor"></span><strong>TK-06</strong></td>
<td>Tài khoản chỉ dùng được sau khi nhập đúng OTP gửi qua email: 6 số, sống 5 phút, sai 5 lần thì mã bị huỷ, gửi lại sau ít nhất 60 giây. Mã lưu dạng băm BCrypt.</td>
<td>otp_ttl_minutes = 5, otp_max_failed = 5, otp_resend_cooldown_seconds = 60</td>
<td><p><a href="#đăng-ký-tài-khoản-và-xác-thực-email">Đăng ký tài khoản và xác thực email</a></p>
<p><a href="#quên-mật-khẩu-và-đổi-mật-khẩu">Quên mật khẩu và đổi mật khẩu</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_TK_07" class="anchor"></span><strong>TK-07</strong></td>
<td>Tài khoản chưa xác thực quá 24 giờ bị xoá mềm; email đó đăng ký lại được.</td>
<td>pending_account_ttl_hours = 24</td>
<td><a href="#đăng-ký-tài-khoản-và-xác-thực-email">Đăng ký tài khoản và xác thực email</a></td>
</tr>
<tr class="even">
<td><span id="BR_TK_08" class="anchor"></span><strong>TK-08</strong></td>
<td>Đăng nhập bằng email + mật khẩu. Sai email hay sai mật khẩu đều cùng một câu báo. Đăng nhập đúng được cấp mã đăng nhập (JWT) sống 24 giờ; hết hạn thì đăng nhập lại.</td>
<td>app.jwt.ttl-minutes = 1440</td>
<td><a href="#đăng-nhập-và-đăng-xuất">Đăng nhập và đăng xuất</a></td>
</tr>
<tr class="odd">
<td><span id="BR_TK_09" class="anchor"></span><strong>TK-09</strong></td>
<td>Quên mật khẩu: hệ thống luôn trả lời giống nhau; chỉ gửi mã nếu email thuộc tài khoản đang hoạt động.</td>
<td>—</td>
<td><a href="#quên-mật-khẩu-và-đổi-mật-khẩu">Quên mật khẩu và đổi mật khẩu</a></td>
</tr>
<tr class="even">
<td><span id="BR_TK_10" class="anchor"></span><strong>TK-10</strong></td>
<td>Đổi mật khẩu: phải đúng mật khẩu hiện tại, mật khẩu mới khác mật khẩu cũ; đổi xong gửi email báo.</td>
<td>—</td>
<td><a href="#quên-mật-khẩu-và-đổi-mật-khẩu">Quên mật khẩu và đổi mật khẩu</a></td>
</tr>
<tr class="odd">
<td><span id="BR_TK_11" class="anchor"></span><strong>TK-11</strong></td>
<td>Đổi SĐT: nhập OTP gửi về email; tối đa 1 lần / 30 ngày; số mới chưa thuộc tài khoản khác; không đổi khi đang có giao dịch Đã chốt lịch hoặc Chờ xác nhận giao nhận.</td>
<td>phone_change_interval_days = 30</td>
<td><a href="#quản-lý-hồ-sơ-và-đổi-số-điện-thoại">Quản lý hồ sơ và đổi số điện thoại</a></td>
</tr>
<tr class="even">
<td><span id="BR_TK_12" class="anchor"></span><strong>TK-12</strong></td>
<td>Chỉ xem, sửa dữ liệu do chính mình tạo (hồ sơ, bài, yêu cầu, tin nhắn).</td>
<td>—</td>
<td><a href="#quản-lý-hồ-sơ-và-đổi-số-điện-thoại">Quản lý hồ sơ và đổi số điện thoại</a></td>
</tr>
<tr class="odd">
<td><span id="BR_TK_13" class="anchor"></span><strong>TK-13</strong></td>
<td>Admin là tài khoản tạo sẵn trong cơ sở dữ liệu, không đăng ký qua web, không đăng bài và không gửi yêu cầu.</td>
<td>—</td>
<td><a href="#đăng-nhập-và-đăng-xuất">Đăng nhập và đăng xuất</a></td>
</tr>
<tr class="even">
<td><span id="BR_TK_14" class="anchor"></span><strong>TK-14</strong></td>
<td>Email chỉ dùng để gửi OTP và báo đổi mật khẩu; không gửi thông tin giao dịch qua email.</td>
<td>—</td>
<td><p><a href="#quên-mật-khẩu-và-đổi-mật-khẩu">Quên mật khẩu và đổi mật khẩu</a></p>
<p><a href="#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc">Chốt lịch giao nhận và xem thông tin liên lạc</a></p>
<p><a href="#xem-thông-báo">Xem thông báo</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_TK_15" class="anchor"></span><strong>TK-15</strong></td>
<td>Mã đăng nhập chỉ chứa mã người dùng và vai trò. Tài khoản bị khoá hoặc bị cấm bị chặn ở mọi request (kể cả mã còn hạn). Đổi mật khẩu không làm mã cũ mất hiệu lực; mã cũ tự hết sau 24 giờ.</td>
<td>—</td>
<td><p><a href="#đăng-nhập-và-đăng-xuất">Đăng nhập và đăng xuất</a></p>
<p><a href="#quên-mật-khẩu-và-đổi-mật-khẩu">Quên mật khẩu và đổi mật khẩu</a></p></td>
</tr>
</tbody>
</table>

### 5.1.B Khoá và cấm tài khoản

Có hai cách chặn một tài khoản (D-09): Admin chủ động khoá có lý do và mở lại được, hoặc hệ thống tự cấm khi sao về 0 và không mở lại.

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 70%" />
<col style="width: 21%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Luật</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_KC_01" class="anchor"></span><strong>KC-01</strong></td>
<td>Admin khoá phải nhập lý do ≤ 500 ký tự, không tự khoá mình. Người bị khoá thấy lý do khi đăng nhập (ví dụ "spam bài", "nghi ngờ lừa đảo").</td>
<td><p><a href="#đăng-nhập-và-đăng-xuất">Đăng nhập và đăng xuất</a></p>
<p><a href="#quản-lý-tài-khoản-khoá-và-mở-khoá">Quản lý tài khoản: khoá và mở khoá</a></p></td>
</tr>
<tr class="even">
<td><span id="BR_KC_02" class="anchor"></span><strong>KC-02</strong></td>
<td>Tài khoản bị khoá hoặc bị cấm bị chặn ở mọi request, kể cả mã đăng nhập cấp trước đó (lỗi 403). Giao diện nhận lỗi này thì đăng xuất và đưa về màn đăng nhập kèm câu báo.</td>
<td><p><a href="#đăng-nhập-và-đăng-xuất">Đăng nhập và đăng xuất</a></p>
<p><a href="#quản-lý-tài-khoản-khoá-và-mở-khoá">Quản lý tài khoản: khoá và mở khoá</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_KC_03" class="anchor"></span><strong>KC-03</strong></td>
<td>Khi khoá, trong cùng một lần xử lý: huỷ mọi giao dịch đang mở của người đó (trừ giao dịch Đang tranh chấp) và nhả bài; từ chối mọi bài Chờ duyệt, bài đang giữ phí thì nhả phí; ẩn mọi bài Đang hiển thị. Một bước lỗi thì cả lần khoá không xảy ra.</td>
<td><a href="#quản-lý-tài-khoản-khoá-và-mở-khoá">Quản lý tài khoản: khoá và mở khoá</a></td>
</tr>
<tr class="even">
<td><span id="BR_KC_04" class="anchor"></span><strong>KC-04</strong></td>
<td>Mở khoá: hiện lại đúng các bài đã bị ẩn lúc khoá, trừ bài đã bị kết luận vi phạm hoặc đang đủ ngưỡng báo cáo. Giao dịch đã huỷ và bài đã bị từ chối không khôi phục.</td>
<td><a href="#quản-lý-tài-khoản-khoá-và-mở-khoá">Quản lý tài khoản: khoá và mở khoá</a></td>
</tr>
<tr class="odd">
<td><span id="BR_KC_05" class="anchor"></span><strong>KC-05</strong></td>
<td>Cấm: khi sao về 0, tài khoản chuyển Bị cấm và dọn dẹp giống khoá (KC-03), ẩn thêm mọi bài còn lại; người dùng nhận thông báo; màn đăng nhập báo "Tài khoản đã bị cấm".</td>
<td><a href="#quản-lý-tài-khoản-khoá-và-mở-khoá">Quản lý tài khoản: khoá và mở khoá</a></td>
</tr>
<tr class="even">
<td><span id="BR_KC_06" class="anchor"></span><strong>KC-06</strong></td>
<td>Tài khoản bị khoá / cấm không gửi yêu cầu được, và người khác không gửi yêu cầu tới bài của họ được.</td>
<td><a href="#quản-lý-tài-khoản-khoá-và-mở-khoá">Quản lý tài khoản: khoá và mở khoá</a></td>
</tr>
</tbody>
</table>

### 5.1.C Uy tín: sao, điểm, hạng

Sao đo mức đáng tin và chỉ giảm khi Admin kết luận vi phạm; điểm đo mức hoạt động và quyết định hạng. Sao, điểm, hạng đều không đổi ra tiền hay Credit.

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 54%" />
<col style="width: 17%" />
<col style="width: 19%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Luật</strong></th>
<th><strong>Con số</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_UT_01" class="anchor"></span><strong>UT-01</strong></td>
<td>Mỗi tài khoản bắt đầu với 5 sao, thang 0–5.</td>
<td>5</td>
<td><a href="#xem-sao-uy-tín-điểm-và-hạng">Xem sao uy tín, điểm và hạng</a></td>
</tr>
<tr class="even">
<td><span id="BR_UT_02" class="anchor"></span><strong>UT-02</strong></td>
<td>Chỉ trừ sao khi Admin chấp thuận một báo cáo / khiếu nại; mỗi lần 1 sao, không âm. Mới bị báo cáo thì chưa trừ.</td>
<td>1 sao / lần</td>
<td><p><a href="#xem-sao-uy-tín-điểm-và-hạng">Xem sao uy tín, điểm và hạng</a></p>
<p><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_UT_03" class="anchor"></span><strong>UT-03</strong></td>
<td>Mỗi lần bị trừ sao, người dùng nhận thông báo "Bạn bị trừ 1 sao. Bạn còn N sao trước khi tài khoản bị cấm."</td>
<td>—</td>
<td><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></td>
</tr>
<tr class="even">
<td><span id="BR_UT_04" class="anchor"></span><strong>UT-04</strong></td>
<td>Sao về 0 → tài khoản Bị cấm (KC-05).</td>
<td>0</td>
<td><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></td>
</tr>
<tr class="odd">
<td><span id="BR_UT_05" class="anchor"></span><strong>UT-05</strong></td>
<td>Hồi 1 sao sau mỗi 10 giao dịch hoàn tất liên tiếp không bị trừ sao, tối đa 5 sao. Bị trừ sao thì chuỗi đếm về 0.</td>
<td>reputation_clean_streak_for_star = 10</td>
<td><a href="#xem-sao-uy-tín-điểm-và-hạng">Xem sao uy tín, điểm và hạng</a></td>
</tr>
<tr class="even">
<td><span id="BR_UT_06" class="anchor"></span><strong>UT-06</strong></td>
<td>Cộng điểm khi giao dịch Hoàn tất: bài Cho +10 người cho, +2 người nhận; bài Trao đổi +6 mỗi bên. Hạng tính lại ngay.</td>
<td>10 / 2 / 6</td>
<td><p><a href="#xem-sao-uy-tín-điểm-và-hạng">Xem sao uy tín, điểm và hạng</a></p>
<p><a href="#xác-nhận-đã-giao-đã-nhận">Xác nhận đã giao, đã nhận</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_UT_07" class="anchor"></span><strong>UT-07</strong></td>
<td>Mỗi tài khoản được cộng tối đa 30 điểm / ngày theo giờ Việt Nam; chạm trần thì chỉ cộng phần còn lại.</td>
<td>daily_points_cap = 30</td>
<td><a href="#xem-sao-uy-tín-điểm-và-hạng">Xem sao uy tín, điểm và hạng</a></td>
</tr>
<tr class="even">
<td><span id="BR_UT_08" class="anchor"></span><strong>UT-08</strong></td>
<td>Chống cày điểm: từ giao dịch hoàn tất thứ 3 giữa cùng một cặp người trong 90 ngày thì không cộng điểm (vẫn ghi lịch sử); cặp có hơn 5 giao dịch hiện ở màn Giám sát bất thường.</td>
<td>90 ngày / thứ 3 / hơn 5</td>
<td><p><a href="#xem-sao-uy-tín-điểm-và-hạng">Xem sao uy tín, điểm và hạng</a></p>
<p><a href="#giám-sát-bất-thường-và-nhật-ký-hoạt-động">Giám sát bất thường và nhật ký hoạt động</a></p></td>
</tr>
<tr class="odd">
<td><strong>UT-09</strong></td>
<td>(Đã bỏ) Không có sao ân hạn.</td>
<td>—</td>
<td>—</td>
</tr>
<tr class="even">
<td><strong>UT-10</strong></td>
<td>Mọi thay đổi sao, điểm ghi nhật ký kèm lý do.</td>
<td>—</td>
<td>—</td>
</tr>
</tbody>
</table>

### 5.1.D Ví Credit và nạp tiền

Credit là tiền trả phí dịch vụ cho nền tảng, nạp duy nhất qua VNPay; hai người dùng không chuyển Credit hay trả tiền cho nhau qua hệ thống.

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 54%" />
<col style="width: 17%" />
<col style="width: 19%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Luật</strong></th>
<th><strong>Con số</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_VI_01" class="anchor"></span><strong>VI-01</strong></td>
<td>1 Credit = 1.000 đ, số nguyên. Credit không đổi ngược ra tiền, không tặng, không chuyển cho người khác.</td>
<td>vnd_per_credit = 1000</td>
<td><p><a href="#nạp-credit-qua-vnpay">Nạp Credit qua VNPay</a></p>
<p><a href="#xem-ví-và-lịch-sử-credit">Xem ví và lịch sử Credit</a></p></td>
</tr>
<tr class="even">
<td><span id="BR_VI_02" class="anchor"></span><strong>VI-02</strong></td>
<td>Số dư khả dụng = số dư − số đang bị giữ. Mọi thao tác có phí kiểm số dư khả dụng ngay lúc bấm.</td>
<td>—</td>
<td><a href="#xem-ví-và-lịch-sử-credit">Xem ví và lịch sử Credit</a></td>
</tr>
<tr class="odd">
<td><span id="BR_VI_03" class="anchor"></span><strong>VI-03</strong></td>
<td>Nạp tối thiểu 10 Credit (10.000 đ), số tiền là bội số của 1.000 đ. Tỉ lệ quy đổi chốt lúc tạo đơn.</td>
<td>topup_min_credits = 10</td>
<td><a href="#nạp-credit-qua-vnpay">Nạp Credit qua VNPay</a></td>
</tr>
<tr class="even">
<td><span id="BR_VI_04" class="anchor"></span><strong>VI-04</strong></td>
<td>Chỉ cộng Credit khi VNPay gọi IPN có chữ ký đúng, số tiền khớp và đơn còn Chờ thanh toán. IPN gửi lại cho đơn đã xử lý thì trả "đã xác nhận", không cộng lần hai. IPN báo thành công nhưng đơn đã quá hạn thì đơn chuyển Hết hạn, không cộng Credit, ghi cảnh báo để đối soát.</td>
<td>—</td>
<td><a href="#nạp-credit-qua-vnpay">Nạp Credit qua VNPay</a></td>
</tr>
<tr class="odd">
<td><span id="BR_VI_05" class="anchor"></span><strong>VI-05</strong></td>
<td>(Đã bỏ) Không có nạp thủ công. Trên máy dev dùng cổng giả lập (MOCK).</td>
<td>—</td>
<td><a href="#nạp-credit-qua-vnpay">Nạp Credit qua VNPay</a></td>
</tr>
<tr class="even">
<td><span id="BR_VI_06" class="anchor"></span><strong>VI-06</strong></td>
<td>Đơn Chờ thanh toán quá 30 phút thì Hết hạn. Cùng mốc này được gửi sang VNPay làm hạn của trang thanh toán.</td>
<td>payment_order_expire_minutes = 30</td>
<td><a href="#nạp-credit-qua-vnpay">Nạp Credit qua VNPay</a></td>
</tr>
<tr class="odd">
<td><span id="BR_VI_07" class="anchor"></span><strong>VI-07</strong></td>
<td>Mỗi lần cộng / trừ Credit nằm trong một giao dịch cơ sở dữ liệu có khoá dòng ví và ghi một dòng sổ cái; mỗi đơn nạp chỉ sinh một dòng Nạp.</td>
<td>—</td>
<td><a href="#nạp-credit-qua-vnpay">Nạp Credit qua VNPay</a></td>
</tr>
<tr class="even">
<td><span id="BR_VI_08" class="anchor"></span><strong>VI-08</strong></td>
<td>Không có chức năng Admin cộng / trừ Credit tay.</td>
<td>—</td>
<td><p><a href="#nạp-credit-qua-vnpay">Nạp Credit qua VNPay</a></p>
<p><a href="#báo-cáo-tài-chính-đơn-nạp-và-tra-cứu-ví">Báo cáo tài chính, đơn nạp và tra cứu ví</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_VI_09" class="anchor"></span><strong>VI-09</strong></td>
<td>Báo cáo tài chính tách ba sổ: tiền mặt thực thu (đơn thành công), Credit cộng vào, Credit trừ ra theo loại phí; tiền mặt thu phải bằng Credit nạp × 1.000 đ.</td>
<td>—</td>
<td><p><a href="#xem-ví-và-lịch-sử-credit">Xem ví và lịch sử Credit</a></p>
<p><a href="#báo-cáo-tài-chính-đơn-nạp-và-tra-cứu-ví">Báo cáo tài chính, đơn nạp và tra cứu ví</a></p></td>
</tr>
</tbody>
</table>

### 5.1.E Phí dịch vụ

Nền tảng thu phí cho việc hiển thị và dùng AI, không thu phí trên món đồ. Mọi khoản trừ đều có hộp xác nhận ghi rõ số Credit trước khi bấm.

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 28%" />
<col style="width: 10%" />
<col style="width: 17%" />
<col style="width: 16%" />
<col style="width: 19%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Thao tác</strong></th>
<th><strong>Phí</strong></th>
<th><strong>Khi nào trừ</strong></th>
<th><strong>Có hoàn không</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_PH_01" class="anchor"></span><strong>PH-01</strong></td>
<td>Đăng bài Cho</td>
<td>0</td>
<td>—</td>
<td>—</td>
<td><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></td>
</tr>
<tr class="even">
<td><span id="BR_PH_02" class="anchor"></span><strong>PH-02</strong></td>
<td>Đăng bài Trao đổi trong hạn mức theo hạng</td>
<td>5 Credit</td>
<td>Giữ khi gửi duyệt, trừ khi Admin duyệt lần đầu</td>
<td>Nhả nếu bị từ chối hoặc gỡ trước khi duyệt</td>
<td><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></td>
</tr>
<tr class="odd">
<td>PH-02</td>
<td>Đăng bài Trao đổi vượt hạn mức (không giới hạn trên)</td>
<td>10 Credit</td>
<td>Như trên</td>
<td>Như trên</td>
<td></td>
</tr>
<tr class="even">
<td><span id="BR_PH_04" class="anchor"></span><strong>PH-04</strong></td>
<td>Sửa bài: lượt đầu sau khi đã được duyệt / từ lượt hai</td>
<td>0 / 5 Credit</td>
<td>Giữ khi gửi bản sửa, trừ khi bản sửa được duyệt</td>
<td>Nhả nếu bản sửa bị từ chối (không tính lượt)</td>
<td><a href="#sửa-bài-và-gỡ-bài">Sửa bài và gỡ bài</a></td>
</tr>
<tr class="odd">
<td><span id="BR_PH_05" class="anchor"></span><strong>PH-05</strong></td>
<td>Gia hạn bài (cả bài Cho), +30 ngày</td>
<td>5 Credit</td>
<td>Ngay</td>
<td>Không</td>
<td><a href="#gia-hạn-bài">Gia hạn bài</a></td>
</tr>
<tr class="even">
<td><span id="BR_PH_06" class="anchor"></span><strong>PH-06</strong></td>
<td>Đẩy bài, +3 ngày</td>
<td>5 Credit</td>
<td>Ngay</td>
<td>Không, kể cả khi bài bị gỡ, ẩn, hết hạn</td>
<td><a href="#đẩy-bài">Đẩy bài</a></td>
</tr>
<tr class="odd">
<td><span id="BR_PH_07" class="anchor"></span><strong>PH-07</strong></td>
<td>AI trợ lý tìm đồ sau khi hết lượt miễn phí</td>
<td>2 Credit / lượt</td>
<td>Khi AI chạy xong và có kết quả</td>
<td>AI lỗi thì không trừ</td>
<td><a href="#ai-trợ-lý-tìm-đồ">AI trợ lý tìm đồ</a></td>
</tr>
<tr class="even">
<td><strong>PH-08</strong></td>
<td>Chuyển bài Cho → Trao đổi lúc chọn người (mục K)</td>
<td>5 Credit</td>
<td>Ngay</td>
<td>Không; Trao đổi → Cho cũng không hoàn phí đã trả</td>
<td>—</td>
</tr>
<tr class="odd">
<td><strong>PH-09</strong></td>
<td>Tìm kiếm thường, gửi yêu cầu, chat, chốt lịch, giao nhận, báo cáo, khiếu nại</td>
<td>0</td>
<td>—</td>
<td>—</td>
<td>—</td>
</tr>
<tr class="even">
<td><span id="BR_PH_03" class="anchor"></span><strong>PH-03</strong></td>
<td>Phí đăng của mỗi bài chỉ bị trừ một lần</td>
<td>Theo PH-02</td>
<td>Lần duyệt đầu tiên</td>
<td>Không hoàn khi bài đã duyệt rồi bị gỡ, hết hạn, vi phạm hay giao dịch không thành</td>
<td><p><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></p>
<p><a href="#sửa-bài-và-gỡ-bài">Sửa bài và gỡ bài</a></p>
<p><a href="#duyệt-bài-theo-checklist">Duyệt bài theo checklist</a></p></td>
</tr>
</tbody>
</table>

Lượt AI miễn phí: tài khoản đã từng nạp có 5 lượt / ngày; tài khoản chưa nạp có 1 lượt dùng thử.

### 5.1.F Bài đăng

Mỗi bài phải qua Admin duyệt trước khi ai đó nhìn thấy; từ lúc chủ bài chọn người thì bài bị khoá nội dung đến khi giao dịch kết thúc.

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 54%" />
<col style="width: 17%" />
<col style="width: 19%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Luật</strong></th>
<th><strong>Con số</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_BD_01" class="anchor"></span><strong>BD-01</strong></td>
<td>Bài có 3–8 ảnh, mỗi ảnh ≤ 5 MB, ảnh do chính người đăng tải lên.</td>
<td>item_photos_min = 3, item_photos_max = 8, image_max_mb = 5</td>
<td><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></td>
</tr>
<tr class="even">
<td><span id="BR_BD_02" class="anchor"></span><strong>BD-02</strong></td>
<td>Bắt buộc chọn hình thức, danh mục đang mở, quận đang dùng, tình trạng; nhập tiêu đề (≤ 150 ký tự) và mô tả. Bài Trao đổi bắt buộc ghi món mong muốn (hiển thị công khai).</td>
<td>—</td>
<td><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></td>
</tr>
<tr class="odd">
<td><span id="BR_BD_03" class="anchor"></span><strong>BD-03</strong></td>
<td>Tình trạng Có lỗi: bắt buộc mô tả lỗi và ít nhất 1 ảnh lỗi. Tình trạng khác không được gửi ảnh lỗi.</td>
<td>—</td>
<td><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></td>
</tr>
<tr class="even">
<td><span id="BR_BD_04" class="anchor"></span><strong>BD-04</strong></td>
<td>Danh mục điện tử bắt buộc khai trạng thái hoạt động; danh mục khác không khai.</td>
<td>—</td>
<td><p><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></p>
<p><a href="#quản-lý-danh-mục-thuộc-tính-khu-vực">Quản lý danh mục, thuộc tính, khu vực</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_BD_05" class="anchor"></span><strong>BD-05</strong></td>
<td>Hạn mức bài Cho: tối đa 5 bài Đang hiển thị hoặc Chờ duyệt, mọi hạng.</td>
<td>max_active_give_items = 5</td>
<td><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></td>
</tr>
<tr class="even">
<td><span id="BR_BD_06" class="anchor"></span><strong>BD-06</strong></td>
<td>Bài Trao đổi được phí thường theo hạng (Đồng 5, Bạc 8, Vàng 15, Kim cương không giới hạn); vượt thì vẫn đăng được với phí 10 Credit (PH-02).</td>
<td>max_active_items_bronze / silver / gold / diamond = 5 / 8 / 15 / −1</td>
<td><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></td>
</tr>
<tr class="odd">
<td><span id="BR_BD_07" class="anchor"></span><strong>BD-07</strong></td>
<td>Tài khoản 0 sao, bị khoá, bị cấm hoặc là Admin thì không đăng bài được.</td>
<td>—</td>
<td><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></td>
</tr>
<tr class="even">
<td><span id="BR_BD_08" class="anchor"></span><strong>BD-08</strong></td>
<td>Phí sửa theo bảng dưới; hệ thống báo trước số phí trước khi gửi.</td>
<td>edit_fee = 5</td>
<td><a href="#sửa-bài-và-gỡ-bài">Sửa bài và gỡ bài</a></td>
</tr>
<tr class="odd">
<td><span id="BR_BD_09" class="anchor"></span><strong>BD-09</strong></td>
<td>Mọi bản sửa quay lại Chờ duyệt, bài ẩn khỏi tìm kiếm tới khi duyệt xong; các yêu cầu đang chờ của bài giữ nguyên.</td>
<td>—</td>
<td><a href="#sửa-bài-và-gỡ-bài">Sửa bài và gỡ bài</a></td>
</tr>
<tr class="even">
<td><span id="BR_BD_10" class="anchor"></span><strong>BD-10</strong></td>
<td>Chỉ sửa / gỡ được khi là chủ bài, còn sao, bài không ở Đang giao dịch / Đã trao / Hết hạn và chưa có yêu cầu nào được chọn. Không đổi hình thức Cho ↔︎ Trao đổi khi sửa (đổi theo mục K hoặc đăng bài mới).</td>
<td>—</td>
<td><a href="#sửa-bài-và-gỡ-bài">Sửa bài và gỡ bài</a></td>
</tr>
<tr class="odd">
<td><span id="BR_BD_11" class="anchor"></span><strong>BD-11</strong></td>
<td>Gỡ bài = xoá mềm, nhả phí đang giữ; các yêu cầu Chờ chủ bài chọn bị huỷ ngay với lý do "[Hệ thống] Bài đã bị gỡ" và người gửi được báo (D-13).</td>
<td>—</td>
<td><a href="#sửa-bài-và-gỡ-bài">Sửa bài và gỡ bài</a></td>
</tr>
<tr class="even">
<td><span id="BR_BD_12" class="anchor"></span><strong>BD-12</strong></td>
<td>Thời gian hiển thị tính từ lần duyệt đầu: 30 ngày; hạng Vàng trở lên 45 ngày.</td>
<td>post_display_days = 30, post_display_days_gold = 45</td>
<td><a href="#duyệt-bài-theo-checklist">Duyệt bài theo checklist</a></td>
</tr>
</tbody>
</table>

### 5.1.G Gia hạn và đẩy bài

Hai cách trả phí để bài được thấy nhiều hơn; cả hai không qua duyệt lại vì nội dung không đổi.

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 54%" />
<col style="width: 17%" />
<col style="width: 19%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Luật</strong></th>
<th><strong>Con số</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_HT_01" class="anchor"></span><strong>HT-01</strong></td>
<td>Gia hạn: bài của mình, Đang hiển thị và không bị ẩn, đủ 5 Credit khả dụng. Hạn mới = (hạn cũ nếu còn, không thì bây giờ) + 30 ngày, mọi hạng. Gia hạn lúc nào cũng được.</td>
<td>renew_fee = 5, +30 ngày</td>
<td><a href="#gia-hạn-bài">Gia hạn bài</a></td>
</tr>
<tr class="even">
<td><span id="BR_HT_02" class="anchor"></span><strong>HT-02</strong></td>
<td>Hộp xác nhận báo trước phí và hạn mới (gia hạn: hạn hiển thị mới; đẩy: đẩy tới ngày nào).</td>
<td>—</td>
<td><p><a href="#gia-hạn-bài">Gia hạn bài</a></p>
<p><a href="#đẩy-bài">Đẩy bài</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_HT_03" class="anchor"></span><strong>HT-03</strong></td>
<td>Bài Hết hạn không gia hạn được; muốn hiện lại thì đăng bài mới (D-06).</td>
<td>—</td>
<td><a href="#gia-hạn-bài">Gia hạn bài</a></td>
</tr>
<tr class="even">
<td><span id="BR_HT_04" class="anchor"></span><strong>HT-04</strong></td>
<td><p>Đẩy bài: bài Đang hiển thị, không bị ẩn, thuộc danh mục cho phép đẩy (Admin bật / tắt theo danh mục). Mỗi lần mua là một gói 3 ngày, trừ ngay. Một đợt đẩy liên tục tối đa 6 ngày tính từ lúc bắt đầu đợt; hết đợt phải nghỉ 3 ngày mới mở đợt mới; không vượt hạn hiển thị của bài.</p>
<p>Ghi chú: Cập nhật theo code 06/11/2026 (thiết kế xoay vòng đã chốt). BR v12 ghi "cộng dồn tối đa 14 ngày tính từ bây giờ".</p></td>
<td>boost_fee = 5, boost_days = 3, boost_max_days = 6, boost_cooldown_days = 3</td>
<td><p><a href="#đẩy-bài">Đẩy bài</a></p>
<p><a href="#quản-lý-danh-mục-thuộc-tính-khu-vực">Quản lý danh mục, thuộc tính, khu vực</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_HT_05" class="anchor"></span><strong>HT-05</strong></td>
<td><p>Khi tìm / duyệt bài: tối đa 3 bài đang đẩy ở đầu mỗi trang, chọn theo vòng xoay (thứ tự cố định theo id, đổi nhóm mỗi 3 phút, mỗi người xem bắt đầu ở một vị trí khác); bài đẩy không lên đầu vẫn có trong phần bài thường. Nhãn "Được đẩy" không hiện trên giao diện.</p>
<p>Ghi chú: Cập nhật theo code 06/11/2026. BR v12 ghi "mọi bài đang được đẩy đứng trước, bài đẩy gần nhất lên đầu".</p></td>
<td>boost_max_per_page = 3, boost_rotate_seconds = 180</td>
<td><p><a href="#đẩy-bài">Đẩy bài</a></p>
<p><a href="#tìm-kiếm-lọc-và-xem-chi-tiết-bài">Tìm kiếm, lọc và xem chi tiết bài</a></p></td>
</tr>
<tr class="even">
<td><span id="BR_HT_06" class="anchor"></span><strong>HT-06</strong></td>
<td>Hết thời gian đẩy thì bài tự về vị trí thường, không cần tác vụ nền (truy vấn so boosted_until với giờ hiện tại).</td>
<td>—</td>
<td><a href="#đẩy-bài">Đẩy bài</a></td>
</tr>
<tr class="odd">
<td><span id="BR_HT_07" class="anchor"></span><strong>HT-07</strong></td>
<td><p>Mỗi người tối đa 3 bài đang đẩy cùng lúc; toàn hệ thống tối đa 30 bài đang đẩy. Mua thêm gói cho bài đang đẩy không chiếm suất mới. Hết suất thì báo thời điểm sớm nhất có suất.</p>
<p>Ghi chú: Luật mới, bổ sung theo code 06/11/2026; chưa có trong BR v12.</p></td>
<td>boost_max_per_user = 3, boost_max_active = 30</td>
<td><a href="#đẩy-bài">Đẩy bài</a></td>
</tr>
<tr class="even">
<td><span id="BR_HT_08" class="anchor"></span><strong>HT-08</strong></td>
<td><p>Banner trang chủ hiển thị một nhóm bài đang đẩy (mặc định 5 bài), đổi nhóm mỗi 3 phút theo cùng vòng xoay.</p>
<p>Ghi chú: Luật mới, bổ sung theo code (GET /items/featured); chưa có trong BR v12.</p></td>
<td>boost_rotate_seconds = 180</td>
<td><a href="#đẩy-bài">Đẩy bài</a></td>
</tr>
<tr class="odd">
<td><span id="BR_HT_09" class="anchor"></span><strong>HT-09</strong></td>
<td><p>24 giờ trước khi bài hết đẩy, chủ bài nhận thông báo kèm mốc còn mua thêm gói được.</p>
<p>Ghi chú: Luật mới, bổ sung theo code; chưa có trong BR v12.</p></td>
<td>boost_ending_notice_hours = 24</td>
<td><a href="#đẩy-bài">Đẩy bài</a></td>
</tr>
</tbody>
</table>

### 5.1.H Kiểm duyệt nội dung và danh mục

Bài mới hoặc vừa sửa đi qua hai lớp: kiểm tra tự động (tầng 1) chặn ngay lúc gửi, rồi Admin duyệt 100% theo checklist. Không dùng AI trong duyệt bài (D-03).

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 70%" />
<col style="width: 21%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Luật</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_KD_01" class="anchor"></span><strong>KD-01</strong></td>
<td>Hai lớp: tầng 1 tự động, rồi Admin duyệt mọi bài qua được tầng 1. Không có bước AI.</td>
<td><a href="#duyệt-bài-theo-checklist">Duyệt bài theo checklist</a></td>
</tr>
<tr class="even">
<td><span id="BR_KD_02" class="anchor"></span><strong>KD-02</strong></td>
<td>Tầng 1 chặn: thiếu trường bắt buộc; danh mục bị hạn chế, đang ẩn hoặc quận không hợp lệ; từ khoá cấm mức Chặn; SĐT, email, URL trong tiêu đề, mô tả, món mong muốn (kể cả viết cách quãng bằng dấu cách, chấm, gạch). Bị chặn thì sửa ngay trên form, không mất phí.</td>
<td><p><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></p>
<p><a href="#quản-lý-từ-khoá-cấm-checklist-lý-do-từ-chối-mẫu">Quản lý từ khoá cấm, checklist, lý do từ chối mẫu</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_KD_03" class="anchor"></span><strong>KD-03</strong></td>
<td>Từ khoá cấm có hai mức: Chặn (BLOCK) chặn ngay; Gắn cờ (REVIEW) vẫn gửi duyệt và Admin thấy từ bị gắn cờ. Từ chỉ khớp sau khi bỏ dấu thì hạ xuống Gắn cờ. Mỗi từ có danh sách ngoại lệ.</td>
<td><p><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></p>
<p><a href="#quản-lý-từ-khoá-cấm-checklist-lý-do-từ-chối-mẫu">Quản lý từ khoá cấm, checklist, lý do từ chối mẫu</a></p></td>
</tr>
<tr class="even">
<td><span id="BR_KD_04" class="anchor"></span><strong>KD-04</strong></td>
<td>Admin duyệt theo checklist do Admin cấu hình (ít nhất 1 câu đang bật); phải tích đạt hết mới duyệt được. Mỗi lần duyệt lưu lại câu hỏi và câu trả lời lúc đó; checklist vừa đổi thì phải tích lại.</td>
<td><p><a href="#duyệt-bài-theo-checklist">Duyệt bài theo checklist</a></p>
<p><a href="#quản-lý-từ-khoá-cấm-checklist-lý-do-từ-chối-mẫu">Quản lý từ khoá cấm, checklist, lý do từ chối mẫu</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_KD_05" class="anchor"></span><strong>KD-05</strong></td>
<td>Từ chối bắt buộc có lý do (chọn lý do mẫu hoặc tự nhập), gửi cho người đăng.</td>
<td><p><a href="#duyệt-bài-theo-checklist">Duyệt bài theo checklist</a></p>
<p><a href="#quản-lý-từ-khoá-cấm-checklist-lý-do-từ-chối-mẫu">Quản lý từ khoá cấm, checklist, lý do từ chối mẫu</a></p></td>
</tr>
<tr class="even">
<td><span id="BR_KD_06" class="anchor"></span><strong>KD-06</strong></td>
<td>Hàng chờ duyệt xếp: người đăng ít sao trước → tài khoản mới hơn trước → bài chờ lâu hơn trước. Không có thông báo nhắc Admin khi bài chờ quá 24 giờ (đã bỏ).</td>
<td><a href="#duyệt-bài-theo-checklist">Duyệt bài theo checklist</a></td>
</tr>
<tr class="odd">
<td><span id="BR_KD_07" class="anchor"></span><strong>KD-07</strong></td>
<td>Không có danh mục thực phẩm. Các từ khoá cấm về thực phẩm tươi sống, đồ ăn chế biến, thực phẩm chức năng, sữa và đồ ăn dặm cho trẻ, rượu bia, thuốc lá giữ nguyên để chặn người đăng đồ ăn.</td>
<td><a href="#quản-lý-từ-khoá-cấm-checklist-lý-do-từ-chối-mẫu">Quản lý từ khoá cấm, checklist, lý do từ chối mẫu</a></td>
</tr>
<tr class="even">
<td><span id="BR_KD_08" class="anchor"></span><strong>KD-08</strong></td>
<td>Chỉ đăng trong danh mục đang mở. Danh mục bị hạn chế (Dược phẩm và thiết bị y tế) không hiện ở bất kỳ chỗ nào người dùng chọn danh mục; Admin vẫn thấy để quản lý.</td>
<td><p><a href="#tìm-kiếm-lọc-và-xem-chi-tiết-bài">Tìm kiếm, lọc và xem chi tiết bài</a></p>
<p><a href="#quản-lý-danh-mục-thuộc-tính-khu-vực">Quản lý danh mục, thuộc tính, khu vực</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_KD_09" class="anchor"></span><strong>KD-09</strong></td>
<td>Cấm: vật phẩm cần sang tên pháp lý (xe có biển số, giấy tờ, sim chính chủ, tài khoản…), vật quy đổi ngay ra tiền (vàng, ngoại tệ, thẻ cào, voucher, tiền mã hoá), hàng nhái, hàng giả, vũ khí, chất cấm.</td>
<td><a href="#đăng-bài-cho-hoặc-trao-đổi">Đăng bài Cho hoặc Trao đổi</a></td>
</tr>
<tr class="even">
<td><span id="BR_KD_10" class="anchor"></span><strong>KD-10</strong></td>
<td>Không có chức năng hàng chính hãng (tải chứng từ, nhãn "đã kiểm chứng từ") (D-08).</td>
<td>—</td>
</tr>
<tr class="odd">
<td><span id="BR_KD_11" class="anchor"></span><strong>KD-11</strong></td>
<td>Hai Admin cùng xử lý một bài thì chỉ một người thành công; phí không bị trừ hai lần.</td>
<td><a href="#duyệt-bài-theo-checklist">Duyệt bài theo checklist</a></td>
</tr>
</tbody>
</table>

### 5.1.I Tìm kiếm và AI

Tìm kiếm thường miễn phí cho cả khách. AI chỉ là trợ lý giúp tìm đúng món; AI không bao giờ tự tạo ra bài, mọi kết quả đều là bài có thật trong cơ sở dữ liệu.

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 54%" />
<col style="width: 17%" />
<col style="width: 19%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Luật</strong></th>
<th><strong>Con số</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_TT_01" class="anchor"></span><strong>TT-01</strong></td>
<td><p>Tìm thường: chỉ bài Đang hiển thị, không bị ẩn; không phân biệt dấu, hoa thường; lọc theo danh mục, quận, tình trạng, hình thức. Bài đang được đẩy đứng trước (HT-05). Từ khoá chỉ tìm trong tiêu đề bài, không tìm trong mô tả.</p>
<p>Ghi chú: Bổ sung theo quyết định ngày 09/11/2026 (D-22), giữ như code.</p></td>
<td>—</td>
<td><a href="#tìm-kiếm-lọc-và-xem-chi-tiết-bài">Tìm kiếm, lọc và xem chi tiết bài</a></td>
</tr>
<tr class="even">
<td><span id="BR_TT_02" class="anchor"></span><strong>TT-02</strong></td>
<td>AI trợ lý chỉ dành cho thành viên đã đăng nhập (không phải Admin); câu hỏi ≤ 500 ký tự, không chứa SĐT, email, URL.</td>
<td>500 ký tự</td>
<td><a href="#ai-trợ-lý-tìm-đồ">AI trợ lý tìm đồ</a></td>
</tr>
<tr class="odd">
<td><span id="BR_TT_03" class="anchor"></span><strong>TT-03</strong></td>
<td>Trước khi gọi AI, hệ thống kiểm còn lượt miễn phí hoặc đủ 2 Credit khả dụng; thiếu thì báo nạp Credit.</td>
<td>ai_search_fee = 2, ai_free_per_day = 5, ai_trial_uses = 1</td>
<td><a href="#ai-trợ-lý-tìm-đồ">AI trợ lý tìm đồ</a></td>
</tr>
<tr class="even">
<td><span id="BR_TT_04" class="anchor"></span><strong>TT-04</strong></td>
<td><p>Hệ thống gửi danh sách bài ứng viên (bài Đang hiển thị, không bị ẩn, còn hạn) theo lô cho AI; AI chỉ chọn ID trong danh sách đó (tối đa 6 bài) và viết một câu trả lời ngắn. ID lạ bị loại và mỗi bài được kiểm lại trước khi trả, nên kết quả luôn là bài có thật. Mỗi câu hỏi được xử lý riêng, không nhớ các câu trước. Câu hỏi ngoài phạm vi tìm đồ bị từ chối, không tính lượt.</p>
<p>Ghi chú: Chốt ngày 09/11/2026 giữ như code (D-21). BR v12 ghi "AI chỉ trả bộ lọc; hệ thống truy vấn, không ra bài thì nới bộ lọc".</p></td>
<td>—</td>
<td><a href="#ai-trợ-lý-tìm-đồ">AI trợ lý tìm đồ</a></td>
</tr>
<tr class="odd">
<td><span id="BR_TT_05" class="anchor"></span><strong>TT-05</strong></td>
<td><p>AI lỗi, quá 15 giây hoặc trả sai định dạng: hệ thống báo trợ lý chưa trả lời được và trả danh sách rỗng, không tính lượt, không trừ Credit; người dùng có thể dùng tìm kiếm thường.</p>
<p>Ghi chú: Chốt ngày 09/11/2026 giữ như code (D-21). BR v12 ghi "tự chuyển sang tìm thường bằng chính câu đã nhập".</p></td>
<td>ai_search_timeout_seconds = 15</td>
<td><a href="#ai-trợ-lý-tìm-đồ">AI trợ lý tìm đồ</a></td>
</tr>
<tr class="even">
<td><span id="BR_TT_06" class="anchor"></span><strong>TT-06</strong></td>
<td>Không gửi email, SĐT, họ tên, dữ liệu ví tới dịch vụ AI; nội dung người dùng được tách khỏi chỉ thị hệ thống.</td>
<td>—</td>
<td><p><a href="#gợi-ý-mô-tả-bằng-ai">Gợi ý mô tả bằng AI</a></p>
<p><a href="#ai-trợ-lý-tìm-đồ">AI trợ lý tìm đồ</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_TT_07" class="anchor"></span><strong>TT-07</strong></td>
<td>Mỗi lượt gọi AI ghi nhật ký (model, kết quả, thời gian, có tính phí không), không ghi câu người dùng hỏi.</td>
<td>—</td>
<td><p><a href="#gợi-ý-mô-tả-bằng-ai">Gợi ý mô tả bằng AI</a></p>
<p><a href="#ai-trợ-lý-tìm-đồ">AI trợ lý tìm đồ</a></p></td>
</tr>
<tr class="even">
<td><span id="BR_TT_08" class="anchor"></span><strong>TT-08</strong></td>
<td><p>Gợi ý mô tả khi đăng bài: người đăng nhập tên món rồi bấm "Gợi ý mô tả bằng AI". AI tạo bản nháp mô tả chỉ từ tên món và 2–4 câu hỏi để người đăng tự điền; chỉ điền vào ô Mô tả khi người đăng chọn dùng. Kết quả được lọc thông tin liên hệ và từ khoá cấm; AI lỗi thì không chặn đăng bài.</p>
<p>Ghi chú: BR v12 ghi "(đề xuất, đang làm)", AI đọc 3 ảnh; code đã làm nhưng chỉ dùng tên món, không gửi ảnh.</p></td>
<td>—</td>
<td><a href="#gợi-ý-mô-tả-bằng-ai">Gợi ý mô tả bằng AI</a></td>
</tr>
<tr class="odd">
<td><span id="BR_TT_09" class="anchor"></span><strong>TT-09</strong></td>
<td><p>Gợi ý mô tả miễn phí, tối đa 5 lượt thành công / người / ngày (giờ Việt Nam), chờ tối đa 20 giây.</p>
<p>Ghi chú: BR v12 ghi "(đề xuất, chờ nhóm chốt)"; code đã áp dụng đúng các con số này.</p></td>
<td>ai_describe_per_day = 5, ai_describe_timeout_seconds = 20</td>
<td><a href="#gợi-ý-mô-tả-bằng-ai">Gợi ý mô tả bằng AI</a></td>
</tr>
</tbody>
</table>

### 5.1.J Yêu cầu và chọn người

Người nhận gửi yêu cầu xin (bài Cho) hoặc đề nghị đổi (bài Trao đổi); chủ bài xem danh sách rồi chọn đúng một người.

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 54%" />
<col style="width: 17%" />
<col style="width: 19%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Luật</strong></th>
<th><strong>Con số</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_YC_01" class="anchor"></span><strong>YC-01</strong></td>
<td>Không gửi yêu cầu cho bài của chính mình.</td>
<td>—</td>
<td><a href="#gửi-yêu-cầu-xin-hoặc-đề-nghị-đổi">Gửi yêu cầu xin hoặc đề nghị đổi</a></td>
</tr>
<tr class="even">
<td><span id="BR_YC_02" class="anchor"></span><strong>YC-02</strong></td>
<td>Chỉ gửi tới bài Đang hiển thị, không bị ẩn, chủ bài không bị khoá / cấm. Người gửi phải còn sao, không bị khoá / cấm, không phải Admin.</td>
<td>—</td>
<td><a href="#gửi-yêu-cầu-xin-hoặc-đề-nghị-đổi">Gửi yêu cầu xin hoặc đề nghị đổi</a></td>
</tr>
<tr class="odd">
<td><span id="BR_YC_03" class="anchor"></span><strong>YC-03</strong></td>
<td>Mỗi người chỉ có một yêu cầu đang mở với cùng một bài; tối đa 5 yêu cầu Chờ chủ bài chọn cùng lúc (gửi song song tới nhiều bài khác nhau).</td>
<td>max_pending_requests = 5</td>
<td><a href="#gửi-yêu-cầu-xin-hoặc-đề-nghị-đổi">Gửi yêu cầu xin hoặc đề nghị đổi</a></td>
</tr>
<tr class="even">
<td><span id="BR_YC_04" class="anchor"></span><strong>YC-04</strong></td>
<td><p>Món đem đổi phải là bài của chính người gửi, Đang hiển thị, không bị ẩn; chọn từ danh sách bài, không nhập tự do. Bài Trao đổi bắt buộc kèm món đổi; bài Cho được kèm món đổi nhưng không bắt buộc (CD-01).</p>
<p>Ghi chú: Cập nhật theo code (CD-01). BR v12 ghi "bài Cho hiện chưa nhận món kèm".</p></td>
<td>—</td>
<td><a href="#gửi-yêu-cầu-xin-hoặc-đề-nghị-đổi">Gửi yêu cầu xin hoặc đề nghị đổi</a></td>
</tr>
<tr class="odd">
<td><span id="BR_YC_05" class="anchor"></span><strong>YC-05</strong></td>
<td>Người nhận tự rút yêu cầu khi còn Chờ chủ bài chọn. Quá 3 ngày chưa được chọn thì tự huỷ.</td>
<td>request_pending_days = 3</td>
<td><a href="#rút-yêu-cầu">Rút yêu cầu</a></td>
</tr>
<tr class="even">
<td><span id="BR_YC_06" class="anchor"></span><strong>YC-06</strong></td>
<td>Chủ bài xem danh sách người xin (sao, hạng, tỉ lệ đúng mô tả, món đem đổi) và chọn đúng một người.</td>
<td>—</td>
<td><a href="#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi">Chọn người giao dịch và chuyển hình thức Cho ↔︎ Trao đổi</a></td>
</tr>
<tr class="odd">
<td><span id="BR_YC_07" class="anchor"></span><strong>YC-07</strong></td>
<td>Khi chọn: yêu cầu → Đã được chọn, bài và món đem đổi → Đang giao dịch; các yêu cầu khác của bài → Không được chọn; các yêu cầu khác đang dùng cùng món đem đổi ở bài khác → Đã huỷ. Mở chat, ghim thẻ bài (bài Trao đổi ghim cả hai món).</td>
<td>—</td>
<td><a href="#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi">Chọn người giao dịch và chuyển hình thức Cho ↔︎ Trao đổi</a></td>
</tr>
</tbody>
</table>

### 5.1.K Chuyển đổi Cho ↔ Trao đổi

Đã chốt (D-02) và đã có trong code: hình thức chỉ được đổi ở hai thời điểm, lúc chủ bài chọn người hoặc trong chat của giao dịch bài Cho trước khi chốt lịch. Không đổi qua màn sửa bài.

| **Mã**                                              | **Luật**                                                                                                                                                                                                                                    | **Con số**   | **Dùng ở**                                                                                                        |
|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------|-------------------------------------------------------------------------------------------------------------------|
| <span id="BR_CD_01" class="anchor"></span>**CD-01** | Ở bài Cho, người xin được kèm một bài Đang hiển thị của mình làm món đổi (không bắt buộc). Ở bài Trao đổi, người xin được gửi yêu cầu không kèm món đổi.                                                                                    | —            | [Gửi yêu cầu xin hoặc đề nghị đổi](#gửi-yêu-cầu-xin-hoặc-đề-nghị-đổi)                                             |
| <span id="BR_CD_02" class="anchor"></span>**CD-02** | Cho → Trao đổi lúc chọn người: chủ bài chọn một người có kèm món đổi → hộp xác nhận "Chuyển bài sang Trao đổi, trừ 5 Credit". Đồng ý và đủ khả dụng → trừ 5 Credit, bài thành Trao đổi, chat mở. Không đồng ý thì không chọn được người đó. | post_fee = 5 | [Chọn người giao dịch và chuyển hình thức Cho ↔ Trao đổi](#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi) |
| <span id="BR_CD_03" class="anchor"></span>**CD-03** | Cho → Trao đổi trong chat: giao dịch bài Cho chưa chốt lịch, người nhận muốn đưa một bài của mình vào đổi → chủ bài được hỏi có chuyển sang Trao đổi (5 Credit) không; đồng ý và trả phí thì món đó thành món đổi và chuyển Đang giao dịch. | post_fee = 5 | [Chọn người giao dịch và chuyển hình thức Cho ↔ Trao đổi](#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi) |
| <span id="BR_CD_04" class="anchor"></span>**CD-04** | Trao đổi → Cho: chủ bài chọn một người không kèm món đổi → hộp xác nhận "Chuyển bài sang Cho, không hoàn phí đã trả". Đồng ý thì bài thành Cho và mở chat.                                                                                  | —            | [Chọn người giao dịch và chuyển hình thức Cho ↔ Trao đổi](#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi) |
| <span id="BR_CD_05" class="anchor"></span>**CD-05** | Trong giao dịch bài Cho, người nhận không gửi ảnh trong chat; chủ bài gửi ảnh từ khi Đã chốt lịch.                                                                                                                                          | —            | [Nhắn tin trong giao dịch](#nhắn-tin-trong-giao-dịch)                                                             |
| <span id="BR_CD_06" class="anchor"></span>**CD-06** | Phí chuyển ghi sổ cái loại Phí đăng (POST_FEE), trừ ngay; bài không phải duyệt lại vì nội dung không đổi. Không kiểm hạn mức bài Trao đổi khi chuyển.                                                                                       | —            | [Chọn người giao dịch và chuyển hình thức Cho ↔ Trao đổi](#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi) |
| <span id="BR_CD_07" class="anchor"></span>**CD-07** | Điểm uy tín tính theo hình thức cuối cùng khi hoàn tất (Cho: +10 / +2; Trao đổi: +6 / +6).                                                                                                                                                  | —            | [Chọn người giao dịch và chuyển hình thức Cho ↔ Trao đổi](#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi) |
| **CD-08**                                           | Chưa có chức năng đổi sang món khác sau khi món đổi đã chốt; nhóm xếp vào nhóm Should, làm sau.                                                                                                                                             | —            | —                                                                                                                 |

### 5.1.L Chat trong giao dịch

Chat giúp hai bên thống nhất mà không lộ liên lạc trước khi chốt lịch; mọi tin nhắn ở lại trong hệ thống để làm bằng chứng khi có khiếu nại.

| **Mã**                                              | **Luật**                                                                                                                                                                           | **Con số**               | **Dùng ở**                                            |
|-----------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|-------------------------------------------------------|
| <span id="BR_CH_01" class="anchor"></span>**CH-01** | Gửi tin được khi giao dịch từ Đã được chọn tới Đang tranh chấp; sau khi kết thúc (hoàn tất / huỷ) chỉ xem lại.                                                                     | —                        | [Nhắn tin trong giao dịch](#nhắn-tin-trong-giao-dịch) |
| <span id="BR_CH_02" class="anchor"></span>**CH-02** | Trước khi Đã chốt lịch: chặn tin chứa SĐT, email, URL, tên mạng xã hội và lời mời liên hệ ngoài (zalo, facebook, ib, inbox…), kể cả viết cách quãng, viết số bằng chữ, chèn ký tự. | —                        | [Nhắn tin trong giao dịch](#nhắn-tin-trong-giao-dịch) |
| <span id="BR_CH_03" class="anchor"></span>**CH-03** | Từ khoá cấm mức Chặn bị chặn ở mọi thời điểm.                                                                                                                                      | —                        | [Nhắn tin trong giao dịch](#nhắn-tin-trong-giao-dịch) |
| <span id="BR_CH_04" class="anchor"></span>**CH-04** | Chỉ kiểm tin bằng tầng 1 (quy tắc và từ khoá); không gọi AI kiểm tin (D-17).                                                                                                       | —                        | [Nhắn tin trong giao dịch](#nhắn-tin-trong-giao-dịch) |
| <span id="BR_CH_05" class="anchor"></span>**CH-05** | Tin bị chặn: người gửi thấy lý do; tính một lần vi phạm. Đủ 3 lần vi phạm trong một giao dịch thì cảnh báo Admin.                                                                  | chat_violation_alert = 3 | [Nhắn tin trong giao dịch](#nhắn-tin-trong-giao-dịch) |
| <span id="BR_CH_06" class="anchor"></span>**CH-06** | Chỉ chia sẻ thẻ của bài Đang hiển thị. Đầu chat ghim thẻ bài; giao dịch Trao đổi ghim cả hai món.                                                                                  | —                        | [Nhắn tin trong giao dịch](#nhắn-tin-trong-giao-dịch) |
| <span id="BR_CH_07" class="anchor"></span>**CH-07** | Ảnh trong chat chỉ gửi từ khi Đã chốt lịch; người nhận của bài Cho không gửi ảnh (CD-05).                                                                                          | —                        | [Nhắn tin trong giao dịch](#nhắn-tin-trong-giao-dịch) |
| <span id="BR_CH_08" class="anchor"></span>**CH-08** | Tin mới hiện sau tối đa khoảng 5 giây (trình duyệt hỏi định kỳ, không dùng kết nối thời gian thực).                                                                                | 5 giây                   | [Nhắn tin trong giao dịch](#nhắn-tin-trong-giao-dịch) |

### 5.1.M Lịch hẹn và giao nhận

Từ lúc chọn người tới lúc hoàn tất. Sơ đồ dưới tóm tắt toàn bộ vòng đời một giao dịch.

| **Mã**                                              | **Luật**                                                                                                                                                                                                                                                  | **Con số**                 | **Dùng ở**                                                                                      |
|-----------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------|-------------------------------------------------------------------------------------------------|
| <span id="BR_GN_01" class="anchor"></span>**GN-01** | Bên nào cũng đề xuất lịch được: hình thức, giờ hẹn (phải ở tương lai), địa điểm (≤ 255 ký tự). Bên đề xuất tự được tính là đồng ý; đề xuất mới xoá đồng ý cũ của bên kia (D-12).                                                                          | 255 ký tự                  | [Chốt lịch giao nhận và xem thông tin liên lạc](#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc) |
| <span id="BR_GN_02" class="anchor"></span>**GN-02** | Gửi qua đơn vị vận chuyển: cả hai bên phải bấm "Cam kết chịu trách nhiệm" với nội dung hiển thị đầy đủ.                                                                                                                                                   | —                          | [Chốt lịch giao nhận và xem thông tin liên lạc](#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc) |
| <span id="BR_GN_03" class="anchor"></span>**GN-03** | Hai bên cùng đồng ý → Đã chốt lịch; lịch bị khoá, không sửa được nữa.                                                                                                                                                                                     | —                          | [Chốt lịch giao nhận và xem thông tin liên lạc](#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc) |
| <span id="BR_GN_04" class="anchor"></span>**GN-04** | Khi Đã chốt lịch: họ tên, email, SĐT của hai bên hiện trong trang giao dịch (không gửi qua email); đã hiện thì không ẩn lại (D-11).                                                                                                                       | —                          | [Chốt lịch giao nhận và xem thông tin liên lạc](#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc) |
| <span id="BR_GN_05" class="anchor"></span>**GN-05** | Quá 10 ngày từ lúc chọn người mà chưa chốt lịch → tự huỷ, bài về Đang hiển thị.                                                                                                                                                                           | chat_open_days = 10        | [Chốt lịch giao nhận và xem thông tin liên lạc](#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc) |
| <span id="BR_GN_06" class="anchor"></span>**GN-06** | Xác nhận giao nhận chỉ sau khi Đã chốt lịch, mỗi bên một lần. Chủ bài bấm "Đã giao"; người nhận bấm "Đã nhận" và bắt buộc trả lời "Món đồ có đúng mô tả không?".                                                                                          | —                          | [Xác nhận đã giao, đã nhận](#xác-nhận-đã-giao-đã-nhận)                                          |
| <span id="BR_GN_07" class="anchor"></span>**GN-07** | Người nhận trả lời Không đúng: bắt buộc nhập lý do → giao dịch Đang tranh chấp ngay, mở khiếu nại Không đúng mô tả (chủ bài có 48 giờ giải trình) trong cùng một lần xử lý; bài chưa Đã trao, chưa cộng điểm (D-04). Trang giao dịch hiện hạn giải trình. | response_window_hours = 48 | [Xác nhận đã giao, đã nhận](#xác-nhận-đã-giao-đã-nhận)                                          |
| <span id="BR_GN_08" class="anchor"></span>**GN-08** | Xác nhận đầu tiên → Chờ xác nhận giao nhận, báo bên kia. Đủ hai xác nhận (và đúng mô tả) → Hoàn tất; bài (và món đổi) → Đã trao; cộng điểm; báo hai bên hạn khiếu nại kèm giờ cụ thể.                                                                     | —                          | [Xác nhận đã giao, đã nhận](#xác-nhận-đã-giao-đã-nhận)                                          |
| <span id="BR_GN_09" class="anchor"></span>**GN-09** | Một bên đã xác nhận, bên kia im lặng 3 ngày và giao dịch không có khiếu nại → tự Hoàn tất.                                                                                                                                                                | auto_confirm_days = 3      | [Xác nhận đã giao, đã nhận](#xác-nhận-đã-giao-đã-nhận)                                          |
| <span id="BR_GN_10" class="anchor"></span>**GN-10** | Ảnh / video minh chứng đính kèm được từ Đã chốt lịch trở đi (cả khi Hoàn tất, Đang tranh chấp); hai bên xem được; Admin xem khi xử lý khiếu nại.                                                                                                          | —                          | [Xác nhận đã giao, đã nhận](#xác-nhận-đã-giao-đã-nhận)                                          |
| <span id="BR_GN_11" class="anchor"></span>**GN-11** | Hệ thống không bồi thường tiền hàng, phí gửi trong mọi trường hợp; khiếu nại chỉ dẫn tới trừ sao.                                                                                                                                                         | —                          | [Chốt lịch giao nhận và xem thông tin liên lạc](#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc) |

### 5.1.N Huỷ giao dịch và sự cố giao nhận

| **Mã**                                              | **Luật**                                                                                                                                                                                                                                                              | **Con số**               | **Dùng ở**                                                                              |
|-----------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|-----------------------------------------------------------------------------------------|
| <span id="BR_HG_01" class="anchor"></span>**HG-01** | Một bên huỷ được giao dịch khi đang Đã được chọn, Đang hẹn lịch, Đã chốt lịch hoặc Chờ xác nhận giao nhận; bắt buộc lý do 5–500 ký tự (ghi kèm "\[Chủ bài\]" / "\[Người nhận\]"). Bài và món đổi về Đang hiển thị, bên kia nhận thông báo. Không hoàn, không thu phí. | 5–500 ký tự              | [Huỷ giao dịch và báo chưa nhận / chưa giao](#huỷ-giao-dịch-và-báo-chưa-nhận-chưa-giao) |
| <span id="BR_HG_02" class="anchor"></span>**HG-02** | Giao dịch Đang tranh chấp: người dùng không tự huỷ, phải chờ Admin.                                                                                                                                                                                                   | —                        | [Huỷ giao dịch và báo chưa nhận / chưa giao](#huỷ-giao-dịch-và-báo-chưa-nhận-chưa-giao) |
| <span id="BR_HG_03" class="anchor"></span>**HG-03** | Báo "Tôi chưa nhận được đồ" / "Tôi chưa giao được": chỉ khi Chờ xác nhận giao nhận, bên kia đã xác nhận còn mình chưa; ghi chú 5–1000 ký tự, ảnh tuỳ chọn. Kết quả: Đang tranh chấp + khiếu nại Không đến / không giao (NO_SHOW), hạn giải trình 48 giờ.              | 5–1000 ký tự, 48 giờ     | [Huỷ giao dịch và báo chưa nhận / chưa giao](#huỷ-giao-dịch-và-báo-chưa-nhận-chưa-giao) |
| <span id="BR_HG_04" class="anchor"></span>**HG-04** | Admin huỷ được giao dịch ở mọi trạng thái chưa kết thúc (kể cả Đang tranh chấp), bắt buộc lý do. Khiếu nại đang mở không tự đóng.                                                                                                                                     | —                        | [Quản lý giao dịch](#quản-lý-giao-dịch)                                                 |
| <span id="BR_HG_05" class="anchor"></span>**HG-05** | Giao dịch "bị kẹt" (Admin lọc được): quá giờ hẹn 48 giờ mà chưa bên nào xác nhận giao nhận, hoặc một bên đang bị khoá.                                                                                                                                                | stuck_request_hours = 48 | [Quản lý giao dịch](#quản-lý-giao-dịch)                                                 |

### 5.1.O Báo cáo và khiếu nại

Báo cáo là về một bài hoặc một người; khiếu nại là về một giao dịch. Cả hai nằm chung bảng reports và chỉ dẫn tới chế tài uy tín, không bồi thường.

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 54%" />
<col style="width: 17%" />
<col style="width: 19%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Luật</strong></th>
<th><strong>Con số</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_BC_01" class="anchor"></span><strong>BC-01</strong></td>
<td>Báo cáo bài hoặc người: chọn lý do, mô tả ≤ 1000 ký tự, ảnh bằng chứng. Không tự báo cáo mình, không báo cáo trùng một đối tượng.</td>
<td>1000 ký tự</td>
<td><a href="#báo-cáo-bài-người-dùng-và-khiếu-nại-giao-dịch">Báo cáo bài, người dùng và khiếu nại giao dịch</a></td>
</tr>
<tr class="even">
<td><span id="BR_BC_02" class="anchor"></span><strong>BC-02</strong></td>
<td>Bài có 3 báo cáo đang chờ (Chờ xử lý / Chờ giải trình) → tạm ẩn bài, trạng thái bài giữ nguyên; báo chủ bài và Admin.</td>
<td>report_hide_threshold = 3</td>
<td><a href="#báo-cáo-bài-người-dùng-và-khiếu-nại-giao-dịch">Báo cáo bài, người dùng và khiếu nại giao dịch</a></td>
</tr>
<tr class="odd">
<td><span id="BR_BC_03" class="anchor"></span><strong>BC-03</strong></td>
<td><p>Admin bấm "Yêu cầu giải trình" trên báo cáo bài hoặc người: bên bị báo cáo có 48 giờ, giải trình kèm ảnh. Một bài nhiều báo cáo thì mọi báo cáo chờ của bài dùng chung một hạn và chủ bài giải trình một lần cho cả bài. Nội dung giải trình tối đa 2000 ký tự.</p>
<p>Ghi chú: Bổ sung con số 2000 ký tự theo quyết định ngày 09/11/2026 (D-24).</p></td>
<td>response_window_hours = 48</td>
<td><p><a href="#giải-trình-báo-cáo-khiếu-nại">Giải trình báo cáo, khiếu nại</a></p>
<p><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></p></td>
</tr>
<tr class="even">
<td><span id="BR_BC_04" class="anchor"></span><strong>BC-04</strong></td>
<td>Báo cáo bài bị bác bỏ: mở lại bài nếu không còn báo cáo chờ, bài chưa từng có báo cáo bị chấp thuận, chủ bài không bị khoá / cấm và còn sao.</td>
<td>—</td>
<td><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></td>
</tr>
<tr class="odd">
<td><span id="BR_BC_05" class="anchor"></span><strong>BC-05</strong></td>
<td>Báo cáo bài được chấp thuận: ẩn bài vĩnh viễn (không tự hiện lại), trừ 1 sao chủ bài; các báo cáo khác đang chờ của bài đóng theo, chỉ trừ 1 sao.</td>
<td>—</td>
<td><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></td>
</tr>
<tr class="even">
<td><span id="BR_BC_06" class="anchor"></span><strong>BC-06</strong></td>
<td>Lý do nghiêm trọng (Hàng cấm, Hàng giả) được chấp thuận → ẩn mọi bài đang hiển thị của người vi phạm để Admin rà soát. Admin sửa được danh sách lý do nghiêm trọng.</td>
<td>report_serious_reasons = PROHIBITED_ITEM, COUNTERFEIT</td>
<td><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></td>
</tr>
<tr class="odd">
<td><span id="BR_BC_07" class="anchor"></span><strong>BC-07</strong></td>
<td>Khiếu nại giao dịch Hoàn tất: là một bên của giao dịch, trong 24 giờ sau hoàn tất, chưa khiếu nại giao dịch này (D-05).</td>
<td>complaint_window_hours = 24</td>
<td><a href="#báo-cáo-bài-người-dùng-và-khiếu-nại-giao-dịch">Báo cáo bài, người dùng và khiếu nại giao dịch</a></td>
</tr>
<tr class="even">
<td><span id="BR_BC_08" class="anchor"></span><strong>BC-08</strong></td>
<td><p>Bên bị khiếu nại giải trình 1 lần kèm bằng chứng, trong 48 giờ; thông báo ghi rõ giờ hết hạn. Nội dung giải trình tối đa 2000 ký tự, giống giải trình báo cáo (BC-03).</p>
<p>Ghi chú: Con số 2000 ký tự chốt ngày 09/11/2026 cho cả giải trình báo cáo và giải trình khiếu nại (D-24): đủ để kể lại sự việc, mốc thời gian và giải thích ảnh bằng chứng, vẫn đủ ngắn để Admin đọc nhanh.</p></td>
<td>48 giờ, 2000 ký tự</td>
<td><p><a href="#báo-cáo-bài-người-dùng-và-khiếu-nại-giao-dịch">Báo cáo bài, người dùng và khiếu nại giao dịch</a></p>
<p><a href="#giải-trình-báo-cáo-khiếu-nại">Giải trình báo cáo, khiếu nại</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_BC_09" class="anchor"></span><strong>BC-09</strong></td>
<td>Khiếu nại tự mở từ: báo chưa nhận / chưa giao (HG-03), báo không đúng mô tả (GN-07).</td>
<td>—</td>
<td><p><a href="#huỷ-giao-dịch-và-báo-chưa-nhận-chưa-giao">Huỷ giao dịch và báo chưa nhận / chưa giao</a></p>
<p><a href="#báo-cáo-bài-người-dùng-và-khiếu-nại-giao-dịch">Báo cáo bài, người dùng và khiếu nại giao dịch</a></p></td>
</tr>
<tr class="even">
<td><span id="BR_BC_10" class="anchor"></span><strong>BC-10</strong></td>
<td>Admin chỉ kết luận khi đã có giải trình hoặc đã quá hạn giải trình (D-15); màn Admin khoá nút Kết luận và hiện hạn.</td>
<td>—</td>
<td><p><a href="#giải-trình-báo-cáo-khiếu-nại">Giải trình báo cáo, khiếu nại</a></p>
<p><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></p></td>
</tr>
<tr class="odd">
<td><span id="BR_BC_11" class="anchor"></span><strong>BC-11</strong></td>
<td>Kết luận Bác bỏ: báo cáo Bác bỏ; giao dịch Đang tranh chấp → Hoàn tất (bài Đã trao, cộng điểm).</td>
<td>—</td>
<td><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></td>
</tr>
<tr class="even">
<td><span id="BR_BC_12" class="anchor"></span><strong>BC-12</strong></td>
<td>Kết luận Chấp thuận: báo cáo Có vi phạm; trừ 1 sao người vi phạm (UT-02); giao dịch Đang tranh chấp → Đã huỷ, bài và món đổi về Đang hiển thị.</td>
<td>—</td>
<td><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></td>
</tr>
<tr class="odd">
<td><span id="BR_BC_13" class="anchor"></span><strong>BC-13</strong></td>
<td>Mọi kết luận ghi nhật ký kèm ghi chú của Admin; các bên nhận thông báo.</td>
<td>—</td>
<td><a href="#xử-lý-báo-cáo-và-khiếu-nại">Xử lý báo cáo và khiếu nại</a></td>
</tr>
</tbody>
</table>

### 5.1.P Quản trị

<table>
<colgroup>
<col style="width: 8%" />
<col style="width: 70%" />
<col style="width: 21%" />
</colgroup>
<thead>
<tr class="header">
<th><strong>Mã</strong></th>
<th><strong>Luật</strong></th>
<th><strong>Dùng ở</strong></th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td><span id="BR_AD_01" class="anchor"></span><strong>AD-01</strong></td>
<td>Admin quản lý danh mục (thêm, sửa, ẩn, sắp xếp; bật / tắt hạn chế và cho phép đẩy bài), thuộc tính động theo danh mục, khu vực (thành phố → quận), từ khoá cấm, checklist, lý do từ chối mẫu.</td>
<td><p><a href="#quản-lý-danh-mục-thuộc-tính-khu-vực">Quản lý danh mục, thuộc tính, khu vực</a></p>
<p><a href="#quản-lý-từ-khoá-cấm-checklist-lý-do-từ-chối-mẫu">Quản lý từ khoá cấm, checklist, lý do từ chối mẫu</a></p></td>
</tr>
<tr class="even">
<td><span id="BR_AD_02" class="anchor"></span><strong>AD-02</strong></td>
<td>Mọi con số nghiệp vụ nằm ở bảng cấu hình; Admin đổi trên màn Cấu hình, có hiệu lực ngay. Giá trị phải đúng kiểu và trong khoảng cho phép; ngưỡng hạng phải tăng dần.</td>
<td><a href="#cấu-hình-phí-hạn-mức-uy-tín-và-hạng">Cấu hình phí, hạn mức, uy tín và hạng</a></td>
</tr>
<tr class="odd">
<td><span id="BR_AD_03" class="anchor"></span><strong>AD-03</strong></td>
<td>Admin xem báo cáo tài chính, xuất CSV, tra cứu ví và lịch sử Credit của từng tài khoản (chỉ xem, không sửa).</td>
<td><a href="#báo-cáo-tài-chính-đơn-nạp-và-tra-cứu-ví">Báo cáo tài chính, đơn nạp và tra cứu ví</a></td>
</tr>
<tr class="even">
<td><span id="BR_AD_04" class="anchor"></span><strong>AD-04</strong></td>
<td><p>Giám sát bất thường: (1) liệt kê các cặp người giao dịch lặp lại nhiều lần (UT-08); (2) liệt kê các địa chỉ IP có từ 3 tài khoản đã xác thực email trở lên đăng ký trong 30 ngày gần nhất, kèm danh sách tài khoản (họ tên, email, ngày đăng ký, sao, trạng thái). Chỉ để Admin xem và tự quyết, hệ thống không tự khoá tài khoản.</p>
<p>Ghi chú: Luật mới, chốt ngày 09/11/2026 (D-26). Cấu hình ip_alert_window_days = 30, ip_alert_min_accounts = 3.</p></td>
<td><a href="#giám-sát-bất-thường-và-nhật-ký-hoạt-động">Giám sát bất thường và nhật ký hoạt động</a></td>
</tr>
<tr class="odd">
<td><span id="BR_AD_05" class="anchor"></span><strong>AD-05</strong></td>
<td>Mọi thao tác Admin (duyệt, từ chối, kết luận, yêu cầu giải trình, khoá, mở khoá, huỷ giao dịch, đổi cấu hình, sửa danh mục, từ khoá, checklist, khu vực) đều ghi nhật ký có người làm; thao tác nặng bắt buộc lý do.</td>
<td><p><a href="#tổng-quan-quản-trị">Tổng quan quản trị</a></p>
<p><a href="#giám-sát-bất-thường-và-nhật-ký-hoạt-động">Giám sát bất thường và nhật ký hoạt động</a></p></td>
</tr>
</tbody>
</table>

### 5.1.Q Tác vụ tự động

Chạy theo lịch trong backend. Mỗi bản ghi xử lý riêng, lỗi một bản ghi không làm hỏng cả lượt; chạy lại không gây tác dụng kép.

| **Mã**                                              | **Lịch**         | **Việc**                                                                                                                                                                                              | **Dùng ở**                  |
|-----------------------------------------------------|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| <span id="BR_TD_01" class="anchor"></span>**TD-01** | Mỗi giờ, phút 00 | Bài Đang hiển thị quá hạn → Hết hạn (mỗi lượt xử lý theo đợt 100 bài).                                                                                                                                | —                           |
| <span id="BR_TD_02" class="anchor"></span>**TD-02** | Mỗi giờ, phút 15 | Yêu cầu Chờ chủ bài chọn quá 3 ngày → Đã huỷ; giao dịch Đã được chọn / Đang hẹn lịch quá 10 ngày → Đã huỷ, nhả bài; Chờ xác nhận giao nhận quá 3 ngày từ xác nhận đầu, không có khiếu nại → Hoàn tất. | [Rút yêu cầu](#rút-yêu-cầu) |
| <span id="BR_TD_03" class="anchor"></span>**TD-03** | Mỗi giờ, phút 30 | Xoá mềm tài khoản Chờ xác thực email quá 24 giờ.                                                                                                                                                      | —                           |
| **TD-04**                                           | (Đã bỏ)          | Không còn nhắc Admin bài chờ duyệt quá 24 giờ.                                                                                                                                                        | —                           |
| <span id="BR_TD_05" class="anchor"></span>**TD-05** | 5 phút / lần     | Đơn nạp Chờ thanh toán quá 30 phút → Hết hạn.                                                                                                                                                         | —                           |
| <span id="BR_TD_06" class="anchor"></span>**TD-06** | Không có tác vụ  | Hết hạn giải trình 48 giờ không đổi trạng thái tự động; Admin được kết luận (BC-10).                                                                                                                  | —                           |

### 5.1.R Thông báo và nhật ký

| **Mã**                                              | **Luật**                                                                                                                                                                                                                                                                                                            | **Dùng ở**                                                                            |
|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| <span id="BR_TB_01" class="anchor"></span>**TB-01** | Thông báo hiện trong app (chuông) với số chưa đọc; trình duyệt hỏi số chưa đọc mỗi 30 giây và tạm dừng khi tab bị ẩn.                                                                                                                                                                                               | [Xem thông báo](#xem-thông-báo)                                                       |
| <span id="BR_TB_02" class="anchor"></span>**TB-02** | Có thông báo khi: có yêu cầu mới; được / không được chọn; yêu cầu, giao dịch bị huỷ; lịch thay đổi; bên kia xác nhận giao nhận; hoàn tất (kèm hạn khiếu nại); bị khiếu nại hoặc được yêu cầu giải trình (kèm hạn); có kết luận; bài được duyệt / bị từ chối / bị ẩn / được mở lại; bị trừ sao (kèm số sao còn lại). | [Xem thông báo](#xem-thông-báo)                                                       |
| <span id="BR_TB_03" class="anchor"></span>**TB-03** | Nhật ký hoạt động lưu thao tác quan trọng (duyệt, từ chối, khoá, mở khoá, cấm, huỷ giao dịch, kết luận, đổi sao / điểm, đổi cấu hình, danh mục, từ khoá, checklist, khu vực, lượt gọi AI). Admin lọc theo loại và thời gian.                                                                                        | [Giám sát bất thường và nhật ký hoạt động](#giám-sát-bất-thường-và-nhật-ký-hoạt-động) |

## 5.2 Bảng cấu hình nghiệp vụ

Giá trị mặc định lấy từ enum ConfigKey (backend/src/main/java/com/shareloop/setting/ConfigKey.java). Admin đổi được trên màn Cấu hình ([AD-02](#BR_AD_02)), trừ hai khoá cố định complaint_window_hours và response_window_hours.

| **Khoá**                         | **Giá trị**                 | **Ý nghĩa**                                                                                                                                                                                                                                                                |
|----------------------------------|-----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| vnd_per_credit                   | 1000                        | Tỉ lệ quy đổi. Số đồng ứng với 1 Credit khi nạp tiền. Mặc định 1.000đ là 1 Credit.                                                                                                                                                                                         |
| topup_min_credits                | 10                          | Nạp tối thiểu. Số Credit tối thiểu cho mỗi lần nạp. Số tiền nạp phải chia hết cho tỉ lệ quy đổi.                                                                                                                                                                           |
| post_fee                         | 5                           | Phí đăng bài. Số Credit được giữ khi đăng bài và chỉ bị trừ khi Admin duyệt. Bài bị từ chối thì được hoàn lại.                                                                                                                                                             |
| post_fee_over_quota              | 10                          | Phí Trao đổi vượt ngưỡng. Số Credit giữ cho bài Trao đổi khi số bài đang hoạt động đã đạt ngưỡng phí thường của hạng.                                                                                                                                                      |
| post_display_days                | 30                          | Số ngày hiển thị bài. Số ngày bài được hiển thị sau khi duyệt hoặc gia hạn.                                                                                                                                                                                                |
| post_display_days_gold           | 45                          | Số ngày hiển thị bài từ hạng Vàng. Số ngày bài được hiển thị sau khi duyệt hoặc gia hạn, áp dụng cho người dùng từ hạng Vàng.                                                                                                                                              |
| max_active_items_bronze          | 5                           | Ngưỡng phí thường Trao đổi hạng Đồng. Số bài Trao đổi đang hoạt động được đăng với phí thường của hạng Đồng. Nhập -1 nếu không giới hạn.                                                                                                                                   |
| max_active_items_silver          | 8                           | Ngưỡng phí thường Trao đổi hạng Bạc. Số bài Trao đổi đang hoạt động được đăng với phí thường của hạng Bạc. Nhập -1 nếu không giới hạn.                                                                                                                                     |
| max_active_items_gold            | 15                          | Ngưỡng phí thường Trao đổi hạng Vàng. Số bài Trao đổi đang hoạt động được đăng với phí thường của hạng Vàng. Nhập -1 nếu không giới hạn.                                                                                                                                   |
| max_active_items_diamond         | -1                          | Ngưỡng phí thường Trao đổi hạng Kim cương. Số bài Trao đổi đang hoạt động được đăng với phí thường của hạng Kim cương. Nhập -1 nếu không giới hạn.                                                                                                                         |
| max_active_give_items            | 5                           | Số bài Cho tặng tối đa. Số bài Cho tặng đang hiển thị hoặc chờ duyệt tối đa, áp dụng cho mọi hạng. Nhập -1 nếu không giới hạn.                                                                                                                                             |
| edit_fee                         | 5                           | Phí sửa bài. Số Credit cho mỗi lần sửa bài đã được duyệt, tính từ lần sửa thứ hai. Lần sửa đầu sau khi duyệt miễn phí.                                                                                                                                                     |
| renew_fee                        | 5                           | Phí gia hạn. Số Credit bị trừ ngay khi gia hạn thêm một kỳ hiển thị cho bài.                                                                                                                                                                                               |
| boost_fee                        | 5                           | Phí đẩy bài. Số Credit cho mỗi gói đẩy bài, bị trừ ngay khi đẩy.                                                                                                                                                                                                           |
| boost_days                       | 3                           | Số ngày mỗi gói đẩy bài. Số ngày bài được đẩy lên đầu cho mỗi gói.                                                                                                                                                                                                         |
| boost_max_days                   | 6                           | Số ngày đẩy tối đa. Tối đa số ngày đẩy liên tục trong một đợt, tính từ lúc bắt đầu đợt. Mua thêm gói khi đang đẩy không được vượt mốc này.                                                                                                                                 |
| boost_cooldown_days              | 3                           | Số ngày nghỉ giữa hai đợt đẩy. Hết một đợt đẩy, bài phải chờ số ngày này mới được đẩy lại.                                                                                                                                                                                 |
| boost_max_per_user               | 3                           | Số bài đang đẩy tối đa mỗi người. Số bài của một người được đẩy cùng lúc. Mua thêm gói cho bài đang đẩy không tính là bài mới.                                                                                                                                             |
| boost_max_active                 | 30                          | Số suất đẩy toàn hệ thống. Số bài được đẩy cùng lúc trên toàn hệ thống. Hết suất thì bài mới phải chờ có bài hết đẩy.                                                                                                                                                      |
| boost_rotate_seconds             | 180                         | Chu kỳ xoay vòng bài đẩy. Số giây giữa hai lần đổi nhóm bài đẩy hiển thị ở đầu trang danh sách.                                                                                                                                                                            |
| boost_max_per_page               | 3                           | Số bài đẩy mỗi trang. Số bài đẩy tối đa hiển thị ở đầu mỗi trang danh sách.                                                                                                                                                                                                |
| boost_ending_notice_hours        | 24                          | Báo trước khi hết đẩy. Số giờ trước lúc bài hết đẩy thì gửi thông báo nhắc chủ bài, kèm mốc còn mua thêm gói được.                                                                                                                                                         |
| ai_search_fee                    | 2                           | Phí tìm bằng AI. Số Credit cho mỗi lượt AI trợ lý sau khi hết lượt miễn phí.                                                                                                                                                                                               |
| ai_free_per_day                  | 5                           | Lượt AI miễn phí mỗi ngày. Số lượt AI trợ lý miễn phí mỗi ngày cho tài khoản đã từng nạp Credit.                                                                                                                                                                           |
| ai_trial_uses                    | 1                           | Lượt AI dùng thử. Số lượt AI trợ lý dùng thử cho tài khoản chưa từng nạp Credit.                                                                                                                                                                                           |
| ai_search_timeout_seconds        | 15                          | Thời gian chờ AI tìm kiếm. Số giây chờ AI trợ lý trả lời. Quá thời gian thì hoàn lượt và chuyển sang tìm kiếm thường.                                                                                                                                                      |
| ai_describe_per_day              | 5                           | Lượt gợi ý mô tả mỗi ngày. Số lần tạo gợi ý mô tả miễn phí mỗi ngày cho mỗi thành viên.                                                                                                                                                                                    |
| ai_describe_timeout_seconds      | 20                          | Thời gian chờ gợi ý mô tả. Số giây tối đa chờ AI tạo gợi ý mô tả từ tên món đồ.                                                                                                                                                                                            |
| chat_violation_alert             | 3                           | Ngưỡng cảnh báo vi phạm chat. Số tin nhắn vi phạm trong một giao dịch thì cảnh báo Admin.                                                                                                                                                                                  |
| review_newcomer_max_approved     | 3                           | Ngưỡng người đăng mới. Người đăng có số bài đã từng được duyệt không vượt quá số này được xem là người mới và xếp trước trong hàng chờ duyệt (trong cùng số sao uy tín).                                                                                                   |
| item_photos_min                  | 3                           | Số ảnh tối thiểu mỗi bài. Số ảnh ít nhất người dùng phải tải lên cho mỗi bài đăng.                                                                                                                                                                                         |
| item_photos_max                  | 8                           | Số ảnh tối đa mỗi bài. Số ảnh nhiều nhất được tải lên cho mỗi bài đăng.                                                                                                                                                                                                    |
| image_max_mb                     | 5                           | Dung lượng ảnh tối đa. Dung lượng tối đa của mỗi ảnh, tính bằng MB.                                                                                                                                                                                                        |
| report_hide_threshold            | 3                           | Ngưỡng tự ẩn bài bị báo cáo. Số báo cáo đang mở khiến một bài tự động bị ẩn.                                                                                                                                                                                               |
| report_serious_reasons           | PROHIBITED_ITEM,COUNTERFEIT | Lý do vi phạm nghiêm trọng. Các mã lý do báo cáo, cách nhau bởi dấu phẩy. Chấp thuận báo cáo có lý do này thì ẩn toàn bộ bài khác của người vi phạm để rà soát. Mã hợp lệ: NOT_AS_DESCRIBED, COUNTERFEIT, PROHIBITED_ITEM, NO_SHOW, WRONG_CONTACT_INFO, HARASSMENT, OTHER. |
| otp_ttl_minutes                  | 5                           | Hạn mã OTP. Số phút mã OTP gửi qua email còn hiệu lực.                                                                                                                                                                                                                     |
| otp_max_failed                   | 5                           | Số lần nhập sai OTP tối đa. Số lần nhập sai mã OTP tối đa trước khi mã bị vô hiệu.                                                                                                                                                                                         |
| otp_resend_cooldown_seconds      | 60                          | Khoảng cách gửi lại OTP. Số giây tối thiểu giữa hai lần gửi lại mã OTP.                                                                                                                                                                                                    |
| pending_account_ttl_hours        | 24                          | Hạn tài khoản chưa xác thực. Số giờ giữ tài khoản chưa xác thực OTP. Quá mốc này tài khoản bị xoá.                                                                                                                                                                         |
| phone_change_interval_days       | 30                          | Khoảng cách đổi số điện thoại. Số ngày tối thiểu giữa hai lần đổi số điện thoại.                                                                                                                                                                                           |
| max_pending_requests             | 5                           | Số yêu cầu chờ tối đa. Số yêu cầu đang chờ phản hồi mà một người được gửi cùng lúc.                                                                                                                                                                                        |
| request_pending_days             | 3                           | Hạn yêu cầu chờ phản hồi. Số ngày một yêu cầu được chờ phản hồi. Quá hạn thì yêu cầu tự huỷ.                                                                                                                                                                               |
| chat_open_days                   | 10                          | Thời gian mở chat. Số ngày kể từ khi chọn người nhận. Chưa chốt lịch trong thời gian này thì giao dịch bị huỷ và chat đóng.                                                                                                                                                |
| auto_confirm_days                | 3                           | Hạn tự động hoàn tất. Một bên đã xác nhận mà bên kia im lặng quá số ngày này thì giao dịch tự hoàn tất.                                                                                                                                                                    |
| stuck_request_hours              | 48                          | Ngưỡng giao dịch bị kẹt. Giao dịch quá giờ hẹn hơn số giờ này mà chưa bên nào xác nhận bàn giao thì Admin thấy ở bộ lọc Bị kẹt.                                                                                                                                            |
| complaint_window_hours           | 24                          | Hạn khiếu nại. Số giờ được khiếu nại sau khi giao dịch hoàn tất.                                                                                                                                                                                                           |
| response_window_hours            | 48                          | Hạn giải trình. Số giờ bên bị khiếu nại được giải trình, tính từ lúc nhận thông báo.                                                                                                                                                                                       |
| daily_points_cap                 | 30                          | Trần điểm mỗi ngày. Số điểm uy tín tối đa một người được cộng trong một ngày.                                                                                                                                                                                              |
| payment_order_expire_minutes     | 30                          | Hạn đơn nạp. Số phút một đơn nạp được chờ thanh toán. Quá hạn thì đơn chuyển sang hết hạn.                                                                                                                                                                                 |
| reputation_silver_min            | 50                          | Điểm tối thiểu hạng Bạc. Tổng điểm cần có để lên hạng Bạc. Phải nhỏ hơn điểm tối thiểu hạng Vàng.                                                                                                                                                                          |
| reputation_gold_min              | 150                         | Điểm tối thiểu hạng Vàng. Tổng điểm cần có để lên hạng Vàng. Phải lớn hơn hạng Bạc và nhỏ hơn hạng Kim cương.                                                                                                                                                              |
| reputation_diamond_min           | 400                         | Điểm tối thiểu hạng Kim cương. Tổng điểm cần có để lên hạng Kim cương. Phải lớn hơn điểm tối thiểu hạng Vàng.                                                                                                                                                              |
| reputation_give_donor            | 10                          | Điểm người cho. Số điểm người cho nhận được khi một giao dịch Cho hoàn tất.                                                                                                                                                                                                |
| reputation_give_receiver         | 2                           | Điểm người nhận. Số điểm người nhận được khi một giao dịch Cho hoàn tất.                                                                                                                                                                                                   |
| reputation_swap_each             | 6                           | Điểm mỗi bên khi trao đổi. Số điểm mỗi bên nhận được khi một giao dịch Trao đổi hoàn tất.                                                                                                                                                                                  |
| reputation_clean_streak_for_star | 10                          | Số giao dịch sạch để hồi sao. Số giao dịch hoàn tất liên tiếp không vi phạm để được hồi 1 sao, khi người dùng đang dưới 5 sao.                                                                                                                                             |
| reputation_pair_no_points_from   | 3                           | Giao dịch lặp cặp hết điểm từ lần thứ. Từ giao dịch thứ mấy giữa cùng hai người, trong thời gian tính lặp cặp, thì cả hai không được cộng điểm.                                                                                                                            |
| reputation_pair_window_days      | 90                          | Số ngày tính lặp cặp. Số ngày gần nhất dùng để đếm các giao dịch giữa cùng hai người.                                                                                                                                                                                      |
| reputation_pair_alert_threshold  | 5                           | Ngưỡng cảnh báo lặp cặp. Số giao dịch giữa cùng hai người vượt mức này thì cảnh báo Admin.                                                                                                                                                                                 |
| ip_alert_window_days             | 30                          | Số ngày tính cảnh báo cùng IP. Số ngày gần nhất dùng để đếm tài khoản đăng ký từ cùng một địa chỉ IP.                                                                                                                                                                      |
| ip_alert_min_accounts            | 3                           | Ngưỡng cảnh báo cùng IP. Địa chỉ IP có từ số tài khoản này trở lên (đã xác thực email, đăng ký trong số ngày ở trên) thì hiện ở màn Giám sát bất thường.                                                                                                                   |

## 5.3 Yêu cầu chung

### 5.3.1 Bảo mật

- Xác thực bằng JWT; mọi API ngoài danh sách công khai (xem bài, danh mục, khu vực, ảnh, IPN) đều cần đăng nhập.

- Kiểm tra dữ liệu đầu vào ở backend; không tin kiểm tra phía trình duyệt.

- Ghi nhật ký mọi thao tác Admin và sự kiện bảo mật ([AD-05](#BR_AD_05), [TB-03](#BR_TB_03)).

### 5.3.2 Hiệu năng

- Phân trang mọi danh sách (mặc định 24 bài / trang, tối đa 100).

- Ảnh tối đa 5 MB; chỉ JPG, PNG, WebP.

### 5.3.3 Khả dụng

- Mọi trạng thái hiển thị bằng tiếng Việt; thời gian hiển thị theo giờ Việt Nam (lưu UTC).

- Thông báo nêu hạn cụ thể (giờ:phút ngày/tháng) cho hạn khiếu nại và hạn giải trình.

### 5.3.4 Tương thích

- Chạy trên trình duyệt hiện đại (Chrome, Edge, Safari, Firefox) trên máy tính và điện thoại.

- Backend Java 21, Spring Boot 4.1.1, PostgreSQL 16; frontend React, Vite, TypeScript, Tailwind.

## 5.4 Danh sách thông báo của ứng dụng

Thông báo lỗi lấy từ các lớp \*ErrorCode trong backend (mỗi module một lớp). Frontend hiển thị nguyên văn câu thông báo dưới dạng toast hoặc chữ đỏ dưới ô nhập.

| **\#** | **Mã**                                   | **HTTP**              | **Module**   | **Nội dung**                                                                         |
|--------|------------------------------------------|-----------------------|--------------|--------------------------------------------------------------------------------------|
| MSG001 | INVALID_DATE_RANGE                       | BAD REQUEST           | audit        | Khoảng ngày không hợp lệ (từ ngày phải trước hoặc bằng đến ngày).                    |
| MSG002 | AUTH_EMAIL_ALREADY_REGISTERED            | CONFLICT              | auth         | Email đã được đăng ký.                                                               |
| MSG003 | AUTH_PHONE_ALREADY_USED                  | CONFLICT              | auth         | Số điện thoại đã được sử dụng.                                                       |
| MSG004 | AUTH_PHONE_INVALID                       | BAD REQUEST           | auth         | Số điện thoại không đúng định dạng.                                                  |
| MSG005 | AUTH_EMAIL_DOMAIN_NOT_ALLOWED            | BAD REQUEST           | auth         | Không thể đăng ký bằng email dùng một lần. Vui lòng dùng email cá nhân của bạn.      |
| MSG006 | AUTH_TOO_MANY_ACCOUNTS_FROM_IP           | TOO MANY REQUESTS     | auth         | Mạng của bạn đã tạo quá nhiều tài khoản trong 24 giờ qua. Vui lòng thử lại sau.      |
| MSG007 | AUTH_OTP_INVALID                         | BAD REQUEST           | auth         | Mã OTP không đúng.                                                                   |
| MSG008 | AUTH_OTP_EXPIRED                         | BAD REQUEST           | auth         | Mã OTP đã hết hạn.                                                                   |
| MSG009 | AUTH_OTP_TOO_MANY_ATTEMPTS               | UNPROCESSABLE ENTITY  | auth         | Bạn đã nhập sai quá nhiều lần, vui lòng gửi lại mã.                                  |
| MSG010 | AUTH_OTP_RESEND_TOO_SOON                 | TOO MANY REQUESTS     | auth         | Vui lòng chờ trước khi gửi lại mã.                                                   |
| MSG011 | AUTH_EMAIL_NOT_VERIFIED                  | FORBIDDEN             | auth         | Email chưa được xác thực.                                                            |
| MSG012 | AUTH_INVALID_CREDENTIALS                 | UNAUTHORIZED          | auth         | Email hoặc mật khẩu không đúng.                                                      |
| MSG013 | AUTH_ACCOUNT_LOCKED                      | FORBIDDEN             | auth         | Tài khoản đã bị khoá.                                                                |
| MSG014 | AUTH_ACCOUNT_BANNED                      | FORBIDDEN             | auth         | Tài khoản đã bị cấm.                                                                 |
| MSG015 | AUTH_CURRENT_PASSWORD_INCORRECT          | BAD REQUEST           | auth         | Mật khẩu hiện tại không đúng.                                                        |
| MSG016 | AUTH_NEW_PASSWORD_SAME_AS_CURRENT        | BAD REQUEST           | auth         | Mật khẩu mới phải khác mật khẩu hiện tại.                                            |
| MSG017 | CATALOG_AREA_NOT_FOUND                   | NOT FOUND             | catalog      | Không tìm thấy khu vực.                                                              |
| MSG018 | CATALOG_INVALID_AREA_PARENT              | UNPROCESSABLE CONTENT | catalog      | Khu vực cha không hợp lệ.                                                            |
| MSG019 | CATALOG_AREA_HAS_CHILDREN                | CONFLICT              | catalog      | Không thể chuyển khu vực cha thành khu vực con.                                      |
| MSG020 | CATALOG_CATEGORY_NOT_FOUND               | NOT FOUND             | catalog      | Không tìm thấy danh mục.                                                             |
| MSG021 | CATALOG_INVALID_PARENT                   | UNPROCESSABLE CONTENT | catalog      | Danh mục cha không hợp lệ.                                                           |
| MSG022 | CATALOG_INVALID_SORT_ORDER               | BAD REQUEST           | catalog      | Danh sách sắp xếp phải gồm đủ mọi danh mục, mỗi danh mục một lần.                    |
| MSG023 | CATALOG_ATTRIBUTE_NOT_FOUND              | NOT FOUND             | catalog      | Không tìm thấy thuộc tính.                                                           |
| MSG024 | CATALOG_INVALID_ATTRIBUTE_OPTIONS        | UNPROCESSABLE CONTENT | catalog      | Tùy chọn thuộc tính không hợp lệ.                                                    |
| MSG025 | CATALOG_REQUIRED_ATTRIBUTE               | CONFLICT              | catalog      | Không thể tắt thuộc tính bắt buộc của đồ điện tử.                                    |
| MSG026 | CHAT_REQUEST_NOT_FOUND                   | NOT FOUND             | chat         | Không tìm thấy yêu cầu giao dịch.                                                    |
| MSG027 | CHAT_FORBIDDEN                           | FORBIDDEN             | chat         | Bạn không có quyền tham gia cuộc trò chuyện này.                                     |
| MSG028 | CHAT_REQUEST_NOT_RESERVED                | CONFLICT              | chat         | Khung chat chỉ mở khi yêu cầu ở trạng thái đã được chọn (RESERVED).                  |
| MSG029 | CHAT_MESSAGE_EMPTY                       | BAD REQUEST           | chat         | Nội dung tin nhắn không được để trống.                                               |
| MSG030 | CHAT_MESSAGE_BLOCKED                     | BAD REQUEST           | chat         | Tin nhắn bị chặn do chứa thông tin liên hệ.                                          |
| MSG031 | CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN    | CONFLICT              | chat         | Chỉ được gửi ảnh sau khi hai bên đã chốt lịch hẹn (LOGISTICS_CONFIRMED).             |
| MSG032 | CHAT_IMAGE_GIVE_RECEIVER_FORBIDDEN       | FORBIDDEN             | chat         | Người nhận trong giao dịch Cho tặng không được gửi ảnh trong cuộc trò chuyện.        |
| MSG033 | CHAT_ITEM_NOT_FOUND                      | NOT FOUND             | chat         | Không tìm thấy bài đăng để chia sẻ thẻ.                                              |
| MSG034 | CHAT_ITEM_NOT_APPROVED                   | BAD REQUEST           | chat         | Chỉ được chia sẻ thẻ của bài đăng đã được duyệt (APPROVED).                          |
| MSG035 | VALIDATION_FAILED                        | BAD REQUEST           | common       | Dữ liệu không hợp lệ.                                                                |
| MSG036 | UNAUTHORIZED                             | UNAUTHORIZED          | common       | Bạn chưa đăng nhập.                                                                  |
| MSG037 | FORBIDDEN                                | FORBIDDEN             | common       | Bạn không có quyền thực hiện thao tác này.                                           |
| MSG038 | NOT_FOUND                                | NOT FOUND             | common       | Không tìm thấy dữ liệu.                                                              |
| MSG039 | CONFLICT                                 | CONFLICT              | common       | Dữ liệu đang xung đột trạng thái.                                                    |
| MSG040 | BUSINESS_RULE_VIOLATED                   | UNPROCESSABLE ENTITY  | common       | Thao tác vi phạm quy tắc nghiệp vụ.                                                  |
| MSG041 | INTERNAL_ERROR                           | INTERNAL SERVER ERROR | common       | Hệ thống gặp lỗi. Vui lòng thử lại sau.                                              |
| MSG042 | ITEM_NOT_FOUND                           | NOT FOUND             | item         | Không tìm thấy bài đăng.                                                             |
| MSG043 | ITEM_SWAP_DESIRED_ITEM_REQUIRED          | BAD REQUEST           | item         | Bài đăng trao đổi bắt buộc phải có món mong muốn.                                    |
| MSG044 | ITEM_DEFECT_NOTE_REQUIRED                | BAD REQUEST           | item         | Bài đăng đồ lỗi bắt buộc phải có mô tả lỗi.                                          |
| MSG045 | ITEM_INVALID_IMAGE                       | BAD REQUEST           | item         | Ảnh phải là URL trả về từ API upload media.                                          |
| MSG046 | ITEM_INVALID_IMAGE_COUNT                 | UNPROCESSABLE CONTENT | item         | Bài đăng cần từ 3 đến 8 ảnh.                                                         |
| MSG047 | ITEM_ACTIVE_LIMIT_REACHED                | CONFLICT              | item         | Bạn đã đạt giới hạn bài đăng đang hoạt động.                                         |
| MSG048 | ITEM_GIVE_LIMIT_REACHED                  | CONFLICT              | item         | Bạn đã đạt giới hạn bài Cho tặng đang hoạt động.                                     |
| MSG049 | ITEM_DEFECT_IMAGES_REQUIRED              | UNPROCESSABLE CONTENT | item         | Đồ có lỗi cần ít nhất một ảnh chụp chỗ lỗi.                                          |
| MSG050 | ITEM_DEFECT_IMAGES_NOT_ALLOWED           | UNPROCESSABLE CONTENT | item         | Chỉ đồ có lỗi mới dùng ảnh lỗi.                                                      |
| MSG051 | ITEM_OPERATING_STATUS_REQUIRED           | UNPROCESSABLE CONTENT | item         | Đồ điện tử cần khai trạng thái hoạt động.                                            |
| MSG052 | ITEM_OPERATING_STATUS_NOT_ALLOWED        | UNPROCESSABLE CONTENT | item         | Danh mục này không dùng trạng thái hoạt động.                                        |
| MSG053 | ITEM_NOT_OWNER                           | FORBIDDEN             | item         | Bạn không phải chủ bài đăng.                                                         |
| MSG054 | ITEM_EDIT_NOT_ALLOWED                    | CONFLICT              | item         | Bài đăng đã có yêu cầu được giữ chỗ hoặc không thể sửa.                              |
| MSG055 | ITEM_REMOVE_NOT_ALLOWED                  | CONFLICT              | item         | Bài đăng đã được giữ chỗ hoặc không thể gỡ.                                          |
| MSG056 | ITEM_OFFER_TYPE_CHANGE_NOT_ALLOWED       | CONFLICT              | item         | Không đổi hình thức Cho tặng / Trao đổi khi sửa bài.                                 |
| MSG057 | ITEM_REPUTATION_BLOCKED                  | FORBIDDEN             | item         | Tài khoản có 0 sao uy tín, không thể đăng bài mới.                                   |
| MSG058 | ITEM_ADMIN_BLOCKED                       | FORBIDDEN             | item         | Quản trị viên không được đăng bài giao dịch.                                         |
| MSG059 | ITEM_MODERATION_BLOCKED                  | UNPROCESSABLE CONTENT | item         | Nội dung bài đăng không qua kiểm duyệt tầng 1.                                       |
| MSG060 | ITEM_INVALID_STATUS_TRANSITION           | CONFLICT              | item         | Không thể chuyển trạng thái bài đăng.                                                |
| MSG061 | LISTINGFEE_NOTHING_HELD                  | CONFLICT              | listingfee   | Bài đăng không có khoản phí nào đang giữ.                                            |
| MSG062 | LISTINGFEE_ALREADY_HELD                  | CONFLICT              | listingfee   | Bài đăng đã có khoản phí đang giữ.                                                   |
| MSG063 | LISTINGFEE_ITEM_NOT_FOUND                | NOT FOUND             | listingfee   | Không tìm thấy bài đăng.                                                             |
| MSG064 | LISTINGFEE_NOT_OWNER                     | FORBIDDEN             | listingfee   | Bạn không phải chủ bài đăng.                                                         |
| MSG065 | LISTINGFEE_EDIT_NOT_ALLOWED              | CONFLICT              | listingfee   | Bài đăng ở trạng thái hiện tại không được sửa.                                       |
| MSG066 | LISTINGFEE_RENEW_NOT_ALLOWED             | CONFLICT              | listingfee   | Chỉ gia hạn được bài đang hiển thị.                                                  |
| MSG067 | LISTINGFEE_BOOST_NOT_ALLOWED             | CONFLICT              | listingfee   | Chỉ đẩy được bài đã duyệt và đang hiển thị.                                          |
| MSG068 | LISTINGFEE_CATEGORY_NOT_BOOSTABLE        | CONFLICT              | listingfee   | Danh mục của bài đăng không cho phép đẩy bài.                                        |
| MSG069 | LISTINGFEE_BOOST_EXCEEDS_EXPIRY          | CONFLICT              | listingfee   | Thời gian đẩy không được vượt quá hạn hiển thị của bài.                              |
| MSG070 | LISTINGFEE_BOOST_COOLDOWN                | CONFLICT              | listingfee   | Bài vừa hết đợt đẩy, cần chờ hết thời gian nghỉ mới đẩy lại được.                    |
| MSG071 | LISTINGFEE_BOOST_CHAIN_LIMIT             | CONFLICT              | listingfee   | Đợt đẩy này đã đạt số ngày đẩy liên tục tối đa.                                      |
| MSG072 | LISTINGFEE_BOOST_USER_LIMIT              | CONFLICT              | listingfee   | Bạn đã có đủ số bài đang đẩy cùng lúc cho phép.                                      |
| MSG073 | LISTINGFEE_BOOST_SOLD_OUT                | CONFLICT              | listingfee   | Đã hết suất đẩy bài, vui lòng thử lại sau.                                           |
| MSG074 | MEDIA_NOT_FOUND                          | NOT FOUND             | media        | Không tìm thấy ảnh.                                                                  |
| MSG075 | MEDIA_INVALID_FILE                       | UNPROCESSABLE CONTENT | media        | Tệp không phải ảnh JPG, PNG hoặc WebP hợp lệ.                                        |
| MSG076 | MEDIA_FILE_TOO_LARGE                     | UNPROCESSABLE CONTENT | media        | Ảnh vượt quá kích thước cho phép.                                                    |
| MSG077 | MEDIA_NOT_OWNER                          | FORBIDDEN             | media        | Bạn không sở hữu ảnh này.                                                            |
| MSG078 | MEDIA_NOT_UPLOADED                       | CONFLICT              | media        | Ảnh đã được gắn hoặc không còn dùng được.                                            |
| MSG079 | MEDIA_STORAGE_ERROR                      | INTERNAL SERVER ERROR | media        | Không thể lưu ảnh.                                                                   |
| MSG080 | MEDIA_FILE_MISSING                       | NOT FOUND             | media        | Ảnh không còn trên máy chủ, vui lòng tải lại.                                        |
| MSG081 | NOTIFICATION_NOT_FOUND                   | NOT FOUND             | notification | Không tìm thấy thông báo.                                                            |
| MSG082 | NOTIFICATION_SEND_FAILED                 | INTERNAL SERVER ERROR | notification | Gửi thông báo hoặc email thất bại.                                                   |
| MSG083 | REPORT_NOT_PARTICIPANT                   | FORBIDDEN             | report       | Bạn không phải bên liên quan của khiếu nại.                                          |
| MSG084 | REPORT_RESPONSE_NOT_ALLOWED              | CONFLICT              | report       | Hồ sơ không còn nhận giải trình.                                                     |
| MSG085 | REPORT_TARGET_REQUIRED                   | BAD REQUEST           | report       | Đối tượng báo cáo là bắt buộc.                                                       |
| MSG086 | REPORT_CANNOT_REPORT_SELF                | BAD REQUEST           | report       | Không thể tự báo cáo chính mình hoặc bài đăng của chính mình.                        |
| MSG087 | REPORT_ALREADY_REPORTED                  | CONFLICT              | report       | Bạn đã báo cáo đối tượng này trước đó.                                               |
| MSG088 | REPORT_TARGET_NOT_FOUND                  | NOT FOUND             | report       | Không tìm thấy đối tượng cần báo cáo.                                                |
| MSG089 | REPORT_NOT_FOUND                         | NOT FOUND             | report       | Không tìm thấy báo cáo vi phạm.                                                      |
| MSG090 | DISPUTE_NOT_A_PARTY                      | FORBIDDEN             | report       | Bạn không phải là một bên trong giao dịch này.                                       |
| MSG091 | DISPUTE_REQUEST_NOT_COMPLETED            | CONFLICT              | report       | Chỉ có thể khiếu nại giao dịch đã hoàn thành.                                        |
| MSG092 | DISPUTE_WINDOW_EXPIRED                   | UNPROCESSABLE ENTITY  | report       | Đã quá thời hạn 24 giờ để khiếu nại giao dịch.                                       |
| MSG093 | DISPUTE_NOT_FOUND                        | NOT FOUND             | report       | Không tìm thấy khiếu nại giao dịch.                                                  |
| MSG094 | DISPUTE_NOT_RESPONDENT                   | FORBIDDEN             | report       | Chỉ bên bị khiếu nại mới có quyền giải trình.                                        |
| MSG095 | DISPUTE_ALREADY_CONCLUDED                | CONFLICT              | report       | Khiếu nại này đã được kết luận, không thể giải trình thêm.                           |
| MSG096 | DISPUTE_ALREADY_RESPONDED                | CONFLICT              | report       | Bạn đã gửi bản giải trình cho khiếu nại này rồi.                                     |
| MSG097 | DISPUTE_RESPONSE_WINDOW_EXPIRED          | UNPROCESSABLE ENTITY  | report       | Đã quá thời hạn 48 giờ để gửi giải trình.                                            |
| MSG098 | DISPUTE_INVALID_EVIDENCE                 | BAD REQUEST           | report       | Bằng chứng không hợp lệ hoặc không thuộc quyền sở hữu của bạn.                       |
| MSG099 | REPORT_ALREADY_CONCLUDED                 | CONFLICT              | report       | Báo cáo này đã được kết luận trước đó.                                               |
| MSG100 | REPORT_RESPONSE_WINDOW_OPEN              | CONFLICT              | report       | Bên bị báo cáo vẫn đang trong hạn giải trình, chưa thể kết luận.                     |
| MSG101 | REPORT_RESPONSE_REQUEST_NOT_ALLOWED      | CONFLICT              | report       | Chỉ yêu cầu giải trình được với báo cáo đang chờ xử lý và chưa có giải trình.        |
| MSG102 | REQUEST_NOT_FOUND                        | NOT FOUND             | request      | Không tìm thấy yêu cầu giao dịch.                                                    |
| MSG103 | REQUEST_INVALID_STATE_TRANSITION         | CONFLICT              | request      | Chuyển trạng thái yêu cầu không hợp lệ.                                              |
| MSG104 | REQUEST_ALREADY_EXISTS                   | CONFLICT              | request      | Bạn đã có một yêu cầu đang hoạt động cho bài đăng này.                               |
| MSG105 | REQUEST_LIMIT_EXCEEDED                   | BAD REQUEST           | request      | Bạn đã đạt giới hạn tối đa 5 yêu cầu đang chờ xử lý.                                 |
| MSG106 | REQUEST_SELF_OFFER_NOT_ALLOWED           | BAD REQUEST           | request      | Không thể gửi yêu cầu cho bài đăng của chính mình.                                   |
| MSG107 | REQUEST_ITEM_NOT_AVAILABLE               | CONFLICT              | request      | Bài đăng không ở trạng thái sẵn sàng để gửi yêu cầu.                                 |
| MSG108 | REQUEST_ITEM_NOT_FOUND                   | NOT FOUND             | request      | Không tìm thấy bài đăng.                                                             |
| MSG109 | REQUEST_SWAP_OFFERED_ITEM_REQUIRED       | UNPROCESSABLE ENTITY  | request      | Yêu cầu trao đổi bắt buộc phải chọn món đề nghị.                                     |
| MSG110 | REQUEST_OFFERED_ITEM_NOT_ALLOWED         | BAD REQUEST           | request      | Bài đăng cho tặng không nhận món đề nghị trao đổi.                                   |
| MSG111 | REQUEST_SCHEDULE_LOCKED                  | CONFLICT              | request      | Lịch hẹn đã được xác nhận và bị khoá, không thể chỉnh sửa.                           |
| MSG112 | REQUEST_SCHEDULE_PAST_TIME               | BAD REQUEST           | request      | Thời gian hẹn giao nhận phải ở tương lai.                                            |
| MSG113 | REQUEST_WAIVER_REQUIRED                  | BAD REQUEST           | request      | Hình thức giao hàng (Shipping) bắt buộc phải xác nhận cam kết chịu trách nhiệm.      |
| MSG114 | REQUEST_SCHEDULE_NOT_SET                 | BAD REQUEST           | request      | Chưa có thông tin lịch hẹn để xác nhận.                                              |
| MSG115 | REQUEST_CONTACT_NOT_REVEALED             | CONFLICT              | request      | Thông tin liên lạc chỉ được hiển thị sau khi hai bên đã chốt lịch hẹn.               |
| MSG116 | REQUEST_AS_DESCRIBED_REQUIRED            | BAD REQUEST           | request      | Người nhận bắt buộc phải trả lời câu hỏi món đồ có đúng mô tả hay không.             |
| MSG117 | REQUEST_NOT_AS_DESCRIBED_REASON_REQUIRED | BAD REQUEST           | request      | Bắt buộc nhập lý do khi chọn món đồ không đúng mô tả.                                |
| MSG118 | REQUEST_HANDOVER_ALREADY_CONFIRMED       | CONFLICT              | request      | Bạn đã xác nhận giao nhận cho yêu cầu này rồi.                                       |
| MSG119 | REQUEST_MEDIA_REQUIRED                   | BAD REQUEST           | request      | Danh sách ảnh/video minh chứng không được rỗng.                                      |
| MSG120 | REQUEST_MEDIA_STAGE_INVALID              | BAD REQUEST           | request      | Giai đoạn đính kèm minh chứng không hợp lệ.                                          |
| MSG121 | REQUEST_FORBIDDEN                        | FORBIDDEN             | request      | Bạn không có quyền thao tác trên yêu cầu này.                                        |
| MSG122 | REQUEST_REPUTATION_BLOCKED               | FORBIDDEN             | request      | Tài khoản có 0 sao uy tín, không thể gửi yêu cầu giao dịch.                          |
| MSG123 | REQUEST_ADMIN_BLOCKED                    | FORBIDDEN             | request      | Quản trị viên không được gửi yêu cầu giao dịch.                                      |
| MSG124 | REVIEW_ITEM_NOT_FOUND                    | NOT FOUND             | review       | Không tìm thấy bài đăng cần duyệt.                                                   |
| MSG125 | REVIEW_REJECT_REASON_REQUIRED            | BAD REQUEST           | review       | Lý do từ chối là bắt buộc.                                                           |
| MSG126 | REVIEW_CHECKLIST_NOT_CONFIGURED          | CONFLICT              | review       | Checklist duyệt bài chưa được cấu hình.                                              |
| MSG127 | REVIEW_CHECKLIST_INCOMPLETE              | BAD REQUEST           | review       | Cần trả lời đạt tất cả mục checklist để duyệt bài.                                   |
| MSG128 | REVIEW_CHECKLIST_OUTDATED                | CONFLICT              | review       | Checklist vừa được cập nhật, vui lòng tải lại trang.                                 |
| MSG129 | REVIEW_DONOR_LOCKED                      | CONFLICT              | review       | Chủ bài đang bị khoá hoặc bị cấm nên không duyệt được bài.                           |
| MSG130 | SEARCH_QUERY_INVALID                     | BAD REQUEST           | search       | Nhu cầu tìm đồ không hợp lệ.                                                         |
| MSG131 | SEARCH_ACCOUNT_NOT_FOUND                 | NOT FOUND             | search       | Không tìm thấy tài khoản tìm kiếm.                                                   |
| MSG132 | SEARCH_AI_UNAVAILABLE                    | SERVICE UNAVAILABLE   | search       | Trợ lý tìm đồ hiện chưa sẵn sàng.                                                    |
| MSG133 | SEARCH_AI_TIMEOUT                        | GATEWAY TIMEOUT       | search       | Trợ lý tìm đồ đã quá thời gian chờ.                                                  |
| MSG134 | SEARCH_AI_FAILED                         | BAD GATEWAY           | search       | Không thể xử lý yêu cầu tìm đồ bằng AI.                                              |
| MSG135 | SEARCH_AI_INVALID_RESPONSE               | BAD GATEWAY           | search       | Trợ lý tìm đồ trả về bộ lọc không hợp lệ.                                            |
| MSG136 | AI_DESCRIPTION_LIMIT_REACHED             | TOO MANY REQUESTS     | search       | Bạn đã dùng hết lượt gợi ý mô tả hôm nay.                                            |
| MSG137 | AI_DESCRIPTION_TITLE_INVALID             | BAD REQUEST           | search       | Tên món đồ không hợp lệ để tạo gợi ý mô tả.                                          |
| MSG138 | SETTING_CONFIG_NOT_FOUND                 | NOT FOUND             | setting      | Không tìm thấy cấu hình.                                                             |
| MSG139 | SETTING_CONFIG_INVALID_TYPE              | BAD REQUEST           | setting      | Giá trị cấu hình không đúng kiểu.                                                    |
| MSG140 | SETTING_CONFIG_OUT_OF_RANGE              | BAD REQUEST           | setting      | Giá trị cấu hình nằm ngoài khoảng cho phép.                                          |
| MSG141 | SETTING_CONFIG_NOT_EDITABLE              | BAD REQUEST           | setting      | Cấu hình này cố định, Admin không sửa được.                                          |
| MSG142 | SETTING_RANK_THRESHOLD_ORDER             | BAD REQUEST           | setting      | Điểm tối thiểu các hạng phải tăng dần: Bạc nhỏ hơn Vàng, Vàng nhỏ hơn Kim cương.     |
| MSG143 | SETTING_LEXICON_NOT_FOUND                | NOT FOUND             | setting      | Không tìm thấy từ khóa kiểm duyệt.                                                   |
| MSG144 | SETTING_LEXICON_INVALID_VALUE            | BAD REQUEST           | setting      | Thông tin từ khóa kiểm duyệt không hợp lệ.                                           |
| MSG145 | SETTING_LEXICON_DUPLICATE                | CONFLICT              | setting      | Từ khóa đã tồn tại trong nhóm và phạm vi này.                                        |
| MSG146 | SETTING_CHECKLIST_NOT_FOUND              | NOT FOUND             | setting      | Không tìm thấy mục checklist.                                                        |
| MSG147 | SETTING_CHECKLIST_INVALID_VALUE          | BAD REQUEST           | setting      | Thông tin mục checklist không hợp lệ.                                                |
| MSG148 | SETTING_CHECKLIST_AT_LEAST_ONE_REQUIRED  | BAD REQUEST           | setting      | Phải luôn có ít nhất 1 mục checklist đang dùng (BR-A06).                             |
| MSG149 | USER_NOT_FOUND                           | NOT FOUND             | user         | Không tìm thấy người dùng.                                                           |
| MSG150 | USER_ALREADY_LOCKED                      | CONFLICT              | user         | Tài khoản đã bị khoá.                                                                |
| MSG151 | USER_NOT_LOCKED                          | CONFLICT              | user         | Tài khoản đang hoạt động, không cần mở khoá.                                         |
| MSG152 | USER_AREA_INVALID                        | BAD REQUEST           | user         | Khu vực không tồn tại hoặc đã ngừng hoạt động.                                       |
| MSG153 | USER_CANNOT_LOCK_SELF                    | BAD REQUEST           | user         | Không thể tự khoá tài khoản của chính mình.                                          |
| MSG154 | USER_PHONE_ALREADY_USED                  | CONFLICT              | user         | Số điện thoại đã được sử dụng bởi tài khoản khác.                                    |
| MSG155 | USER_PHONE_SAME_AS_CURRENT               | BAD REQUEST           | user         | Số điện thoại mới trùng với số điện thoại hiện tại.                                  |
| MSG156 | USER_PHONE_CHANGE_COOLDOWN               | BAD REQUEST           | user         | Chỉ được đổi số điện thoại tối đa 1 lần trong 30 ngày.                               |
| MSG157 | USER_PHONE_CHANGE_ACTIVE_DELIVERY        | CONFLICT              | user         | Không thể đổi số điện thoại khi đang có giao dịch chưa hoàn tất.                     |
| MSG158 | INVALID_DATE_RANGE                       | BAD REQUEST           | wallet       | Khoảng ngày không hợp lệ (từ ngày phải trước hoặc bằng đến ngày).                    |
| MSG159 | WALLET_INSUFFICIENT_BALANCE              | PAYMENT REQUIRED      | wallet       | Số dư Credit khả dụng không đủ.                                                      |
| MSG160 | WALLET_NOT_FOUND                         | NOT FOUND             | wallet       | Không tìm thấy ví của người dùng.                                                    |
| MSG161 | WALLET_INVALID_AMOUNT                    | UNPROCESSABLE ENTITY  | wallet       | Số Credit phải lớn hơn 0.                                                            |
| MSG162 | WALLET_HELD_INSUFFICIENT                 | CONFLICT              | wallet       | Số Credit đang giữ không đủ để nhả hoặc trừ.                                         |
| MSG163 | WALLET_PAYMENT_ORDER_NOT_FOUND           | NOT FOUND             | wallet       | Không tìm thấy đơn nạp.                                                              |
| MSG164 | WALLET_PAYMENT_ORDER_NOT_PAYABLE         | CONFLICT              | wallet       | Đơn nạp đã thất bại hoặc hết hạn, không thể cộng Credit.                             |
| MSG165 | WALLET_TOPUP_AMOUNT_INVALID              | UNPROCESSABLE ENTITY  | wallet       | Số tiền nạp phải đạt mức tối thiểu và là bội số của giá một Credit.                  |
| MSG166 | WALLET_INVALID_DATE_RANGE                | UNPROCESSABLE ENTITY  | wallet       | Thời điểm bắt đầu phải trước thời điểm kết thúc.                                     |
| MSG167 | WALLET_AI_FEE_NOT_FOUND                  | NOT FOUND             | wallet       | Không tìm thấy giao dịch phí AI cần hoàn.                                            |
| MSG168 | WALLET_AI_FEE_ALREADY_REFUNDED           | CONFLICT              | wallet       | Giao dịch phí AI này đã được hoàn.                                                   |
| MSG169 | CHAT_OFFER_CHANGE_NOT_FOUND              | CONFLICT              | chat         | Tin nhắn này không phải đề nghị đổi món của cuộc trò chuyện hiện tại.                |
| MSG170 | CHAT_OFFER_CHANGE_NOT_PENDING            | CONFLICT              | chat         | Đề nghị đổi món này đã được trả lời hoặc đã bị huỷ.                                  |
| MSG171 | REQUEST_OFFER_CHANGE_NOT_ALLOWED         | CONFLICT              | request      | Chỉ giao dịch Cho đang chờ chốt lịch mới được chuyển sang Trao đổi bằng món đề nghị. |

## 5.5 Các quyết định đã chốt

| **Mã** | **Quyết định**                                                                                                                                                                                   | **Luật liên quan**                                         |
|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|
| D-01   | Bài Cho miễn phí, tối đa 5 bài; bài Trao đổi 5 Credit trong hạn mức theo hạng, vượt hạn mức đăng thêm được với 10 Credit, không giới hạn trên.                                                   | [PH-02](#BR_PH_02), [BD-05](#BR_BD_05), [BD-06](#BR_BD_06) |
| D-02   | Giữ chuyển đổi hình thức, chỉ lúc chọn người hoặc trong chat bài Cho; Cho → Trao đổi trả 5 Credit, Trao đổi → Cho không hoàn phí; không kiểm hạn mức; không đồng ý thì không chọn được người đó. | Mục K                                                      |
| D-03   | Bỏ AI trong duyệt bài.                                                                                                                                                                           | [KD-01](#BR_KD_01)                                         |
| D-04   | Người nhận báo Không đúng mô tả → Đang tranh chấp, chưa Đã trao, chưa cộng điểm.                                                                                                                 | [GN-07](#BR_GN_07)                                         |
| D-05   | Khiếu nại trong 24 giờ sau hoàn tất; giải trình trong 48 giờ.                                                                                                                                    | [BC-07](#BR_BC_07), [BC-08](#BR_BC_08)                     |
| D-06   | Không gia hạn bài Hết hạn; đăng bài mới.                                                                                                                                                         | [HT-03](#BR_HT_03)                                         |
| D-07   | Bài 3 báo cáo tạm ẩn → Admin yêu cầu giải trình 48 giờ → kết luận; bác bỏ mở lại bài, chấp thuận ẩn hẳn và trừ 1 sao.                                                                            | [BC-02](#BR_BC_02) … [BC-05](#BR_BC_05)                    |
| D-08   | Bỏ chức năng hàng chính hãng.                                                                                                                                                                    | [KD-10](#BR_KD_10)                                         |
| D-09   | Hai mức: Khoá do Admin (có lý do hiển thị), Cấm tự động khi sao về 0; mỗi lần trừ sao báo số sao còn lại; không có sao ân hạn; giao dịch Đang tranh chấp của người bị cấm giữ lại cho Admin.     | Mục B, [UT-03](#BR_UT_03), [UT-04](#BR_UT_04)              |
| D-10   | VNPay sandbox chạy trọn vòng; bỏ nạp thủ công.                                                                                                                                                   | [VI-04](#BR_VI_04), [VI-05](#BR_VI_05)                     |
| D-11   | Thông tin liên lạc chỉ hiện trong app.                                                                                                                                                           | [GN-04](#BR_GN_04)                                         |
| D-12   | Bên nào cũng đề xuất lịch được.                                                                                                                                                                  | [GN-01](#BR_GN_01)                                         |
| D-13   | Gỡ bài thì huỷ ngay các yêu cầu đang chờ.                                                                                                                                                        | [BD-11](#BR_BD_11)                                         |
| D-14   | Có tác nhân Khách (chưa đăng nhập).                                                                                                                                                              | [TK-01](#BR_TK_01)                                         |
| D-15   | Admin chỉ kết luận khi đã có giải trình hoặc đã hết hạn.                                                                                                                                         | [BC-10](#BR_BC_10)                                         |
| D-16   | Bỏ danh mục thực phẩm khỏi hệ thống, giữ từ khoá cấm thực phẩm.                                                                                                                                  | [KD-07](#BR_KD_07)                                         |
| D-17   | Bỏ AI kiểm tra tin nhắn, chỉ dùng tầng 1.                                                                                                                                                        | [CH-04](#BR_CH_04)                                         |
| D-18   | Quá 3 tài khoản mới / IP / 24 giờ thì chặn đăng ký.                                                                                                                                              | [TK-05](#BR_TK_05)                                         |
| D-19   | Đẩy bài theo đợt: mỗi đợt tối đa 6 ngày, nghỉ 3 ngày giữa hai đợt, tối đa 3 bài / người và 30 bài toàn hệ thống, xoay vòng 3 phút, banner trang chủ (chốt 06/11/2026).                           | HT-04 … HT-09                                              |
| D-20   | Gợi ý mô tả bằng AI chỉ dùng tên món, không gửi ảnh.                                                                                                                                             | TT-08, TT-09                                               |
| D-21   | AI trợ lý tìm đồ giữ như code: AI chọn bài trong danh sách ứng viên, mỗi câu hỏi xử lý riêng; AI lỗi thì trả danh sách rỗng, không tự tìm thường.                                                | TT-04, TT-05                                               |
| D-22   | Tìm kiếm thường chỉ tìm từ khoá trong tiêu đề bài.                                                                                                                                               | TT-01                                                      |
| D-23   | Hồ sơ công khai của người dùng chỉ xem được khi đã đăng nhập.                                                                                                                                    | TK-01                                                      |
| D-24   | Nội dung giải trình tối đa 2000 ký tự cho cả báo cáo và khiếu nại.                                                                                                                               | BC-03, BC-08                                               |
| D-25   | Gỡ hẳn code nạp Credit thủ công; chỉ nạp qua VNPay, máy dev và chế độ dự phòng ngày bảo vệ dùng cổng giả lập.                                                                                    | VI-05                                                      |
| D-26   | Thêm cảnh báo nhiều tài khoản cùng một địa chỉ IP (từ 3 tài khoản trong 30 ngày) trên màn Giám sát bất thường.                                                                                   | AD-04                                                      |

# Phụ lục A. Rà soát các sơ đồ đính kèm

Bảng ghi rõ chỗ sai trong từng file sơ đồ dùng để cắt ảnh đưa vào report, so với BR v12 và code nhánh develop (07/11/2026). Các file gốc không bị chỉnh sửa; chỉ conceptual diagram được vẽ lại thành file mới.

## A.1 Sơ đồ ngữ cảnh (ShareLoop_Context_Diagram.drawio)

| **\#** | **Vị trí trên sơ đồ** | **Đang vẽ**                                                                                                    | **Đúng theo hệ thống**                                                                                                                                                          | **Căn cứ**          |
|--------|-----------------------|----------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------|
| 1      | Khách ↔ ShareLoop     | Khách chỉ có "Search Query" và "Item listings and search results".                                             | Khách còn đăng ký tài khoản, nhập OTP, quên mật khẩu (TK-01, UC-01, UC-04). Thêm dữ liệu "thông tin đăng ký, OTP" vào luồng Khách.                                              | TK-01, D-14         |
| 2      | Member → ShareLoop    | "Account details, post data, transaction requests".                                                            | Đúng ý; nên thêm "nhu cầu tìm bằng AI", "báo cáo / khiếu nại" để đủ các luồng chính.                                                                                            | UC-19, UC-29        |
| 3      | Brevo → ShareLoop     | "Email delivery status".                                                                                       | Hệ thống không nhận trạng thái gửi từ Brevo (chỉ nhận kết quả gọi API). Email chỉ chứa OTP và báo đổi mật khẩu; nên ghi "nội dung OTP, báo đổi mật khẩu".                       | TK-14               |
| 4      | ShareLoop ↔ Gemini    | Bốn luồng "Product Search Prompt / Product Recommendations / Text Generation Input / Generated Text Response". | Đúng hướng; nên gọi đúng tên: "nhu cầu + danh sách bài ứng viên" → "ID bài phù hợp, lời nhắn"; "tên món" → "bản nháp mô tả, câu hỏi". Không gửi ảnh, không gửi dữ liệu cá nhân. | TT-04, TT-06, TT-08 |
| 5      | Toàn sơ đồ            | Hộp tác nhân tô xanh dương / xanh lá, hình tròn hệ thống tô xanh lá.                                           | Report yêu cầu trắng đen: ảnh trong tài liệu đã chuyển sang thang xám; nên đổi màu trong file nguồn trước khi nộp.                                                              | Yêu cầu trình bày   |
| 6      | Thiếu tác nhân        | Không có bộ hẹn giờ.                                                                                           | Tác vụ nền do Cloud Scheduler gọi /internal/jobs; có thể thêm tác nhân "Cloud Scheduler" (tuỳ chọn).                                                                            | TD-01 … TD-05       |

## A.2 Sơ đồ use case (ShareLoop_UseCase.drawio)

| **\#** | **Vị trí trên sơ đồ**                                      | **Đang vẽ**                                                                  | **Đúng theo hệ thống**                                                                                                                                                                              | **Căn cứ**    |
|--------|------------------------------------------------------------|------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|
| 1      | Tác nhân "Email/SMS Service"                               | Tên có SMS; chỉ nối "Verify OTP".                                            | Không có SMS (TK-03); đổi thành "Email Service (Brevo)". OTP còn dùng cho quên mật khẩu và đổi số điện thoại (UC-64 dùng bởi UC-03, UC-04).                                                         | TK-03, TK-06  |
| 2      | Tác nhân Khách                                             | Chỉ nối "View public post"; nhãn "Guest" là ô chữ cỡ 8 tách khỏi hình người. | Khách nối thêm "Register account", "Search & filter items" (Register đang nối Member). Đặt nhãn ngay dưới hình người, cùng cỡ chữ các tác nhân khác.                                                | TK-01, D-14   |
| 3      | "View points & rank"                                       | Nối tác nhân User (gồm cả Admin).                                            | Admin không có điểm, hạng (không giao dịch). Nối Member.                                                                                                                                            | TK-13         |
| 4      | "Confirm meetup schedule" «include» "Check Credit balance" | Chốt lịch phải kiểm số dư.                                                   | Giao dịch miễn phí, không kiểm số dư khi chốt lịch. Xoá use case và quan hệ này.                                                                                                                    | PH-09         |
| 5      | "Restore grace star"                                       | Admin cấp sao ân hạn.                                                        | Đã bỏ sao ân hạn. Xoá.                                                                                                                                                                              | UT-09, D-09   |
| 6      | "Handle expired posts"                                     | Admin xử lý bài quá hạn.                                                     | Không có chức năng Admin; bài hết hạn do tác vụ nền, menu "Bài quá hạn" đã ẩn. Xoá hoặc thay bằng use case hệ thống "Xử lý tự động theo lịch".                                                      | TD-01         |
| 7      | "Detect contact info in message" — AI Service              | Có đường nối AI Service.                                                     | Chat chỉ kiểm bằng quy tắc tầng 1, không gọi AI. Bỏ đường nối AI Service.                                                                                                                           | CH-04, D-17   |
| 8      | "provide suggestions"                                      | Use case lơ lửng chỉ nối AI Service.                                         | Đổi thành "Suggest item description" (UC-19A), nối Member, «extend» "Create Give/Swap post".                                                                                                        | TT-08, TT-09  |
| 9      | "Resolve disputes" «include» "Deduct trust star"           | Luôn trừ sao khi xử lý khiếu nại.                                            | Chỉ trừ khi Admin chấp thuận; đổi thành «extend» (điều kiện: chấp thuận).                                                                                                                           | BC-11, BC-12  |
| 10     | "Review violator posts" «extend» "Resolve disputes"        | Admin tự rà soát bài người vi phạm.                                          | Hệ thống tự ẩn mọi bài của người vi phạm khi lý do nghiêm trọng được chấp thuận; đổi tên "Ẩn bài của người vi phạm" (UC-49, tác nhân hệ thống).                                                     | BC-06         |
| 11     | Thiếu use case Member                                      | Không có.                                                                    | Thêm: Boost post (UC-16), Change / reset password (UC-04), Cancel transaction (UC-30), Report not received (UC-31), Respond to report (UC-32), Share post card (UC-25), View notifications (UC-33). | Mục 3         |
| 12     | Thiếu use case Admin                                       | Không có.                                                                    | Thêm: Manage banned keywords (UC-44), Manage checklist (UC-42), Manage transactions (UC-57), View activity logs (UC-58), Look up user wallet (UC-59).                                               | AD-01 … AD-05 |

## A.3 Sơ đồ dữ liệu khái niệm cũ (conceptual.drawio) – đã vẽ lại

File cũ không khớp với 17 bảng hiện tại nên được thay bằng ShareLoop_Conceptual_ERD.drawio (Hình ở mục 3.1.5). Các chỗ sai của file cũ:

| **\#** | **Vị trí trên sơ đồ**         | **Đang vẽ**                    | **Đúng theo hệ thống**                                                                                                                                | **Căn cứ**              |
|--------|-------------------------------|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------|
| 1      | Số thực thể                   | 13 thực thể.                   | Cơ sở dữ liệu có 17 bảng; thiếu WebsiteAttribute, ItemAttributeMapping, ItemMediaMapping, RequestMediaMapping.                                        | V1\_\_init.sql          |
| 2      | Item – MediaFile              | Quan hệ 1 – N trực tiếp "has". | Quan hệ N – N qua item_media_mappings (vai trò ảnh thường / ảnh lỗi, thứ tự). MediaFile còn dùng cho ảnh đại diện, ảnh chat, bằng chứng.              | item_media_mappings     |
| 3      | Request – MediaFile           | 1 – N trực tiếp "attaches".    | N – N qua request_media_mappings (giai đoạn HANDOVER / DISPUTE).                                                                                      | request_media_mappings  |
| 4      | ItemAttribute                 | Chỉ nối ItemCategory.          | Thiếu giá trị thuộc tính của từng bài (item_attribute_mappings nối Item – ItemAttribute).                                                             | item_attribute_mappings |
| 5      | User – Request                | Nhãn "sends/receives".         | requests chỉ có receiver_id (người gửi yêu cầu); chủ bài suy ra qua Item. Thiếu quan hệ Item 0..1 – N Request "là món đề nghị đổi" (offered_item_id). | requests                |
| 6      | Report                        | Chỉ nối User "submits".        | Thiếu Report – Item (bài bị báo cáo), Report – Request (giao dịch bị khiếu nại), Report – User (người bị báo cáo, Admin kết luận).                    | reports                 |
| 7      | Message                       | Chỉ nối Request.               | Thiếu người gửi (User, rỗng = tin hệ thống), ảnh (MediaFile), thẻ bài (Item).                                                                         | messages                |
| 8      | CreditLedger                  | Chỉ nối User.                  | Thiếu Item (phí của bài) và PaymentOrder (mỗi đơn nạp sinh đúng một dòng nạp).                                                                        | credit_ledger           |
| 9      | Tự tham chiếu                 | Không có.                      | Area và ItemCategory có quan hệ cha – con.                                                                                                            | areas, item_categories  |
| 10     | User – Area, User – MediaFile | Không có.                      | User có khu vực mặc định và ảnh đại diện.                                                                                                             | users                   |
| 11     | ActivityLog                   | User bắt buộc 1.               | actor_id có thể rỗng (tác vụ hệ thống): 0..1.                                                                                                         | activity_logs           |

## A.4 Sơ đồ luồng swimlane v12 (bản EN và VI)

Hai bản tiếng Anh và tiếng Việt cùng cấu trúc 24 trang nên có cùng các chỗ sai. Tài liệu chèn bản tiếng Việt.

| **\#** | **Vị trí trên sơ đồ**  | **Đang vẽ**                                                             | **Đúng theo hệ thống**                                                                                                                                                              | **Căn cứ**                     |
|--------|------------------------|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| 1      | Trang 00 Luồng chính   | Một trang dọc tỉ lệ khoảng 1 : 2,75.                                    | Chèn vào trang A4 thì chữ quá nhỏ nên không chèn. Nên tách thành hai trang (Pha 1–2 và Pha 3–4).                                                                                    | Trình bày                      |
| 2      | F06 bước 3             | "Tick checklist (10 mục khởi tạo)".                                     | Hiện có 9 câu đang bật (câu an toàn thực phẩm đã gỡ).                                                                                                                               | KD-07, migration V202611011600 |
| 3      | F08 nhánh B (đẩy bài)  | Kiểm "≤ now + 14 ngày"; "mọi bài đẩy đứng trước, đẩy gần nhất lên đầu". | Đợt đẩy ≤ 6 ngày từ lúc bắt đầu đợt, nghỉ 3 ngày giữa hai đợt; 3 bài / người, 30 bài toàn hệ thống; vòng xoay 3 phút, tối đa 3 bài đẩy / trang; banner trang chủ; báo trước 24 giờ. | HT-04, HT-05, HT-07 … HT-09    |
| 4      | F09 làn AI             | "Dịch vụ AI (OpenRouter)".                                              | Gemini (gemini-3.5-flash-lite).                                                                                                                                                     | docs/api/ai.md                 |
| 5      | F09 bước 11, 14        | Trừ lượt / Credit trước khi gọi AI, lỗi thì hoàn.                       | Chỉ trừ sau khi AI thành công và có ít nhất một bài hợp lệ; không có bước hoàn.                                                                                                     | PH-07                          |
| 6      | F09 bước 12, 15        | AI tách câu thành bộ lọc JSON; không có bài thì nới bộ lọc.             | Hệ thống gửi danh sách bài ứng viên theo lô, AI chọn ID; không có bước nới bộ lọc. Câu ngoài phạm vi bị từ chối.                                                                    | TT-04                          |
| 7      | F09 bước 14 (fallback) | AI lỗi → tìm thường bằng câu đã nhập.                                   | Code trả fallback = true và danh sách rỗng, không tự tìm thường.                                                                                                                    | TT-05                          |
| 8      | F09 nhánh A bước 3     | "Mọi bài đẩy đứng trước, đẩy gần nhất lên đầu".                         | Vòng xoay, tối đa 3 bài đẩy / trang.                                                                                                                                                | HT-05                          |
| 9      | F20 ghi chú đầu trang  | "Đang làm trong code (02/11/2026)".                                     | Cả ba nhánh A, B, C đã có trong code (nhánh C từ 08/11/2026).                                                                                                                       | CD-02 … CD-04                  |
| 10     | S1                     | Cạnh LOGISTICS_CONFIRMED → DISPUTED.                                    | Không có trong RequestStatus.canTransition; code đi qua AWAITING_HANDOVER rồi sang DISPUTED trong cùng một lần xử lý.                                                               | RequestStatus.java             |
| 11     | Tiêu đề trang 00       | "Business Rules v12.1 (02/11/2026)".                                    | Tài liệu BR mang tên v12; nên thống nhất tên phiên bản.                                                                                                                             | BR v12                         |

## A.5 Sơ đồ kiến trúc (system.drawio)

Sơ đồ này thuộc phần IV.1.1 System Architecture (SDD), không chèn vào SRS. Các chỗ cần sửa trước khi đưa vào SDD:

| **\#** | **Vị trí trên sơ đồ**       | **Đang vẽ**                                                                  | **Đúng theo hệ thống**                                                                                  | **Căn cứ**          |
|--------|-----------------------------|------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|---------------------|
| 1      | Khối Database               | Nằm trong vùng Google Cloud Platform.                                        | PostgreSQL chạy trên Neon (dịch vụ managed ngoài GCP). Đưa ra ngoài vùng GCP, ghi "Neon PostgreSQL 16". | docs/decisions/0003 |
| 2      | "AI Service LLM API"        | Không ghi tên dịch vụ.                                                       | Ghi "Gemini API".                                                                                       | docs/api/ai.md      |
| 3      | Tác vụ nền                  | Không có.                                                                    | Thêm Cloud Scheduler / Cloud Run Jobs gọi /internal/jobs.                                               | docs/decisions/0003 |
| 4      | Logo                        | Logo màu Spring, PostgreSQL, R2, VNPay, React, Vercel, Docker, Google Cloud. | Report yêu cầu trắng đen; đổi logo sang đen trắng hoặc chỉ dùng chữ.                                    | Yêu cầu trình bày   |
| 5      | Mũi tên trong khối Database | Một mũi tên nhỏ thừa nằm trong hình trụ.                                     | Xoá.                                                                                                    | —                   |

# Phụ lục B. Danh sách ảnh màn hình cần chụp

Chụp trên bản staging (frontend Vercel, backend Cloud Run) bằng các tài khoản demo; khung ảnh nên ở chiều rộng 1440 px, ẩn dữ liệu cá nhân thật. Mỗi dòng ứng với một khung \[Ảnh chụp màn hình cần chèn\] trong mục 3.

| **\#** | **Chức năng**                                                                                                     | **Ảnh cần chụp**                                                                                             |
|--------|-------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| 1      | [Đăng ký tài khoản và xác thực email](#đăng-ký-tài-khoản-và-xác-thực-email)                                       | Trang Đăng ký (/register): form trống và form có lỗi ở từng ô.                                               |
| 2      | [Đăng ký tài khoản và xác thực email](#đăng-ký-tài-khoản-và-xác-thực-email)                                       | Trang Xác thực email (/verify-email): ô nhập 6 số, nút Gửi lại mã đang đếm ngược 60 giây.                    |
| 3      | [Đăng nhập và đăng xuất](#đăng-nhập-và-đăng-xuất)                                                                 | Trang Đăng nhập (/login).                                                                                    |
| 4      | [Đăng nhập và đăng xuất](#đăng-nhập-và-đăng-xuất)                                                                 | Thông báo khi đăng nhập bằng tài khoản bị khoá (có lý do).                                                   |
| 5      | [Quên mật khẩu và đổi mật khẩu](#quên-mật-khẩu-và-đổi-mật-khẩu)                                                   | Trang Quên mật khẩu: bước nhập email và bước nhập OTP + mật khẩu mới.                                        |
| 6      | [Quên mật khẩu và đổi mật khẩu](#quên-mật-khẩu-và-đổi-mật-khẩu)                                                   | Trang Đổi mật khẩu (/change-password).                                                                       |
| 7      | [Quản lý hồ sơ và đổi số điện thoại](#quản-lý-hồ-sơ-và-đổi-số-điện-thoại)                                         | Trang Hồ sơ (/profile).                                                                                      |
| 8      | [Quản lý hồ sơ và đổi số điện thoại](#quản-lý-hồ-sơ-và-đổi-số-điện-thoại)                                         | Hộp thoại Đổi số điện thoại ở bước nhập OTP.                                                                 |
| 9      | [Xem sao uy tín, điểm và hạng](#xem-sao-uy-tín-điểm-và-hạng)                                                      | Khối uy tín trên trang Hồ sơ (sao, điểm, hạng, lịch sử).                                                     |
| 10     | [Nạp Credit qua VNPay](#nạp-credit-qua-vnpay)                                                                     | Trang Ví Credit: hộp thoại nhập số tiền nạp.                                                                 |
| 11     | [Nạp Credit qua VNPay](#nạp-credit-qua-vnpay)                                                                     | Trang thanh toán VNPay sandbox (thẻ test NCB).                                                               |
| 12     | [Nạp Credit qua VNPay](#nạp-credit-qua-vnpay)                                                                     | Trang Ví sau khi nạp thành công (số dư mới).                                                                 |
| 13     | [Xem ví và lịch sử Credit](#xem-ví-và-lịch-sử-credit)                                                             | Trang Ví Credit (/credit): thẻ số dư, số đang giữ, bảng lịch sử có bộ lọc.                                   |
| 14     | [Đăng bài Cho hoặc Trao đổi](#đăng-bài-cho-hoặc-trao-đổi)                                                         | Trang Đăng đồ (/post): form Cho tặng.                                                                        |
| 15     | [Đăng bài Cho hoặc Trao đổi](#đăng-bài-cho-hoặc-trao-đổi)                                                         | Trang Đăng đồ: form Trao đổi có ô Món mong muốn và hộp thoại xác nhận giữ phí.                               |
| 16     | [Đăng bài Cho hoặc Trao đổi](#đăng-bài-cho-hoặc-trao-đổi)                                                         | Lỗi tầng 1 khi mô tả có số điện thoại.                                                                       |
| 17     | [Gợi ý mô tả bằng AI](#gợi-ý-mô-tả-bằng-ai)                                                                       | Hộp thoại Gợi ý mô tả trên trang Đăng đồ (bản nháp và các câu hỏi).                                          |
| 18     | [Sửa bài và gỡ bài](#sửa-bài-và-gỡ-bài)                                                                           | Trang Hoạt động của tôi (/activities): danh sách bài với các nút Sửa, Gỡ, Gia hạn, Đẩy.                      |
| 19     | [Sửa bài và gỡ bài](#sửa-bài-và-gỡ-bài)                                                                           | Hộp thoại báo phí trước khi sửa (miễn phí / giữ 5 Credit).                                                   |
| 20     | [Gia hạn bài](#gia-hạn-bài)                                                                                       | Hộp thoại Gia hạn bài (phí và hạn hiển thị mới).                                                             |
| 21     | [Đẩy bài](#đẩy-bài)                                                                                               | Hộp thoại Đẩy bài: phí, mốc kết thúc, số suất còn trống.                                                     |
| 22     | [Đẩy bài](#đẩy-bài)                                                                                               | Trang chủ: banner bài đang đẩy.                                                                              |
| 23     | [Tìm kiếm, lọc và xem chi tiết bài](#tìm-kiếm-lọc-và-xem-chi-tiết-bài)                                            | Trang Tìm đồ (/browse) có bộ lọc và bài đẩy ở đầu trang.                                                     |
| 24     | [Tìm kiếm, lọc và xem chi tiết bài](#tìm-kiếm-lọc-và-xem-chi-tiết-bài)                                            | Trang Chi tiết bài (/items/:id) với nút Gửi yêu cầu, Báo cáo.                                                |
| 25     | [AI trợ lý tìm đồ](#ai-trợ-lý-tìm-đồ)                                                                             | Trang Trợ lý AI (/ai): ô nhập, số lượt miễn phí còn lại, kết quả có thẻ bài.                                 |
| 26     | [Gửi yêu cầu xin hoặc đề nghị đổi](#gửi-yêu-cầu-xin-hoặc-đề-nghị-đổi)                                             | Hộp thoại Gửi yêu cầu (có chọn món đem đổi).                                                                 |
| 27     | [Rút yêu cầu](#rút-yêu-cầu)                                                                                       | Tab Yêu cầu đã gửi trên trang Hoạt động, có nút Rút yêu cầu.                                                 |
| 28     | [Chọn người giao dịch và chuyển hình thức Cho ↔ Trao đổi](#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi) | Danh sách người xin của một bài (sao, hạng, tỉ lệ đúng mô tả, món đổi).                                      |
| 29     | [Chọn người giao dịch và chuyển hình thức Cho ↔ Trao đổi](#chọn-người-giao-dịch-và-chuyển-hình-thức-cho-trao-đổi) | Hộp thoại xác nhận "Chuyển bài sang Trao đổi, trừ 5 Credit".                                                 |
| 30     | [Nhắn tin trong giao dịch](#nhắn-tin-trong-giao-dịch)                                                             | Trang Tin nhắn (/messages): thẻ bài ghim ở đầu, một tin bị chặn kèm lý do.                                   |
| 31     | [Chốt lịch giao nhận và xem thông tin liên lạc](#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc)                   | Trang Tiến trình giao dịch: thẻ lịch hẹn đang chờ bên kia xác nhận.                                          |
| 32     | [Chốt lịch giao nhận và xem thông tin liên lạc](#chốt-lịch-giao-nhận-và-xem-thông-tin-liên-lạc)                   | Trang Tiến trình giao dịch sau khi chốt: khối thông tin liên lạc của đối tác.                                |
| 33     | [Xác nhận đã giao, đã nhận](#xác-nhận-đã-giao-đã-nhận)                                                            | Hộp thoại Xác nhận đã nhận với câu hỏi "Món đồ có đúng mô tả không?".                                        |
| 34     | [Xác nhận đã giao, đã nhận](#xác-nhận-đã-giao-đã-nhận)                                                            | Trang Tiến trình giao dịch ở trạng thái Hoàn tất (hạn khiếu nại).                                            |
| 35     | [Huỷ giao dịch và báo chưa nhận / chưa giao](#huỷ-giao-dịch-và-báo-chưa-nhận-chưa-giao)                           | Hộp thoại Huỷ giao dịch có ô lý do.                                                                          |
| 36     | [Huỷ giao dịch và báo chưa nhận / chưa giao](#huỷ-giao-dịch-và-báo-chưa-nhận-chưa-giao)                           | Hộp thoại "Tôi chưa nhận được đồ".                                                                           |
| 37     | [Báo cáo bài, người dùng và khiếu nại giao dịch](#báo-cáo-bài-người-dùng-và-khiếu-nại-giao-dịch)                  | Hộp thoại Báo cáo bài (chọn lý do, mô tả, ảnh).                                                              |
| 38     | [Báo cáo bài, người dùng và khiếu nại giao dịch](#báo-cáo-bài-người-dùng-và-khiếu-nại-giao-dịch)                  | Hộp thoại Khiếu nại giao dịch.                                                                               |
| 39     | [Giải trình báo cáo, khiếu nại](#giải-trình-báo-cáo-khiếu-nại)                                                    | Trang Gửi giải trình (/me/reports/:id hoặc /me/disputes/:id) có hạn giải trình.                              |
| 40     | [Xem thông báo](#xem-thông-báo)                                                                                   | Menu chuông thông báo đang mở (có thông báo chưa đọc).                                                       |
| 41     | [Tổng quan quản trị](#tổng-quan-quản-trị)                                                                         | Trang Tổng quan (/admin).                                                                                    |
| 42     | [Duyệt bài theo checklist](#duyệt-bài-theo-checklist)                                                             | Trang Duyệt bài (/admin/moderation): hàng chờ.                                                               |
| 43     | [Duyệt bài theo checklist](#duyệt-bài-theo-checklist)                                                             | Trang Chi tiết duyệt bài: checklist, từ bị gắn cờ, nút Duyệt / Từ chối.                                      |
| 44     | [Quản lý danh mục, thuộc tính, khu vực](#quản-lý-danh-mục-thuộc-tính-khu-vực)                                     | Trang Danh mục (/admin/categories) có hộp thoại thêm / sửa.                                                  |
| 45     | [Quản lý danh mục, thuộc tính, khu vực](#quản-lý-danh-mục-thuộc-tính-khu-vực)                                     | Trang Khu vực (/admin/districts).                                                                            |
| 46     | [Quản lý từ khoá cấm, checklist, lý do từ chối mẫu](#quản-lý-từ-khoá-cấm-checklist-lý-do-từ-chối-mẫu)             | Trang Từ khóa cấm (/admin/keywords) với ô thử nhanh.                                                         |
| 47     | [Quản lý từ khoá cấm, checklist, lý do từ chối mẫu](#quản-lý-từ-khoá-cấm-checklist-lý-do-từ-chối-mẫu)             | Trang Checklist duyệt bài (/admin/checklist).                                                                |
| 48     | [Cấu hình phí, hạn mức, uy tín và hạng](#cấu-hình-phí-hạn-mức-uy-tín-và-hạng)                                     | Trang Cấu hình phí (/admin/settings/fees).                                                                   |
| 49     | [Cấu hình phí, hạn mức, uy tín và hạng](#cấu-hình-phí-hạn-mức-uy-tín-và-hạng)                                     | Trang Uy tín và hạng (/admin/reputation).                                                                    |
| 50     | [Xử lý báo cáo và khiếu nại](#xử-lý-báo-cáo-và-khiếu-nại)                                                         | Trang Khiếu nại và báo cáo (/admin/complaints): chi tiết hồ sơ, nút Kết luận bị khoá khi còn hạn giải trình. |
| 51     | [Quản lý tài khoản: khoá và mở khoá](#quản-lý-tài-khoản-khoá-và-mở-khoá)                                          | Trang Tài khoản (/admin/users) với hộp thoại Khoá tài khoản.                                                 |
| 52     | [Quản lý giao dịch](#quản-lý-giao-dịch)                                                                           | Trang Giao dịch (/admin/transactions) với bộ lọc Bị kẹt.                                                     |
| 53     | [Giám sát bất thường và nhật ký hoạt động](#giám-sát-bất-thường-và-nhật-ký-hoạt-động)                             | Trang Cảnh báo bất thường (/admin/alerts): khối cặp giao dịch lặp và khối nhiều tài khoản cùng IP.           |
| 54     | [Giám sát bất thường và nhật ký hoạt động](#giám-sát-bất-thường-và-nhật-ký-hoạt-động)                             | Trang Nhật ký hoạt động (/admin/audit-logs).                                                                 |
| 55     | [Báo cáo tài chính, đơn nạp và tra cứu ví](#báo-cáo-tài-chính-đơn-nạp-và-tra-cứu-ví)                              | Trang Báo cáo tài chính (/admin/finance) có biểu đồ và nút xuất CSV.                                         |
| 56     | [Báo cáo tài chính, đơn nạp và tra cứu ví](#báo-cáo-tài-chính-đơn-nạp-và-tra-cứu-ví)                              | Trang Đơn nạp Credit (/admin/finance/topup-orders).                                                          |
| 57     | [Báo cáo tài chính, đơn nạp và tra cứu ví](#báo-cáo-tài-chính-đơn-nạp-và-tra-cứu-ví)                              | Trang Chi tiết ví người dùng (/admin/finance/users/:userId).                                                 |

# Phụ lục C. Đối chiếu BR v12 với code

Những chỗ code nhánh develop khác với BR v12 (02/11/2026) và kết quả nhóm chốt. Bản đối chiếu đầu tiên dùng commit 6835fa0 (07/11/2026); cột Kết quả cập nhật theo code commit b82737d (08/11/2026) và các quyết định ngày 09/11/2026 (mục 5.5).

| **\#** | **Nội dung**                             | **BR v12 ghi**                                             | **Code hiện tại**                                                                                                      | Kết quả                                                                        |
|--------|------------------------------------------|------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| 1      | Đẩy bài (HT-04, HT-05)                   | Cộng dồn tối đa 14 ngày; bài đẩy gần nhất lên đầu.         | Đợt ≤ 6 ngày, nghỉ 3 ngày, 3 bài / người, 30 suất toàn hệ thống, vòng xoay 3 phút, banner trang chủ, báo trước 24 giờ. | Đã chốt 06/11; cập nhật BR theo code (tài liệu này đã làm, xem HT-04 … HT-09). |
| 2      | AI trợ lý (TT-04, TT-05)                 | AI trả bộ lọc, nới bộ lọc; lỗi thì tìm thường.             | AI chọn ID trong danh sách ứng viên; lỗi trả danh sách rỗng.                                                           | Chốt 09/11 (D-21): giữ như code; đã sửa TT-04, TT-05.                          |
| 3      | Gợi ý mô tả (TT-08, TT-09)               | Đề xuất; AI đọc 3 ảnh.                                     | Đã làm; chỉ dùng tên món, 5 lượt / ngày, 20 giây.                                                                      | Chốt (D-20): chỉ dùng tên món; đã sửa TT-08.                                   |
| 4      | Chuyển Cho → Trao đổi trong chat (CD-03) | Có.                                                        | Đã có từ 08/11/2026 (PR \#430, \#431).                                                                                 | Đã làm; đã bỏ ghi chú ở CD-03.                                                 |
| 5      | Món kèm ở bài Cho (YC-04)                | Chưa nhận món kèm.                                         | Đã nhận (CD-01).                                                                                                       | Đã sửa câu trong YC-04.                                                        |
| 6      | Nạp thủ công (VI-05)                     | Đã bỏ.                                                     | Còn code, tắt bằng cấu hình (07/11/2026).                                                                              | Chốt 09/11 (D-25): gỡ code nạp thủ công, chỉ dùng VNPay.                       |
| 7      | Phạm vi tìm kiếm (TT-01)                 | Không nêu.                                                 | Chỉ tìm trong tiêu đề.                                                                                                 | Chốt 09/11 (D-22): chỉ tìm trong tiêu đề; đã ghi vào TT-01.                    |
| 8      | Hồ sơ công khai (TK-01)                  | Khách xem trang chủ, tìm, chi tiết bài, danh mục, khu vực. | Hồ sơ công khai cần đăng nhập; file mô tả UC-07 ghi Khách xem được.                                                    | Chốt 09/11 (D-23): giữ cần đăng nhập; đã sửa UC-07 và TK-01.                   |
| 9      | Độ dài giải trình                        | Không nêu.                                                 | /reports/{id}/response ≤ 2000 ký tự; /disputes/{id}/response ≤ 1000 ký tự.                                             | Chốt 09/11 (D-24): 2000 ký tự cho cả hai; đã ghi vào BC-03, BC-08.             |
| 10     | Cảnh báo nhiều tài khoản cùng IP (AD-04) | Không nêu.                                                 | Chưa có; màn Giám sát bất thường chỉ có cặp giao dịch lặp.                                                             | Chốt 09/11 (D-26): làm thêm; đã ghi vào AD-04, mục 3.8.9 và bảng cấu hình.     |
