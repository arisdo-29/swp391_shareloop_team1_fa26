package com.shareloop.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Luật mật khẩu dùng chung cho đăng ký và đổi mật khẩu: bắt buộc, 8–64 ký tự (BCrypt chỉ dùng 72 byte đầu). Đổi
 * luật thì chỉ sửa ở đây.
 */
// Annotation gộp: gắn @ValidPassword tương đương gắn cả @NotBlank và @Size bên dưới lên trường đó.
// validatedBy = {}: không cần lớp kiểm riêng, Bean Validation chạy các ràng buộc đã gộp.
// @ReportAsSingleViolation: sai bất kỳ ràng buộc nào cũng chỉ trả một lỗi với message() bên dưới.
@NotBlank
@Size(min = 8, max = 64)
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValidPassword {

    String message() default "Mật khẩu phải từ 8 đến 64 ký tự.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
