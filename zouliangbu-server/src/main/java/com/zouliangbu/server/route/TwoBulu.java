package com.zouliangbu.server.route;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 两步路路线抓取与解析（从用户提交的链接提取路线数据）。
 *
 * 反编译两步路前端 bundle 逆向出的两个真实 API：
 *   - GET /api/proSpecial/routeDetails?routeId={id}  —— 详情 JSON（明文）
 *   - GET /api/destintion/getRoutePoints?routeId={id} —— 轨迹 JSON（单字节 XOR 0x4B）
 *
 * 坐标系：两步路存 WGS-84（原始 GPS），解析后转换为 GCJ-02（走两步 App 全链路）。
 */
public final class TwoBulu {

    private static final Pattern URL_ROUTE_ID = Pattern.compile("id=(\\d+)");
    private static final byte XOR_KEY = 0x4B;

    private TwoBulu() {
    }

    /** 从用户提交的链接里提取路线 id（如 https://www.2bulu.com/mc/route/detail?id=244439）。 */
    public static Long extractRouteId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher m = URL_ROUTE_ID.matcher(url);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    /** 解密轨迹响应（单字节 XOR 0x4B）。 */
    public static String decryptTrackResponse(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        byte[] out = new byte[body.length];
        for (int i = 0; i < body.length; i++) {
            out[i] = (byte) (body[i] ^ XOR_KEY);
        }
        return new String(out, java.nio.charset.StandardCharsets.UTF_8);
    }

    // 解析结果载体见 RouteData（两步路 / 六只脚共用），本类只保留平台特有的抓取规则。
}
