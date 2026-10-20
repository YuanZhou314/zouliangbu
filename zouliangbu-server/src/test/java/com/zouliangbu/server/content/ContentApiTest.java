package com.zouliangbu.server.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内容接口回归测试。
 *
 * 用 @SpringBootTest 而不是 @WebMvcTest：要覆盖 Flyway 建表+种子数据+MyBatis-Plus 查询的完整链路，
 * "搜武功搜不到"这类问题恰好出在查询/过滤层，切掉 DB 就测不出来。
 * 默认 dev profile → H2 内存库，每次启动重建，测试之间无脏数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContentApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("健康检查返回 ok")
    void healthz() throws Exception {
        mvc.perform(get("/api/v1/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("动态列表按 id 倒序，size 生效")
    void feedRespectsSize() throws Exception {
        mvc.perform(get("/api/v1/feed").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value("10"))
                .andExpect(jsonPath("$.data.items[1].id").value("9"))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty());
    }

    @Test
    @DisplayName("游标翻页不重不漏，最后一页 nextCursor 为 null")
    void feedCursorPaging() throws Exception {
        String cursor = null;
        int total = 0;
        int pages = 0;
        for (int i = 0; i < 10; i++) {
            var req = get("/api/v1/feed").param("size", "4");
            if (cursor != null) {
                req.param("cursor", cursor);
            }
            MvcResult r = mvc.perform(req).andExpect(status().isOk()).andReturn();
            String body = r.getResponse().getContentAsString();
            int count = com.jayway.jsonpath.JsonPath.read(body, "$.data.items.length()");
            total += count;
            pages++;
            String next = com.jayway.jsonpath.JsonPath.read(body, "$.data.nextCursor");
            if (next == null) {
                break;
            }
            cursor = next;
        }
        // 种子数据 10 条，按 size=4 翻页应为 4+4+2 共 3 页，不重复不漏
        assertThat(pages).isEqualTo(3);
        assertThat(total).isEqualTo(10);
    }

    @Test
    @DisplayName("city 过滤只返回该城市动态")
    void feedFiltersByCity() throws Exception {
        mvc.perform(get("/api/v1/feed").param("city", "萍乡"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].city").value("萍乡"))
                .andExpect(jsonPath("$.data.items[1].city").value("萍乡"));
    }

    @Test
    @DisplayName("搜索命中推荐位线路（回归：推荐位无分组曾导致搜不到）")
    void routeSearchHitsRecommendationRoute() throws Exception {
        mvc.perform(get("/api/v1/routes").param("keyword", "武功"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 搜索态不展示推荐位，避免顶部出现无关推荐
                .andExpect(jsonPath("$.data.recommendations.length()").value(0))
                .andExpect(jsonPath("$.data.sections[0].title").value("搜索结果"))
                .andExpect(jsonPath("$.data.sections[0].routes[0].name").value("武功山反穿线"));
    }

    @Test
    @DisplayName("搜索跨分组命中时保留原分组")
    void routeSearchKeepsSectionGrouping() throws Exception {
        mvc.perform(get("/api/v1/routes").param("keyword", "莫干山"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections.length()").value(2))
                .andExpect(jsonPath("$.data.sections[*].title",
                        org.hamcrest.Matchers.hasItems("新手友好线", "口碑山野线")));
    }

    @Test
    @DisplayName("无关键词时返回推荐位 + 全部分组")
    void routeCatalogWithoutKeyword() throws Exception {
        mvc.perform(get("/api/v1/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations.length()").value(3))
                .andExpect(jsonPath("$.data.sections.length()").value(3));
    }

    @Test
    @DisplayName("装备分类按 sortWeight 排序")
    void gearCategories() throws Exception {
        mvc.perform(get("/api/v1/gear/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8))
                .andExpect(jsonPath("$.data[0].id").value("g1"))
                .andExpect(jsonPath("$.data[0].name").value("背包"))
                .andExpect(jsonPath("$.data[7].id").value("g8"));
    }

    @Test
    @DisplayName("参数越界返回 400 而非 500")
    void outOfRangeSizeIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/feed").param("size", "9999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("参数类型错误返回 400 而非 500")
    void malformedSizeIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/feed").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("未知路径返回 404 统一结构")
    void unknownPathIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @DisplayName("只读接口收到 POST 返回 405")
    void postToReadOnlyEndpoint() throws Exception {
        mvc.perform(post("/api/v1/feed"))
                .andExpect(status().isMethodNotAllowed());
    }
}
