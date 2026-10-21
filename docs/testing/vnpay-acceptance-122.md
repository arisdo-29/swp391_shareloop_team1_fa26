# VNPay — nghiệm thu issue #122

Phạm vi: BE1, cấu hình và kiểm thử luồng đã có; không thêm SDK, bảng, migration hay đổi contract.
Chưa có merchant sandbox/TmnCode/HashSecret thật nên **chưa xác minh sandbox end-to-end**.
Không đổi biến môi trường Render, không đăng comment, mở PR hay đóng issue tự động.

## Đối chiếu 5 acceptance criteria

| Tiêu chí #122 | Bằng chứng trước thay đổi | Bổ sung / giới hạn |
| --- | --- | --- |
| Chọn client bằng cấu hình | Ba client có `@ConditionalOnProperty`, mặc định MOCK | `PaymentProviderConfigurationTest`: đúng một client, MOCK/MANUAL không cần merchant; mapping dev/prod; lỗi rõ khi thiếu cấu hình VNPAY |
| Webhook sai chữ ký thì từ chối | `VnPaySigner.verify`, IPN trả 97; có IT chữ ký sai | IT chữ ký thiếu/rỗng/malformed/sai, kiểm không đổi đơn/ví/ledger |
| Test bằng dữ liệu mẫu VNPay | Fixture tự tạo, expected HMAC độc lập | Fixture lấy các trường từ URL Return chính thức, ký lại bằng khóa test; unit test canonical/hash/tamper và IT cộng theo snapshot. Chưa xác minh chữ ký merchant thật |
| Có test nhánh lỗi chính | IT thành công, lặp/đồng thời, sai tiền, thất bại, return hợp lệ | IT số tiền thiếu/rỗng/sai định dạng, provider sai, FAILED/EXPIRED, rollback sau ghi tiền, return sai chữ ký, chặn mock/admin MANUAL cho VNPAY |
| Cập nhật docs/api/payment.md | Đã mô tả IPN/return và biến prod | Bổ sung dev, HTTP status khác RspCode, cách bật/fallback, checklist merchant/Render và SQL bên dưới |

Các lớp liên quan nằm ở `backend/src/test/java/com/shareloop/integration/payment/` và
`backend/src/test/java/com/shareloop/wallet/VnPayCallbackIT.java`.
Năm tiêu chí có bằng chứng code/test/tài liệu; tiêu chí dữ liệu mẫu được kiểm bằng khóa test,
chữ ký merchant thật và sandbox end-to-end vẫn chưa xác minh.

## Kết quả kiểm chứng ngày 21/10/2026

- Java: Temurin 21.0.12.1; Maven Wrapper 3.9.16; Docker Desktop hoạt động sau khi cấp quyền chạy test.
- `.\mvnw.cmd spotless:apply`: **BUILD SUCCESS**.
- `.\mvnw.cmd verify`: Maven **BUILD SUCCESS**, Surefire **897 tests**, Failsafe **268 tests**,
  tất cả failures/errors/skipped bằng **0**. PostgreSQL 16 chạy bằng Testcontainers.
- `VnPayCallbackIT`: **30 tests pass**, gồm fixture, concurrency, rollback và chặn provider sai.
- `PaymentProviderConfigurationTest`: **10 tests pass**; `VnPaySignerTest`: **4**;
  `VnPayClientTest`: **1**. `PaymentServiceIT`: **3**; `ManualTopUpIT`: **14**;
  `PaymentExpiryServiceIT`: **2**, đều pass.
- `git diff --check`: pass.
- Sau chỉnh sửa giới hạn log cấu hình, chạy lại
  `cmd /d /c ".\mvnw.cmd spotless:apply test -Dtest=PaymentProviderConfigurationTest > target\vnpay-final-check.log 2>&1"`:
  **BUILD SUCCESS**, **10 tests pass**, failures/errors/skipped = 0, shell exit **0**.
  Lượt chạy độc lập đầu tiên phát hiện logger chưa khởi tạo; đã sửa xử lý null và chạy lại thành công.

Báo cáo: `backend/target/surefire-reports/`, `backend/target/failsafe-reports/`,
log lifecycle: `backend/target/vnpay-verify.log` (không đưa giá trị cấu hình vào tài liệu).
Các artifact trong target không được commit.

