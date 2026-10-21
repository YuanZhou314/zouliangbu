package com.zouliangbu.server.admin;

import com.zouliangbu.server.common.ApiResponse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zouliangbu.server.admin.entity.AdminAuditEntity;
import com.zouliangbu.server.admin.mapper.AdminAuditMapper;
import com.zouliangbu.server.content.entity.FeedPost;
import com.zouliangbu.server.content.entity.RouteEntity;
import com.zouliangbu.server.content.mapper.FeedPostMapper;
import com.zouliangbu.server.content.mapper.RouteMapper;
import com.zouliangbu.server.route.RouteSubmissionEntity;
import com.zouliangbu.server.route.mapper.RouteSubmissionMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.zouliangbu.server.admin.AdminSupport.ok;

/**
 * 数据看板：一屏看清"线上有多少内容、还有多少待审"。
 *
 * 只做<b>总量统计</b>，不做趋势图 —— 趋势要按天聚合，现在的表都只有 created_at 没有统计表，
 * 每加一个图表就要扫全表一次。等量级上来再加日汇总表（ADMIN-PLAN §3 C2）。
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final FeedPostMapper feedMapper;
    private final RouteMapper routeMapper;
    private final RouteSubmissionMapper submissionMapper;
    private final AdminAuditMapper auditMapper;

    public AdminDashboardController(FeedPostMapper feedMapper,
                                    RouteMapper routeMapper,
                                    RouteSubmissionMapper submissionMapper,
                                    AdminAuditMapper auditMapper) {
        this.feedMapper = feedMapper;
        this.routeMapper = routeMapper;
        this.submissionMapper = submissionMapper;
        this.auditMapper = auditMapper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Dashboard>> overview() {
        // 动态
        long feedOnline = count(feedMapper, new LambdaQueryWrapper<FeedPost>().eq(FeedPost::getStatus, 1));
        long feedOffline = count(feedMapper, new LambdaQueryWrapper<FeedPost>().eq(FeedPost::getStatus, 0));
        // 首页第一屏有没有图，是 App 观感的关键指标，单独算
        long feedNoCover = count(feedMapper, new LambdaQueryWrapper<FeedPost>()
                .eq(FeedPost::getStatus, 1)
                .and(w -> w.isNull(FeedPost::getCoverUrl).or().eq(FeedPost::getCoverUrl, "")));

        // 线路
        long routeOnline = count(routeMapper, new LambdaQueryWrapper<RouteEntity>().eq(RouteEntity::getStatus, 1));
        long routeOffline = count(routeMapper, new LambdaQueryWrapper<RouteEntity>().eq(RouteEntity::getStatus, 0));
        long routeRecommend = count(routeMapper, new LambdaQueryWrapper<RouteEntity>()
                .eq(RouteEntity::getStatus, 1).eq(RouteEntity::getIsRecommend, true));

        // 提交 / 审核
        long pending = count(submissionMapper, new LambdaQueryWrapper<RouteSubmissionEntity>()
                .eq(RouteSubmissionEntity::getStatus, "PENDING"));
        long approved = count(submissionMapper, new LambdaQueryWrapper<RouteSubmissionEntity>()
                .eq(RouteSubmissionEntity::getStatus, "APPROVED"));
        long rejected = count(submissionMapper, new LambdaQueryWrapper<RouteSubmissionEntity>()
                .eq(RouteSubmissionEntity::getStatus, "REJECTED"));

        // 最近 10 条审计，首页直接当前情提示用
        List<AuditItem> recentItems = auditMapper.selectList(
                        new LambdaQueryWrapper<AdminAuditEntity>()
                                .orderByDesc(AdminAuditEntity::getId)
                                .last("LIMIT 10"))   // 常量，无外部输入，不构成注入面
                .stream()
                .map(e -> new AuditItem(e.getAction(), e.getDetail(),
                        e.getCreatedAt() == null ? "" : e.getCreatedAt().toString()))
                .toList();

        return ok(new Dashboard(feedOnline, feedOffline, feedNoCover,
                routeOnline, routeOffline, routeRecommend,
                pending, approved, rejected, recentItems));
    }

    /** 各表计数。写成带泛型的工具方法而不是通配符，否则 LambdaQueryWrapper 的实参类型对不上。 */
    private static <T> long count(BaseMapper<T> mapper, LambdaQueryWrapper<T> qw) {
        Long n = mapper.selectCount(qw);
        return n == null ? 0 : n;
    }

    // ===== DTO =====

    public record Dashboard(long feedOnline, long feedOffline, long feedNoCover,
                            long routeOnline, long routeOffline, long routeRecommend,
                            long submissionPending, long submissionApproved, long submissionRejected,
                            List<AuditItem> recentAudit) {}

    public record AuditItem(String action, String detail, String createdAt) {}
}
