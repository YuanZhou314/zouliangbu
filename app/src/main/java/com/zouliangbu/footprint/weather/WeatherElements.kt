package com.zouliangbu.footprint.weather

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max

/**
 * 某一时刻的气象要素集合（景观算法的输入）。
 *
 * 字段命名尽量贴近 Open-Meteo 的 WMO code 体系，方便 [WeatherRepository] 直接映射。
 * 所有云量为百分数 0..100；温度单位摄氏度；湿度百分数；速度 m/s。
 *
 * 设计为不可变 data class + 可空分层云量：Open-Meteo 免费层就给 cloud_low/mid/high，
 * 而和风（QWeather）只给总云量 cloudTotal。两种源都能填充本结构，缺的分填 0。
 */
data class WeatherElements(
    /** 总云量 0..100。和风只有这个；Open-Meteo 给 cloudcover。 */
    val cloudTotal: Double,
    /** 低云量 0..100（Open-Meteo cloud_low）。 */
    val cloudLow: Double = 0.0,
    /** 中云量 0..100（Open-Meteo cloud_mid）。 */
    val cloudMid: Double = 0.0,
    /** 高云量 0..100（Open-Meteo cloud_high）。 */
    val cloudHigh: Double = 0.0,
    /** 能见度（米）。低于 8000 算差。 */
    val visibilityMeters: Double = 10000.0,
    /** 近 1 小时降水量（毫米）。 */
    val precipitationMm: Double = 0.0,
    /** 气温（℃）。 */
    val temperatureC: Double = 15.0,
    /** 露点（℃），云海判据的核心。 */
    val dewPointC: Double = 10.0,
    /** 相对湿度（%）。 */
    val relativeHumidity: Double = 60.0,
    /** 10m 风速（m/s）。 */
    val windSpeedMs: Double = 3.0,
)

/**
 * 加权云量：给某个方位角 [azDeg]（正北 0，顺时针），返回该方向扇区
 * （±45°）内的有效云量。
 *
 * 模型层会优先采用分层云量；若只有总云量，因 Open-Meteo/和风都不给
 * 分方位角云量，这里退化为「中高云占比 × 总云量」的整体估计——
 * 严格意义不区分方位，但保留了接口以便未来接 X波段雷达反射率。
 */
fun WeatherElements.weightedCloud(azDeg: Double): Double {
    val midHigh = cloudMid + cloudHigh
    // 中高云占比，clamp 到 0..100
    val ratio = (midHigh / 100.0).coerceIn(0.0, 1.0)
    // 有效加权 = 总云量 × 中高云占比 + 一部分低云底色
    val effective = cloudTotal * ratio + cloudLow * 0.3
    return effective.coerceIn(0.0, 100.0)
}
