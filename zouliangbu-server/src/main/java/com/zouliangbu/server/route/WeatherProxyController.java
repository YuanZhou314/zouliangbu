package com.zouliangbu.server.route;

import com.zouliangbu.server.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 气象代理接口（天气系统第 2 项：服务端代理）。
 *
 * 为什么 App 不直连 Open-Meteo / 和风：
 *  1. **缓存**：景观预报每天只需几次刷新，服务端集中缓存省上游额度。
 *  2. **熔断**：上游抖动时服务端降级返回上次有效值，App 只感知"慢"。
 *  3. **密钥隔离**：和风 Key 只在服务端，App 二进制不携带。
 *  4. **统一字段**：Open-Meteo 给分层云量、和风给总云量，服务端归一成同一份。
 *
 * 接口：
 *   GET /api/v1/weather/forecast?lat=&lng=
 *     返回当前 + 未来 24h 逐小时的归一化气象要素（Open-Meteo 主源，和风兜底）。
 *   GET /api/v1/weather/now?lat=&lng=
 *     返回当前实况（和风优先，因它有预警/实况，Open-Meteo 兜底）。
 */
@RestController
@RequestMapping("/api/v1/weather")
public class WeatherProxyController {

    private static final Logger log = LoggerFactory.getLogger(WeatherProxyController.class);

    private final OpenMeteoClient openMeteoClient;
    private final QWeatherClient qWeatherClient;

    /** 缓存：key=lat,lng 的量化桶（0.05°），value=带时间戳的 JSON 串。 */
    private final java.util.concurrent.ConcurrentHashMap<String, CacheEntry> forecastCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, CacheEntry> nowcastCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** 缓存有效期（秒）。景观预报精度不需要分钟级，10 分钟足够。 */
    @Value("${zlb.weather.cache-ttl-sec:600}")
    private long forecastTtlSec;

    /** 实况缓存短一些（和风更新频率约 10 分钟）。 */
    @Value("${zlb.weather.nowcast-cache-ttl-sec:300}")
    private long nowcastTtlSec;

    public WeatherProxyController(OpenMeteoClient openMeteoClient, QWeatherClient qWeatherClient) {
        this.openMeteoClient = openMeteoClient;
        this.qWeatherClient = qWeatherClient;
    }

    @GetMapping("/forecast")
    public ResponseEntity<ApiResponse<String>> forecast(
            @RequestParam double lat,
            @RequestParam double lng) {
        String key = bucketKey(lat, lng);
        CacheEntry hit = forecastCache.get(key);
        if (hit != null && !hit.expired(forecastTtlSec)) {
            return ResponseEntity.ok(ApiResponse.ok(hit.json));
        }
        try {
            String json = openMeteoClient.fetchForecast(lat, lng);
            forecastCache.put(key, new CacheEntry(json, System.currentTimeMillis()));
            return ResponseEntity.ok(ApiResponse.ok(json));
        } catch (Exception e) {
            log.warn("Open-Meteo 拉取失败，尝试和风兜底：{}", e.getMessage());
            try {
                String json = qWeatherClient.fetchWeather(lat, lng);
                qWeatherClient.fillFromCache(nowcastCache, key);
                nowcastCache.put(key, new CacheEntry(json, System.currentTimeMillis()));
                forecastCache.put(key, new CacheEntry(json, System.currentTimeMillis()));
                return ResponseEntity.ok(ApiResponse.ok(json));
            } catch (Exception e2) {
                // 熔断：返回上次缓存（即使过期），App 拿到的是"陈旧但可用"
                if (hit != null) {
                    log.warn("上游全挂，返回过期缓存：{}", e2.getMessage());
                    return ResponseEntity.ok(ApiResponse.ok(hit.json));
                }
                return ApiResponse.failResponse(ApiResponse.ErrorCode.SERVER_ERROR, "气象源不可用：" + e2.getMessage());
            }
        }
    }

    @GetMapping("/now")
    public ResponseEntity<ApiResponse<String>> nowcast(
            @RequestParam double lat,
            @RequestParam double lng) {
        String key = bucketKey(lat, lng);
        CacheEntry hit = nowcastCache.get(key);
        if (hit != null && !hit.expired(nowcastTtlSec)) {
            return ResponseEntity.ok(ApiResponse.ok(hit.json));
        }
        try {
            String json = qWeatherClient.fetchWeather(lat, lng);
            nowcastCache.put(key, new CacheEntry(json, System.currentTimeMillis()));
            return ResponseEntity.ok(ApiResponse.ok(json));
        } catch (Exception e) {
            log.warn("和风实况失败，Open-Meteo 兜底：{}", e.getMessage());
            try {
                String json = openMeteoClient.fetchForecast(lat, lng);
                nowcastCache.put(key, new CacheEntry(json, System.currentTimeMillis()));
                return ResponseEntity.ok(ApiResponse.ok(json));
            } catch (Exception e2) {
                if (hit != null) {
                    return ResponseEntity.ok(ApiResponse.ok(hit.json));
                }
                return ApiResponse.failResponse(ApiResponse.ErrorCode.SERVER_ERROR, "气象源不可用：" + e2.getMessage());
            }
        }
    }

    /** 把经纬度量化到 0.05° 桶，避免相邻请求各打一次上游。 */
    private static String bucketKey(double lat, double lng) {
        return String.format(java.util.Locale.ROOT, "%.3f,%.3f",
                Math.round(lat * 20.0) / 20.0, Math.round(lng * 20.0) / 20.0);
    }

    private record CacheEntry(String json, long cachedAt) {
        boolean expired(long ttlSec) {
            return System.currentTimeMillis() - cachedAt > ttlSec * 1000L;
        }
    }
}
