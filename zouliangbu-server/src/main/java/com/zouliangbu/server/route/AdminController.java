package com.zouliangbu.server.route;

import com.zouliangbu.server.common.ApiResponse;
import com.zouliangbu.server.content.entity.RouteEntity;
import com.zouliangbu.server.content.mapper.RouteMapper;
import com.zouliangbu.server.route.mapper.RouteDetailMapper;
import com.zouliangbu.server.route.mapper.RouteSubmissionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台接口：登录、审批、编辑、下架、解析。
 *
 * 状态码约定：业务失败一律走 {@link ApiResponse#failResponse}，HTTP 状态码跟着错误码走。
 * 直接用 {@code return ApiResponse.ok/fail} 会恒为 200，前端只看状态码时会把故障当成功。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminAuthInterceptor adminAuth;
    private final RouteImportService routeImportService;
    private final RouteSubmissionMapper submissionMapper;
    private final RouteDetailMapper detailMapper;
    private final RouteMapper routeMapper;

    public AdminController(AdminAuthInterceptor adminAuth,
                           RouteImportService routeImportService,
                           RouteSubmissionMapper submissionMapper,
                           RouteDetailMapper detailMapper,
                           RouteMapper routeMapper) {
        this.adminAuth = adminAuth;
        this.routeImportService = routeImportService;
        this.submissionMapper = submissionMapper;
        this.detailMapper = detailMapper;
        this.routeMapper = routeMapper;
    }

    /** 统一成功包装：省掉每个方法手写 ResponseEntity.ok(...) 的括号噪音。 */
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /** 登录（用固定密码换 token）。 */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<String>> login(@RequestBody LoginRequest req) {
        String token = adminAuth.login(req.password());
        if (token == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, "密码错误");
        }
        return ok(token);
    }

    /** 登出。 */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (token != null) {
            adminAuth.logout(token);
        }
        return ok(null);
    }

    /** 提交列表（按 status 筛选）。 */
    @GetMapping("/submissions")
    public ResponseEntity<ApiResponse<List<SubmissionRow>>> listSubmissions(
            @RequestParam(required = false) String status) {
        var qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RouteSubmissionEntity>();
        if (status != null && !status.isBlank()) {
            qw.eq(RouteSubmissionEntity::getStatus, status);
        }
        qw.orderByDesc(RouteSubmissionEntity::getId);
        List<RouteSubmissionEntity> rows = submissionMapper.selectList(qw);

        List<SubmissionRow> out = rows.stream()
                .map(e -> new SubmissionRow(e.getId(), e.getSourceUrl(), e.getSourceRouteId(),
                        e.getSubmitterUuid(), e.getSubmitterNote(), e.getStatus(),
                        e.getRejectReason(), e.getRouteId(),
                        e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
                        e.getProcessedAt() != null ? e.getProcessedAt().toString() : null))
                .toList();
        return ok(out);
    }

    /** 审批通过：解析 URL 并落库。 */
    @PostMapping("/submissions/{id}/approve")
    public ResponseEntity<ApiResponse<Long>> approve(@PathVariable long id) {
        try {
            return ok(routeImportService.approveSubmission(id));
        } catch (IllegalStateException e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.SERVER_ERROR, "抓取失败：" + e.getMessage());
        }
    }

    /** 驳回。 */
    @PostMapping("/submissions/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable long id, @RequestBody RejectRequest req) {
        if (submissionMapper.selectById(id) == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, "提交不存在");
        }
        routeImportService.rejectSubmission(id, req.reason());
        return ok(null);
    }

    /**
     * 编辑线路（基本信息 + 正文 + 推荐位）。
     *
     * 之前是 TODO 空实现：返回 ok 但不落库，前端提示"保存成功"而数据毫无变化。
     * 现在按"部分更新"语义：传 null 的字段保持原值，不会把没填的字段清空。
     */
    @PutMapping("/routes/{id}")
    public ResponseEntity<ApiResponse<Void>> editRoute(@PathVariable long id,
                                                       @RequestBody EditRouteRequest req) {
        RouteEntity route = routeMapper.selectById(id);
        RouteDetailEntity detail = detailMapper.selectById(id);
        if (route == null && detail == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, "线路不存在");
        }

        if (route != null) {
            if (req.name() != null) route.setName(req.name().trim());
            if (req.region() != null) route.setRegion(req.region().trim());
            if (req.distanceKm() != null) route.setDistanceKm(BigDecimal.valueOf(req.distanceKm()));
            if (req.ascentMeters() != null) route.setAscentMeters(req.ascentMeters());
            if (req.tags() != null) route.setTags(req.tags().trim());
            if (req.coverUrl() != null) route.setCoverUrl(req.coverUrl().trim());
            if (req.isRecommend() != null) route.setIsRecommend(req.isRecommend());
            routeMapper.updateById(route);
        }

        if (detail != null) {
            if (req.introHtml() != null) detail.setIntroHtml(req.introHtml());
            if (req.contentHtml() != null) detail.setContentHtml(req.contentHtml());
            if (req.trafficHtml() != null) detail.setTrafficHtml(req.trafficHtml());
            detailMapper.updateById(detail);
        } else if (req.introHtml() != null || req.contentHtml() != null || req.trafficHtml() != null) {
            // 线路存在但还没有 detail 记录（理论上不该发生），补一条最小记录，避免正文存不进去
            RouteDetailEntity created = new RouteDetailEntity();
            created.setRouteId(id);
            created.setIntroHtml(req.introHtml());
            created.setContentHtml(req.contentHtml());
            created.setTrafficHtml(req.trafficHtml());
            created.setSourceUrl("");
            created.setSourceName("manual");
            created.setParsedAt(LocalDateTime.now());
            detailMapper.insert(created);
        }
        return ok(null);
    }

    /**
     * 下架 / 重新上架。
     *
     * 用 status 软删除而不是物理删除：历史轨迹与用户收藏仍引用 route_id，
     * 物理删会导致外键悬空和数据无法恢复。status=0 后公开接口自动过滤。
     */
    @DeleteMapping("/routes/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoute(@PathVariable long id) {
        RouteEntity route = routeMapper.selectById(id);
        if (route == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, "线路不存在");
        }
        route.setStatus(0);
        routeMapper.updateById(route);
        return ok(null);
    }

    /** 重新上架（配合下架使用，后台点错可恢复）。 */
    @PostMapping("/routes/{id}/publish")
    public ResponseEntity<ApiResponse<Void>> publishRoute(@PathVariable long id) {
        RouteEntity route = routeMapper.selectById(id);
        if (route == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, "线路不存在");
        }
        route.setStatus(1);
        routeMapper.updateById(route);
        return ok(null);
    }

    /**
     * 后台直接新建空白线路（不依赖用户提交）。
     *
     * 之前"新建"只能先让用户提交链接再审批，运营没法凭空建一条线。
     * 现在创建 route + 一条最小 route_detail（sourceName="manual"），
     * 返回 routeId 供编辑页继续填内容。
     *
     * 为什么同时建 detail：App 的路线详情页读的是 route_detail，
     * 只建 route 不建 detail 会让新线路点进去是空白（而不是"暂无内容"）。
     */
    @PostMapping("/routes")
    public ResponseEntity<ApiResponse<Long>> createRoute(@RequestBody CreateRouteRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, "线路名称不能为空");
        }
        RouteEntity route = new RouteEntity();
        route.setName(req.name().trim());
        route.setRegion(req.region() == null ? "" : req.region().trim());
        route.setDistanceKm(BigDecimal.valueOf(req.distanceKm() == null ? 0d : req.distanceKm()));
        route.setAscentMeters(req.ascentMeters() == null ? 0 : req.ascentMeters());
        route.setTags(req.tags() == null ? "" : req.tags().trim());
        route.setCoverUrl(req.coverUrl() == null || req.coverUrl().isBlank() ? null : req.coverUrl().trim());
        route.setSectionId(req.sectionId());
        route.setIsRecommend(Boolean.TRUE.equals(req.isRecommend()));
        route.setSortWeight(50);
        route.setStatus(1);
        route.setCreatedAt(LocalDateTime.now());
        routeMapper.insert(route);

        Long routeId = route.getId();

        RouteDetailEntity detail = new RouteDetailEntity();
        detail.setRouteId(routeId);
        detail.setIntroHtml(req.introHtml());
        detail.setContentHtml(req.contentHtml());
        detail.setTrafficHtml(req.trafficHtml());
        detail.setSourceUrl(req.sourceUrl() == null ? "" : req.sourceUrl().trim());
        detail.setSourceName("manual");
        detail.setParsedAt(LocalDateTime.now());
        detailMapper.insert(detail);

        return ok(routeId);
    }

    /** 重新解析（管理员手动触发）。 */
    @PostMapping("/routes/{id}/reparse")
    public ResponseEntity<ApiResponse<Long>> reparse(@PathVariable long id,
                                                     @RequestBody ReparseRequest req) {
        try {
            return ok(routeImportService.importFromUrl(req.sourceUrl()));
        } catch (Exception e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.SERVER_ERROR, "抓取失败：" + e.getMessage());
        }
    }

    /** 解析路线链接（不入库，只返回解析结果供编辑页填表）：两步路 / 六只脚自动识别。 */
    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<ParseResult>> parse(@RequestBody ParseRequest req) {
        try {
            RouteData.ParsedRoute p = routeImportService.preview(req.url());
            return ok(new ParseResult(
                    p.name, p.region,
                    p.totalMileageM / 1000.0,
                    (int) p.cumulativeClimbM,
                    p.difficulty, p.risk, p.suitableMonths, p.roadType,
                    p.introHtml, p.contentHtml, p.trafficHtml,
                    p.trackPoints.size(), p.markers.size()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.SERVER_ERROR, "抓取失败：" + e.getMessage());
        }
    }

    /**
     * 后台查看任意状态的提交详情。
     *
     * 为什么需要这个接口：编辑页原来读的是公开接口 GET /api/v1/routes/{id}，
     * 那个接口依赖 route_detail（只在审批通过时才写入）且不区分上下架，
     * 所以待审批 / 已驳回的记录点进去一律"路线不存在"。
     *
     * 四种情况都返回 200：
     *   ① 有 route_detail        → 已抓取内容（已上线 / 已下架）
     *   ② 无 detail 但有 route   → 基本信息 + 空正文
     *   ③ 只有 submission + routeId → 同上
     *   ④ 只有 submission（未审批）→ 只剩链接，前端给"先解析"入口
     *
     * 正文类字段一律返回空串而非 null：前端直接赋给 textarea，null 会显示成 "null"。
     */
    @GetMapping("/submissions/{id}/detail")
    public ResponseEntity<ApiResponse<AdminRouteDetail>> submissionDetail(@PathVariable long id) {
        RouteSubmissionEntity sub = submissionMapper.selectById(id);
        if (sub == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, "提交不存在");
        }

        Long routeId = sub.getRouteId();
        if (routeId == null) {
            routeId = findRouteIdBySourceUrl(sub.getSourceUrl());
        }

        RouteEntity re = routeId == null ? null : routeMapper.selectById(routeId);
        RouteDetailEntity detail = routeId == null ? null : detailMapper.selectById(routeId);

        int trackPointCount = 0;
        int markerCount = 0;
        if (routeId != null) {
            trackPointCount = routeImportService.getTrackPoints(routeId).size();
            markerCount = routeImportService.getMarkers(routeId).size();
        }

        AdminRouteDetail d = new AdminRouteDetail(
                sub.getId(),
                sub.getStatus(),
                nz(sub.getRejectReason()),
                routeId,
                nz(sub.getSourceUrl()),
                nz(sub.getSubmitterNote()),
                nz(sub.getSubmitterUuid()),
                sub.getCreatedAt() != null ? sub.getCreatedAt().toString() : "",
                sub.getProcessedAt() != null ? sub.getProcessedAt().toString() : "",
                nz(re != null ? re.getName() : null),
                nz(re != null ? re.getRegion() : null),
                re != null && re.getDistanceKm() != null ? re.getDistanceKm().doubleValue() : 0d,
                re != null && re.getAscentMeters() != null ? re.getAscentMeters() : 0,
                nz(re != null ? re.getTags() : null),
                nz(re != null ? re.getCoverUrl() : null),
                re != null && Boolean.TRUE.equals(re.getIsRecommend()),
                re != null && re.getStatus() != null ? re.getStatus() : -1,
                nz(detail != null ? detail.getIntroHtml() : null),
                nz(detail != null ? detail.getContentHtml() : null),
                nz(detail != null ? detail.getTrafficHtml() : null),
                nz(detail != null ? detail.getDifficulty() : null),
                nz(detail != null ? detail.getRisk() : null),
                nz(detail != null ? detail.getSuitableMonths() : null),
                nz(detail != null ? detail.getRoadType() : null),
                nz(detail != null ? detail.getDurationName() : null),
                nz(detail != null ? detail.getSourceName() : null),
                nz(detail != null ? detail.getSourceAuthor() : null),
                trackPointCount,
                markerCount,
                detail != null);
        return ok(d);
    }

    /**
     * 按线路 id 取后台详情（不经过提交记录）。
     *
     * 与 {@link #submissionDetail} 的区别：那条是从"用户提交"出发反查线路，
     * 这条是从"线路"本身出发 —— 运营直接新建的线路没有提交记录，只能走这里。
     * 两条路径最终都返回同一个 {@link AdminRouteDetail}，前端填表逻辑只有一份。
     */
    @GetMapping("/routes/{id}/detail")
    public ResponseEntity<ApiResponse<AdminRouteDetail>> routeDetail(@PathVariable long id) {
        RouteEntity re = routeMapper.selectById(id);
        RouteDetailEntity detail = detailMapper.selectById(id);
        if (re == null && detail == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, "线路不存在");
        }
        int trackPointCount = routeImportService.getTrackPoints(id).size();
        int markerCount = routeImportService.getMarkers(id).size();

        AdminRouteDetail d = new AdminRouteDetail(
                0L,                       // 无关联提交
                "",                       // 提交状态为空表示"不是从提交来的"
                "",
                id,
                nz(detail != null ? detail.getSourceUrl() : null),
                "",
                "",
                re != null && re.getCreatedAt() != null ? re.getCreatedAt().toString() : "",
                "",
                nz(re != null ? re.getName() : null),
                nz(re != null ? re.getRegion() : null),
                re != null && re.getDistanceKm() != null ? re.getDistanceKm().doubleValue() : 0d,
                re != null && re.getAscentMeters() != null ? re.getAscentMeters() : 0,
                nz(re != null ? re.getTags() : null),
                nz(re != null ? re.getCoverUrl() : null),
                re != null && Boolean.TRUE.equals(re.getIsRecommend()),
                re != null && re.getStatus() != null ? re.getStatus() : -1,
                nz(detail != null ? detail.getIntroHtml() : null),
                nz(detail != null ? detail.getContentHtml() : null),
                nz(detail != null ? detail.getTrafficHtml() : null),
                nz(detail != null ? detail.getDifficulty() : null),
                nz(detail != null ? detail.getRisk() : null),
                nz(detail != null ? detail.getSuitableMonths() : null),
                nz(detail != null ? detail.getRoadType() : null),
                nz(detail != null ? detail.getDurationName() : null),
                nz(detail != null ? detail.getSourceName() : null),
                nz(detail != null ? detail.getSourceAuthor() : null),
                trackPointCount,
                markerCount,
                detail != null);
        return ok(d);
    }

    /** 按来源 URL 反查已导入的同源线路（提交量极小，无需索引）。 */
    private Long findRouteIdBySourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        var qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RouteDetailEntity>();
        qw.eq(RouteDetailEntity::getSourceUrl, sourceUrl).last("LIMIT 1");
        RouteDetailEntity hit = detailMapper.selectOne(qw);
        return hit != null ? hit.getRouteId() : null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ===== DTO =====

    record LoginRequest(String password) {}

    record RejectRequest(String reason) {}

    /** 所有字段可空 —— 部分更新语义，null 表示"不改"。 */
    record EditRouteRequest(String name, String region, Double distanceKm, Integer ascentMeters,
                            String tags, String coverUrl, Boolean isRecommend,
                            String introHtml, String contentHtml, String trafficHtml) {}

    record ReparseRequest(String sourceUrl) {}

    /** 直接新建线路的表单。sectionId 可空（为空则只可能出现在推荐位）。 */
    record CreateRouteRequest(String name, String region, Double distanceKm, Integer ascentMeters,
                              String tags, String coverUrl, Boolean isRecommend, Long sectionId,
                              String introHtml, String contentHtml, String trafficHtml,
                              String sourceUrl) {}

    record ParseRequest(String url) {}

    record ParseResult(String name, String region, double distanceKm, int ascentMeters,
                       String difficulty, String risk, String suitableMonths, String roadType,
                       String introHtml, String contentHtml, String trafficHtml,
                       int trackPointCount, int markerCount) {}

    record SubmissionRow(long id, String sourceUrl, String sourceRouteId, String submitterUuid,
                         String submitterNote, String status, String rejectReason,
                         Long routeId, String createdAt, String processedAt) {}

    /** 后台详情：提交信息 + 线路基本信息 + 正文 + 轨迹统计，任何状态都可取值。 */
    record AdminRouteDetail(
            long submissionId, String submissionStatus, String rejectReason,
            Long routeId, String sourceUrl, String submitterNote, String submitterUuid,
            String createdAt, String processedAt,
            String name, String region, double distanceKm, int ascentMeters,
            String tags, String coverUrl, boolean isRecommend, int routeStatus,
            String introHtml, String contentHtml, String trafficHtml,
            String difficulty, String risk, String suitableMonths, String roadType,
            String durationName, String sourceName, String sourceAuthor,
            int trackPointCount, int markerCount, boolean hasDetail) {}
}
