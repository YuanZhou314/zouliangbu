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

    /** 对应 App: FeedItem */
    public record FeedItem(
            String id,
            String title,
            String author,
            int likeCount,
            String city,
            String coverUrl,
            float coverAspectRatio
    ) {
    }

    /** 对应 App: 一页动态 */
    public record FeedPage(List<FeedItem> items, String nextCursor) {
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
            String coverUrl
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
