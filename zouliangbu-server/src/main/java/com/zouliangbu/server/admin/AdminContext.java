package com.zouliangbu.server.admin;

/**
 * 当前请求的管理员身份。
 *
 * 现在是单账号（固定密码换 token），operator 恒为 {@code "admin"}。
 * 将来加多账号时只改 {@link AdminAuthInterceptor} 的登录逻辑，这里与审计表都不用动。
 */
public final class AdminContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void set(String operator) {
        CURRENT.set(operator);
    }

    /** 未登录/拦截器未命中时返回 "system"，审计表 operator 是 NOT NULL。 */
    public static String current() {
        String s = CURRENT.get();
        return s == null || s.isBlank() ? "system" : s;
    }

    public static void clear() {
        CURRENT.remove();
    }

    private AdminContext() {
    }
}
