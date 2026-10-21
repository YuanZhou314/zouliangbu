package com.zouliangbu.server.route;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 两步路 API 客户端（抓取路线详情 + 轨迹）。
 *
 * 依赖 Jackson（Spring Boot 自带）做 JSON 解析，HTTP 用 JDK 17 内置的 HttpClient（无需额外依赖）。
 * 网络失败抛 IOException / InterruptedException，由调用方捕获处理。
 */
@Component
public class TwoBuluClient {

    private static final Logger log = LoggerFactory.getLogger(TwoBuluClient.class);
    private static final String BASE_URL = "https://www.2bulu.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8)";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public TwoBuluClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * 抓取并解析一条路线（详情 + 轨迹）。
     *
     * @param routeId 两步路路线 id
     * @param sourceUrl 原始链接（存入 source_url）
     */
    public RouteData.ParsedRoute fetchRoute(long routeId, String sourceUrl) throws Exception {
        RouteData.ParsedRoute result = new RouteData.ParsedRoute();
        result.sourceUrl = sourceUrl;

        // 1. 详情
        JsonNode detail = fetchJson(BASE_URL + "/api/proSpecial/routeDetails?routeId=" + routeId);
        parseDetail(detail, result);

        // 2. 轨迹（XOR 加密）
        byte[] trackBody = fetchRaw(BASE_URL + "/api/destintion/getRoutePoints?routeId=" + routeId);
        String trackJson = TwoBulu.decryptTrackResponse(trackBody);
        parseTrack(trackJson, result);

        return result;
    }

    // ===== HTTP =====

    private JsonNode fetchJson(String url) throws Exception {
        String body = fetchText(url);
        return mapper.readTree(body);
    }

    private byte[] fetchRaw(String url) throws Exception {
        HttpResponse<byte[]> resp = httpClient.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "*/*")
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        return resp.body();
    }

    private String fetchText(String url) throws Exception {
        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json, text/html, */*")
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        return resp.body();
    }

    // ===== 解析 =====

    private void parseDetail(JsonNode root, RouteData.ParsedRoute out) {
        JsonNode d = root.path("data").path("details");
        if (d.isMissingNode() || d.isNull()) {
            throw new RuntimeException("两步路返回的详情格式异常");
        }
        out.name = text(d, "name");
        out.region = extractRegion(out.name, text(d, "cityId"));
        out.totalMileageM = d.path("totalMileage").asDouble(0);
        out.maxAltitudeM = d.path("maxAltitude").asDouble(0);
        out.cumulativeClimbM = d.path("cumulativeClimb").asDouble(0);
        out.cumulativeDeclineM = d.path("cumulativeDecline").asDouble(0);
        out.durationHours = text(d, "timeUsed").isEmpty() ? 0
                : d.path("timeUsed").asDouble(0) / 3600.0;
        out.difficulty = text(d, "difficultyName");
        out.risk = text(d, "riskName");
        out.suitableMonths = formatMonths(text(d, "suitableMonth"));
        out.roadType = text(d, "roadTypeName");
        out.viewType = text(d, "viewTypeName");
        out.introHtml = text(d, "intro");
        out.contentHtml = text(d, "content");
        out.trafficHtml = text(d, "traffic");
        out.sourceAuthor = text(d, "nickName");
        out.sourceTrackId = d.path("trackId").asLong(0);

        // 起终点坐标（WGS-84 → GCJ-02）
        double slat = d.path("latStart").asDouble(0);
        double slng = d.path("lonStart").asDouble(0);
        double[] gcjStart = CoordTransform.wgs84ToGcj02(slat, slng);
        out.latStart = gcjStart[0];
        out.lngStart = gcjStart[1];

        double elat = d.path("latEnd").asDouble(0);
        double elng = d.path("lonEnd").asDouble(0);
        double[] gcjEnd = CoordTransform.wgs84ToGcj02(elat, elng);
        out.latEnd = gcjEnd[0];
        out.lngEnd = gcjEnd[1];
    }

    private void parseTrack(String trackJson, RouteData.ParsedRoute out) {
        if (trackJson == null || trackJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(trackJson);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() == 0) {
                return;
            }
            JsonNode track = data.get(0);

            // 轨迹点（WGS-84 → GCJ-02）
            JsonNode positions = track.path("positions");
            if (positions.isArray() && positions.size() > 0) {
                JsonNode pts = positions.get(0);
                if (pts.isArray()) {
                    for (int i = 0; i < pts.size(); i++) {
                        JsonNode p = pts.get(i);
                        RouteData.TrackPoint tp = new RouteData.TrackPoint();
                        double wlat = p.path("latitude").asDouble(0);
                        double wlng = p.path("longitude").asDouble(0);
                        double[] gcj = CoordTransform.wgs84ToGcj02(wlat, wlng);
                        tp.lat = gcj[0];
                        tp.lng = gcj[1];
                        tp.altitude = p.path("altitude").asDouble(0);
                        tp.speed = p.path("speed").asDouble(0);
                        tp.time = p.path("time").asLong(0);
                        out.trackPoints.add(tp);
                    }
                }
            }

            // 标注点（WGS-84 → GCJ-02）
            JsonNode marks = track.path("marks");
            if (marks.isArray()) {
                for (int i = 0; i < marks.size(); i++) {
                    JsonNode m = marks.get(i);
                    RouteData.Marker mk = new RouteData.Marker();
                    mk.name = m.has("name") && !m.get("name").isNull() ? m.get("name").asText() : null;
                    double wlat = m.path("latitude").asDouble(0);
                    double wlng = m.path("longitude").asDouble(0);
                    double[] gcj = CoordTransform.wgs84ToGcj02(wlat, wlng);
                    mk.lat = gcj[0];
                    mk.lng = gcj[1];
                    mk.altitude = m.path("altitude").asDouble(0);
                    mk.time = m.path("time").asLong(0);
                    out.markers.add(mk);
                }
            }
        } catch (Exception e) {
            log.warn("parse track failed: {}", e.getMessage());
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText();
    }

    private String extractRegion(String name, String cityId) {
        if (name != null && name.length() >= 2) {
            // 取名字前 8 字再按常见后缀截断："深圳户外毕业线三水线…" → "深圳"
            String head = name.substring(0, Math.min(8, name.length()));
            for (String suffix : new String[]{"户外", "徒步", "穿越", "线", "山", "古道", "村"}) {
                int idx = head.indexOf(suffix);
                if (idx >= 2) {
                    return head.substring(0, idx);
                }
            }
            return head.substring(0, Math.min(3, head.length()));
        }
        return cityId.isEmpty() ? "" : cityId;
    }

    private String formatMonths(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.split(",");
        if (parts.length == 0) {
            return raw;
        }
        List<String> ranges = new ArrayList<>();
        int start = Integer.parseInt(parts[0].trim());
        int prev = start;
        for (int i = 1; i < parts.length; i++) {
            int cur = Integer.parseInt(parts[i].trim());
            if (cur != prev + 1) {
                ranges.add(start == prev ? String.valueOf(start) : start + "~" + prev);
                start = cur;
            }
            prev = cur;
        }
        ranges.add(start == prev ? String.valueOf(start) : start + "~" + prev);
        return String.join(",", ranges);
    }
}
