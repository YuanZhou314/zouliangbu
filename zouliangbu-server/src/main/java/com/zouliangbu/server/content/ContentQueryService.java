package com.zouliangbu.server.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zouliangbu.server.common.ApiResponse;
import com.zouliangbu.server.content.dto.ContentDtos.GearCategory;
import com.zouliangbu.server.content.dto.ContentDtos.RouteCatalog;
import com.zouliangbu.server.content.dto.ContentDtos.RouteItem;
import com.zouliangbu.server.content.dto.ContentDtos.RouteSection;
import com.zouliangbu.server.content.entity.GearCategoryEntity;
import com.zouliangbu.server.content.entity.RouteEntity;
import com.zouliangbu.server.content.entity.RouteSectionEntity;
import com.zouliangbu.server.content.mapper.GearCategoryMapper;
import com.zouliangbu.server.content.mapper.RouteMapper;
import com.zouliangbu.server.content.mapper.RouteSectionMapper;
import com.zouliangbu.server.route.RouteTrackPointEntity;
import com.zouliangbu.server.route.mapper.RouteTrackPointMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容查询服务。全部只读，无事务需求。
 *
 * 分页策略：cursor（按 id 倒序），不用 offset —— 深翻页时 offset 会退化成全表扫描。
 * 过滤策略：本期数据量 < 千行，关键词/城市过滤在内存里做；数据上量后改为 SQL LIKE + 索引。
 */
@Service
public class ContentQueryService {

    private final RouteMapper routeMapper;
    private final RouteSectionMapper routeSectionMapper;
    private final GearCategoryMapper gearCategoryMapper;
    /** 只用来取线路起点（首轨迹点），跨包复用 route 域的 mapper。 */
    private final RouteTrackPointMapper routeTrackPointMapper;

    public ContentQueryService(RouteMapper routeMapper,
                               RouteSectionMapper routeSectionMapper,
                               GearCategoryMapper gearCategoryMapper,
                               RouteTrackPointMapper routeTrackPointMapper) {
        this.routeMapper = routeMapper;
        this.routeSectionMapper = routeSectionMapper;
        this.gearCategoryMapper = gearCategoryMapper;
        this.routeTrackPointMapper = routeTrackPointMapper;
    }

