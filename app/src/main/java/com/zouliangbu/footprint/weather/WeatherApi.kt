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
