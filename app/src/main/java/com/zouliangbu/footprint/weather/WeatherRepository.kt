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
            val key = bucketKey(lat, lng)
            val cached = dao.get(key)
            val now = System.currentTimeMillis()
            // 缓存新鲜则直接用，省一次网络
            val fresh = cached != null && now - cached.cachedAt < cacheTtlMs
            val json = if (fresh) {
                cached!!.forecastJson
            } else {
                try {
                    val resp = ContentApiFactory.retrofit.create(WeatherApi::class.java)
                        .forecast(lat, lng)
                    if (resp.ok && resp.data != null) {
                        dao.upsert(WeatherCacheEntity(key, lat, lng, resp.data, now))
                        resp.data
                    } else {
                        cached?.forecastJson ?: return@withContext null
                    }
                } catch (e: Exception) {
                    Log.w("WeatherRepo", "网络失败，回退缓存: ${e.message}")
                    cached?.forecastJson ?: return@withContext null
                }
            }
            parseElementsAt(json, dateMs)
        }

    /**
     * 取当前实况文本（和风 now.text），用于 UI 顶部天气条。
     * 没有和风数据时返回 null，UI 隐藏该条。
     */
    suspend fun nowcastText(lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val resp = ContentApiFactory.retrofit.create(WeatherApi::class.java)
                    .nowcast(lat, lng)
                if (!resp.ok || resp.data == null) return@withContext null
                val bundle = JSON.decodeFromString<QWeatherBundle>(resp.data)
                bundle.now?.now?.text
            } catch (e: Exception) {
                Log.w("WeatherRepo", "实况失败: ${e.message}")
                null
            }
        }

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

    private fun bucketKey(lat: Double, lng: Double): String =
        String.format(java.util.Locale.ROOT, "%.3f,%.3f",
            Math.round(lat * 20.0) / 20.0, Math.round(lng * 20.0) / 20.0)

    private fun isoFromMs(ms: Long): String {
        val instant = java.time.Instant.ofEpochMilli(ms)
        return instant.toString().substring(0, 13) // yyyy-MM-ddTHH
    }

    /** Open-Meteo hourly time 形如 2026-10-22T14:00（无时区，UTC 语义）。 */
    private fun msFromIso(iso: String): Long =
        java.time.LocalDateTime.parse(iso, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
            .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
}