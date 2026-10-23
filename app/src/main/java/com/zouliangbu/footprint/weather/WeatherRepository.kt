package com.zouliangbu.footprint.weather

import android.util.Log
import com.zouliangbu.footprint.data.db.AppDatabase
import com.zouliangbu.footprint.data.db.WeatherCacheEntity
import com.zouliangbu.footprint.data.remote.ContentApiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

private val JSON = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * 逐小时时间序列上的一个点：整段气象要素 + 空气质量（服务端未并出 airHourly 时为 null）。
 * 逐小时多维表的数据项；与 [WeatherElements] 分开是因为多表还要时间和空气两维。
 */
data class HourlyPoint(
    /** 该小时起点的 epoch 毫秒（time 串按服务端时区 Asia/Shanghai 解析），UI 按本地时区格式化。 */
    val timeMs: Long,
    val elements: WeatherElements,
    val weatherCode: Int? = null,
    /** PM2.5 浓度（µg/m³）。 */
    val pm25: Double? = null,
    /** US AQI。 */
    val usAqi: Double? = null,
    /** 风向角（°，气象角：北=0 顺时针）；服务端未并出 wind_direction_10m 时为 null。 */
    val windDirectionDeg: Double? = null,
)

/**
 * 天气数据仓库（天气系统第 3 项）。
 *
 * 三级数据策略，与 [WeatherApi]/Room 缓存配合：
 *  1. 取服务端 /api/v1/weather/forecast（服务端再代理 Open-Meteo/和风）。
 *  2. 成功则写 Room 缓存（整块覆盖）。
 *  3. 失败则读 Room 缓存，缓存也空就抛异常让 UI 显示离线态。
 *
 * 景观算法需要的逐小时 [WeatherElements] 由 [elementsForHour] 给出：
 * 取目标时刻前后 ±1h 的两个预报点线性插值。
 */
class WeatherRepository(private val db: AppDatabase) {

    private val dao get() = db.weatherCacheDao()

    /** 缓存有效期（景观预报不需要分钟级新鲜度）。 */
    private val cacheTtlMs = TimeUnit.MINUTES.toMillis(30)

    /**
     * 取目标时刻 [dateMs] 的气象要素。
     * 先尝试网络刷新；网络失败回退缓存；缓存过期但仍可用也用。
     */
    suspend fun elementsAt(lat: Double, lng: Double, dateMs: Long): WeatherElements? =
        withContext(Dispatchers.IO) {
            forecastJson(lat, lng)?.let { parseElementsAt(it, dateMs) }
        }

    /**
     * 取整块预报 JSON（缓存新鲜直接用，否则网络刷新并回写；失败回退缓存）。
     * elementsAt / dailyForecast / hourlyPoints 共用这一条缓存链路，30min TTL 不变。
     */
    private suspend fun forecastJson(lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            val key = bucketKey(lat, lng)
            val cached = dao.get(key)
            val now = System.currentTimeMillis()
            val fresh = cached != null && now - cached.cachedAt < cacheTtlMs
            if (fresh) {
                return@withContext cached!!.forecastJson
            }
            try {
                val resp = ContentApiFactory.retrofit.create(WeatherApi::class.java)
                    .forecast(lat, lng)
                if (resp.ok && resp.data != null) {
                    dao.upsert(WeatherCacheEntity(key, lat, lng, resp.data, now))
                    resp.data
                } else {
                    cached?.forecastJson
                }
            } catch (e: Exception) {
                Log.w("WeatherRepo", "网络失败，回退缓存: ${e.message}")
                cached?.forecastJson
            }
        }

    /**
     * 取逐日预报（服务端 forecast_days=4：今天 + 未来 3 天）。
     * 调用方（WeatherViewModel）负责跳过今天；旧缓存没有 daily 块时返回空列表，UI 显示空态。
     */
    suspend fun dailyForecast(lat: Double, lng: Double): List<DailyForecast> =
        withContext(Dispatchers.IO) {
            val json = forecastJson(lat, lng) ?: return@withContext emptyList()
            parseDaily(json)
        }

