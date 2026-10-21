package com.zouliangbu.server.route;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zouliangbu.server.content.entity.RouteEntity;
import com.zouliangbu.server.content.mapper.RouteMapper;
import com.zouliangbu.server.route.mapper.ContentProposalMapper;
import com.zouliangbu.server.route.mapper.ContentVoteMapper;
import com.zouliangbu.server.route.mapper.RouteDetailMapper;
import com.zouliangbu.server.route.mapper.RouteDetailRevisionMapper;
import com.zouliangbu.server.route.mapper.RouteTrackPointMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社区共维护：提案（提议）+ 投票（信号）+ 版本快照（回滚）。
 *
 * 设计口径（见 docs/product-philosophy.md）：
 *  - 规则层（机器判）→ 提交即校验，不合格直接拒。
 *  - 信号层（认票）→ 客观勘误达阈值且 0 反对 → 自动合并成新版本。
 *  - 升级通道 → 外部导入 / 主观调整 / 隐私 / 版本冲突 才需要管理员。
 *  - 回滚层 → 每次合并留快照，错了能一键退回上一版（替代"审核防错"）。
 *
 * 不做用户动态、不做关注；提案只针对"线路内容"。
 */
@Service
public class ProposalService {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

    /** 提案类型。 */
    public static final String TYPE_IMPORT = "IMPORT";   // 外部平台链接导入
    public static final String TYPE_UPLOAD = "UPLOAD";   // 贡献者自己的轨迹
    public static final String TYPE_EDIT = "EDIT";       // 已有线路的勘误

    /** 提案分类：决定能不能自动合并。 */
    public static final String CAT_OBJECTIVE = "OBJECTIVE"; // 客观修正（错别字/坐标/顺序）
    public static final String CAT_SUBJECTIVE = "SUBJECTIVE"; // 主观（评级/推荐语/下架）
    public static final String CAT_PRIVACY = "PRIVACY";   // 隐私请求，永不自动合并

    /** 状态。 */
    public static final String ST_PENDING = "PENDING";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED = "REJECTED";
    public static final String ST_MERGED = "MERGED";
    public static final String ST_CONFLICT = "CONFLICT";

    /** 允许改的详情字段（白名单，其余一律忽略）。 */
    private static final Set<String> DETAIL_FIELDS = Set.of(
            "intro_html", "content_html", "traffic_html", "difficulty", "risk",
            "suitable_months", "road_type", "view_type", "duration_name");

    /** 允许改的线路字段。 */
    private static final Set<String> ROUTE_FIELDS = Set.of("name", "region");

    private final ContentProposalMapper proposalMapper;
    private final ContentVoteMapper voteMapper;
    private final RouteDetailMapper detailMapper;
    private final RouteDetailRevisionMapper revisionMapper;
    private final RouteTrackPointMapper trackPointMapper;
    private final RouteMapper routeMapper;
    private final RouteImportService importService;

    private final ObjectMapper json = new ObjectMapper();

    /** 客观修正自动合并所需的认票数（含提案人自己那一票）。 */
    @Value("${zlb.proposal.auto-merge-threshold:2}")
    private int autoMergeThreshold;

    /** 自动合并前的最短等待（分钟），给反对票留时间。 */
    @Value("${zlb.proposal.min-age-minutes:0}")
    private long minAgeMinutes;

    /** 自有轨迹上传是否自动公开（默认关：服务端没有 POI 库，判不了"家在哪"）。 */
    @Value("${zlb.proposal.upload-autopublish:false}")
    private boolean uploadAutoPublish;

    /** 同一提交者每小时最多提多少条（内存计数，防刷）。 */
    @Value("${zlb.proposal.rate-limit-per-hour:10}")
    private int rateLimitPerHour;

    public ProposalService(ContentProposalMapper proposalMapper,
                           ContentVoteMapper voteMapper,
                           RouteDetailMapper detailMapper,
                           RouteDetailRevisionMapper revisionMapper,
                           RouteTrackPointMapper trackPointMapper,
                           RouteMapper routeMapper,
                           RouteImportService importService) {
        this.proposalMapper = proposalMapper;
        this.voteMapper = voteMapper;
        this.detailMapper = detailMapper;
        this.revisionMapper = revisionMapper;
        this.trackPointMapper = trackPointMapper;
        this.routeMapper = routeMapper;
        this.importService = importService;
    }

