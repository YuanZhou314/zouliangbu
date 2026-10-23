package com.zouliangbu.server.content.dto;

import java.util.List;

/**
 * 内容接口的响应 DTO。
 * 字段名必须与 App 侧 data/model 里的 model 完全一致（camelCase），
 * 这样 App 接网络时只写反序列化，不改 UI / ViewModel。
 */
public final class ContentDtos {

    private ContentDtos() {
    }

    /** 对应 App: RouteItem */
    public record RouteItem(
            String id,
            String name,
            String region,
            double distanceKm,
            int ascentMeters,
            int coverStyle,
            List<String> tags,
            String coverUrl,
            /** 起点纬度（GCJ-02，取 route_track_point 首个轨迹点）；无轨迹的线路为 null。 */
            Double startLat,
            /** 起点经度（GCJ-02）；App 地图同为 GCJ-02，直接透传。 */
            Double startLng
    ) {
    }

    /** 对应 App: RouteSection */
    public record RouteSection(String id, String title, String subtitle, List<RouteItem> routes) {
    }

    /** 对应 App: RouteCatalog */
    public record RouteCatalog(List<RouteItem> recommendations, List<RouteSection> sections) {
    }

    /** 对应 App: GearCategory（id 用 code，保证图标映射不失效） */
    public record GearCategory(String id, String name, String note) {
    }
}