    /**
     * 取 [fromMs] 起的逐小时时间序列（最多 [hours] 个整点），逐小时多维表用。
     * 与 parseElementsAt 的单点插值不同：这里按小时索引直取，不插值。
     * 空气质量按 time 串与主 hourly 对齐，服务端没并出 airHourly 时两字段为 null。
     */
    suspend fun hourlyPoints(lat: Double, lng: Double, fromMs: Long, hours: Int = 36): List<HourlyPoint> =
        withContext(Dispatchers.IO) {
            val json = forecastJson(lat, lng) ?: return@withContext emptyList()
            parseHourlyPoints(json, fromMs, hours)
        }

    /**
     * 取当前实况（和风 text / temp / humidity / windDir / windSpeed / vis）。
     * 天气页头部要展示气温湿度风力，只给一行 text 不够，所以整块返回。
     * 取不到返回 null，由调用方降级到 [elementsAt] 的预报要素。
     */
    suspend fun nowcast(lat: Double, lng: Double): QWeatherNow? =
        withContext(Dispatchers.IO) {
            try {
                val resp = ContentApiFactory.retrofit.create(WeatherApi::class.java)
                    .nowcast(lat, lng)
                if (!resp.ok || resp.data == null) return@withContext null
                JSON.decodeFromString<QWeatherBundle>(resp.data).now?.now
            } catch (e: Exception) {
                Log.w("WeatherRepo", "实况失败: ${e.message}")
                null
            }
        }

    /**
     * 只要实况文本（和风 now.text）。
     * 保留给只需要一行文案的调用方，避免它们自己再解析一遍 DTO。
     */
    suspend fun nowcastText(lat: Double, lng: Double): String? = nowcast(lat, lng)?.text

    /** 从 Open-Meteo 透传 JSON 里取目标时刻的气象要素。 */
    private fun parseElementsAt(json: String, dateMs: Long): WeatherElements? {
        return try {
            val resp = JSON.decodeFromString<OpenMeteoResponse>(json)
            val h = resp.hourly ?: return null
            if (h.time.isEmpty()) return null
            // 找最接近 dateMs 的两个点做线性插值
            val targetIso = isoFromMs(dateMs)
            val idx = h.time.indexOfFirst { it >= targetIso }.let { if (it <= 0) 0 else it - 1 }
            val t0 = h.time.getOrNull(idx) ?: return null
            val t1 = h.time.getOrNull(idx + 1) ?: t0
            val w0 = msFromIso(t0)
            val w1 = msFromIso(t1)
            val frac = if (w1 == w0) 0.0 else ((dateMs - w0).toDouble() / (w1 - w0)).coerceIn(0.0, 1.0)
            fun pickHex(get: (OpenMeteoHourly) -> List<Double?>): Double {
                val a = get(h).getOrNull(idx) ?: 0.0
                val b = get(h).getOrNull(idx + 1) ?: a
                return a + (b - a) * frac
            }
            WeatherElements(
                cloudTotal = pickHex { it.cloudCover },
                cloudLow = pickHex { it.cloudCoverLow },
                cloudMid = pickHex { it.cloudCoverMid },
                cloudHigh = pickHex { it.cloudCoverHigh },
                visibilityMeters = pickHex { it.visibility },
                precipitationMm = pickHex { it.precipitation },
                temperatureC = pickHex { it.temperature },
                dewPointC = pickHex { it.dewPoint },
                relativeHumidity = pickHex { it.relativeHumidity },
                windSpeedMs = pickHex { it.windSpeed },
            )
        } catch (e: Exception) {
            Log.w("WeatherRepo", "解析失败: ${e.message}")
            null
        }
    }

    /** 从 Open-Meteo 透传 JSON 里解析逐日预报（daily 块缺省时返回空列表）。 */
    private fun parseDaily(json: String): List<DailyForecast> {
        return try {
            val resp = JSON.decodeFromString<OpenMeteoResponse>(json)
            val d = resp.daily ?: return emptyList()
            d.time.indices.map { i ->
                DailyForecast(
                    dateMs = msFromDayIso(d.time[i]),
                    weatherCode = d.weatherCode.getOrNull(i),
                    tempMaxC = d.tempMax.getOrNull(i),
                    tempMinC = d.tempMin.getOrNull(i),
                    precipProbMax = d.precipProbMax.getOrNull(i),
                    precipSumMm = d.precipSum.getOrNull(i),
                    windSpeedMaxMs = d.windSpeedMax.getOrNull(i),
                )
            }
        } catch (e: Exception) {
            Log.w("WeatherRepo", "逐日解析失败: ${e.message}")
            emptyList()
        }
    }

