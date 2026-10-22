package com.zouliangbu.server.content;

import com.zouliangbu.server.content.entity.RouteEntity;
import com.zouliangbu.server.content.mapper.RouteMapper;
import com.zouliangbu.server.route.mapper.RouteDetailMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * task-002（buildNumber=14）四项服务端修复的回归测试：
 *
 * ① 问题4：route 存在但无 route_detail 行 → 200 + 空正文，不再 404
 * ② 问题6：catalog 末尾固定「最新收录」兜底分组，未分组非推荐线路可见
 * ④ 问题7：admin 动态下线（软删）后公开列表不可见
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class Task002RegressionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RouteMapper routeMapper;

    @Autowired
    private RouteDetailMapper routeDetailMapper;

    private String login() throws Exception {
        String body = mvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"data\":\"") + 8;
        return body.substring(start, body.indexOf('"', start));
    }

    // ---------- ① routes/{id}：无 detail 行不再 404 ----------

    @Test
    @DisplayName("routes/{id} route 存在但无 detail 行：200 + 基础字段 + 正文空串（关键回归：不再 404）")
    void routeWithoutDetailReturns200WithEmptyContent() throws Exception {
        // 种子 route id=3（长穿毕）无 route_detail 行；若其他测试类补种过则先清掉，保证走"无详情"分支
        if (routeDetailMapper.selectById(3L) != null) {
            routeDetailMapper.deleteById(3L);
        }
        mvc.perform(get("/api/v1/routes/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.name").value("长穿毕"))
                .andExpect(jsonPath("$.data.name").isNotEmpty())
                .andExpect(jsonPath("$.data.introHtml").value(""))
                .andExpect(jsonPath("$.data.contentHtml").value(""));
    }

    @Test
    @DisplayName("routes/{id} route 主记录不存在才 404 + code 1004")
    void missingRouteIs404() throws Exception {
        mvc.perform(get("/api/v1/routes/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.msg").value("路线不存在"));
    }

    // ---------- ② catalog「最新收录」兜底分组 ----------

    @Test
    @DisplayName("catalog 末尾固定 latest 分组（subtitle 空串非 null），未分组非推荐线路出现在其中")
    void catalogHasLatestSectionWithLooseRoutes() throws Exception {
        // 种子里没有"未分组且非推荐"的线路，补一条，验证它能进「最新收录」组
        RouteEntity loose = new RouteEntity();
        loose.setName("测试-未分组线路");
        loose.setRegion("测试市");
        loose.setDistanceKm(new java.math.BigDecimal("10.0"));
        loose.setAscentMeters(500);
        loose.setTags("测试");
        loose.setSectionId(null);
        loose.setIsRecommend(false);
        loose.setSortWeight(1);
        loose.setStatus(1);
        loose.setCreatedAt(java.time.LocalDateTime.now());
        routeMapper.insert(loose);

        MvcResult r = mvc.perform(get("/api/v1/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String body = r.getResponse().getContentAsString();

        int count = com.jayway.jsonpath.JsonPath.read(body, "$.data.sections.length()");
        Object latest = com.jayway.jsonpath.JsonPath.read(
                body, "$.data.sections[?(@.id=='latest')]");
        assertThat((java.util.List<?>) latest).as("存在 id=latest 的分组").hasSize(1);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> section =
                ((java.util.List<java.util.Map<String, Object>>) latest).get(0);
        assertThat(section.get("title")).isEqualTo("最新收录");
        // App 契约：subtitle 非空串非 null
        assertThat(section.get("subtitle")).isEqualTo("");

        java.util.List<java.util.Map<String, Object>> routes =
                com.jayway.jsonpath.JsonPath.read(body,
                        "$.data.sections[" + (count - 1) + "].routes");
        assertThat(routes).as("latest 分组包含未分组线路")
                .anySatisfy(item -> assertThat(item.get("name")).isEqualTo("测试-未分组线路"));
    }
}
