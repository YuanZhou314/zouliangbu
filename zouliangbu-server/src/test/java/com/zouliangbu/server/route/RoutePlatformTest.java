package com.zouliangbu.server.route;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RoutePlatformTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("登录：密码正确返回 token")
    void loginSuccess() throws Exception {
        mvc.perform(post("/api/v1/admin/login")
                        .contentType("application/json")
                        .content("{\"password\":\"zouliangbu-admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    @DisplayName("登录：密码错误返回 1001")
    void loginWrongPassword() throws Exception {
        mvc.perform(post("/api/v1/admin/login")
                        .contentType("application/json")
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("无 token 访问管理接口返回 401")
    void adminWithoutToken() throws Exception {
        mvc.perform(get("/api/v1/admin/submissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正确 token 可访问管理接口")
    void adminWithToken() throws Exception {
        // 先登录拿 token
        String token = mvc.perform(post("/api/v1/admin/login")
                        .contentType("application/json")
                        .content("{\"password\":\"zouliangbu-admin\"}"))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"data\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mvc.perform(get("/api/v1/admin/submissions")
                        .header("X-Admin-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("提交路线链接返回 submission id")
    void submitRoute() throws Exception {
        mvc.perform(post("/api/v1/routes/submit")
                        .contentType("application/json")
                        .content("{\"sourceUrl\":\"https://www.2bulu.com/mc/route/detail?id=244439\","
                                + "\"submitterUuid\":\"test-uuid-001\",\"submitterNote\":\"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    @DisplayName("提交缺少 URL 返回 1001")
    void submitMissingUrl() throws Exception {
        mvc.perform(post("/api/v1/routes/submit")
                        .contentType("application/json")
                        .content("{\"sourceUrl\":\"\",\"submitterUuid\":\"u1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("提交无效 URL 返回 1001")
    void submitInvalidUrl() throws Exception {
        mvc.perform(post("/api/v1/routes/submit")
                        .contentType("application/json")
                        .content("{\"sourceUrl\":\"https://example.com/\",\"submitterUuid\":\"u1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("查询不存在的提交返回 1004")
    void getMissingSubmission() throws Exception {
        mvc.perform(get("/api/v1/routes/submissions/99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));
    }
}
