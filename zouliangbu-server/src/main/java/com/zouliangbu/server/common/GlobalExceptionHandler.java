package com.zouliangbu.server.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把异常统一转成 { code, msg, data }，避免把堆栈暴露给客户端。
 *
 * 分层原则：
 *  - 客户端传错（参数/路径/方法/转义） → 4xx，code 用 1001/1004，日志只记 warn 不记堆栈
 *  - 服务端自身故障 → 500 + code 5000，完整堆栈写日志
 * 否则线上全是 500，客户端无法区分"我传错了"和"服务挂了"。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** @Valid 作用于请求体（DTO 校验）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> onValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("参数不合法");
        log.warn("validation failed: {}", msg);
        return badRequest(msg);
    }

    /** @Min/@Max 等直接标注在 @RequestParam 上的校验失败。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> onMethodValidation(HandlerMethodValidationException e) {
        String msg = e.getAllErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("参数不合法");
        log.warn("method validation failed: {}", msg);
        return badRequest(msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> onConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .orElse("参数不合法");
        log.warn("constraint violation: {}", msg);
        return badRequest(msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> onMissingParam(MissingServletRequestParameterException e) {
        String msg = "缺少参数 " + e.getParameterName();
        log.warn(msg);
        return badRequest(msg);
    }

    /** 参数类型转不过去，例如 size=abc。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> onTypeMismatch(MethodArgumentTypeMismatchException e) {
        String msg = "参数 " + e.getName() + " 类型不正确";
        log.warn(msg);
        return badRequest(msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> onUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("unreadable request body: {}", e.getMessage());
        return badRequest("请求体格式错误");
    }

    /** URL 里出现非法百分号转义时，Tomcat 在进入 Controller 前就抛这个。 */
    @ExceptionHandler(org.apache.tomcat.util.http.InvalidParameterException.class)
    public ResponseEntity<ApiResponse<Void>> onInvalidParameter(
            org.apache.tomcat.util.http.InvalidParameterException e) {
        log.warn("invalid query parameter encoding: {}", e.getMessage());
        return badRequest("参数编码错误，请检查 URL 转义");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> onNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ApiResponse.ErrorCode.NOT_FOUND, "接口不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> onMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(ApiResponse.ErrorCode.BAD_REQUEST, "请求方法不支持"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> onUnknown(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ApiResponse.ErrorCode.SERVER_ERROR, "服务异常"));
    }

    private static ResponseEntity<ApiResponse<Void>> badRequest(String msg) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ApiResponse.ErrorCode.BAD_REQUEST, msg));
    }
}