    /** 从 Open-Meteo 透传 JSON 里解析 [fromMs] 起的逐小时序列（含空气质量对齐）。 */
    private fun parseHourlyPoints(json: String, fromMs: Long, hours: Int): List<HourlyPoint> {
        return try {
            val resp = JSON.decodeFromString<OpenMeteoResponse>(json)
            val h = resp.hourly ?: return emptyList()
            if (h.time.isEmpty()) return emptyList()
            // 空气质量数组与主 hourly 的 time 串是两套独立数组，按 time 字符串对齐
            val airByTime = resp.airHourly?.let { air ->
                air.time.indices.associate { idx ->
                    air.time[idx] to (air.pm25.getOrNull(idx) to air.usAqi.getOrNull(idx))
                }
            } ?: emptyMap()
            val targetIso = isoFromMs(fromMs)
            var start = h.time.indexOfFirst { it >= targetIso }
            if (start < 0) start = 0 // 目标时刻早于预报起点：从头开始给
            val end = minOf(h.time.size, start + hours)
            (start until end).map { i ->
                val airPair = airByTime[h.time[i]]
                HourlyPoint(
                    timeMs = msFromIso(h.time[i]),
                    elements = WeatherElements(
                        cloudTotal = h.cloudCover.getOrNull(i) ?: 0.0,
                        cloudLow = h.cloudCoverLow.getOrNull(i) ?: 0.0,
                        cloudMid = h.cloudCoverMid.getOrNull(i) ?: 0.0,
                        cloudHigh = h.cloudCoverHigh.getOrNull(i) ?: 0.0,
                        // 缺省取 WeatherElements 的中性默认值，避免表格里出现 0km
                        visibilityMeters = h.visibility.getOrNull(i) ?: 10000.0,
                        precipitationMm = h.precipitation.getOrNull(i) ?: 0.0,
                        temperatureC = h.temperature.getOrNull(i) ?: 0.0,
                        dewPointC = h.dewPoint.getOrNull(i) ?: 0.0,
                        relativeHumidity = h.relativeHumidity.getOrNull(i) ?: 0.0,
                        windSpeedMs = h.windSpeed.getOrNull(i) ?: 0.0,
                    ),
                    weatherCode = h.weatherCode.getOrNull(i),
                    pm25 = airPair?.first,
                    usAqi = airPair?.second,
                    windDirectionDeg = h.windDirection.getOrNull(i),
                )
            }
        } catch (e: Exception) {
            Log.w("WeatherRepo", "逐小时序列解析失败: ${e.message}")
            emptyList()
        }
    }

    private fun bucketKey(lat: Double, lng: Double): String =
        String.format(java.util.Locale.ROOT, "%.3f,%.3f",
            Math.round(lat * 20.0) / 20.0, Math.round(lng * 20.0) / 20.0)

    /**
     * Open-Meteo 返回的 time 串无时区后缀，服务端请求时固定 `timezone=Asia/Shanghai`
     * （见服务端 OpenMeteoClient.QUERY_TIMEZONE），所以这里的「墙钟」都是上海时区。
     * 解析、匹配、换算必须与服务端同区，错一处 hourly/daily 就整体偏 8 小时。
     */
    private val queryZone: java.time.ZoneId = java.time.ZoneId.of("Asia/Shanghai")

    /** 取目标时刻在「服务器时区墙钟」下的 yyyy-MM-ddTHH，用于在 hourly.time 里定位。 */
    private fun isoFromMs(ms: Long): String {
        val zdt = java.time.Instant.ofEpochMilli(ms).atZone(queryZone)
        return "%04d-%02d-%02dT%02d".format(zdt.year, zdt.monthValue, zdt.dayOfMonth, zdt.hour)
    }

    /** Open-Meteo hourly time 形如 2026-10-22T14:00（无时区，Asia/Shanghai 墙钟语义）。 */
    private fun msFromIso(iso: String): Long =
        java.time.LocalDateTime.parse(iso, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
            .atZone(queryZone).toInstant().toEpochMilli()

    /** Open-Meteo daily time 形如 2026-10-24（当日 Asia/Shanghai 0 点；本地日切，App 侧 drop(1) 语义成立）。 */
    private fun msFromDayIso(iso: String): Long =
        java.time.LocalDate.parse(iso, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            .atStartOfDay(queryZone).toInstant().toEpochMilli()
}