Lần chạy trong sandbox hạn chế ban đầu bị chặn: wrapper PowerShell báo `Cannot index into a null array`,
Docker pipe bị từ chối quyền. Thử Maven cache trực tiếp không khai repo báo không tạo được `C:\.m2\repository`;
thử với `-Dmaven.repo.local=C:\Users\admin\.m2\repository` định dạng thành công nhưng verify dừng ở quyền đọc
dependency Hamcrest khi compile test. Sau khi chạy wrapper ngoài sandbox hạn chế, Maven đã hoàn tất toàn bộ lifecycle.
Lệnh gom log `verify *> target/vnpay-verify.log` khiến lớp PowerShell trả exit 1 và ghi `NativeCommandError`
tại cảnh báo Mockito trên stderr, dù Maven cuối log báo BUILD SUCCESS và cả Surefire/Failsafe đều không lỗi.
Để thu log mà không bị lớp PowerShell diễn giải stderr, có thể chạy:

```powershell
cmd /d /c ".\mvnw.cmd verify > target\vnpay-verify.log 2>&1"
```

Không bỏ qua hay xóa test; không dùng credentials VNPay thật. Chưa chạy thanh toán sandbox end-to-end.

## File thay đổi và lý do

- `backend/src/main/resources/application.yml`, `application-prod.yml`: chuyển mapping payment provider/VNPay về cấu hình dùng chung, loại khai báo trùng; giữ nguyên giá trị mặc định và mapping hiệu lực của prod.
- `backend/src/main/java/com/shareloop/integration/payment/VnPayClient.java`: báo lỗi startup cả khi pay-url trắng.
- `backend/src/test/java/com/shareloop/integration/payment/PaymentProviderConfigurationTest.java`: kiểm đúng một provider, không cần merchant cho MOCK/MANUAL, mapping dev/prod và cấu hình bắt buộc.
- `backend/src/test/java/com/shareloop/integration/payment/VnPayClientTest.java`, `VnPaySignerTest.java`: kiểm thuật toán/encoding và chữ ký bằng expected độc lập, không tự xác thực bằng chính signer đang kiểm thử.
- `backend/src/test/java/com/shareloop/integration/payment/VnPayTestData.java`, `backend/src/test/resources/payment/vnpay-official-return.properties`: helper test và fixture nguồn chính thức, chỉ dùng khóa giả.
- `backend/src/test/java/com/shareloop/wallet/VnPayCallbackIT.java`: các nhánh lỗi, trạng thái cuối, rollback và chặn xác nhận VNPAY qua luồng khác.
- `docs/api/payment.md`, tài liệu này: hướng dẫn dev/deploy, đối chiếu acceptance và checklist sau khi có merchant.

Không cần sửa `.env.example`: đã có các biến VNPay với giá trị giả. Giữ nguyên thay đổi có sẵn của người dùng
ngoài khối payment trong `application.yml`. Không chỉnh SecurityConfig, pom.xml, frontend, schema hay module BE2/BE3.

## Fixture và giới hạn kiểm chứng

Nguồn: [VNPay PAY 2.1.0 — Cài đặt Code Return URL](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html),
URL ví dụ có `vnp_TxnRef=166117`, `vnp_TransactionNo=14226112`, ngày `20231207170112`.
Resource: `backend/src/test/resources/payment/vnpay-official-return.properties`.
Giữ các trường thanh toán của ví dụ; không dùng chữ ký công bố vì không có khóa merchant tương ứng.
`test-secret` là khóa giả công khai, chỉ dùng test. Expected hash lưu cố định, tính ngoài signer bằng .NET:

```powershell
$fixture = Get-Content src/test/resources/payment/vnpay-official-return.properties -Raw | ConvertFrom-StringData
$hmac = New-Object System.Security.Cryptography.HMACSHA512
$hmac.Key = [Text.Encoding]::UTF8.GetBytes($fixture.'test-key')
-join ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($fixture.canonical)) | ForEach-Object { $_.ToString('x2') })
$hmac.Dispose()
```

Chạy trong `backend/`. Canonical sắp tên tham số, form URL-encode từng key/value, bỏ hai trường hash;
không URL-decode chuỗi canonical trước khi tính HMAC. `VnPaySignerTest` so exact canonical và expected hash;
`VnPayCallbackIT` dùng nguyên hash cố định cho fixture. Các callback biến thể dùng helper test độc lập,
không gọi signer production để sinh chữ ký. Đây là kiểm thử dữ liệu mẫu với khóa test,
không phải một giao dịch sandbox của ShareLoop hay xác thực chữ ký gốc VNPay.

## Chạy automated tests không cần merchant

