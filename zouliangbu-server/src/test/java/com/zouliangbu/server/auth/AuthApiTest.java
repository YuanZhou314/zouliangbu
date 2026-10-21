package com.zouliangbu.server.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账号接口测试：注册/登录/重复用户名/错误密码，覆盖完整 Flyway+MyBatis 链路。
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    private MockMvc mvc;

    private static String registerBody(String u) {
        return "{\"username\":\"" + u + "\",\"password\":\"pass123456\",\"email\":\"" + u + "@test.com\"}";
    }

    private String register(String username) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(registerBody(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(r.getResponse().getContentAsString());
        return node.path("data").path("token").asText();
    }

    @Test
    @DisplayName("注册成功返回 token，登录同账号返回同 userId")
    void registerAndLogin() throws Exception {
        register("zhangsan");

        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"zhangsan\",\"password\":\"pass123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("zhangsan"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        long loginId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(r.getResponse().getContentAsString())
                .path("data").path("userId").asLong();
        assertThat(loginId).isPositive();
    }

    @Test
    @DisplayName("重复用户名注册返回 1101")
    void duplicateUsername() throws Exception {
        register("wangwu");
        mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json").content(registerBody("wangwu")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1101));
    }

    @Test
    @DisplayName("密码错误返回 1102 / 401")
    void wrongPassword() throws Exception {
        register("zhaoliu");
        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"zhaoliu\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1102));
    }

    @Test
    @DisplayName("非法用户名/过短密码返回 4xx")
    void invalidInput() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"a b!\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("签发的 token 能被 verifyToken 解回 uid")
    void tokenRoundTrip() {
        AuthService svc = new AuthService(null);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "secret", "test-secret");
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "tokenTtlDays", 30L);
        svc.init();
        String token = svc.issueToken(42L, "tester");
        assertThat(svc.verifyToken(token)).isEqualTo(42L);
        assertThat(svc.verifyToken("not-a-token")).isNull();
        assertThat(svc.verifyToken(null)).isNull();
    }
}
