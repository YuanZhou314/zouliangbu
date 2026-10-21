package com.zouliangbu.server.route;

import com.zouliangbu.server.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台接口：登录、审批、编辑。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminAuthInterceptor adminAuth;
    private final RouteImportService routeImportService;
    private final com.zouliangbu.server.route.mapper.RouteSubmissionMapper submissionMapper;

    public AdminController(AdminAuthInterceptor adminAuth,
                           RouteImportService routeImportService,
                           com.zouliangbu.server.route.mapper.RouteSubmissionMapper submissionMapper) {
        this.adminAuth = adminAuth;
        this.routeImportService = routeImportService;
        this.submissionMapper = submissionMapper;
    }

    /** 登录（用固定密码换 token）。 */
    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody LoginRequest req) {
        String token = adminAuth.login(req.password());
        if (token == null) {
            return ApiResponse.fail(ApiResponse.ErrorCode.BAD_REQUEST, "密码错误");
        }
        return ApiResponse.ok(token);
    }

    /** 登出。 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (token != null) {
            adminAuth.logout(token);
        }
        return ApiResponse.ok(null);
    }

    /** 待审批列表（按 status 筛选）。 */
    @GetMapping("/submissions")
    public ApiResponse<List<SubmissionRow>> listSubmissions(
            @RequestParam(required = false) String status) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RouteSubmissionEntity> qw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq(RouteSubmissionEntity::getStatus, status);
        }
        qw.orderByDesc(RouteSubmissionEntity::getId);
        List<RouteSubmissionEntity> rows = submissionMapper.selectList(qw);
        return ApiResponse.ok(rows.stream()
                .map(e -> new SubmissionRow(e.getId(), e.getSourceUrl(), e.getSourceRouteId(),
                        e.getSubmitterUuid(), e.getSubmitterNote(), e.getStatus(),
                        e.getRejectReason(), e.getRouteId(),
                        e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
                        e.getProcessedAt() != null ? e.getProcessedAt().toString() : null))
                .toList());
    }

    /** 审批通过：解析 URL 并落库。 */
    @PostMapping("/submissions/{id}/approve")
    public ApiResponse<Long> approve(@PathVariable long id) throws Exception {
        try {
            Long routeId = routeImportService.approveSubmission(id);
            return ApiResponse.ok(routeId);
        } catch (IllegalStateException e) {
            return ApiResponse.fail(ApiResponse.ErrorCode.BAD_REQUEST, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(ApiResponse.ErrorCode.NOT_FOUND, e.getMessage());
        }
    }

    /** 驳回。 */
    @PostMapping("/submissions/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable long id, @RequestBody RejectRequest req) {
        routeImportService.rejectSubmission(id, req.reason());
        return ApiResponse.ok(null);
    }

    /** 编辑线路（更新正文/封面/推荐位）。 */
    @PutMapping("/routes/{id}")
    public ApiResponse<Void> editRoute(@PathVariable long id, @RequestBody EditRouteRequest req) {
        // TODO: 更新 route + route_detail
        return ApiResponse.ok(null);
    }

    /** 下架。 */
    @DeleteMapping("/routes/{id}")
    public ApiResponse<Void> deleteRoute(@PathVariable long id) {
        // TODO: status = 0
        return ApiResponse.ok(null);
    }

    /** 重新解析（管理员手动触发）。 */
    @PostMapping("/routes/{id}/reparse")
    public ApiResponse<Long> reparse(@PathVariable long id, @RequestBody ReparseRequest req) throws Exception {
        Long routeId = routeImportService.importFromUrl(req.sourceUrl());
        return ApiResponse.ok(routeId);
    }

    /** 解析路线链接（不入库，只返回解析结果供编辑页填表）：两步路 / 六只脚自动识别。 */
    @PostMapping("/parse")
    public ApiResponse<ParseResult> parse(@RequestBody ParseRequest req) {
        try {
            RouteData.ParsedRoute p = routeImportService.preview(req.url());
            return ApiResponse.ok(new ParseResult(
                    p.name, p.region,
                    p.totalMileageM / 1000.0,
                    (int) p.cumulativeClimbM,
                    p.difficulty, p.risk, p.suitableMonths, p.roadType,
                    p.introHtml, p.contentHtml, p.trafficHtml,
                    p.trackPoints.size(), p.markers.size()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(ApiResponse.ErrorCode.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(ApiResponse.ErrorCode.SERVER_ERROR, "抓取失败：" + e.getMessage());
        }
    }

    // ===== DTO =====

    record LoginRequest(String password) {}
    record RejectRequest(String reason) {}
    record EditRouteRequest(String name, String introHtml, String contentHtml,
                            String trafficHtml, String coverUrl, Boolean isRecommend) {}
    record ReparseRequest(String sourceUrl) {}
    record ParseRequest(String url) {}
    record ParseResult(String name, String region, double distanceKm, int ascentMeters,
                       String difficulty, String risk, String suitableMonths, String roadType,
                       String introHtml, String contentHtml, String trafficHtml,
                       int trackPointCount, int markerCount) {}

    record SubmissionRow(long id, String sourceUrl, String sourceRouteId, String submitterUuid,
                         String submitterNote, String status, String rejectReason,
                         Long routeId, String createdAt, String processedAt) {}
}
