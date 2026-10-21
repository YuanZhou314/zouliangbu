package com.zouliangbu.server.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
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
 * test profile → SQLite 共享内存库，每次启动重建，测试之间无脏数据。
 */
@SpringBootTest
@ActiveProfiles("test")
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
                .andExpect(jsonPath("$.data.items[0].id").value("14"))
                .andExpect(jsonPath("$.data.items[1].id").value("13"))
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
        // 种子数据 10 条 + V5 新增 4 条 = 14 条，按 size=4 翻页应为 4+4+4+2 共 4 页，不重复不漏
        assertThat(pages).isEqualTo(4);
        assertThat(total).isEqualTo(14);
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
                .andExpect(jsonPath("$.data.sections.length()").value(3))
                // 末尾固定追加「最新收录」兜底分组（可为空组）
                .andExpect(jsonPath("$.data.sections[2].id").value("latest"))
                .andExpect(jsonPath("$.data.sections[2].title").value("最新收录"))
                .andExpect(jsonPath("$.data.sections[2].subtitle").isString());
    }

    @Test
    @DisplayName("无关键词时返回推荐位 + 全部分组")
    void routeCatalogWithoutKeyword() throws Exception {
        mvc.perform(get("/api/v1/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations.length()").value(3))
                // 3 个种子分组 + 末尾「最新收录」兜底分组
                .andExpect(jsonPath("$.data.sections.length()").value(4))
                .andExpect(jsonPath("$.data.sections[3].id").value("latest"));
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

    // ---------- 契约完整性：字段增删必须让测试失败 ----------
    //
    // 这组测试是补漏来的：FeedItem 曾经漏返回 coverStyle，
    // 而 App 侧 `FeedItem.coverStyle: Int` **没有默认值**，
    // kotlinx.serialization 遇到缺字段会直接抛 MissingFieldException（不会降级成 0）。
    // 只看 HTTP 200 / 只看 jsonPath 里几个字段是查不出来的，
    // 必须"数清字段个数 + 逐个点名"才能拦住。

    @Test
    @DisplayName("契约：feed 返回 App 需要的全部 8 个字段")
    void feedItemCarriesAllAppRequiredFields() throws Exception {
        mvc.perform(get("/api/v1/feed").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].length()").value(8))
                .andExpect(jsonPath("$.data.items[0].id").exists())
                .andExpect(jsonPath("$.data.items[0].title").exists())
                .andExpect(jsonPath("$.data.items[0].author").exists())
                .andExpect(jsonPath("$.data.items[0].likeCount").exists())
                .andExpect(jsonPath("$.data.items[0].city").exists())
                .andExpect(jsonPath("$.data.items[0].coverStyle").exists())
                .andExpect(jsonPath("$.data.items[0].coverAspectRatio").exists())
                // coverUrl 本期恒为 null，用 hasJsonPath 断言"字段在"而不是"值非空"
                .andExpect(jsonPath("$.data.items[0].coverUrl").hasJsonPath());
    }

    @Test
    @DisplayName("契约：route 返回 App 需要的全部 8 个字段")
    void routeItemCarriesAllAppRequiredFields() throws Exception {
        mvc.perform(get("/api/v1/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations[0].length()").value(8))
                .andExpect(jsonPath("$.data.recommendations[0].id").exists())
                .andExpect(jsonPath("$.data.recommendations[0].name").exists())
                .andExpect(jsonPath("$.data.recommendations[0].region").exists())
                .andExpect(jsonPath("$.data.recommendations[0].distanceKm").exists())
                .andExpect(jsonPath("$.data.recommendations[0].ascentMeters").exists())
                .andExpect(jsonPath("$.data.recommendations[0].coverStyle").exists())
                .andExpect(jsonPath("$.data.recommendations[0].tags").isArray())
                .andExpect(jsonPath("$.data.recommendations[0].coverUrl").hasJsonPath());
    }

    @Test
    @DisplayName("契约：section 与 gear 字段齐全，subtitle 不能为 null")
    void sectionAndGearCarryAllAppRequiredFields() throws Exception {
        mvc.perform(get("/api/v1/routes"))
                .andExpect(jsonPath("$.data.sections[0].length()").value(4))
                .andExpect(jsonPath("$.data.sections[0].id").exists())
                .andExpect(jsonPath("$.data.sections[0].title").exists())
                // App 侧 RouteSection.subtitle 是非空 String，返回 null 会让解析抛异常
                .andExpect(jsonPath("$.data.sections[0].subtitle").isString())
                .andExpect(jsonPath("$.data.sections[0].routes").isArray());

        mvc.perform(get("/api/v1/gear/categories"))
                .andExpect(jsonPath("$.data[0].length()").value(3))
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].name").exists())
                .andExpect(jsonPath("$.data[0].note").exists());
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

    // ---------- feed/{id} 详情 ----------

    @Test
    @DisplayName("feed/{id} 详情返回全部契约字段，content 是字符串（可空串不可 null）")
    void feedDetailReturnsAllFields() throws Exception {
        mvc.perform(get("/api/v1/feed/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.title").exists())
                .andExpect(jsonPath("$.data.author").exists())
                .andExpect(jsonPath("$.data.likeCount").exists())
                .andExpect(jsonPath("$.data.city").exists())
                .andExpect(jsonPath("$.data.coverStyle").exists())
                .andExpect(jsonPath("$.data.coverUrl").hasJsonPath())
                .andExpect(jsonPath("$.data.coverAspectRatio").exists())
                .andExpect(jsonPath("$.data.coverAuthor").hasJsonPath())
                .andExpect(jsonPath("$.data.content",
                        org.hamcrest.Matchers.isA(String.class)));
    }

    @Test
    @DisplayName("feed/{id} 不存在返回 404 + code 1004 内容不存在")
    void feedDetailNotFound() throws Exception {
        mvc.perform(get("/api/v1/feed/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.msg").value("内容不存在"));
    }

    @Test
    @DisplayName("feed/{id} coverStyle = id % 5")
    void feedDetailCoverStyleIsIdModuloFive() throws Exception {
        mvc.perform(get("/api/v1/feed/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverStyle").value(2));
    }

    @Test
    @DisplayName("feed/{id} 传入非数字 id 返回 4xx 而非 500")
    void feedDetailNonNumericId() throws Exception {
        mvc.perform(get("/api/v1/feed/abc"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("只读接口收到 POST 返回 405")
    void postToReadOnlyEndpoint() throws Exception {
        mvc.perform(post("/api/v1/feed"))
                .andExpect(status().isMethodNotAllowed());
    }
}
