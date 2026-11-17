package com.shareloop.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Luật mật khẩu đặt mới dùng chung cho đăng ký, đổi và đặt lại: 8–64 ký tự, có chữ, số và ký tự đặc biệt. Đổi
 * luật thì chỉ sửa ở đây.
 */
// Annotation gộp: gắn @ValidPassword tương đương gắn cả @NotBlank và @Size bên dưới lên trường đó.
// validatedBy = {}: không cần lớp kiểm riêng, Bean Validation chạy các ràng buộc đã gộp.
// @ReportAsSingleViolation: sai bất kỳ ràng buộc nào cũng chỉ trả một lỗi với message() bên dưới.
@NotBlank
@Size(min = 8, max = 64)
@Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d)(?=.*[^\\p{L}\\d\\s]).{8,64}$")
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValidPassword {

    String message() default "Mật khẩu phải có từ 8–64 ký tự, gồm chữ, số và ký tự đặc biệt.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
