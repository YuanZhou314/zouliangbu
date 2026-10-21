package com.zouliangbu.server.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.zouliangbu.server.auth.entity.UserEntity;
import com.zouliangbu.server.auth.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

/**
 * 注册 / 登录 + JWT 签发与校验。
 *
 * 设计取舍：
 *  - BCrypt 哈希密码（自带盐，不怕彩虹表）；只引 spring-security-crypto，
 *    不启用 Spring Security 过滤链——鉴权目前只有"拿到 token"，受保护接口后续再加拦截器。
 *  - JWT 无状态（HS256，30 天），服务端不存 session；改密/踢人需求出现时再上 token 黑名单表。
 *  - secret 从配置读（ZLB_JWT_SECRET 环境变量覆盖），dev 默认值只用于本地。
 */
@Service
public class AuthService {

    /** 业务错误码（/v1 只增不改，新增不复用）。 */
    public static final int USERNAME_TAKEN = 1101;
    public static final int BAD_CREDENTIALS = 1102;

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${zlb.auth.jwt-secret}")
    private String secret;

    @Value("${zlb.auth.token-ttl-days:30}")
    private long tokenTtlDays;

    private Algorithm algorithm;

    @PostConstruct
    void init() {
        algorithm = Algorithm.HMAC256(secret);
    }

    public AuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 注册：用户名唯一，邮箱可空。返回带 token 的用户信息。 */
    public record AuthResult(long userId, String username, String token) {}

    public AuthResult register(String username, String password, String email) {
        Long exists = userMapper.selectCount(new QueryWrapper<UserEntity>().eq("username", username));
        if (exists != null && exists > 0) {
            throw new ApiException(USERNAME_TAKEN, "用户名已被占用");
        }
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        userMapper.insert(u);
        return new AuthResult(u.getId(), u.getUsername(), issueToken(u.getId(), u.getUsername()));
    }

    /** 登录：用户名（暂不支持邮箱登录名，登录名=注册名）。 */
    public AuthResult login(String username, String password) {
        UserEntity u = userMapper.selectOne(new QueryWrapper<UserEntity>().eq("username", username));
        if (u == null || !encoder.matches(password, u.getPasswordHash())) {
            throw new ApiException(BAD_CREDENTIALS, "用户名或密码错误");
        }
        return new AuthResult(u.getId(), u.getUsername(), issueToken(u.getId(), u.getUsername()));
    }

    /** 校验请求携带的 token，非法/过期返回 null（受保护接口自行决定怎么响应）。 */
    public Long verifyToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            DecodedJWT jwt = JWT.require(algorithm).build()
                    .verify(token.replaceFirst("(?i)^Bearer\\s+", ""));
            return jwt.getClaim("uid").asLong();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 包私有：单元测试直接验证 token 往返。 */
    String issueToken(long userId, String username) {
        Instant now = Instant.now();
        return JWT.create()
                .withClaim("uid", userId)
                .withClaim("name", username)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(tokenTtlDays * 86400)))
                .sign(algorithm);
    }
}