    // ===== 提交 =====

    /** 提交提案的参数。 */
    public record SubmitCommand(String type, String category, Long targetRouteId, String sourceUrl,
                                String payloadJson, Integer baseVersion, String reason,
                                String proposerUuid, String proposerName, Long proposerUserId) {}

    /**
     * 提交提案。
     *
     * IMPORT：只存链接，等管理员做**合规**审核（不是事实审核）。
     * UPLOAD：先过规则层（点数/坐标范围/时间单调/距离合理性），合格才建单。
     * EDIT：校验目标线路存在，记下 baseVersion（合并时做冲突检测）。
     */
    @Transactional
    public ContentProposalEntity submit(SubmitCommand cmd) {
        if (cmd.type() == null || !Set.of(TYPE_IMPORT, TYPE_UPLOAD, TYPE_EDIT).contains(cmd.type())) {
            throw new IllegalArgumentException("提案类型只能是 IMPORT / UPLOAD / EDIT");
        }
        if (cmd.proposerUuid() == null || cmd.proposerUuid().isBlank()) {
            throw new IllegalArgumentException("提交者标识不能为空");
        }
        checkRateLimit(cmd.proposerUuid());

        String category = cmd.category() == null || cmd.category().isBlank()
                ? CAT_OBJECTIVE : cmd.category().toUpperCase();
        if (!Set.of(CAT_OBJECTIVE, CAT_SUBJECTIVE, CAT_PRIVACY).contains(category)) {
            throw new IllegalArgumentException("提案分类只能是 OBJECTIVE / SUBJECTIVE / PRIVACY");
        }

        String payload = cmd.payloadJson();
        String sourceUrl = cmd.sourceUrl();

        switch (cmd.type()) {
            case TYPE_IMPORT -> {
                if (sourceUrl == null || sourceUrl.isBlank()) {
                    throw new IllegalArgumentException("导入提案必须带链接");
                }
                String sourceRouteId = RouteContentController.extractSourceRouteId(sourceUrl);
                if (sourceRouteId == null) {
                    throw new IllegalArgumentException("无法识别的链接，目前仅支持两步路 / 六只脚行程链接");
                }
                payload = writeJson(Map.of("sourceRouteId", sourceRouteId));
            }
            case TYPE_UPLOAD -> {
                if (payload == null || payload.isBlank()) {
                    throw new IllegalArgumentException("轨迹数据不能为空");
                }
                validateUpload(payload);
            }
            case TYPE_EDIT -> {
                if (cmd.targetRouteId() == null) {
                    throw new IllegalArgumentException("勘误提案必须指定线路");
                }
                RouteDetailEntity detail = detailMapper.selectById(cmd.targetRouteId());
                if (detail == null) {
                    throw new IllegalArgumentException("线路详情不存在，无法提交勘误");
                }
                Map<String, String> fields = parseEditFields(payload);
                if (fields.isEmpty()) {
                    throw new IllegalArgumentException("勘误内容为空");
                }
            }
            default -> throw new IllegalArgumentException("未知提案类型");
        }

        ContentProposalEntity p = new ContentProposalEntity();
        p.setType(cmd.type());
        p.setCategory(category);
        p.setTargetRouteId(cmd.targetRouteId());
        p.setSourceUrl(sourceUrl);
        p.setPayloadJson(payload);
        p.setBaseVersion(cmd.baseVersion() != null ? cmd.baseVersion() : currentVersion(cmd.targetRouteId()));
        p.setReason(cmd.reason());
        p.setProposerUuid(cmd.proposerUuid());
        p.setProposerName(blankToNull(cmd.proposerName()));
        p.setProposerUserId(cmd.proposerUserId());
        p.setStatus(ST_PENDING);
        p.setVotesUp(0);
        p.setVotesDown(0);
        p.setThreshold(autoMergeThreshold);
        p.setCreatedAt(LocalDateTime.now());
        proposalMapper.insert(p);

        // 提案人自己一票（计入阈值）：阈值 2 = "提案人 + 另一个人"确认即可合并。
        ContentVoteEntity own = new ContentVoteEntity();
        own.setProposalId(p.getId());
        own.setVoterKey(cmd.proposerUuid());
        own.setValue(1);
        own.setCreatedAt(LocalDateTime.now());
        voteMapper.insert(own);
        p.setVotesUp(1);

        proposalMapper.updateById(p);

        // 自有轨迹上传：规则层通过且实例允许自动公开 → 直接上线（管理员事后抽查）
        if (TYPE_UPLOAD.equals(p.getType()) && uploadAutoPublish) {
            approve(p.getId(), "auto");
        }
        return p;
    }