Yêu cầu Java 21 và Docker hoạt động; test DB dùng PostgreSQL 16 Testcontainers, không dùng DB deploy.
Surefire chạy `*Test`, Failsafe chạy `*IT` ở lifecycle `verify`; không chỉ chạy `test` rồi kết luận IT pass.

```powershell
.\mvnw.cmd spotless:apply
.\mvnw.cmd verify
```

Linux/macOS dùng `./mvnw`. Khi cần chạy riêng phần payment sau khi sửa:

```powershell
.\mvnw.cmd '-Dtest=PaymentProviderConfigurationTest,VnPaySignerTest,VnPayClientTest,PaymentServiceTest' test
.\mvnw.cmd '-Dit.test=VnPayCallbackIT,PaymentServiceIT,ManualTopUpIT,PaymentExpiryServiceIT' verify
```

Đọc `target/surefire-reports` và `target/failsafe-reports`; không skip test hay sửa test module khác để báo xanh.

## Các bước BE1 sau khi nhận thông tin VNPay

1. Lấy TmnCode và HashSecret **sandbox** từ kênh merchant được cấp; xác nhận merchant dùng PAY 2.1.0.
   Giữ HashSecret ở biến môi trường backend; không đưa vào FE, repository, ảnh chụp hay log.
2. Chọn backend Render dành cho sandbox/demo và ghi lại provider hiện tại để có thể quay về.
   Giữ nguyên cấu hình deploy thật ngoài thay đổi payment do người vận hành thực hiện cho môi trường được chọn.
3. Trong Render → backend service → Environment, đặt:

   | Biến | Giá trị cần đặt |
   | --- | --- |
   | `PAYMENT_PROVIDER` | `vnpay` |
   | `PAYMENT_MOCK_ENABLED` | `false` |
   | `VNPAY_TMN_CODE` | TmnCode sandbox được cấp |
   | `VNPAY_HASH_SECRET` | HashSecret sandbox được cấp |
   | `VNPAY_PAY_URL` | `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` |
   | `VNPAY_RETURN_URL` | `https://<backend-domain>/api/v1/payments/vnpay/return` |
   | `VNPAY_FE_RESULT_URL` | `https://<frontend-domain>/credit` |

4. Trong merchant sandbox, đăng ký IPN GET công khai HTTPS:
   `https://<backend-domain>/api/v1/payments/vnpay/ipn`.
   Nếu giao diện merchant không cho tự cấu hình IPN, yêu cầu bộ phận VNPay gắn URL này cho TmnCode.
   Return URL đi qua backend; FE result URL đi tới frontend, không hoán đổi hai URL.
5. Save/redeploy backend. Kiểm `/actuator/health`, không có lỗi thiếu cấu hình VNPay.
   Gọi IPN không kèm chữ ký phải HTTP 200, JSON `RspCode=97`;
   return không chữ ký phải HTTP 302 về FE với `status=INVALID_SIGNATURE`, không cộng tiền.
   Các API ví/tạo đơn vẫn yêu cầu Bearer Token. Không mở thêm endpoint public.
6. Đăng nhập user test, ghi số dư ban đầu, tạo đơn mới từ `/credit` với số tiền hợp lệ theo ConfigService.
   Ghi `orderCode`, `amountVnd`, `credits`, `vndPerCredit`. Response phải `provider=VNPAY`;
   URL thanh toán phải dùng sandbox host. Không tái sử dụng đơn MOCK/MANUAL hay sửa provider trong DB.
7. Thanh toán bằng phương thức/thẻ thử do VNPay cung cấp cho sandbox. Chờ IPN;
   return `SUCCESS` chưa chứng minh tiền đã cộng. Dùng Bearer Token chủ đơn đọc
   `GET /api/v1/wallet/topup-orders/{orderCode}` đến khi backend trả kết quả.
   Kiểm SUCCESS, `paid_at`, `signature_verified`, provider transaction và snapshot không đổi.
8. Kiểm số dư tăng đúng `credits` snapshot, `hasToppedUp=true`, đúng một TOP_UP gắn đơn,
   `amount=credits` và `balance_after` đúng số dư sau nạp. Dùng API ví/lịch sử và SQL read-only bên dưới.
9. Replay IPN hợp lệ của chính giao dịch test (giữ đủ tham số/chữ ký, gửi query đúng encoding):
   HTTP 200, `RspCode=02`; số dư/ledger không tăng thêm. Refresh return cũng không cộng lại.
   Không đưa full callback chứa thông tin merchant vào tài liệu công khai.
