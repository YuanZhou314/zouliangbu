package com.zouliangbu.server.route;

import com.zouliangbu.server.route.mapper.RouteDetailMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开路线详情 GET /api/v1/routes/{id} 回归测试。
 *
 * 重点是本次新增的三个末尾字段：coverUrl / coverStyle(id%5) / tags(逗号拆数组)，
 * 以及确认"无详情行 → 404"的原行为没有被字段扩展破坏。
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RouteContentDetailTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RouteDetailMapper routeDetailMapper;

    @BeforeEach
    void seedDetail() {
        // 种子库 route 表有 id=1（武功山反穿线，tags=户外毕业线,露营），但 route_detail 无种子行。
        // 补一行详情，让公开详情接口走 200 分支。
        if (routeDetailMapper.selectById(1L) == null) {
            RouteDetailEntity d = new RouteDetailEntity();
            d.setRouteId(1L);
            d.setIntroHtml("<p>intro</p>");
            d.setContentHtml("<p>content</p>");
            d.setSourceUrl("https://www.2bulu.com/mc/route/detail?id=test-1");
            d.setSourceName("两步路");
            routeDetailMapper.insert(d);
        }
    }

    @Test
    @DisplayName("路线详情返回 coverStyle=id%5、tags 拆数组，原有字段不受影响")
    void routeDetailCarriesNewTrailingFields() throws Exception {
        mvc.perform(get("/api/v1/routes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 原有字段不受影响
                .andExpect(jsonPath("$.data.name").value("武功山反穿线"))
                .andExpect(jsonPath("$.data.region").value("萍乡"))
                .andExpect(jsonPath("$.data.distanceKm").value(13.5))
                // 新增字段
                .andExpect(jsonPath("$.data.coverStyle").value(1))
                .andExpect(jsonPath("$.data.tags").isArray())
                .andExpect(jsonPath("$.data.tags.length()").value(2))
                .andExpect(jsonPath("$.data.tags[0]").value("户外毕业线"))
                .andExpect(jsonPath("$.data.tags[1]").value("露营"))
                .andExpect(jsonPath("$.data.coverUrl").hasJsonPath());
    }

    @Test
    @DisplayName("路线无详情行返回 404 + code 1004（route 主记录不存在时）")
    void routeDetailWithoutDetailRowIs404() throws Exception {
        mvc.perform(get("/api/v1/routes/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.msg").value("路线不存在"));
    }

    @Test
    @DisplayName("route 存在但无 detail 行：返回空详情版（基础字段照常，正文空串，不再 404）")
    void routeWithoutDetailRowReturnsEmptyDetail() throws Exception {
        // 种子 route id=2（雨崩 inner 环线）没有 detail 行，也未在上面补种
        if (routeDetailMapper.selectById(2L) != null) {
            return;
        }
        mvc.perform(get("/api/v1/routes/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.name").value("雨崩 inner 环线"))
                .andExpect(jsonPath("$.data.region").value("迪庆"))
                .andExpect(jsonPath("$.data.distanceKm").value(26.0))
                .andExpect(jsonPath("$.data.introHtml").value(""))
                .andExpect(jsonPath("$.data.contentHtml").value(""))
                .andExpect(jsonPath("$.data.trafficHtml").value(""))
                .andExpect(jsonPath("$.data.difficulty").value(""))
                .andExpect(jsonPath("$.data.trackPointCount").value(0))
                .andExpect(jsonPath("$.data.markerCount").value(0));
    }
}
