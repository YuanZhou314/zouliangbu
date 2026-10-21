package com.zouliangbu.server.common;

import java.util.Base64;

import com.zouliangbu.server.auth.AuthService;

/**
 * 统一响应体：{ code, msg, data }。
 * code = 0 成功；非 0 为业务错误码（见 {@link ErrorCode}）。
 *
 * 注意：本类只负责 body，不决定 HTTP 状态码。
 * Controller 里请用 {@link #failResponse}（带状态码）而不是 {@link #fail}（恒 200），
 * 否则故障会被包成 HTTP 200 —— 客户端只看状态码时会当成成功，"请求成功但数据是空的"极难定位。
 */
public record ApiResponse<T>(int code, String msg, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    /** 仅构造错误 body，HTTP 仍是 200。业务代码请改用 {@link #failResponse}。 */
    public static <T> ApiResponse<T> fail(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }

    /** 错误 body + 对应 HTTP 状态码（推荐用法）。 */
    public static <T> org.springframework.http.ResponseEntity<ApiResponse<T>> failResponse(int code, String msg) {
        return org.springframework.http.ResponseEntity
                .status(httpStatusOf(code))
                .body(new ApiResponse<>(code, msg, null));
    }

    /** 业务码 → HTTP 状态码的唯一映射处，避免各处各写一套。 */
    public static org.springframework.http.HttpStatus httpStatusOf(int code) {
        return switch (code) {
            case ErrorCode.BAD_REQUEST -> org.springframework.http.HttpStatus.BAD_REQUEST;
            case ErrorCode.NOT_FOUND -> org.springframework.http.HttpStatus.NOT_FOUND;
            case ErrorCode.SERVER_ERROR -> org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
            // 鉴权类业务码（AuthService）：占用 400，凭据错误 401
            case AuthService.USERNAME_TAKEN -> org.springframework.http.HttpStatus.BAD_REQUEST;
            case AuthService.BAD_CREDENTIALS -> org.springframework.http.HttpStatus.UNAUTHORIZED;
            default -> org.springframework.http.HttpStatus.OK;
        };
    }

    public static final class ErrorCode {
        public static final int BAD_REQUEST = 1001;
        public static final int NOT_FOUND = 1004;
        public static final int SERVER_ERROR = 5000;

        private ErrorCode() {
        }
    }

    /** cursor 编解码：把自增 id 转成不透明字符串，避免客户端依赖具体 id 语义。 */
    public static final class Cursor {
        public static String encode(long id) {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Long.toString(id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        public static Long decode(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return null;
            }
            try {
                String raw = new String(
                        Base64.getUrlDecoder().decode(cursor),
                        java.nio.charset.StandardCharsets.UTF_8);
                return Long.parseLong(raw);
            } catch (RuntimeException e) {
                return null; // 非法 cursor 当作"从头开始"，不抛异常打断用户
            }
        }

        private Cursor() {
        }
    }
}
