package com.zouliangbu.server.route;

/**
 * 路线来源平台识别：按提交链接的 host 路由到对应抓取器。
 * 新平台只需在此加枚举 + detect 分支 + Client 实现。
 */
public enum RoutePlatform {

    TWOBULU("两步路", "2bulu.com"),
    SIXFOOT("六只脚", "foooooot.com");

    /** 展示名（存 route_detail.source_name）。 */
    public final String displayName;
    /** 主域（含子域匹配）。 */
    public final String host;

    RoutePlatform(String displayName, String host) {
        this.displayName = displayName;
        this.host = host;
    }

    /** 从链接识别平台；不认识的域名返回 null。 */
    public static RoutePlatform detect(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String lower = url.toLowerCase();
        for (RoutePlatform p : values()) {
            if (lower.contains(p.host)) {
                return p;
            }
        }
        return null;
    }
}