10. Tạo đơn mới để thử hủy/thất bại. Nếu gateway gửi IPN đã ký với một trong hai mã khác `00`,
    PENDING phải sang FAILED, không tăng ví/ledger. Nếu hủy chỉ dẫn tới return hoặc không có IPN,
    đơn có thể vẫn PENDING rồi EXPIRED theo job hiện có; không coi return là lệnh đổi trạng thái.
11. Thử return tới trước IPN hoặc reload trang kết quả: FE hiển thị chờ khi API còn PENDING;
    chỉ hiển thị đã cộng khi API đơn SUCCESS và tải lại ví/lịch sử. Ghi nhận mã đơn, thời điểm,
    HTTP/RspCode, số dư trước/sau và số ledger, che dữ liệu nhạy cảm.
12. Chỉ sau các kiểm tra trên mới ghi kết quả sandbox end-to-end vào issue #122 và để người phụ trách
    quyết định nghiệm thu/đóng. Nếu chưa có credentials hoặc demo cần chạy ngay, giữ cấu hình hiện tại;
    khi chọn fallback MANUAL, người vận hành đặt `PAYMENT_PROVIDER=manual`, `PAYMENT_MOCK_ENABLED=false`,
    restart và tạo đơn mới. MANUAL dùng QR minh họa → mark-paid → admin approve/reject.
    Không biến đơn VNPAY đang chờ thành MANUAL và không admin duyệt thay IPN.

SQL chạy read-only trên đúng DB môi trường test; thay `:orderCode` bằng mã đơn cần đối chiếu qua công cụ SQL:

```sql
SELECT id, order_code, user_id, provider, status, amount_vnd, credits,
       vnd_per_credit, signature_verified, provider_txn_id, paid_at
FROM payment_orders WHERE order_code = :orderCode;

SELECT u.id, u.credit_balance, u.has_topped_up
FROM users u JOIN payment_orders p ON p.user_id = u.id
WHERE p.order_code = :orderCode;

SELECT l.id, l.user_id, l.type, l.amount, l.balance_after, l.payment_order_id
FROM credit_ledger l JOIN payment_orders p ON p.id = l.payment_order_id
WHERE p.order_code = :orderCode;
```

So với số dư chụp trước giao dịch khi user test không có giao dịch khác đồng thời.
Giao dịch thành công phải đúng một dòng TOP_UP; thất bại/hủy chưa thành công phải không có TOP_UP.

## FE phối hợp

Không cần đổi contract hay code FE trong task này. Xác nhận `/credit` nhận redirect,
giữ `orderCode`, gọi API đọc trạng thái bằng token chủ đơn, xử lý IPN đến chậm và tải lại ví/lịch sử khi SUCCESS.
Không suy ra đã cộng từ `status=SUCCESS` trên query return; MANUAL vẫn đi checkout/mark-paid/admin như trước.

## Nội dung đề xuất, chưa đăng

Comment issue:

> test(payment): bổ sung nghiệm thu VNPay #122 bằng cấu hình test
>
> Đã bổ sung mapping VNPay dùng chung dev/prod, kiểm tra provider/cấu hình,
> fixture dữ liệu mẫu chính thức ký lại độc lập bằng khóa test và các nhánh lỗi callback.
> Spotless pass; Maven verify BUILD SUCCESS: 897 unit tests và 268 integration tests, không lỗi/skip;
> trong đó VnPayCallbackIT có 30 test pass trên PostgreSQL 16 Testcontainers.
> Chưa có merchant sandbox/TmnCode/HashSecret thật, chưa xác minh sandbox end-to-end.
> Giữ issue mở để BE1 thực hiện checklist Render/merchant trong docs/testing/vnpay-acceptance-122.md.

Tiêu đề PR:

> fix(payment): complete VNPay configuration and callback acceptance coverage

Mô tả PR:

> fix(payment): share VNPay environment mapping across dev and prod and validate required payment settings
>
> Add provider selection tests, official sample fields signed with a test key, and callback failure/rollback coverage.
> Preserve MOCK/MANUAL behavior, snapshot crediting and existing IPN/return contracts.
> Document merchant/Render verification steps and MANUAL fallback. Refs #122; sandbox end-to-end remains pending credentials.
> Validation: Spotless passed; Maven verify BUILD SUCCESS (897 unit tests, 268 integration tests, zero failures/errors/skips).
