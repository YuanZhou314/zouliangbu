package com.zouliangbu.server.route;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zouliangbu.server.content.entity.RouteEntity;
import com.zouliangbu.server.content.mapper.RouteMapper;
import com.zouliangbu.server.route.mapper.RouteDetailMapper;
import com.zouliangbu.server.route.mapper.RouteMarkerMapper;
import com.zouliangbu.server.route.mapper.RouteSubmissionMapper;
import com.zouliangbu.server.route.mapper.RouteTrackPointMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 路线导入服务：解析第三方路线链接（两步路 / 六只脚），
 * 存入 route + route_detail + route_track_point + route_marker。
 *
 * 坐标系：各 Client 内部已把 WGS-84 转成 GCJ-02，本服务直接存 GCJ-02。
 */
@Service
public class RouteImportService {

    private static final Logger log = LoggerFactory.getLogger(RouteImportService.class);

    private final TwoBuluClient twoBuluClient;
    private final SixFootClient sixFootClient;
    private final RouteMapper routeMapper;
    private final RouteDetailMapper detailMapper;
    private final RouteTrackPointMapper trackMapper;
    private final RouteMarkerMapper markerMapper;
    private final RouteSubmissionMapper submissionMapper;

    public RouteImportService(TwoBuluClient twoBuluClient,
                              SixFootClient sixFootClient,
                              RouteMapper routeMapper,
                              RouteDetailMapper detailMapper,
                              RouteTrackPointMapper trackMapper,
                              RouteMarkerMapper markerMapper,
                              RouteSubmissionMapper submissionMapper) {
        this.twoBuluClient = twoBuluClient;
        this.sixFootClient = sixFootClient;
        this.routeMapper = routeMapper;
        this.detailMapper = detailMapper;
        this.trackMapper = trackMapper;
        this.markerMapper = markerMapper;
        this.submissionMapper = submissionMapper;
    }

    /**
     * 从 URL 导入一条路线（创建 route + 详情 + 轨迹 + 标注）。
     * 按链接域名自动路由到对应平台抓取器。
     *
     * @return 新创建的 route_id
     */
    @Transactional
    public Long importFromUrl(String sourceUrl) throws Exception {
        RouteData.ParsedRoute p = preview(sourceUrl);
        RoutePlatform platform = RoutePlatform.detect(sourceUrl);

        // 1. 插入 route
        RouteEntity re = new RouteEntity();
        re.setName(p.name);
        re.setRegion(p.region);
        re.setDistanceKm(BigDecimal.valueOf(p.totalMileageM / 1000.0).setScale(1, java.math.RoundingMode.HALF_UP));
        re.setAscentMeters((int) p.cumulativeClimbM);
        re.setTags("");
        re.setIsRecommend(false);
        re.setSortWeight(50);
        re.setStatus(1);
        re.setCreatedAt(LocalDateTime.now());
        routeMapper.insert(re);
        long routeId = re.getId();

        // 2. 详情
        RouteDetailEntity detail = new RouteDetailEntity();
        detail.setRouteId(routeId);
        detail.setIntroHtml(p.introHtml);
        detail.setContentHtml(p.contentHtml);
        detail.setTrafficHtml(p.trafficHtml);
        detail.setDifficulty(p.difficulty);
        detail.setRisk(p.risk);
        detail.setSuitableMonths(p.suitableMonths);
        detail.setRoadType(p.roadType);
        detail.setViewType(p.viewType);
        detail.setDurationName(String.valueOf(Math.round(p.durationHours)));
        detail.setTrackSourceId(p.sourceTrackId);
        detail.setSourceUrl(p.sourceUrl);
        detail.setSourceName(platform.displayName);
        detail.setSourceAuthor(p.sourceAuthor);
        detail.setParsedAt(LocalDateTime.now());
        detailMapper.insert(detail);

        // 3. 轨迹点
        int seq = 0;
        for (RouteData.TrackPoint tp : p.trackPoints) {
            RouteTrackPointEntity e = new RouteTrackPointEntity();
            e.setRouteId(routeId);
            e.setSeq(seq++);
            e.setLat(tp.lat);
            e.setLng(tp.lng);
            e.setAltitude(tp.altitude);
            e.setSpeed(tp.speed);
            e.setTrackTime(tp.time);
            trackMapper.insert(e);
        }

        // 4. 标注点
        seq = 0;
        for (RouteData.Marker mk : p.markers) {
            RouteMarkerEntity e = new RouteMarkerEntity();
            e.setRouteId(routeId);
            e.setSeq(seq++);
            e.setName(mk.name);
            e.setLat(mk.lat);
            e.setLng(mk.lng);
            e.setAltitude(mk.altitude);
            e.setMarkerTime(mk.time);
            markerMapper.insert(e);
        }

        log.info("imported route: platform={}, name={}, routeId={}, points={}, markers={}",
                platform, p.name, routeId, p.trackPoints.size(), p.markers.size());
        return routeId;
    }

