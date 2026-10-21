package com.zouliangbu.server.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 六只脚客户端：抓行程详情页 + 轨迹 JSON（均无需登录）。
 *
 * 与 TwoBuluClient 产出同一个 RouteData.ParsedRoute，落库逻辑完全复用。
 */
@Component
public class SixFootClient {

    private static final Logger log = LoggerFactory.getLogger(SixFootClient.class);
    private static final String BASE_URL = "https://www.foooooot.com";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public SixFootClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * 抓取并解析一条行程。
     *
     * @param tripId    六只脚行程 id
     * @param sourceUrl 原始链接（存入 source_url）
     */
    public RouteData.ParsedRoute fetchTrip(long tripId, String sourceUrl) throws Exception {
        RouteData.ParsedRoute result = new RouteData.ParsedRoute();
        result.sourceUrl = sourceUrl;
        result.sourceTrackId = tripId;

        // 1. 详情页（服务端渲染 HTML）
        String html = fetchText(BASE_URL + "/trip/" + tripId + "/");
        SixFoot.parsePage(html, result);
        if (result.name == null || result.name.isBlank()) {
            throw new RuntimeException("六只脚返回的行程页格式异常（未取到标题）");
        }

        // 2. 轨迹 JSON
        String trackJson = fetchText(BASE_URL + "/v4/api/trip/" + tripId + "/trackjson");
        result.trackPoints = SixFoot.parseTrackJson(trackJson, mapper);

        // 起终点取自轨迹（转好 GCJ-02 的值）
        if (!result.trackPoints.isEmpty()) {
            RouteData.TrackPoint first = result.trackPoints.get(0);
            RouteData.TrackPoint last = result.trackPoints.get(result.trackPoints.size() - 1);
            result.latStart = first.lat;
            result.lngStart = first.lng;
            result.latEnd = last.lat;
            result.lngEnd = last.lng;
        }

        log.info("sixfoot parsed: tripId={}, name={}, points={}", tripId, result.name, result.trackPoints.size());
        return result;
    }

    private String fetchText(String url) throws Exception {
        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json, text/html, */*")
                        .timeout(Duration.ofSeconds(25))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return resp.body();
    }
}
