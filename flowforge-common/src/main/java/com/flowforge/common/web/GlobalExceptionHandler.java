package com.flowforge.common.web;

import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 统一异常出口。
 *
 * <p>只把错误码与脱敏后的摘要返回给客户端，原始堆栈只进日志。
 * 未经预期的异常一律记 ERROR，避免被业务异常掩盖。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("业务异常 code={} message={}", errorCode.code(), ex.getMessage());
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResponse.fail(errorCode, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return badRequest(detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return badRequest(detail);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        log.warn("请求参数不合法: {}", ex.getMessage());
        return badRequest("请求参数不合法");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("请求体无法解析: {}", ex.getMostSpecificCause().getMessage());
        return badRequest("请求体格式错误");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.httpStatus())
                .body(ApiResponse.fail(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.httpStatus())
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未预期异常", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String detail) {
        return ResponseEntity.status(ErrorCode.INVALID_PARAM.httpStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_PARAM, detail));
    }

    private String describe(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
