package com.zouliangbu.server.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 管理员鉴权：固定 token，存 application.yml。
 *
 * 使用方式：前端登录时 POST 密码 → 校验后签发 token（内存 Map，2h 过期），
 * 后续请求在 Header 携带 token，此处校验。
 * 这是开源小项目够用的方案，不引入 Spring Security。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Value("${admin.password:zouliangbu-admin}")
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
        return true;
    }
}
