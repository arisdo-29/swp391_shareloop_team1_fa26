package com.shareloop.common.exception;

import com.shareloop.common.api.ErrorResponse;
import com.shareloop.common.web.TraceIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return build(ex.getErrorCode(), ex.getMessage(), ex.getField(), ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        var fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst();
        if (fieldError.isPresent()) {
            return build(
                    CommonErrorCode.VALIDATION_FAILED,
                    fieldError.get().getDefaultMessage(),
                    fieldError.get().getField(),
                    null);
        }
        return build(CommonErrorCode.VALIDATION_FAILED, null, null, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        var first = ex.getConstraintViolations().stream().findFirst();
        if (first.isPresent()) {
            ConstraintViolation<?> violation = first.get();
            String path = violation.getPropertyPath().toString();
            String field = path.substring(path.lastIndexOf('.') + 1);
            return build(CommonErrorCode.VALIDATION_FAILED, violation.getMessage(), field, null);
        }
        return build(CommonErrorCode.VALIDATION_FAILED, null, null, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(CommonErrorCode.VALIDATION_FAILED, "Nội dung yêu cầu không đọc được.", null, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Class<?> required = ex.getRequiredType();
        String expected = required == null ? "hợp lệ" : describeType(required);
        return build(
                CommonErrorCode.VALIDATION_FAILED,
                "Tham số '" + ex.getName() + "' phải có kiểu " + expected + ".",
                ex.getName(),
                null);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoResource(Exception ex) {
        return build(CommonErrorCode.NOT_FOUND, null, null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(CommonErrorCode.FORBIDDEN, null, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception, traceId={}", MDC.get(TraceIdFilter.MDC_KEY), ex);
        return build(CommonErrorCode.INTERNAL_ERROR, null, null, null);
    }

    private static String describeType(Class<?> type) {
        if (Number.class.isAssignableFrom(type)
                || (type.isPrimitive() && type != boolean.class && type != char.class)) {
            return "số";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true/false";
        }
        if (type.isEnum()) {
            return "một trong các giá trị cho phép";
        }
        return type.getSimpleName();
    }

    private ResponseEntity<ErrorResponse> build(
            ErrorCode errorCode, String message, String field, Map<String, Object> details) {
        var body = new ErrorResponse(
                errorCode.code(),
                message != null ? message : errorCode.defaultMessage(),
                field,
                details,
                MDC.get(TraceIdFilter.MDC_KEY));
        return ResponseEntity.status(errorCode.status()).body(body);
    }
}
