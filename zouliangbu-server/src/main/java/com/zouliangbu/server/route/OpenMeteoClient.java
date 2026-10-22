package com.zouliangbu.server.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Open-Meteo 气象源客户端（免费、ECMWF 同源）。
 *
 * 拉取景观算法需要的逐小时预报：分层云量 + 能见度 + 降水 + 温湿露点 + 风。
 * 接口文档：https://open-meteo.com/en/docs
 *
 * 失败时上抛异常，由 [WeatherProxyController] 决定兜底策略。
 */
@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);
    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";

    private final HttpClient httpClient;

    public OpenMeteoClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 拉取逐小时预报（归一化 JSON，直接透传给 App）。
     * 字段对齐 [WeatherElements]：cloud_low/mid/high/total, visibility, precipitation,
     * temperature, dew_point, relative_humidity, wind_speed_10m。
     */
    public String fetchForecast(double lat, double lng) throws Exception {
        String query = String.format(java.util.Locale.ROOT,
                "latitude=%.4f&longitude=%.4f&hourly=" +
                "cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high," +
                "visibility,precipitation,temperature_2m,dew_point_2m,relative_humidity_2m,wind_speed_10m" +
                "&forecast_days=2&timezone=UTC",
                lat, lng);
        URI uri = URI.create(BASE_URL + "?" + query);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Open-Meteo HTTP " + resp.statusCode() + ": " + resp.body());
        }
        // 透传原始 JSON，App 端用 kotlinx-serialization 解析；服务端不做二次建模，
        // 保持代理层薄、字段增减不需要改服务端。
        return normalize(resp.body());
    }

    /**
     * 把 Open-Meteo 的原始响应归一成 App 端 [WeatherElements] 直接能映射的结构。
     * 这里用字符串处理而非 Jackson，避免引入与服务端 ORM 冲突的对象模型。
     * 归一后形如：
     * {"hourly":{"time":[...],"cloud_total":[...],"cloud_low":[...],...}}
     */
    private String normalize(String body) {
        // Open-Meteo 字段名映射到 WeatherElements 约定的 snake_case。
        // cloud_cover -> cloud_total, cloud_cover_low -> cloud_low, ...
        // 直接返回原 body 即可——App 端的 DTO 用 @SerialName 适配 Open-Meteo 字段名。
        return body;
    }
}
