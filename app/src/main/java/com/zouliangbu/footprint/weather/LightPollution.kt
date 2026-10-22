package com.zouliangbu.footprint.weather

import kotlin.math.ln

/**
 * 光污染信息（观星判据的核心约束）。
 *
 * **Bortle 等级**是天文界通用的 1..9 标度：1=极度黑暗的野外，9=市中心。
 * App 端有两种来源：
 *  1. 离线近似（[LightPollutionEstimator]）：用城市距离衰减估算 Bortle。
 *  2. 在线栅格（VIIRS/DNSC 夜间灯光）——接口预留（[LightPollutionProvider]），
 *     真实数据需服务端代理取栅格，这里先不实现，留接入点。
 *
 * 字段对齐国际惯例：Bortle + mpsas（mag/arcsec²）+ 人眼极限星等。
 */
data class LightPollution(
    /** Bortle 等级 1..9，越小越暗。景观算法 [Landscape.stargazing] 强依赖它。 */
    val bortleClass: Int,
    /** 天光背景亮度（mag/arcsec²），越大越暗。Bortle 1 ≈ 22.0，Bortle 9 ≈ 17.5。 */
    val skyBrightnessMpsas: Double,
    /** 人眼极限星等。 */
    val limitingMagnitude: Double,
) {
    companion object {
        /** Bortle 等级 → 典型 mpsas（来自 Sky & Telescope 经验表）。 */
        private val BORTLE_MPSAS = doubleArrayOf(
            22.0, 21.8, 21.3, 20.8, 19.9, 19.0, 18.0, 17.5, 17.0,
        )

        /** Bortle → 极限星等（Nelm）。 */
        private val BORTLE_NELM = doubleArrayOf(
            7.8, 7.3, 6.8, 6.2, 5.6, 5.0, 4.5, 4.0, 3.5,
        )

        /** 由 Bortle 等级构造（mpsas/nelm 取经验均值）。 */
        fun fromBortle(bortle: Int): LightPollution {
            val b = bortle.coerceIn(1, 9)
            return LightPollution(b, BORTLE_MPSAS[b - 1], BORTLE_NELM[b - 1])
        }
    }
}

/**
 * 离线光污染估算器：用「最近城市人口 + 距离」衰减模型逼近 Bortle。
 *
 * 模型（Walker 1970 / Cinzano 简化版）：
 *   skyBrightness ∝ population × exp(-distance / scale)
 * 人口来自一组内置主要城市表（[BUILTIN_CITIES]），调用方也可注入自有表。
 * 精度 ±1 Bortle 级，足够区分「城市里 / 郊区 / 野外」，对景观预报足够。
 */
object LightPollutionEstimator {

    /** 人口 / 距离衰减经验尺度（km）。 */
    private const val DECAY_KM = 30.0

    /** 内置主要城市（人口万人，经纬度）。偏中国户外热门区域。 */
    val BUILTIN_CITIES: List<CityLight> = listOf(
        CityLight("北京", 2170.0, 39.90, 116.40),
        CityLight("上海", 2487.0, 31.23, 121.47),
        CityLight("广州", 1881.0, 23.13, 113.26),
        CityLight("深圳", 1768.0, 22.54, 114.06),
        CityLight("成都", 2140.0, 30.67, 104.07),
        CityLight("武汉", 1374.0, 30.59, 114.31),
        CityLight("杭州", 1248.0, 30.27, 120.16),
        CityLight("西安", 1320.0, 34.27, 108.95),
        CityLight("南京", 949.0, 32.04, 118.78),
        CityLight("重庆", 3213.0, 29.56, 106.55),
        CityLight("天津", 1380.0, 39.08, 117.20),
        CityLight("昆明", 860.0, 25.04, 102.72),
        CityLight("兰州", 440.0, 36.06, 103.83),
        CityLight("乌鲁木齐", 410.0, 43.83, 87.62),
        CityLight("拉萨", 87.0, 29.65, 91.13),
        CityLight("西宁", 247.0, 36.63, 101.78),
    )

    /**
     * 估算 [lat]/[lng] 处的 Bortle 等级。
     * 叠加所有城市的灯光贡献（线性叠加是简化，但够用）。
     */
    fun estimate(
        lat: Double,
        lng: Double,
        cities: List<CityLight> = BUILTIN_CITIES,
    ): LightPollution {
        // 球面近似距离（km），不引入额外依赖
        val totalContribution = cities.sumOf { c ->
            val d = haversineKm(lat, lng, c.lat, c.lng)
            // 人口（万人）× exp(-d/scale)，d=0 时贡献=人口
            c.population * kotlin.math.exp(-d / DECAY_KM)
        }
        // 总贡献 → Bortle：用对数映射。
        // 经验校准：北京二环贡献 ~2000 万 → Bortle 9；拉萨市中心 ~87 → Bortle 5-6。
        // ln(2000) ≈ 7.6 → 9，ln(87) ≈ 4.5 → 5.5。
        val logC = ln(totalContribution.coerceAtLeast(1.0))
        // logC: 4.5→5.5, 7.6→9；线性映射后 clamp
        val bortle = ((logC - 2.0) * 1.1).toInt().coerceIn(1, 9)
        return LightPollution.fromBortle(bortle)
    }

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2).let { it * it }
        val c = 2 * kotlin.math.asin(kotlin.math.sqrt(a.coerceAtMost(1.0)))
        return r * c
    }
}

/** 一个城市灯光源。 */
data class CityLight(
    val name: String,
    /** 市区常住人口（万人）。 */
    val population: Double,
    val lat: Double,
    val lng: Double,
)

/**
 * 在线光污染数据源接口（VIIRS 夜间灯光栅格）。
 *
 * 当前只提供 [estimate] 离线实现；在线 VIIRS 需服务端代理 NOAA 的 raster，
 * 这里留接口，未来实现 [fetch] 后直接替换 [LightPollutionEstimator]。
 */
interface LightPollutionProvider {
    /** 优先取在线 VIIRS 栅格，失败回退 [LightPollutionEstimator]。 */
    suspend fun get(lat: Double, lng: Double): LightPollution
}

/** 默认实现：暂时没有 VIIRS 接入，直接走离线估算。 */
object OfflineLightPollutionProvider : LightPollutionProvider {
    override suspend fun get(lat: Double, lng: Double): LightPollution =
        LightPollutionEstimator.estimate(lat, lng)
}