    /** 线路目录：横向推荐位 + 分组；keyword 过滤名称/地区，命中后只保留非空分组。 */
    public RouteCatalog catalog(String keyword, String city) {
        String kw = keyword == null ? "" : keyword.trim();
        String region = city == null ? "" : city.trim();

        List<RouteEntity> recommendRows = routeMapper.selectList(
                new LambdaQueryWrapper<RouteEntity>()
                        .eq(RouteEntity::getStatus, 1)
                        .eq(RouteEntity::getIsRecommend, true)
                        .orderByDesc(RouteEntity::getSortWeight));

        List<RouteSectionEntity> sectionRows = routeSectionMapper.selectList(
                new LambdaQueryWrapper<RouteSectionEntity>()
                        .orderByAsc(RouteSectionEntity::getSortWeight));

        // 搜索必须覆盖"全部已上线线路"，不能只搜有分组归属的：
        // 推荐位线路的 section_id 为 NULL，若按分组过滤，搜"武功"会漏掉顶部的"武功山反穿线"。
        List<RouteEntity> allRoutes = routeMapper.selectList(
                new LambdaQueryWrapper<RouteEntity>()
                        .eq(RouteEntity::getStatus, 1)
                        .orderByDesc(RouteEntity::getSortWeight));

        List<RouteEntity> matched = allRoutes.stream()
                .filter(r -> kw.isEmpty()
                        || containsIgnoreNull(r.getName(), kw)
                        || containsIgnoreNull(r.getRegion(), kw))
                .filter(r -> region.isEmpty() || region.equals(r.getRegion()))
                .toList();

        Map<Long, List<RouteEntity>> bySection = new LinkedHashMap<>();
        List<RouteEntity> loose = new ArrayList<>();     // 无分组归属（推荐位线路）
        for (RouteEntity r : matched) {
            if (r.getSectionId() == null) {
                loose.add(r);
            } else {
                bySection.computeIfAbsent(r.getSectionId(), k -> new ArrayList<>()).add(r);
            }
        }

        // 起点坐标一次装配：只会出现在响应里的线路才查（去重），每条一次 LIMIT 1 子查询，
        // 15~30 条线路约 30 次索引查询，不做全轨迹拉取。
        java.util.LinkedHashSet<RouteEntity> catalogRoutes = new java.util.LinkedHashSet<>(matched);
        catalogRoutes.addAll(recommendRows);
        Map<Long, double[]> startById = firstTrackPoints(catalogRoutes);

        List<RouteSection> sections = new ArrayList<>(sectionRows.stream()
                .map(s -> new RouteSection(
                        String.valueOf(s.getId()),
                        s.getTitle(),
                        s.getSubtitle() == null ? "" : s.getSubtitle(),
                        bySection.getOrDefault(s.getId(), List.of()).stream()
                                .map(r -> toRouteItem(r, startById)).toList()))
                .filter(s -> !s.routes().isEmpty())
                .toList());

        // 搜索命中推荐位线路时，单独归到"搜索结果"分组，避免搜到却看不到
        if (!loose.isEmpty() && !kw.isEmpty()) {
            sections.add(0, new RouteSection(
                    "search",
                    "搜索结果",
                    "",
                    loose.stream().map(r -> toRouteItem(r, startById)).toList()));
        }

        // 兜底分组「最新收录」：section_id=NULL 且非推荐位的已上线线路（审批导入 /
        // 后台新建没勾推荐位的）原先完全不可见。固定 id="latest" 追加在末尾，
        // 组为空也返回该 section（App 端处理空组），subtitle 空串兜底（App 契约非空）。
        List<RouteItem> latestItems = loose.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsRecommend()))
                .sorted(java.util.Comparator.comparing(RouteEntity::getId,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .limit(12)
                .map(r -> toRouteItem(r, startById))
                .toList();
        sections.add(new RouteSection("latest", "最新收录", "", latestItems));

        // 搜索时就隐藏推荐位，避免"搜了关键词但顶部还是无关推荐"的割裂感
        List<RouteItem> recommendations = kw.isEmpty()
                ? recommendRows.stream().map(r -> toRouteItem(r, startById)).toList()
                : List.of();

        return new RouteCatalog(recommendations, sections);
    }

    /** 装备分类：静态数据，按 sortWeight 排序。 */
    public List<GearCategory> gearCategories() {
        return gearCategoryMapper.selectList(
                        new LambdaQueryWrapper<GearCategoryEntity>()
                                .orderByAsc(GearCategoryEntity::getSortWeight))
                .stream()
                .map(g -> new GearCategory(g.getCode(), g.getName(), g.getNote()))
                .toList();
    }

    /** 空值安全的忽略大小写包含判断（与 App 端 contains(kw, ignoreCase = true) 对齐）。 */
    private static boolean containsIgnoreNull(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword.toLowerCase());
    }

    private RouteItem toRouteItem(RouteEntity r, Map<Long, double[]> startById) {
        List<String> tags = (r.getTags() == null || r.getTags().isBlank())
                ? List.of()
                : Arrays.stream(r.getTags().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        // coverStyle：无真实封面图时，用 id 取模让客户端选择渐变色板
        int coverStyle = r.getId() == null ? 0 : (int) (r.getId() % 5);
        // 起点坐标：route_track_point 首点（GCJ-02），App 地图同为 GCJ-02 直接透传；无轨迹为 null
        double[] start = r.getId() == null ? null : startById.get(r.getId());
        return new RouteItem(
                String.valueOf(r.getId()),
                r.getName(),
                r.getRegion(),
                r.getDistanceKm() == null ? 0d : r.getDistanceKm().doubleValue(),
                r.getAscentMeters() == null ? 0 : r.getAscentMeters(),
                coverStyle,
                tags,
                r.getCoverUrl(),
                start == null ? null : start[0],
                start == null ? null : start[1]);
    }

    /**
     * 批量取「每条线路的第一个轨迹点」作为起点坐标（lat, lng）。
     * 每条一次 LIMIT 1 子查询（route_id+seq 有索引），无轨迹点的线路不出现在结果里。
     */
    private Map<Long, double[]> firstTrackPoints(Collection<RouteEntity> routes) {
        Map<Long, double[]> out = new HashMap<>();
        for (RouteEntity r : routes) {
            if (r.getId() == null || out.containsKey(r.getId())) {
                continue;
            }
            List<RouteTrackPointEntity> rows = routeTrackPointMapper.selectList(
                    new LambdaQueryWrapper<RouteTrackPointEntity>()
                            .eq(RouteTrackPointEntity::getRouteId, r.getId())
                            .orderByAsc(RouteTrackPointEntity::getSeq)
                            .orderByAsc(RouteTrackPointEntity::getId)
                            .last("LIMIT 1"));
            if (!rows.isEmpty() && rows.get(0).getLat() != null && rows.get(0).getLng() != null) {
                out.put(r.getId(), new double[]{rows.get(0).getLat(), rows.get(0).getLng()});
            }
        }
        return out;
    }
}
