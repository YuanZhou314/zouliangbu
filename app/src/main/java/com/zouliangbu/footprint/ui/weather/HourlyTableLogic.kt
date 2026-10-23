package com.zouliangbu.footprint.ui.weather

import java.time.LocalDate

/**
 * 逐小时多维表的纯逻辑函数（不依赖 Compose / Android，可 JVM 单测）。
 * 图标映射等需要 Compose 类型的函数放 [HourlyTableBlock.kt]，不在此处。
 */

/** 空气质量无数据时的等级哨兵值。 */
internal const val AQI_GRADE_MISSING = -1

/**
 * 空气质量等级（按国标六档）：0 优 / 1 良 / 2 轻度 / 3 中度 / 4 重度 / 5 严重，
 * 无数据返回 [AQI_GRADE_MISSING]。US AQI 直接分段；只有 PM2.5 浓度时
 * 先按国标 24h 浓度限值折算成 AQI 再分段（与「优绿/良黄/…」着色对齐）。
 */
internal fun aqiGrade(usAqi: Double?, pm25: Double?): Int {
    val v = usAqi ?: pm25?.let { estimateAqiFromPm25(it) } ?: return AQI_GRADE_MISSING
    return when {
        v <= 50.0 -> 0
        v <= 100.0 -> 1
        v <= 150.0 -> 2
        v <= 200.0 -> 3
        v <= 300.0 -> 4
        else -> 5
    }
}

/**
 * 国标 PM2.5 24h 浓度限值（µg/m³）→ AQI 分级上下限，段内线性插值：
 * 浓度 0/35/75/115/150/250/350/500 ↔ AQI 0/50/100/150/200/300/400/500。
 */
internal fun estimateAqiFromPm25(pm25: Double): Double {
    val c = pm25.coerceIn(0.0, 500.0)
    val bounds = doubleArrayOf(0.0, 35.0, 75.0, 115.0, 150.0, 250.0, 350.0, 500.0)
    val aqi = doubleArrayOf(0.0, 50.0, 100.0, 150.0, 200.0, 300.0, 400.0, 500.0)
    for (i in 1 until bounds.size) {
        if (c <= bounds[i]) {
            val span = bounds[i] - bounds[i - 1]
            if (span <= 0.0) return aqi[i]
            return aqi[i - 1] + (c - bounds[i - 1]) / span * (aqi[i] - aqi[i - 1])
        }
    }
    return 500.0
}

/** 夜间时段（18 点~次日 6 点）天气图标行用月亮系图标。 */
internal fun isNightHour(hourOfDay: Int): Boolean = hourOfDay >= 18 || hourOfDay < 6

/** WMO 天气码是否为雪（71-77 / 85-86），雨雪行的强调色按它区分蓝/白。 */
internal fun isSnowCode(code: Int?): Boolean = code != null && (code in 71..77 || code == 85 || code == 86)

/** 逐小时序列里每个「新的一天」的起始下标（日期行分段显示用）；空表返回空表。 */
internal fun dayBreakIndices(dates: List<LocalDate>): List<Int> {
    if (dates.isEmpty()) return emptyList()
    val out = mutableListOf(0)
    for (i in 1 until dates.size) {
        if (dates[i] != dates[i - 1]) out.add(i)
    }
    return out
}
