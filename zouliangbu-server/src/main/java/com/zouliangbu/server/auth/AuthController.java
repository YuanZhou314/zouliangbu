package com.zouliangbu.server.auth;

import com.zouliangbu.server.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号接口（V6）：
 *   POST /api/v1/auth/register { username, password, email? } → { userId, username, token }
 *   POST /api/v1/auth/login    { username, password }         → { userId, username, token }
 *
 * 校验都在 DTO 上：用户名 3-30 位字母/数字/下划线；密码 6-64 位；email 可空。
 * 邮箱只存不验证（验证邮件要额外服务，暂不做）。
 */
@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterReq(
            @NotBlank @Size(min = 3, max = 30)
            @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "只能包含字母、数字、下划线")
            String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            @jakarta.validation.constraints.Email String email) {}

    public record LoginReq(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthService.AuthResult>> register(@jakarta.validation.Valid @RequestBody RegisterReq req) {
        String email = (req.email() != null && !req.email().isBlank()) ? req.email() : null;
        return ResponseEntity.ok(ApiResponse.ok(authService.register(req.username(), req.password(), email)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthService.AuthResult>> login(@jakarta.validation.Valid @RequestBody LoginReq req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(req.username(), req.password())));
    }
}
