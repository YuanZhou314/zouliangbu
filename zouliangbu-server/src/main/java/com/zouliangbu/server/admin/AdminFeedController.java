package com.zouliangbu.server.admin;

import com.zouliangbu.server.common.ApiResponse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zouliangbu.server.content.entity.FeedPost;
import com.zouliangbu.server.content.mapper.FeedPostMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static com.zouliangbu.server.admin.AdminSupport.badRequest;
import static com.zouliangbu.server.admin.AdminSupport.notFound;
import static com.zouliangbu.server.admin.AdminSupport.ok;
import static com.zouliangbu.server.admin.AdminSupport.parseId;
import static com.zouliangbu.server.admin.AdminSupport.trimToNull;

/**
 * 首页「同城动态」管理 —— 后台第一个完整模块，其余模块照这个写。
 *
 * <pre>
 *   GET  /api/v1/admin/feed?city=萍乡&amp;status=1   列表（两个参数都可空）
 *   GET  /api/v1/admin/feed/{id}                  详情
 *   POST /api/v1/admin/feed                       新增
 *   PUT  /api/v1/admin/feed/{id}                  编辑（整体覆盖）
 *   POST /api/v1/admin/feed/{id}/status           上线 / 下线
 * </pre>
 *
 * 用 PUT 整体覆盖而不是部分更新：动态是 6 个字段的短卡片，表单永远全量提交，
 * "传 null = 不改"只会让"清空字段"变别扭。长表单（线路正文）才需要部分更新。
 */
@RestController
@RequestMapping("/api/v1/admin/feed")
public class AdminFeedController {

    private final FeedPostMapper feedMapper;
    private final AuditService audit;

    public AdminFeedController(FeedPostMapper feedMapper, AuditService audit) {
        this.feedMapper = feedMapper;
        this.audit = audit;
    }

