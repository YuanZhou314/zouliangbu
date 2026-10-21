package com.zouliangbu.server.auth;

/** 鉴权业务异常：由 GlobalExceptionHandler 统一转成 {code,msg} + 对应 HTTP 状态码。 */
public class ApiException extends RuntimeException {
    public final int code;

    public ApiException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
