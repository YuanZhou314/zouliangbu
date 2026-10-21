package com.zouliangbu.server.route;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 六只脚（foooooot.com）行程抓取与解析。
 *
 * 两个无需登录的数据源（2026-10 实测 100142397 验证）：
 *   - GET /trip/{id}/                 —— 服务端渲染的行程详情页（标题/作者/地区/数字/正文）
 *   - GET /v4/api/trip/{id}/trackjson —— 轨迹 JSON：[[[ts, lat, lng, alt, speed, cumdist_km], ...]]
 *                                        多段行程为数组套数组，坐标 WGS-84，cumdist 单位 km。
 *
 * 页面结构锚点（均为服务端渲染，可直接正则）：
 *   标题   <h1 class="title">...</h1>
 *   作者   <h2><a title=".." href="/userindex/{uid}/">..</a></h2>
 *   地区   "江苏 苏州 苏州市区&nbsp;-&nbsp;..." 的出发地 dd
 *   距离   "全程&nbsp;11.6&nbsp;公里"
 *   难度   <strong>难度级别：</strong>简单
 *   爬升   <strong>累计上升</strong>：647米，<strong>累计下降：</strong>642米
 *   海拔   <span class="low">8</span>米 / <span class="height">119</span>米
 *   用时   "历时&nbsp;6 hours, 54 minutes"
 *   正文   <div class="trip_box_description fix_720">...</div>（脚印图片直接剥掉）
 */
public final class SixFoot {

    private static final Pattern URL_TRIP_ID = Pattern.compile("/trip/(\\d+)");
    private static final Pattern P_TITLE = Pattern.compile("<h1[^>]*class=\"title\"[^>]*>(.*?)</h1>", Pattern.DOTALL);
    private static final Pattern P_AUTHOR = Pattern.compile("<h2>\\s*<a[^>]*href=\"/userindex/\\d+/\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern P_REGION = Pattern.compile("<dd>\\s*([^<>\\n]+?)&nbsp;-&nbsp;");
    private static final Pattern P_DISTANCE = Pattern.compile("全程&nbsp;([\\d.]+)&nbsp;公里");
    private static final Pattern P_DIFFICULTY = Pattern.compile("难度级别：</strong>([^<\\s]+)");
    private static final Pattern P_CLIMB = Pattern.compile("累计上升</strong>：([\\d.]+)米，<strong>累计下降：</strong>([\\d.]+)米");
    private static final Pattern P_ALT = Pattern.compile("<span class=\"low\">(-?[\\d.]+)</span>米.*?<span class=\"height\">(-?[\\d.]+)</span>米", Pattern.DOTALL);
    private static final Pattern P_DURATION = Pattern.compile("历时&nbsp;([\\d]+) hours?, ([\\d]+) minutes");
    private static final Pattern P_DESCRIPTION = Pattern.compile("<div class=\"trip_box_description[^\"]*\">(.*?)</div>", Pattern.DOTALL);
    private static final Pattern P_IMG = Pattern.compile("<img[^>]*>", Pattern.DOTALL);
    private static final Pattern P_TAG = Pattern.compile("<[^>]+>");

    private SixFoot() {
    }

    /** 从链接提取行程 id（支持 /trip/100142397/ 与 /mobile/trip/100142397/）。 */
    public static Long extractTripId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher m = URL_TRIP_ID.matcher(url);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    /**
     * 解析 trackjson。结构 [[段1点...], [段2点...]]，点为 [ts, lat, lng, alt, speed, cumdist_km]。
     * 输出坐标转 GCJ-02；时间戳为秒。
     */
    public static List<RouteData.TrackPoint> parseTrackJson(String json, tools.jackson.databind.ObjectMapper mapper) {
        List<RouteData.TrackPoint> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            tools.jackson.databind.JsonNode root = mapper.readTree(json);
            if (!root.isArray() || root.isEmpty()) {
                return out;
            }
            for (tools.jackson.databind.JsonNode seg : root) {
                if (!seg.isArray()) {
                    continue;
                }
                for (tools.jackson.databind.JsonNode p : seg) {
                    if (!p.isArray() || p.size() < 3) {
                        continue;
                    }
                    RouteData.TrackPoint tp = new RouteData.TrackPoint();
                    tp.time = p.get(0).asLong(0);
                    double wlat = p.get(1).asDouble(0);
                    double wlng = p.get(2).asDouble(0);
                    double[] gcj = CoordTransform.wgs84ToGcj02(wlat, wlng);
                    tp.lat = gcj[0];
                    tp.lng = gcj[1];
                    tp.altitude = p.size() > 3 ? p.get(3).asDouble(0) : 0;
                    tp.speed = p.size() > 4 ? p.get(4).asDouble(0) : 0;
                    out.add(tp);
                }
            }
        } catch (Exception ignored) {
            // 解析失败返回已解析部分，由调用方按空轨迹兜底
        }
        return out;
    }

    /** 解析行程详情页 HTML，填充 ParsedRoute（轨迹除外，用 parseTrackJson 单独处理）。 */
    public static void parsePage(String html, RouteData.ParsedRoute out) {
        out.name = stripTags(firstGroup(P_TITLE, html));
        out.sourceAuthor = stripTags(firstGroup(P_AUTHOR, html));
        out.region = normalizeRegion(firstGroup(P_REGION, html));
        out.difficulty = firstGroup(P_DIFFICULTY, html);
        out.contentHtml = stripFootprintImages(firstGroup(P_DESCRIPTION, html));
        out.introHtml = "";
        out.trafficHtml = "";
        out.suitableMonths = "";
        out.risk = "";
        out.roadType = "";
        out.viewType = "";

        String dist = firstGroup(P_DISTANCE, html);
        if (!dist.isEmpty()) {
            out.totalMileageM = Double.parseDouble(dist) * 1000.0;
        }
        Matcher climb = P_CLIMB.matcher(html);
        if (climb.find()) {
            out.cumulativeClimbM = Double.parseDouble(climb.group(1));
            out.cumulativeDeclineM = Double.parseDouble(climb.group(2));
        }
        Matcher alt = P_ALT.matcher(html);
        if (alt.find()) {
            out.maxAltitudeM = Double.parseDouble(alt.group(2));
        }
        Matcher dur = P_DURATION.matcher(html);
        if (dur.find()) {
            out.durationHours = Integer.parseInt(dur.group(1)) + Integer.parseInt(dur.group(2)) / 60.0;
        }
    }

    /** 地区规范：取出发地最后一段，去掉"市区/地区/城区/区/县"尾巴（如"江苏 苏州 苏州市区"→"苏州"、"迪庆 德钦县"→"德钦"）。 */
    static String normalizeRegion(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.trim().split("\\s+");
        String last = parts[parts.length - 1];
        last = last.replaceAll("(市区|地区|城区|区|县)$", "");
        return last.isEmpty() ? raw.trim() : last;
    }

    /** 剥掉脚印图片标签，只留文字与换行结构。 */
    static String stripFootprintImages(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return html.replaceAll(P_IMG.pattern(), "").trim();
    }

    private static String firstGroup(Pattern p, String html) {
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : "";
    }

    private static String stripTags(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return P_TAG.matcher(s).replaceAll("").trim();
    }
}
