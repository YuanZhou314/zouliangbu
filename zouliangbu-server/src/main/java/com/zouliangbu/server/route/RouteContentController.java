package com.zouliangbu.server.route;

import com.zouliangbu.server.common.ApiResponse;
import com.zouliangbu.server.content.entity.RouteEntity;
import com.zouliangbu.server.content.mapper.RouteMapper;
import com.zouliangbu.server.route.mapper.RouteSubmissionMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 路线内容接口（公开，无需 token）：提交、查状态、查详情、轨迹、标注。
 *
 * 失败一律走 {@link ApiResponse#failResponse}，HTTP 状态码与业务码一致。
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteContentController {

    private final RouteImportService routeImportService;
    private final RouteSubmissionMapper submissionMapper;
    private final RouteMapper routeMapper;

    public RouteContentController(RouteImportService routeImportService,
                                  RouteSubmissionMapper submissionMapper,
                                  RouteMapper routeMapper) {
        this.routeImportService = routeImportService;
        this.submissionMapper = submissionMapper;
        this.routeMapper = routeMapper;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /** 提交路线链接：支持两步路 / 六只脚，审批通过后才抓取入库。 */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<Long>> submit(@RequestBody SubmitRequest req) {
        if (req.sourceUrl() == null || req.sourceUrl().isBlank()) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, "链接不能为空");
        }
        if (req.submitterUuid() == null || req.submitterUuid().isBlank()) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, "提交者标识不能为空");
        }
        String sourceRouteId = extractSourceRouteId(req.sourceUrl());
        if (sourceRouteId == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST,
                    "无法识别的链接，目前仅支持两步路 / 六只脚行程链接");
        }
        return ok(saveSubmission(req.sourceUrl(), sourceRouteId, req.submitterUuid(), req.submitterNote()));
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
    public ResponseEntity<ApiResponse<SubmissionStatus>> getSubmission(@PathVariable long id) {
        RouteSubmissionEntity e = submissionMapper.selectById(id);
        if (e == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, "提交不存在");
        }
        return ok(new SubmissionStatus(e.getId(), e.getStatus(), e.getRejectReason(), e.getRouteId()));
    }

    /**
     * 查路线详情（正文 + 轨迹 + 标注）。
     *
     * route 主记录存在但 route_detail 行缺失时不再 404：返回基础信息 + 空正文
     * （旧种子 / 历史审批数据可能只有 route 没有 detail，404 会让 App 详情页永远停在骨架屏）。
     * 正文类字段一律空串兜底，绝不返回 null（App 端非空字段收到 null 会直接抛异常）。
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteDetail>> getRouteDetail(@PathVariable long id) {
        RouteEntity re = routeMapper.selectById(id);
        if (re == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, "路线不存在");
        }
        RouteDetailEntity detail = routeImportService.getDetail(id);
        List<RouteTrackPointEntity> track = routeImportService.getTrackPoints(id);
        List<RouteMarkerEntity> markers = routeImportService.getMarkers(id);
        return ok(new RouteDetail(
                id,
                nz(re.getName()),
                nz(re.getRegion()),
                detail != null ? nz(detail.getSourceName()) : "",
                detail != null ? nz(detail.getSourceUrl()) : "",
                detail != null ? nz(detail.getSourceAuthor()) : "",
                detail != null ? nz(detail.getIntroHtml()) : "",
                detail != null ? nz(detail.getContentHtml()) : "",
                detail != null ? nz(detail.getTrafficHtml()) : "",
                detail != null ? nz(detail.getDifficulty()) : "",
                detail != null ? nz(detail.getRisk()) : "",
                detail != null ? nz(detail.getSuitableMonths()) : "",
                detail != null ? nz(detail.getRoadType()) : "",
                detail != null ? nz(detail.getViewType()) : "",
                re.getDistanceKm() != null ? re.getDistanceKm().doubleValue() : 0,
                re.getAscentMeters() != null ? re.getAscentMeters() : 0,
                detail != null ? nz(detail.getDurationName()) : "",
                track.size(),
                markers.size(),
                re.getCoverUrl(),
                re.getId() == null ? 0 : (int) (re.getId() % 5),
                re.getTags() == null || re.getTags().isBlank()
                        ? List.of()
                        : java.util.Arrays.stream(re.getTags().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 轨迹点（单独接口，体量大）。 */
    @GetMapping("/{id}/track")
    public ResponseEntity<ApiResponse<List<TrackPointDto>>> getTrack(@PathVariable long id) {
        List<RouteTrackPointEntity> pts = routeImportService.getTrackPoints(id);
        return ok(pts.stream()
                .map(p -> new TrackPointDto(p.getSeq(), p.getLat(), p.getLng(),
                        p.getAltitude(), p.getSpeed(), p.getTrackTime()))
                .toList());
    }

    /** 标注点。 */
    @GetMapping("/{id}/markers")
    public ResponseEntity<ApiResponse<List<MarkerDto>>> getMarkers(@PathVariable long id) {
        List<RouteMarkerEntity> pts = routeImportService.getMarkers(id);
        return ok(pts.stream()
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
                       int trackPointCount, int markerCount,
                       String coverUrl, int coverStyle, List<String> tags) {}

    record TrackPointDto(int seq, double lat, double lng,
                         Double altitude, Double speed, Long time) {}

    record MarkerDto(int seq, String name, double lat, double lng,
                     Double altitude, Long time) {}

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
