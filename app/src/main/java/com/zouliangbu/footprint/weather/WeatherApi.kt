package com.zouliangbu.footprint.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 服务端气象代理的 Retrofit 接口（天气系统第 3 项：App 数据层）。
 *
 * App **不直连** Open-Meteo / 和风，统一走服务端 /api/v1/weather/ 接口，
 * 理由见服务端 [com.zouliangbu.server.route.WeatherProxyController]。
 * 网络失败时由 [WeatherRepository] 回退 Room 缓存。
 */
interface WeatherApi {
    /** 逐小时预报（Open-Meteo 主源，归一化 JSON 透传）。 */
    @retrofit2.http.GET("api/v1/weather/forecast")
    suspend fun forecast(
        @retrofit2.http.Query("lat") lat: Double,
        @retrofit2.http.Query("lng") lng: Double,
    ): com.zouliangbu.footprint.data.remote.ApiResponse<String>

    /** 当前实况（和风优先）。 */
    @retrofit2.http.GET("api/v1/weather/now")
    suspend fun nowcast(
        @retrofit2.http.Query("lat") lat: Double,
        @retrofit2.http.Query("lng") lng: Double,
    ): com.zouliangbu.footprint.data.remote.ApiResponse<String>
}

// ===== Open-Meteo 透传 DTO（forecast 接口返回的原始 JSON 解析） =====

@Serializable
data class OpenMeteoResponse(
    val hourly: OpenMeteoHourly? = null,
    /** 逐日预报（服务端 forecast_days=4：今天 + 未来 3 天；旧缓存可能没有）。 */
    val daily: OpenMeteoDaily? = null,
    /** 逐小时空气质量（服务端并入，上游失败时缺省）。 */
    @SerialName("airHourly") val airHourly: OpenMeteoAirHourly? = null,
)

@Serializable
data class OpenMeteoHourly(
    val time: List<String> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Double?> = emptyList(),
    @SerialName("cloud_cover_low") val cloudCoverLow: List<Double?> = emptyList(),
    @SerialName("cloud_cover_mid") val cloudCoverMid: List<Double?> = emptyList(),
    @SerialName("cloud_cover_high") val cloudCoverHigh: List<Double?> = emptyList(),
    val visibility: List<Double?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("dew_point_2m") val dewPoint: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m") val relativeHumidity: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    /** 风向（°，气象角：北=0 顺时针）。服务端 hourly 参数尚未请求该字段，旧缓存缺省为空列表。 */
    @SerialName("wind_direction_10m") val windDirection: List<Double?> = emptyList(),
    /** WMO 天气码（0 晴 / 1-3 少云 / 3 阴 / 45-48 雾 / 51-67,80-82 雨 / 71-77,85-86 雪 / 95-99 雷暴）。 */
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
)

@Serializable
data class OpenMeteoDaily(
    /** 日期串，形如 2026-10-24（Open-Meteo daily.time 语义，UTC 0 点）。 */
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val tempMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val tempMin: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipProbMax: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windSpeedMax: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipSum: List<Double?> = emptyList(),
)

@Serializable
data class OpenMeteoAirHourly(
    val time: List<String> = emptyList(),
    @SerialName("pm2_5") val pm25: List<Double?> = emptyList(),
    @SerialName("us_aqi") val usAqi: List<Double?> = emptyList(),
)

/** 解析后的单日预报（未来 3 天区块的数据项）。 */
data class DailyForecast(
    /** 当日 00:00（服务端时区 Asia/Shanghai）的 epoch 毫秒；本地日切，App 侧「跳过今天」语义成立。 */
    val dateMs: Long,
    val weatherCode: Int?,
    val tempMaxC: Double?,
    val tempMinC: Double?,
    /** 当日最大降水概率 0..100。 */
    val precipProbMax: Double?,
    /** 当日累计降水量 mm。 */
    val precipSumMm: Double?,
    /** 当日最大风速 m/s。 */
    val windSpeedMaxMs: Double?,
)

// ===== 和风实况 DTO（nowcast 接口包装） =====

@Serializable
data class QWeatherBundle(
    val location: String? = null,
    val now: QWeatherNowWrap? = null,
)

@Serializable
data class QWeatherNowWrap(
    val code: String? = null,
    val now: QWeatherNow? = null,
)

@Serializable
data class QWeatherNow(
    val text: String? = null,
    val temp: String? = null,
    val humidity: String? = null,
    val windDir: String? = null,
    val windSpeed: String? = null,
    val vis: String? = null,
)
