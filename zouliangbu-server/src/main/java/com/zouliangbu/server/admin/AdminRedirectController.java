package com.zouliangbu.server.admin;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 后台根路由：{@code GET /admin} 与 {@code GET /admin/} 302 到登录页。
 *
 * 后台页面是静态资源（static/admin/login.html），没有根路径映射时 /admin/ 会 404。
 * 这里用 Controller 显式接管（Controller 优先于静态资源映射），并指向登录页而非 index：
 * 未登录进 index 只会看到一堆 401 的空面板，直接去登录页更符合使用路径。
 */
@RestController
public class AdminRedirectController {

    @GetMapping({"/admin", "/admin/"})
    public ResponseEntity<Void> redirect() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/admin/login.html")
                .location(URI.create("/admin/login.html"))
                .build();
    }
}