    /** 只解析不入库（后台"解析预览"用）：按平台分发抓取。 */
    public RouteData.ParsedRoute preview(String sourceUrl) throws Exception {
        RoutePlatform platform = RoutePlatform.detect(sourceUrl);
        if (platform == null) {
            throw new IllegalArgumentException("暂不支持该链接，目前仅支持两步路 / 六只脚");
        }
        return fetch(platform, sourceUrl);
    }

    /** 按平台抓取并解析（各平台 id 提取规则不同）。 */
    private RouteData.ParsedRoute fetch(RoutePlatform platform, String sourceUrl) throws Exception {
        switch (platform) {
            case TWOBULU -> {
                Long id = TwoBulu.extractRouteId(sourceUrl);
                if (id == null) {
                    throw new IllegalArgumentException("无法从两步路链接提取路线 id");
                }
                return twoBuluClient.fetchRoute(id, sourceUrl);
            }
            case SIXFOOT -> {
                Long id = SixFoot.extractTripId(sourceUrl);
                if (id == null) {
                    throw new IllegalArgumentException("无法从六只脚链接提取行程 id");
                }
                return sixFootClient.fetchTrip(id, sourceUrl);
            }
            default -> throw new IllegalArgumentException("暂不支持该链接来源");
        }
    }

    /**
     * 审批通过：解析 URL 并更新 submission 状态。
     */
    @Transactional
    public Long approveSubmission(long submissionId) throws Exception {
        RouteSubmissionEntity sub = submissionMapper.selectById(submissionId);
        if (sub == null) {
            throw new IllegalArgumentException("提交不存在");
        }
        if (!"PENDING".equals(sub.getStatus())) {
            throw new IllegalStateException("提交已处理过");
        }

        Long routeId = importFromUrl(sub.getSourceUrl());

        sub.setRouteId(routeId);
        sub.setStatus("APPROVED");
        sub.setProcessedAt(LocalDateTime.now());
        submissionMapper.updateById(sub);
        return routeId;
    }

    /**
     * 驳回提交。
     */
    @Transactional
    public void rejectSubmission(long submissionId, String reason) {
        RouteSubmissionEntity sub = submissionMapper.selectById(submissionId);
        if (sub == null) {
            throw new IllegalArgumentException("提交不存在");
        }
        sub.setStatus("REJECTED");
        sub.setRejectReason(reason != null ? reason : "不符合收录标准");
        sub.setProcessedAt(LocalDateTime.now());
        submissionMapper.updateById(sub);
    }

    /**
     * 查询某条路线的详情（含正文）。
     */
    public RouteDetailEntity getDetail(long routeId) {
        return detailMapper.selectById(routeId);
    }

    /**
     * 查询轨迹点列表。
     */
    public List<RouteTrackPointEntity> getTrackPoints(long routeId) {
        return trackMapper.selectList(
                new LambdaQueryWrapper<RouteTrackPointEntity>()
                        .eq(RouteTrackPointEntity::getRouteId, routeId)
                        .orderByAsc(RouteTrackPointEntity::getSeq));
    }

    /**
     * 查询标注点列表。
     */
    public List<RouteMarkerEntity> getMarkers(long routeId) {
        return markerMapper.selectList(
                new LambdaQueryWrapper<RouteMarkerEntity>()
                        .eq(RouteMarkerEntity::getRouteId, routeId)
                        .orderByAsc(RouteMarkerEntity::getSeq));
    }
}