    /** 列表：按城市 / 状态筛选，不传则全部。 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FeedRow>>> list(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<FeedPost> qw = new LambdaQueryWrapper<>();
        if (city != null && !city.isBlank()) {
            qw.eq(FeedPost::getCity, city.trim());
        }
        if (status != null) {
            qw.eq(FeedPost::getStatus, status);
        }
        qw.orderByDesc(FeedPost::getId);
        return ok(feedMapper.selectList(qw).stream().map(AdminFeedController::toRow).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FeedRow>> detail(@PathVariable String id) {
        FeedPost e = load(id);
        return e == null ? notFound("动态不存在") : ok(toRow(e));
    }

    /** 新增。coverStyle 不用管：App 侧那个字段由公开接口按 id % 5 现算，不进库。 */
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@RequestBody FeedUpsert body) {
        String err = validate(body);
        if (err != null) {
            return badRequest(err);
        }
        FeedPost e = new FeedPost();
        fill(e, body);
        e.setStatus(body.status() == null ? 1 : body.status());
        if (e.getLikeCount() == null) {
            e.setLikeCount(0);
        }
        e.setCreatedAt(LocalDateTime.now());
        feedMapper.insert(e);
        audit.record("feed.create", "feed", e.getId(), "新增动态：" + e.getTitle());
        return ok(e.getId());
    }

    /** 编辑：整体覆盖，前端提交完整表单。 */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> update(@PathVariable String id, @RequestBody FeedUpsert body) {
        FeedPost e = load(id);
        if (e == null) {
            return notFound("动态不存在");
        }
        String err = validate(body);
        if (err != null) {
            return badRequest(err);
        }
        String before = e.getTitle();
        fill(e, body);
        if (body.status() != null) {
            e.setStatus(body.status());
        }
        feedMapper.updateById(e);
        audit.record("feed.update", "feed", e.getId(), "编辑动态：" + before + " → " + e.getTitle());
        return ok(null);
    }

    /**
     * 上下线：status=1 上线，0 下线。
     *
     * 一条路径同时承担"软删除"和"恢复" —— 动态下架后经常要再上架，前端只要一个 switch。
     */
    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> setStatus(@PathVariable String id,
                                                       @RequestBody StatusRequest req) {
        FeedPost e = load(id);
        if (e == null) {
            return notFound("动态不存在");
        }
        if (req == null || req.status() == null || (req.status() != 0 && req.status() != 1)) {
            return badRequest("status 只能是 0 或 1");
        }
        e.setStatus(req.status());
        feedMapper.updateById(e);
        audit.record(req.status() == 1 ? "feed.publish" : "feed.offline", "feed", e.getId(), e.getTitle());
        return ok(null);
    }

    // ===== 内部工具 =====

    private FeedPost load(String rawId) {
        Long id = parseId(rawId);
        return id == null ? null : feedMapper.selectById(id);
    }

    private static FeedRow toRow(FeedPost e) {
        return new FeedRow(
                e.getId(), e.getTitle(), e.getAuthor(), e.getCity(),
                e.getLikeCount() == null ? 0 : e.getLikeCount(),
                e.getCoverUrl(), e.getCoverAuthor(),
                e.getCoverAspectRatio() == null ? 1.0 : e.getCoverAspectRatio().doubleValue(),
                e.getContent(),
                e.getStatus() == null ? 0 : e.getStatus(),
                e.getCreatedAt() == null ? "" : e.getCreatedAt().toString(),
                // 派生字段：后台有封面 App 才画得出卡片，提前让运营看到哪条还缺图
                e.getCoverUrl() != null && !e.getCoverUrl().isBlank());
    }

    /** 必填校验。返回 null 表示通过。 */
    private static String validate(FeedUpsert b) {
        if (b == null) {
            return "请求体不能为空";
        }
        if (trimToNull(b.title()) == null) {
            return "标题不能为空";
        }
        if (b.title().trim().length() > 120) {
            return "标题最多 120 字";
        }
        if (trimToNull(b.author()) == null) {
            return "作者不能为空";
        }
        if (b.author().trim().length() > 40) {
            return "作者最多 40 字";
        }
        if (trimToNull(b.city()) == null) {
            return "城市不能为空";
        }
        return null;
    }

    /** 请求体 → 实体。空串统一存 null，避免 App 拿到空图地址去发请求。 */
    private static void fill(FeedPost e, FeedUpsert b) {
        e.setTitle(b.title().trim());
        e.setAuthor(b.author().trim());
        e.setCity(b.city().trim());
        e.setContent(trimToNull(b.content()));
        e.setCoverUrl(trimToNull(b.coverUrl()));
        e.setCoverAuthor(trimToNull(b.coverAuthor()));
        if (b.likeCount() != null) {
            e.setLikeCount(Math.max(0, b.likeCount()));
        }
        if (b.coverAspectRatio() != null) {
            // 夹在 0.5..2.0：App 卡片按这个比例留位，0 或 100 会让整排错位
            double r = Math.max(0.5, Math.min(2.0, b.coverAspectRatio()));
            e.setCoverAspectRatio(BigDecimal.valueOf(r).setScale(2, RoundingMode.HALF_UP));
        } else if (e.getCoverAspectRatio() == null) {
            e.setCoverAspectRatio(new BigDecimal("1.00"));
        }
    }

    // ===== DTO =====

    /** 行 DTO。hasCover 是派生字段（不落库），给前端提示"缺图"。 */
    public record FeedRow(Long id, String title, String author, String city,
                          int likeCount, String coverUrl, String coverAuthor,
                          double coverAspectRatio, String content, int status,
                          String createdAt, boolean hasCover) {}

    /** 新增 / 编辑请求体。 */
    public record FeedUpsert(String title, String author, String city, String content,
                             String coverUrl, String coverAuthor, Integer likeCount,
                             Double coverAspectRatio, Integer status) {}

    public record StatusRequest(Integer status) {}
}
