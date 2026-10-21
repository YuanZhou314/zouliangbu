package com.zouliangbu.server.route;

import com.zouliangbu.server.route.mapper.RouteSubmissionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理后台接口回归测试。
 *
 * 覆盖两件容易回归的事：
 * ① 固定密码登录 → 拿 token → 才能访问 /admin/**（未带 token 必须 401）
 * ② **任意状态**的提交都能打开详情（PENDING / REJECTED / APPROVED），
 *    这是修复"待审批记录点进去报路线不存在"的守卫。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiTest {

    private static final String PASSWORD = "admin";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RouteSubmissionMapper submissionMapper;

    private String login() throws Exception {
        String body = mvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"data\":\"") + 8;
        return body.substring(start, body.indexOf('"', start));
    }

    @Test
    @DisplayName("密码正确返回 token，密码错误返回业务错误码")
    void loginIssuesToken() throws Exception {
        String token = login();
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();

        mvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.msg").value("密码错误"));
    }

    @Test
    @DisplayName("未带 token 访问后台接口返回 401")
    void adminApiRequiresToken() throws Exception {
        mvc.perform(get("/api/v1/admin/submissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("任意状态的提交都能打开详情（PENDING / APPROVED / REJECTED）")
    void detailOpensForEveryStatus() throws Exception {
        String token = login();

        for (String st : new String[]{"PENDING", "APPROVED", "REJECTED"}) {
            RouteSubmissionEntity e = new RouteSubmissionEntity();
            e.setSourceUrl("https://www.2bulu.com/mc/route/detail?id=999" + st);
            e.setSourceRouteId("999" + st);
            e.setSubmitterUuid("test-uuid");
            e.setSubmitterNote("note-" + st);
            e.setStatus(st);
            e.setCreatedAt(java.time.LocalDateTime.now());
            submissionMapper.insert(e);

            mvc.perform(get("/api/v1/admin/submissions/" + e.getId() + "/detail")
                            .header("X-Admin-Token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.submissionStatus").value(st))
                    .andExpect(jsonPath("$.data.submissionId").value(e.getId()))
                    // 未审批时没有 routeId / route_detail，字段也必须存在且是空串而非 null：
                    // 前端直接塞进 textarea，null 会显示成字符串 "null"
                    .andExpect(jsonPath("$.data.name").isString())
                    .andExpect(jsonPath("$.data.introHtml").isString())
                    .andExpect(jsonPath("$.data.contentHtml").isString())
                    .andExpect(jsonPath("$.data.hasDetail").exists());
        }
    }

    @Test
    @DisplayName("GET /admin 302 到登录页")
    void adminRootRedirectsToLogin() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Location")).contains("login.html"));
    }

    @Test
    @DisplayName("GET /admin/ 302 到登录页")
    void adminTrailingSlashRedirectsToLogin() throws Exception {
        mvc.perform(get("/admin/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Location")).contains("login.html"));
    }

    @Test
    @DisplayName("不存在的提交详情返回 404 + code 1004")
    void detailOfMissingSubmissionIs404() throws Exception {
        String token = login();
        mvc.perform(get("/api/v1/admin/submissions/999999/detail")
                        .header("X-Admin-Token", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }
}
