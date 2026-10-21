package com.zouliangbu.server.route;

import com.zouliangbu.server.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 路线内容接口（公开）：提交、查状态、查详情。
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteContentController {

    private final RouteImportService routeImportService;
    private final com.zouliangbu.server.route.mapper.RouteSubmissionMapper submissionMapper;
    private final com.zouliangbu.server.content.mapper.RouteMapper routeMapper;

    public RouteContentController(RouteImportService routeImportService,
                                  com.zouliangbu.server.route.mapper.RouteSubmissionMapper submissionMapper,
                                  com.zouliangbu.server.content.mapper.RouteMapper routeMapper) {
        this.routeImportService = routeImportService;
        this.submissionMapper = submissionMapper;
        this.routeMapper = routeMapper;
    }

    /** 提交路线链接（POST）：支持两步路 / 六只脚，审批通过后才抓取入库。 */
    @PostMapping("/submit")
    public ApiResponse<Long> submit(@RequestBody SubmitRequest req) {
        if (req.sourceUrl() == null || req.sourceUrl().isBlank()) {
            return ApiResponse.fail(ApiResponse.ErrorCode.BAD_REQUEST, "链接不能为空");
        }
        if (req.submitterUuid() == null || req.submitterUuid().isBlank()) {
            return ApiResponse.fail(ApiResponse.ErrorCode.BAD_REQUEST, "提交者标识不能为空");
        }
        String sourceRouteId = extractSourceRouteId(req.sourceUrl());
        if (sourceRouteId == null) {
            return ApiResponse.fail(ApiResponse.ErrorCode.BAD_REQUEST,
                    "无法识别的链接，目前仅支持两步路 / 六只脚行程链接");
        }
        // 保存 submission（审批前不落 route）
        return ApiResponse.ok(saveSubmission(req.sourceUrl(), sourceRouteId,
                req.submitterUuid(), req.submitterNote()));
    }

    /** 按平台提取源站 id；不支持或格式异常返回 null。 */
    static String extractSourceRouteId(String url) {
        RoutePlatform platform = RoutePlatform.detect(url);
        if (platform == null) {
            return null;
        }
        Long id = switch (platform) {
            case TWOBULU -> TwoBulu.extractRouteId(url);
            case SIXFOOT -> SixFoot.extractTripId(url);
        };
        return id == null ? null : String.valueOf(id);
    }

    /** 查提交状态。 */
    @GetMapping("/submissions/{id}")
    public ApiResponse<SubmissionStatus> getSubmission(@PathVariable long id) {
        RouteSubmissionEntity e = submissionMapper.selectById(id);
        if (e == null) {
            return ApiResponse.fail(ApiResponse.ErrorCode.NOT_FOUND, "提交不存在");
        }
        return ApiResponse.ok(new SubmissionStatus(e.getId(), e.getStatus(),
                e.getRejectReason(), e.getRouteId()));
    }

    /** 查路线详情（正文 + 轨迹 + 标注）。 */
    @GetMapping("/{id}")
    public ApiResponse<RouteDetail> getRouteDetail(@PathVariable long id) {
        RouteDetailEntity detail = routeImportService.getDetail(id);
        if (detail == null) {
            return ApiResponse.fail(ApiResponse.ErrorCode.NOT_FOUND, "路线不存在");
        }
        List<RouteTrackPointEntity> track = routeImportService.getTrackPoints(id);
        List<RouteMarkerEntity> markers = routeImportService.getMarkers(id);
        // join route table for name/region/distance/ascent
        com.zouliangbu.server.content.entity.RouteEntity re = routeMapper.selectById(id);
        return ApiResponse.ok(new RouteDetail(
                detail.getRouteId(),
                re != null ? re.getName() : null,
                re != null ? re.getRegion() : null,
                detail.getSourceName(),
                detail.getSourceUrl(),
                detail.getSourceAuthor(),
                detail.getIntroHtml(),
                detail.getContentHtml(),
                detail.getTrafficHtml(),
                detail.getDifficulty(),
                detail.getRisk(),
                detail.getSuitableMonths(),
                detail.getRoadType(),
                detail.getViewType(),
                re != null && re.getDistanceKm() != null ? re.getDistanceKm().doubleValue() : 0,
                re != null && re.getAscentMeters() != null ? re.getAscentMeters() : 0,
                detail.getDurationName(),
                track.size(),
                markers.size()));
    }

    /** 轨迹点（单独接口，体量大）。 */
    @GetMapping("/{id}/track")
    public ApiResponse<List<TrackPointDto>> getTrack(@PathVariable long id) {
        List<RouteTrackPointEntity> pts = routeImportService.getTrackPoints(id);
        return ApiResponse.ok(pts.stream()
                .map(p -> new TrackPointDto(p.getSeq(), p.getLat(), p.getLng(),
                        p.getAltitude(), p.getSpeed(), p.getTrackTime()))
                .toList());
    }

    /** 标注点。 */
    @GetMapping("/{id}/markers")
    public ApiResponse<List<MarkerDto>> getMarkers(@PathVariable long id) {
        List<RouteMarkerEntity> pts = routeImportService.getMarkers(id);
        return ApiResponse.ok(pts.stream()
                .map(p -> new MarkerDto(p.getSeq(), p.getName(), p.getLat(), p.getLng(),
                        p.getAltitude(), p.getMarkerTime()))
                .toList());
    }

    // ===== DTO =====

    record SubmitRequest(String sourceUrl, String submitterUuid, String submitterNote) {}

    record SubmissionStatus(long id, String status, String rejectReason, Long routeId) {}

    record RouteDetail(long id, String name, String region,
                       String sourceName, String sourceUrl, String sourceAuthor,
                       String introHtml, String contentHtml, String trafficHtml,
                       String difficulty, String risk, String suitableMonths,
                       String roadType, String viewType,
                       double distanceKm, int ascentMeters, String durationName,
                       int trackPointCount, int markerCount) {}

    record TrackPointDto(int seq, double lat, double lng,
                         Double altitude, Double speed, Long time) {}

    record MarkerDto(int seq, String name, double lat, double lng,
                     Double altitude, Long time) {}

    // 存 route_submission 表
    private Long saveSubmission(String url, String routeId, String uuid, String note) {
        RouteSubmissionEntity e = new RouteSubmissionEntity();
        e.setSourceUrl(url);
        e.setSourceRouteId(routeId);
        e.setSubmitterUuid(uuid);
        e.setSubmitterNote(note);
        e.setStatus("PENDING");
        e.setCreatedAt(java.time.LocalDateTime.now());
        submissionMapper.insert(e);
        return e.getId();
    }
}
