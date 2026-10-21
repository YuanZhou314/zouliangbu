package com.zouliangbu.server.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 管理员鉴权：固定密码换 token，token 存内存。
 *
 * 使用方式：前端登录 POST 密码 → 校验后签发 token（内存 Map，2h 过期），
 * 后续请求在 Header 携带 token，此处校验。
 * 这是开源小项目够用的方案，不引入 Spring Security。
 *
 * 校验通过时把操作者写入 {@link com.zouliangbu.server.admin.AdminContext}，
 * 供审计服务取"这次是谁改的"。当前只支持单账号，所以 operator 恒为 {@code admin}；
 * 将来加多账号只改这里，审计表不用动。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    /** 单账号场景下的操作者标识。多账号时改成从 token 反查用户名。 */
    private static final String OPERATOR = "admin";

    @Value("${admin.password:admin}")
    private String adminPassword;

    @Value("${admin.token-ttl-hours:2}")
    private long tokenTtlHours;

    // token → 过期时间（ms）
    private final java.util.concurrent.ConcurrentHashMap<String, Long> tokens = new java.util.concurrent.ConcurrentHashMap<>();

    /** 生成 token（登录成功后调用）。 */
    public String issueToken() {
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        long expireAt = System.currentTimeMillis() + tokenTtlHours * 3600_000L;
        tokens.put(token, expireAt);
        return token;
    }

    /** 校验密码，成功则发 token。 */
    public String login(String password) {
        return adminPassword.equals(password) ? issueToken() : null;
    }

    /** 登出（使 token 失效）。 */
    public void logout(String token) {
        tokens.remove(token);
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        String token = req.getHeader("X-Admin-Token");
        if (token == null) {
            token = req.getParameter("token");
        }
        Long expireAt = token != null ? tokens.get(token) : null;
        if (expireAt == null || expireAt < System.currentTimeMillis()) {
            if (token != null && expireAt != null && expireAt < System.currentTimeMillis()) {
                tokens.remove(token);
            }
            resp.setStatus(401);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":401,\"msg\":\"未授权或 token 已过期\"}");
            return false;
        }
        com.zouliangbu.server.admin.AdminContext.set(OPERATOR);
        return true;
    }

    /**
     * 请求结束后必须清掉 ThreadLocal。
     * Tomcat 线程是复用的，不清理会让下一个请求（哪怕是未登录的）读到上一个管理员身份，
     * 审计就会记错人 —— 这是 ThreadLocal 用于请求上下文的经典泄漏点。
     */
    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        com.zouliangbu.server.admin.AdminContext.clear();
    }
}
