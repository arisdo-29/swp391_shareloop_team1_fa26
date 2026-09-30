# ShareLoop — hướng dẫn cho trợ lý AI

Trả lời và giải thích bằng tiếng Việt. Tên class, biến, commit message viết tiếng Anh.

## Git, commit và ghi công (luật cứng)
- KHÔNG chạy git commit, git push, git merge, git rebase, gh pr create/merge.
  Người làm tự xem diff, tự chạy test và tự commit dưới tên mình.
- KHÔNG ghi công AI ở bất kỳ đâu: không Co-Authored-By, không "Generated with/by",
  không tên công cụ AI trong commit message, mô tả PR, comment, @author,
  README hay tài liệu.
- Khi được nhờ soạn commit message hoặc mô tả PR: chỉ viết nội dung
  dạng type(scope): mô tả, không thêm dòng ký tên nào.

## Dự án
- Monorepo: backend/ (Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 16, Flyway),
  frontend/ (React + Vite; có AGENTS.md riêng, làm trong frontend/ thì theo file đó).
- Spring Boot khoá ở 4.1.1, không tự đổi phiên bản. Viết theo Spring Boot 4:
  spring-boot-starter-webmvc, spring-boot-starter-flyway, Jackson 3 (tools.jackson),
  @MockitoBean. Không dùng starter "classic".
- Đặc tả: docs/srs/ (SRS v10; Luồng hoạt động & ERD — 17 bảng ĐÃ CHỐT;
  Lộ trình triển khai Backend). Không tự thêm bảng.
- Contract API: docs/api/<module>.md. Đọc file của module trước khi viết controller.

## Lệnh (chạy trong backend/)
- Database dev: docker compose up -d
- Định dạng: ./mvnw spotless:apply
- Build + test: ./mvnw verify

## Cấu trúc (Lộ trình, chương 4)
- Package theo module: com.shareloop.<module>/
  {controller, service, repository, entity, dto, mapper, event, job}
- Chỉ sửa trong module của task. Gọi module khác qua <Tên>Service;
  KHÔNG inject repository của module khác.
- Giữa các module chỉ lưu id (Long), không @ManyToOne sang entity module khác.
- Bảng users/items: mỗi module map nhóm cột của mình bằng entity lát cắt;
  không map một cột ở hai entity (trừ updated_at, updated_by qua SliceAudit).

## Cột chuẩn và xoá mềm (ERD mục 7.11)
- Entity bảng chính và bảng danh mục kế thừa common.entity.BaseEntity
  (created_at, updated_at, created_by, updated_by, is_active, is_deleted),
  gắn @SQLRestriction("is_deleted = false"); status là enum riêng từng entity.
- Không xoá cứng: gọi markDeleted(). Ẩn / khoá: is_active = false, không đổi status.
- Không tự gán created_by, updated_by, created_at, updated_at: JPA Auditing lo.

## Luật bắt buộc
- Tiền chỉ đổi trong wallet.CreditService, có khoá dòng và ghi credit_ledger.
- Trạng thái Request/Item chỉ đổi qua service sở hữu, luôn kiểm canTransition().
- Mọi con số nghiệp vụ đọc ConfigService(ConfigKey), không viết cứng.
- Không trả entity ra API; DTO là record có @Valid.
- Lỗi nghiệp vụ: throw BusinessException(<Module>ErrorCode.X).
- Schema chỉ đổi bằng migration MỚI: V<yyyyMMddHHmm>__<module>_<mo_ta>.sql;
  không sửa migration cũ.
- Service có business rule phải có test; test DB dùng Testcontainers, không dùng H2.

## Không được làm
- Không đổi pom.xml, SecurityConfig, common/, config/ trừ khi task yêu cầu rõ.
- Không đọc, tạo hay commit .env, API key, mật khẩu.
- Không tắt hoặc xoá test để build xanh.