    // ===== 投票 =====

    /** 投票结果。 */
    public record VoteResult(long proposalId, String status, int votesUp, int votesDown,
                              boolean merged, Integer mergedVersion) {}

    /**
     * 投票（一人一票，可改票）。
     *
     * 客观勘误：认票数达阈值且 0 反对 → 自动合并成新版本。
     */
    @Transactional
    public VoteResult vote(long proposalId, String voterKey, int value) {
        if (voterKey == null || voterKey.isBlank()) {
            throw new IllegalArgumentException("投票人标识不能为空");
        }
        if (value != 1 && value != -1) {
            throw new IllegalArgumentException("投票值只能是 1 或 -1");
        }
        ContentProposalEntity p = proposalMapper.selectById(proposalId);
        if (p == null) {
            throw new IllegalArgumentException("提案不存在");
        }
        if (!ST_PENDING.equals(p.getStatus())) {
            throw new IllegalStateException("提案已处理，不能再投票");
        }
        if (voterKey.equals(p.getProposerUuid())) {
            throw new IllegalStateException("提案人不能重复投票");
        }

        ContentVoteEntity existing = voteMapper.selectOne(new QueryWrapper<ContentVoteEntity>()
                .eq("proposal_id", proposalId)
                .eq("voter_key", voterKey)
                .last("limit 1"));
        if (existing == null) {
            ContentVoteEntity v = new ContentVoteEntity();
            v.setProposalId(proposalId);
            v.setVoterKey(voterKey);
            v.setValue(value);
            v.setCreatedAt(LocalDateTime.now());
            voteMapper.insert(v);
        } else {
            // 改票：先回退旧计数
            if (existing.getValue() == 1) {
                p.setVotesUp(p.getVotesUp() - 1);
            } else {
                p.setVotesDown(p.getVotesDown() - 1);
            }
            existing.setValue(value);
            existing.setCreatedAt(LocalDateTime.now());
            voteMapper.updateById(existing);
        }

        if (value == 1) {
            p.setVotesUp(p.getVotesUp() + 1);
        } else {
            p.setVotesDown(p.getVotesDown() + 1);
        }
        proposalMapper.updateById(p);

        boolean merged = tryAutoMerge(p);
        return new VoteResult(p.getId(), p.getStatus(), p.getVotesUp(), p.getVotesDown(),
                merged, p.getMergedVersion());
    }

    /** 客观勘误自动合并的条件判断。 */
    private boolean tryAutoMerge(ContentProposalEntity p) {
        if (!TYPE_EDIT.equals(p.getType()) || !CAT_OBJECTIVE.equals(p.getCategory())) {
            return false;
        }
        if (p.getVotesUp() < p.getThreshold() || p.getVotesDown() > 0) {
            return false;
        }
        if (minAgeMinutes > 0 && p.getCreatedAt() != null) {
            long ageMin = Duration.between(p.getCreatedAt(), LocalDateTime.now()).toMinutes();
            if (ageMin < minAgeMinutes) {
                return false;
            }
        }
        Integer merged = merge(p, "auto");
        return merged != null;
    }

    // ===== 管理员处理 =====

