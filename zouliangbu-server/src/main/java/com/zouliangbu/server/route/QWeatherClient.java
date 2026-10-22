package com.zouliangbu.server.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * 和风天气（QWeather）客户端。和风 Key 仅在服务端，App 不持有。
 *
 * 认证说明：和风 2024 起对新项目默认 JWT 认证（Ed25519 签名），
 * 老项目的 API Key query 参数方式仍在但分主机。若本 Key 返回 403 Invalid Host，
 * 需在和风控制台确认项目类型并用对应主机（devapi / api）。
 */
@Component
public class QWeatherClient {

    private static final Logger log = LoggerFactory.getLogger(QWeatherClient.class);

    @Value("${zlb.weather.qweather-host:https://devapi.qweather.com/v7}")
    private String baseUrl;

    @Value("${zlb.weather.qweather-key:}")
    private String apiKey;

    private final HttpClient httpClient;

    public QWeatherClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String fetchWeather(double lat, double lng) throws Exception {
        if (!available()) {
            throw new RuntimeException("和风 Key 未配置");
        }
        String loc = String.format(java.util.Locale.ROOT, "%.2f,%.2f", lng, lat);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"location\":\"").append(loc).append("\"");
        try {
            String now = get(baseUrl + "/weather/now?location=" + loc + "&key=" + apiKey);
            sb.append(",\"now\":").append(now);
        } catch (Exception e) {
            log.warn("和风实况失败：{}", e.getMessage());
            sb.append(",\"now\":null");
        }
        try {
            String h = get(baseUrl + "/weather/24h?location=" + loc + "&key=" + apiKey);
            sb.append(",\"hourly\":").append(h);
        } catch (Exception e) {
            log.warn("和风 24h 失败：{}", e.getMessage());
            sb.append(",\"hourly\":null");
        }
        try {
            String a = get(baseUrl + "/alarm/now?location=" + loc + "&key=" + apiKey);
            sb.append(",\"alarm\":").append(a);
        } catch (Exception e) {
            sb.append(",\"alarm\":null");
        }
        sb.append("}");
        return sb.toString();
    }

    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("和风 HTTP " + resp.statusCode());
        }
        return decodeBody(resp);
    }

    private String decodeBody(HttpResponse<byte[]> resp) throws IOException {
        String enc = resp.headers().firstValue("Content-Encoding").orElse("");
        byte[] body = resp.body();
        if (enc.contains("gzip")) {
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(body))) {
                body = gz.readAllBytes();
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    public void fillFromCache(ConcurrentHashMap<String, ?> cache, String key) { }
}
