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
    /** 空气质量是 Open-Meteo 的独立端点，参数与主查询同坐标。 */
    private static final String AIR_URL = "https://air-quality-api.open-meteo.com/v1/air-quality";

    private final HttpClient httpClient;

    public OpenMeteoClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 拉取预报（归一化 JSON，直接透传给 App）。
     *
     * 逐小时字段对齐 [WeatherElements]：cloud_low/mid/high/total, visibility, precipitation,
     * temperature, dew_point, relative_humidity, wind_speed_10m；另加 weather_code 供逐小时表画天气图标。
     * daily 块承载未来 3 天逐日预报（今天 + 3 天，forecast_days=4）。
     *
     * 空气质量走独立上游：失败只省略 airHourly 字段，绝不拖垮主天气响应。
     */
    public String fetchForecast(double lat, double lng) throws Exception {
        String body = fetchMain(lat, lng);
        try {
            String air = fetchAirQuality(lat, lng);
            body = mergeAirHourly(body, air);
        } catch (Exception e) {
            log.warn("Open-Meteo 空气质量拉取失败，airHourly 省略：{}", e.getMessage());
        }
        return body;
    }

    /**
     * 上游时区固定 Asia/Shanghai（本 App 服务城市在深圳）：
     * daily 桶按本地日切，App 侧「跳过今天取未来 3 天」的语义才成立；
     * hourly/空气质量与 daily 用同一时区，三套 time 串可互相对齐。
     * App 端 WeatherRepository 按同一时区解析，若改动必须两侧同步。
     */
    private static final String QUERY_TIMEZONE = "Asia/Shanghai";

    private String fetchMain(double lat, double lng) throws Exception {
        String query = String.format(java.util.Locale.ROOT,
                "latitude=%.4f&longitude=%.4f&hourly=" +
                "cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high," +
                "visibility,precipitation,temperature_2m,dew_point_2m,relative_humidity_2m,wind_speed_10m,wind_direction_10m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min," +
                "precipitation_probability_max,wind_speed_10m_max,precipitation_sum" +
                "&forecast_days=4&timezone=" + QUERY_TIMEZONE,
                lat, lng);
        return httpGet(BASE_URL + "?" + query);
    }

    /**
     * 拉取逐小时空气质量（pm2_5 / us_aqi），forecast_days 与时区均与主查询对齐。
     * 独立方法、独立异常，由 [fetchForecast] 决定降级策略。
     */
    public String fetchAirQuality(double lat, double lng) throws Exception {
        String query = String.format(java.util.Locale.ROOT,
                "latitude=%.4f&longitude=%.4f&hourly=pm2_5,us_aqi&forecast_days=4&timezone=" + QUERY_TIMEZONE,
                lat, lng);
        return httpGet(AIR_URL + "?" + query);
    }

    private String httpGet(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Open-Meteo HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    /**
     * 把空气质量的 hourly 块并进主响应顶层 airHourly 键。
     * 只挑 time/pm2_5/us_aqi 三个数组，App 端按 time 字符串对齐主 hourly。
     * 合并任何一步失败都返回原 body，不影响主数据。
     */
    private String mergeAirHourly(String body, String air) {
        try {
            // Spring Boot 4 带的是 Jackson 3（tools.jackson），只做本地的键级合并
            tools.jackson.databind.json.JsonMapper mapper = new tools.jackson.databind.json.JsonMapper();
            tools.jackson.databind.node.ObjectNode root =
                    (tools.jackson.databind.node.ObjectNode) mapper.readTree(body);
            tools.jackson.databind.JsonNode airHourly = mapper.readTree(air).get("hourly");
            if (root == null || airHourly == null || airHourly.get("time") == null) {
                return body;
            }
            tools.jackson.databind.node.ObjectNode slim = mapper.createObjectNode();
            for (String field : new String[]{"time", "pm2_5", "us_aqi"}) {
                tools.jackson.databind.JsonNode node = airHourly.get(field);
                if (node != null) {
                    slim.set(field, node);
                }
            }
            root.set("airHourly", slim);
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("airHourly 合并失败，返回原 body：{}", e.getMessage());
            return body;
        }
    }
}
