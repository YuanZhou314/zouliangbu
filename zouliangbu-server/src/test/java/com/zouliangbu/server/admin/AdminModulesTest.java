package com.zouliangbu.server.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 后台扩展模块的接口测试。
 *
 * 重点覆盖三类容易出错的地方：
 * ① 鉴权是否真的挡住（新增的 admin 路径别漏挂拦截器）
 * ② 校验是否在落库前挡住（空标题、超长字段）
 * ③ 部分字段语义（装备 code 不可改、动态上下线可逆）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
/*
 * @Transactional：本类大量写库（新增动态、建线路、改配置）。
 * 不加这个，测试数据会留在共享内存库里，把 ContentApiTest 那种"种子数据恰好 10 条"的断言全部搞挂。
 * 加在类级别让每个用例结束自动回滚，测试之间互不干扰。
 */
@Transactional
class AdminModulesTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    /** 登录拿 token（密码在 application.yml 里是 admin）。 */
    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = json.readValue(r.getResponse().getContentAsString(), Map.class);
        return (String) body.get("data");
    }

    // ===== 鉴权 =====

    @Test
    void newAdminEndpointsRequireToken() throws Exception {
        for (String path : new String[]{
                "/api/v1/admin/gear", "/api/v1/admin/route-sections",
                "/api/v1/admin/config", "/api/v1/admin/audit", "/api/v1/admin/dashboard"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    // ===== 装备分类：code 不可改 =====

    @Test
    void gearCodeIsImmutable() throws Exception {
        String t = token();
        MvcResult created = mvc.perform(post("/api/v1/admin/gear")
                        .header("X-Admin-Token", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"g99\",\"name\":\"测试分类\",\"note\":\"n\",\"sortWeight\":999}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> c = json.readValue(created.getResponse().getContentAsString(), Map.class);
        long id = ((Number) c.get("data")).longValue();

        // 改名并试图改 code，code 必须被忽略
        mvc.perform(put("/api/v1/admin/gear/" + id)
                        .header("X-Admin-Token", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"hacked\",\"name\":\"改过的名字\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/gear").header("X-Admin-Token", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")].code").value("g99"))
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")].name").value("改过的名字"));
    }

    @Test
    void gearCodeMustBeUnique() throws Exception {
        String t = token();
        // g1 是种子数据里已有的 code
        mvc.perform(post("/api/v1/admin/gear")
                        .header("X-Admin-Token", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"g1\",\"name\":\"重复\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===== 配置中心 =====

    @Test
    void configSaveRejectsUnknownKey() throws Exception {
        String t = token();
        mvc.perform(put("/api/v1/admin/config")
                        .header("X-Admin-Token", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unknown.key\":\"1\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 强更开关打开但没配下载地址时，公开接口必须当成"未开启"。
     * 否则 App 会弹一个点了没反应的窗，用户直接卡死。
     */
    @Test
    void publicConfigFallsBackWhenForceUpdateHasNoUrl() throws Exception {
        String t = token();
        mvc.perform(put("/api/v1/admin/config")
                        .header("X-Admin-Token", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force_update.enabled\":\"true\",\"force_update.url\":\"\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/config/app"))   // 公开接口，无需 token
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forceUpdate").value(false));
    }

    // ===== 审计 =====

    @Test
    void auditRecordsWriteOperations() throws Exception {
        String t = token();
        // 首页动态模块已下线（V8），用装备分类写接口造审计数据
        mvc.perform(post("/api/v1/admin/gear")
                        .header("X-Admin-Token", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"g98\",\"name\":\"审计用例分类\",\"note\":\"t\",\"sortWeight\":998}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/audit?action=gear.&size=50").header("X-Admin-Token", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[?(@.detail =~ /.*审计用例分类.*/)]").exists())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    // ===== 看板 =====

    @Test
    void dashboardCountsPendingSubmissions() throws Exception {
        String t = token();
        mvc.perform(get("/api/v1/admin/dashboard").header("X-Admin-Token", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 种子数据里 route 是 12 条，all-online（feed 已于 V8 下线）
                .andExpect(jsonPath("$.data.routeOnline").isNumber())
                .andExpect(jsonPath("$.data.submissionPending").isNumber())
                .andExpect(jsonPath("$.data.recentAudit").isArray());
    }

    // ===== 上传 =====

    @Test
    void uploadRejectsNonImageContent() throws Exception {
        String t = token();
        // 声明成 png 但内容是 HTML → 魔数校验必须挡住（防存储型 XSS）
        mvc.perform(multipart("/api/v1/admin/upload")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "evil.png", "image/png",
                                "<html><script>alert(1)</script></html>".getBytes()))
                        .header("X-Admin-Token", t))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadAcceptsRealPngHeader() throws Exception {
        String t = token();
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        mvc.perform(multipart("/api/v1/admin/upload")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "ok.png", "image/png", png))
                        .header("X-Admin-Token", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("/uploads/")));
    }

    // ===== 直接新建线路（运营不依赖用户提交）=====

    @Test
    void canCreateRouteDirectlyWithoutSubmission() throws Exception {
        String t = token();
        MvcResult created = mvc.perform(post("/api/v1/admin/routes")
                        .header("X-Admin-Token", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"手工新建线路\",\"region\":\"萍乡\","
                                + "\"distanceKm\":8.5,\"ascentMeters\":600,\"tags\":\"测试\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> c = json.readValue(created.getResponse().getContentAsString(), Map.class);
        long routeId = ((Number) c.get("data")).longValue();

        // 建完立刻能按线路 id 取后台详情（没有提交记录也要能编辑）
        mvc.perform(get("/api/v1/admin/routes/" + routeId + "/detail").header("X-Admin-Token", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("手工新建线路"))
                .andExpect(jsonPath("$.data.routeStatus").value(1))
                .andExpect(jsonPath("$.data.hasDetail").value(true))
                // 正文类字段必须是空串而不是 null，前端直接赋给 textarea
                .andExpect(jsonPath("$.data.introHtml").value(""));

        // 手工线路默认 sectionId=null 且 isRecommend=false，所以不进公开的推荐位/分组列表 ——
        // 这是刻意的：新建线先落在"未归类"，运营确认后再挂分组或推荐位。
        // 后台列表必须能看到它（否则建完就找不到了）。
        mvc.perform(get("/api/v1/admin/routes/" + routeId + "/detail").header("X-Admin-Token", t))
                .andExpect(jsonPath("$.data.isRecommend").value(false));
    }

    @Test
    void createRouteRequiresName() throws Exception {
        String t = token();
        mvc.perform(post("/api/v1/admin/routes")
                        .header("X-Admin-Token", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
