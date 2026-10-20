package com.zouliangbu.server.common;

import java.util.Base64;

/**
 * 统一响应体：{ code, msg, data }。
 * code = 0 成功；非 0 为业务错误码（见 {@link ErrorCode}）。
 */
public record ApiResponse<T>(int code, String msg, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> fail(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
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