    /**
     * 批准提案。
     *
     * IMPORT → 抓取入库（管理员审的是合规，不是事实）。
     * UPLOAD → 用贡献者自己的轨迹建线路。
     * EDIT → 合并成新版本（管理员可强制合并，忽略认票数）。
     */
    @Transactional
    public Long approve(long proposalId, String operator) {
        ContentProposalEntity p = proposalMapper.selectById(proposalId);
        if (p == null) {
            throw new IllegalArgumentException("提案不存在");
        }
        if (!ST_PENDING.equals(p.getStatus()) && !ST_CONFLICT.equals(p.getStatus())) {
            throw new IllegalStateException("提案已处理");
        }
        Long routeId;
        switch (p.getType()) {
            case TYPE_IMPORT -> {
                try {
                    routeId = importService.importFromUrl(p.getSourceUrl());
                } catch (Exception ex) {
                    throw new IllegalStateException("抓取失败：" + ex.getMessage());
                }
                p.setStatus(ST_APPROVED);
            }
            case TYPE_UPLOAD -> {
                routeId = createRouteFromUpload(p);
                p.setStatus(ST_APPROVED);
            }
            case TYPE_EDIT -> {
                Integer mergedVersion = merge(p, operator);
                if (mergedVersion == null) {
                    throw new IllegalStateException("线路版本已变化，需人工处理（冲突）");
                }
                routeId = p.getTargetRouteId();
                return routeId;
            }
            default -> throw new IllegalArgumentException("未知提案类型");
        }
        p.setRouteId(routeId);
        p.setProcessedAt(LocalDateTime.now());
        proposalMapper.updateById(p);
        return routeId;
    }

    /** 驳回提案。 */
    @Transactional
    public void reject(long proposalId, String reason) {
        ContentProposalEntity p = proposalMapper.selectById(proposalId);
        if (p == null) {
            throw new IllegalArgumentException("提案不存在");
        }
        p.setStatus(ST_REJECTED);
        p.setRejectReason(reason != null && !reason.isBlank() ? reason : "不符合收录标准");
        p.setProcessedAt(LocalDateTime.now());
        proposalMapper.updateById(p);
    }

    // ===== 合并 / 版本 / 回滚 =====

    /**
     * 把 EDIT 提案合并进线路：改字段 → 版本 +1 → 存快照。
     *
     * @return 新版本号；版本冲突（base_version 对不上）时把提案标成 CONFLICT 并返回 null
     */
    @Transactional
    public Integer merge(ContentProposalEntity p, String operator) {
        Long routeId = p.getTargetRouteId();
        if (routeId == null) {
            throw new IllegalArgumentException("提案没有目标线路");
        }
        RouteDetailEntity detail = detailMapper.selectById(routeId);
        RouteEntity route = routeMapper.selectById(routeId);
        if (detail == null || route == null) {
            throw new IllegalArgumentException("线路不存在");
        }
        int current = detail.getVersion() == null ? 1 : detail.getVersion();
        if (p.getBaseVersion() != null && p.getBaseVersion() != current) {
            p.setStatus(ST_CONFLICT);
            p.setProcessedAt(LocalDateTime.now());
            proposalMapper.updateById(p);
            log.info("proposal {} conflict: base={} current={}", p.getId(), p.getBaseVersion(), current);
            return null;
        }

        Map<String, String> fields = parseEditFields(p.getPayloadJson());
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("勘误内容为空");
        }
        for (Map.Entry<String, String> e : fields.entrySet()) {
            applyField(route, detail, e.getKey(), e.getValue());
        }

        int newVersion = current + 1;
        detail.setVersion(newVersion);
        detail.setCuratorName(blankToNull(p.getProposerName()));
        detail.setUpdatedAt(LocalDateTime.now());
        detailMapper.updateById(detail);
        routeMapper.updateById(route);

        writeSnapshot(route, detail, newVersion, p.getProposerName(), p.getReason());

        p.setStatus(ST_MERGED);
        p.setMergedVersion(newVersion);
        p.setRouteId(routeId);
        p.setProcessedAt(LocalDateTime.now());
        proposalMapper.updateById(p);

