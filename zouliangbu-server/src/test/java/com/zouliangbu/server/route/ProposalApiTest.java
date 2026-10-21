package com.zouliangbu.server.route;

import com.zouliangbu.server.route.mapper.RouteDetailMapper;
import com.zouliangbu.server.route.mapper.RouteDetailRevisionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 社区共维护提案机制回归测试。
 *
 * 覆盖：客观勘误自动合并（认票达阈值）→ 版本 +1 → 快照 → 冲突检测 → 回滚，
 * 以及规则层（自有轨迹上传坐标越界直接拒）和导入链接识别。
 *
 * 注意：JSON 请求体一律用字符串拼接，不用 Map.of。
 * Map.of 在这里会踩两个坑：一是 12 个参数时泛型推断把 Integer 和 String 混成怪类型，
 * 二是嵌套的转义字符串肉眼不可读，出错时 javac 报的位置和真实原因差很远。
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProposalApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RouteDetailMapper detailMapper;

    @Autowired
    private RouteDetailRevisionMapper revisionMapper;

    @Autowired
    private com.zouliangbu.server.route.mapper.ContentProposalMapper proposalMapper;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void seed() {
        if (detailMapper.selectById(1L) == null) {
            RouteDetailEntity d = new RouteDetailEntity();
            d.setRouteId(1L);
            d.setIntroHtml("<p>intro</p>");
            d.setContentHtml("<p>content</p>");
            d.setSourceUrl("https://www.2bulu.com/mc/route/detail?id=test-1");
            d.setSourceName("two-bulu");
            detailMapper.insert(d);
        }
        // 每条用例都从"v1 + 入门"开始，断言才独立
        RouteDetailEntity d = detailMapper.selectById(1L);
        d.setVersion(1);
        d.setDifficulty("easy");
        detailMapper.updateById(d);

        // 每个用例都重建快照表：uk_revision(route_id, version) 是唯一索引，
        // 上一个用例合并出的 v2 会跟这个用例的 v2 撞车（表现为 500，不是断言失败）。
        revisionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RouteDetailRevisionEntity>()
                .eq("route_id", 1L));
        RouteDetailRevisionEntity rev = new RouteDetailRevisionEntity();
        rev.setRouteId(1L);
        rev.setVersion(1);
        // 快照里的线路名必须写真实种子值：rollback 用例会把它写回 route 表，
        // 写成 "test-route" 会让 ContentApiTest 搜不到"武功"（跨测试类污染，很难定位）。
        rev.setSnapshotJson("{\"route\":{\"name\":\"武功山反穿线\",\"region\":\"萍乡\"},"
                + "\"detail\":{\"difficulty\":\"easy\",\"intro_html\":\"<p>intro</p>\"}}");
        rev.setReason("seed");
        rev.setCreatedAt(java.time.LocalDateTime.now());
        revisionMapper.insert(rev);
    }

    /**
     * 收尾清理：测试库是跨测试类共享的 SQLite 内存库，
     * 本类往 route 1 写了 route_detail / revision / proposal，
     * 不清会让 ContentApiTest 的"最新收录"分组多出一条（表现为它的断言随机失败）。
     */
    @org.junit.jupiter.api.AfterEach
    void cleanup() {
        revisionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RouteDetailRevisionEntity>()
                .eq("route_id", 1L));
        detailMapper.deleteById(1L);
        proposalMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ContentProposalEntity>());
    }

    /** 构造 EDIT 提案请求体。 */
    private String editBody(String field, String value, String uuid, Integer baseVersion) {
        String payload = "{\"fields\":{\"" + field + "\":\"" + value + "\"}}";
        return "{"
                + "\"type\":\"EDIT\","
                + (baseVersion == null ? "" : "\"baseVersion\":" + baseVersion + ",")
                + "\"targetRouteId\":1,"
                + "\"payloadJson\":" + json.writeValueAsString(payload) + ","
                + "\"reason\":\"checked on site\","
                + "\"proposerUuid\":\"" + uuid + "\","
                + "\"proposerName\":\"tester\""
                + "}";
    }

    private String voteBody(String voterKey, int value) {
        return "{\"voterKey\":\"" + voterKey + "\",\"value\":" + value + "}";
    }

    private long submit(String body) throws Exception {
        String resp = mvc.perform(post("/api/v1/proposals")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("objective edit auto-merges after quorum and keeps a snapshot")
    void objectiveEditAutoMerges() throws Exception {
        long id = submit(editBody("difficulty", "advanced", "u-1", null));

        // 提案人自动一票，阈值 2 → 再来一个人认可就合并
        mvc.perform(post("/api/v1/proposals/" + id + "/vote")
                        .contentType(MediaType.APPLICATION_JSON).content(voteBody("u-2", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merged").value(true))
                .andExpect(jsonPath("$.data.status").value("MERGED"))
                .andExpect(jsonPath("$.data.mergedVersion").value(2));

        RouteDetailEntity d = detailMapper.selectById(1L);
        assertEquals(2, d.getVersion());
        assertEquals("advanced", d.getDifficulty());
        assertEquals("tester", d.getCuratorName());
        Long v2Snapshots = revisionMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RouteDetailRevisionEntity>()
                        .eq("route_id", 1L).eq("version", 2));
        assertEquals(1L, v2Snapshots == null ? -1L : v2Snapshots.longValue());
    }

    @Test
    @DisplayName("version conflict is detected and nothing is overwritten")
    void conflictIsDetected() throws Exception {
        long id = submit(editBody("difficulty", "advanced", "u-3", 1));

        // 先把版本推到 2，制造"提案基于旧版本"的冲突
        RouteDetailEntity d = detailMapper.selectById(1L);
        d.setVersion(2);
        detailMapper.updateById(d);

        mvc.perform(post("/api/v1/proposals/" + id + "/vote")
                        .contentType(MediaType.APPLICATION_JSON).content(voteBody("u-4", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merged").value(false))
                .andExpect(jsonPath("$.data.status").value("CONFLICT"));

        assertEquals("easy", detailMapper.selectById(1L).getDifficulty());
    }

    @Test
    @DisplayName("a down vote blocks auto-merge")
    void downVoteBlocksMerge() throws Exception {
        long id = submit(editBody("difficulty", "advanced", "u-5", null));

        mvc.perform(post("/api/v1/proposals/" + id + "/vote")
                        .contentType(MediaType.APPLICATION_JSON).content(voteBody("u-6", -1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merged").value(false))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mvc.perform(post("/api/v1/proposals/" + id + "/vote")
                        .contentType(MediaType.APPLICATION_JSON).content(voteBody("u-7", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merged").value(false));

        assertEquals("easy", detailMapper.selectById(1L).getDifficulty());
    }

    @Test
    @DisplayName("rollback restores the old snapshot and keeps version monotonic")
    void rollbackRestoresPreviousState() throws Exception {
        long id = submit(editBody("difficulty", "advanced", "u-8", null));
        mvc.perform(post("/api/v1/proposals/" + id + "/vote")
                        .contentType(MediaType.APPLICATION_JSON).content(voteBody("u-9", 1)))
                .andExpect(jsonPath("$.data.merged").value(true));

        mvc.perform(post("/api/v1/admin/proposals/routes/1/rollback")
                        .header("X-Admin-Token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));

        RouteDetailEntity d = detailMapper.selectById(1L);
        assertEquals(3, d.getVersion());
        assertEquals("easy", d.getDifficulty());
    }

    @Test
    @DisplayName("rule layer: upload with out-of-range coordinates is rejected")
    void uploadOutOfRangeRejected() throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 12; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"lat\":").append(10.0 + i * 0.001).append(",\"lng\":0.0}");
        }
        sb.append(']');

        String payload = "{\"name\":\"upload-test\",\"region\":\"test\",\"points\":" + sb + "}";
        String body = "{"
                + "\"type\":\"UPLOAD\","
                + "\"payloadJson\":" + json.writeValueAsString(payload) + ","
                + "\"proposerUuid\":\"u-10\","
                + "\"proposerName\":\"uploader\""
                + "}";

        mvc.perform(post("/api/v1/proposals")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("import proposal rejects unknown platform links")
    void importRejectsUnknownLink() throws Exception {
        String body = "{"
                + "\"type\":\"IMPORT\","
                + "\"sourceUrl\":\"https://example.com/route/1\","
                + "\"proposerUuid\":\"u-11\""
                + "}";
        mvc.perform(post("/api/v1/proposals")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("proposal list can be filtered by route")
    void listByRoute() throws Exception {
        mvc.perform(get("/api/v1/proposals").param("routeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 拿一个管理员 token（/api/v1/admin/** 被 AdminAuthInterceptor 拦）。 */
    private String adminToken() throws Exception {
        String resp = mvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").asText();
    }
}