        log.info("merged proposal {} into route {} v{} by {}", p.getId(), routeId, newVersion, operator);
        return newVersion;
    }

    /** 线路版本列表（新→旧）。 */
    public List<RouteDetailRevisionEntity> revisions(long routeId) {
        return revisionMapper.selectList(new LambdaQueryWrapper<RouteDetailRevisionEntity>()
                .eq(RouteDetailRevisionEntity::getRouteId, routeId)
                .orderByDesc(RouteDetailRevisionEntity::getVersion));
    }

    /**
     * 回滚到某个历史版本：把快照写回当前线路（版本号继续 +1，保持单调）。
     */
    @Transactional
    public int rollback(long routeId, int version, String operator) {
        RouteDetailRevisionEntity rev = revisionMapper.selectOne(new QueryWrapper<RouteDetailRevisionEntity>()
                .eq("route_id", routeId)
                .eq("version", version)
                .last("limit 1"));
        if (rev == null) {
            throw new IllegalArgumentException("该版本不存在");
        }
        RouteDetailEntity detail = detailMapper.selectById(routeId);
        RouteEntity route = routeMapper.selectById(routeId);
        if (detail == null || route == null) {
            throw new IllegalArgumentException("线路不存在");
        }
        JsonNode snap = json.readTree(rev.getSnapshotJson());
        JsonNode snapRoute = snap.path("route");
        JsonNode snapDetail = snap.path("detail");
        if (snapRoute.has("name")) {
            route.setName(snapRoute.path("name").asText());
        }
        if (snapRoute.has("region")) {
            route.setRegion(snapRoute.path("region").asText());
        }
        if (snapDetail.has("intro_html")) {
            detail.setIntroHtml(snapDetail.path("intro_html").asText());
        }
        if (snapDetail.has("content_html")) {
            detail.setContentHtml(snapDetail.path("content_html").asText());
        }
        if (snapDetail.has("traffic_html")) {
            detail.setTrafficHtml(snapDetail.path("traffic_html").asText());
        }
        if (snapDetail.has("difficulty")) {
            detail.setDifficulty(snapDetail.path("difficulty").asText());
        }
        if (snapDetail.has("risk")) {
            detail.setRisk(snapDetail.path("risk").asText());
        }
        if (snapDetail.has("suitable_months")) {
            detail.setSuitableMonths(snapDetail.path("suitable_months").asText());
        }
        if (snapDetail.has("road_type")) {
            detail.setRoadType(snapDetail.path("road_type").asText());
        }
        if (snapDetail.has("view_type")) {
            detail.setViewType(snapDetail.path("view_type").asText());
        }
        if (snapDetail.has("duration_name")) {
            detail.setDurationName(snapDetail.path("duration_name").asText());
        }

        int newVersion = (detail.getVersion() == null ? 1 : detail.getVersion()) + 1;
        detail.setVersion(newVersion);
        detail.setCuratorName(operator);
        detail.setUpdatedAt(LocalDateTime.now());
        detailMapper.updateById(detail);
        routeMapper.updateById(route);
        writeSnapshot(route, detail, newVersion, operator, "回滚到 v" + version);
        return newVersion;
    }

    // ===== 查询 =====

    /** 提案列表（按状态 / 线路过滤）。 */
    public List<ContentProposalEntity> list(String status, Long routeId, int limit) {
        LambdaQueryWrapper<ContentProposalEntity> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq(ContentProposalEntity::getStatus, status.toUpperCase());
        }
        if (routeId != null) {
            qw.eq(ContentProposalEntity::getTargetRouteId, routeId);
        }
        qw.orderByDesc(ContentProposalEntity::getId)
          .last("limit " + Math.max(1, Math.min(limit, 200)));
        return proposalMapper.selectList(qw);
    }

    public ContentProposalEntity get(long id) {
        return proposalMapper.selectById(id);
    }

    /** 某条线路当前版本号。 */
    public int currentVersion(Long routeId) {
        if (routeId == null) {
            return 1;
        }
        RouteDetailEntity d = detailMapper.selectById(routeId);
        return d == null || d.getVersion() == null ? 1 : d.getVersion();
    }

    // ===== 内部实现 =====

    /** 规则层：自有轨迹上传的自动校验（不合规直接拒，不进人工队列）。 */
    private void validateUpload(String payloadJson) {
        JsonNode root = readJson(payloadJson);
        JsonNode points = root.path("points");
        if (!points.isArray() || points.size() < 10) {
            throw new IllegalArgumentException("轨迹点太少（至少 10 个点）");
        }
        if (points.size() > 20000) {
            throw new IllegalArgumentException("轨迹点过多（上限 20000）");
        }
        long lastTime = Long.MIN_VALUE;
        for (JsonNode p : points) {
            double lat = p.path("lat").asDouble(Double.NaN);
            double lng = p.path("lng").asDouble(Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                throw new IllegalArgumentException("轨迹点坐标非法");
            }
            // 本项目面向国内路线，越界坐标直接拒（也顺带挡掉坐标系写反的脏数据）
            if (lng < 73 || lng > 135 || lat < 3 || lat > 54) {
                throw new IllegalArgumentException("坐标超出国内范围，请检查坐标系");
            }
            JsonNode t = p.path("t");
            if (t.isNumber()) {
                long ts = t.asLong();
                if (ts < lastTime) {
                    throw new IllegalArgumentException("轨迹时间戳不是递增的");
                }
                lastTime = ts;
            }
        }
    }

    /** 用贡献者自己的轨迹建线路。 */
    private Long createRouteFromUpload(ContentProposalEntity p) {
        JsonNode root = readJson(p.getPayloadJson());
        String name = root.path("name").asText("");
        if (name.isBlank()) {
            throw new IllegalArgumentException("缺少线路名称");
        }
        String region = root.path("region").asText("未知");

        RouteEntity route = new RouteEntity();
        route.setName(name);
        route.setRegion(region);
        route.setDistanceKm(new BigDecimal(root.path("distanceKm").asDouble(0)).setScale(1, java.math.RoundingMode.HALF_UP));
        route.setAscentMeters(root.path("ascentMeters").asInt(0));
        route.setTags(root.path("tags").asText(""));
        route.setIsRecommend(false);
        route.setSortWeight(50);
        route.setStatus(1);
        route.setCreatedAt(LocalDateTime.now());
        routeMapper.insert(route);
        long routeId = route.getId();

        RouteDetailEntity detail = new RouteDetailEntity();
        detail.setRouteId(routeId);
        detail.setIntroHtml(root.path("intro").asText("由社区用户实地记录并贡献"));
        detail.setContentHtml(root.path("content").asText(""));
        detail.setTrafficHtml("");
        detail.setDifficulty(root.path("difficulty").asText(""));
        detail.setRisk("");
        detail.setSuitableMonths("");
        detail.setRoadType("");
        detail.setViewType("");
        detail.setDurationName(root.path("durationName").asText(""));
        detail.setSourceUrl("");
        detail.setSourceName("社区贡献");
        detail.setSourceAuthor(p.getProposerName());
        detail.setParsedAt(LocalDateTime.now());
        detail.setVersion(1);
        detail.setCuratorName(p.getProposerName());
        detail.setUpdatedAt(LocalDateTime.now());
        detailMapper.insert(detail);

        int seq = 0;
        for (JsonNode pt : root.path("points")) {
            RouteTrackPointEntity e = new RouteTrackPointEntity();
            e.setRouteId(routeId);
            e.setSeq(seq++);
            e.setLat(pt.path("lat").asDouble());
            e.setLng(pt.path("lng").asDouble());
            JsonNode alt = pt.path("alt");
            e.setAltitude(alt.isNumber() ? alt.asDouble() : null);
            JsonNode sp = pt.path("speed");
            e.setSpeed(sp.isNumber() ? sp.asDouble() : null);
            JsonNode t = pt.path("t");
            e.setTrackTime(t.isNumber() ? t.asLong() : null);
            trackPointMapper.insert(e);
        }

        writeSnapshot(route, detail, 1, p.getProposerName(), "社区贡献上传");
        log.info("created route {} from community upload proposal {}", routeId, p.getId());
        return routeId;
    }

    /** 写版本快照。 */
    private void writeSnapshot(RouteEntity route, RouteDetailEntity detail, int version,
                               String proposerName, String reason) {
        Map<String, Object> snap = new LinkedHashMap<>();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", route.getName());
        r.put("region", route.getRegion());
        r.put("distanceKm", route.getDistanceKm());
        r.put("ascentMeters", route.getAscentMeters());
        r.put("tags", route.getTags());
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("intro_html", detail.getIntroHtml());
        d.put("content_html", detail.getContentHtml());
        d.put("traffic_html", detail.getTrafficHtml());
        d.put("difficulty", detail.getDifficulty());
        d.put("risk", detail.getRisk());
        d.put("suitable_months", detail.getSuitableMonths());
        d.put("road_type", detail.getRoadType());
        d.put("view_type", detail.getViewType());
        d.put("duration_name", detail.getDurationName());
        snap.put("route", r);
        snap.put("detail", d);

        RouteDetailRevisionEntity rev = new RouteDetailRevisionEntity();
        rev.setRouteId(route.getId());
        rev.setVersion(version);
        rev.setSnapshotJson(writeJson(snap));
        rev.setProposerName(blankToNull(proposerName));
        rev.setReason(reason);
        rev.setCreatedAt(LocalDateTime.now());
        revisionMapper.insert(rev);
    }

    /** 字段白名单落地。 */
    private void applyField(RouteEntity route, RouteDetailEntity detail, String key, String value) {
        if (key.startsWith("route.")) {
            String f = key.substring("route.".length());
            if (ROUTE_FIELDS.contains(f)) {
                switch (f) {
                    case "name" -> route.setName(value);
                    case "region" -> route.setRegion(value);
                    default -> { }
                }
            }
            return;
        }
        if (!DETAIL_FIELDS.contains(key)) {
            return;
        }
        switch (key) {
            case "intro_html" -> detail.setIntroHtml(value);
            case "content_html" -> detail.setContentHtml(value);
            case "traffic_html" -> detail.setTrafficHtml(value);
            case "difficulty" -> detail.setDifficulty(value);
            case "risk" -> detail.setRisk(value);
            case "suitable_months" -> detail.setSuitableMonths(value);
            case "road_type" -> detail.setRoadType(value);
            case "view_type" -> detail.setViewType(value);
            case "duration_name" -> detail.setDurationName(value);
            default -> { }
        }
    }

    /** 解析 EDIT 的字段表：{"fields":{"difficulty":"进阶","route.name":"新名"}}。 */
    private Map<String, String> parseEditFields(String payloadJson) {
        Map<String, String> out = new LinkedHashMap<>();
        if (payloadJson == null || payloadJson.isBlank()) {
            return out;
        }
        JsonNode root = readJson(payloadJson);
        JsonNode fields = root.path("fields");
        if (!fields.isObject()) {
            return out;
        }
        for (Map.Entry<String, JsonNode> e : fields.properties()) {
            String key = e.getKey();
            boolean allowed = DETAIL_FIELDS.contains(key)
                    || (key.startsWith("route.") && ROUTE_FIELDS.contains(key.substring("route.".length())));
            if (!allowed) {
                continue;
            }
            out.put(key, e.getValue().asText(""));
        }
        return out;
    }

    /** 防刷：同一提交者每小时提交数上限。 */
    private void checkRateLimit(String proposerUuid) {
        if (rateLimitPerHour <= 0) {
            return;
        }
        long since = System.currentTimeMillis() - 3600_000L;
        Long count = proposalMapper.selectCount(new QueryWrapper<ContentProposalEntity>()
                .eq("proposer_uuid", proposerUuid)
                .ge("created_at", java.sql.Timestamp.valueOf(LocalDateTime.now().minusHours(1))));
        if (count != null && count >= rateLimitPerHour) {
            throw new IllegalStateException("提交过于频繁，请稍后再试");
        }
    }

    private JsonNode readJson(String payloadJson) {
        try {
            return json.readTree(payloadJson);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("数据格式错误");
        }
    }

    private String writeJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (RuntimeException e) {
            throw new IllegalStateException("数据序列化失败");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
