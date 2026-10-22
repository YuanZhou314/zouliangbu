package com.zouliangbu.footprint.weather

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 景观预测（朝霞 / 晚霞 / 云海 / 观星）。**纯本地算，输入是 [Astronomy] + [WeatherElements]。
 *
 * 判定依据（与「莉景天气」作者公开的原理一致）：霞光的关键不是「云多不多」，
 * 而是**日落时刻太阳所在方位角的上游云况**——只有当太阳沉到云层之下、
 * 而云底仍被照亮时，才会出现火烧云。所以本实现按「方位角扇区加权云量」建模，
 * 而不是简单看总云量。
 *
 * 概率刻意**不给百分比**，只给三档定性（较差/ 一般 / 较好）：
 * 景观预报天然会翻车，伪精确的数字比模糊更伤信任（莉景自己也被用户吐槽过概率不准）。
 */
object Landscape {

    /** 定性档位。UI 直接映射文案与配色。 */
    enum class Rating(val score: Int) {
        POOR(0), FAIR(1), GOOD(2);

        companion object {
            /** 按 0..100 的原始分转档。阈值取自经验值，宁可保守不夸大。 */
            fun of(raw: Int): Rating = when {
                raw >= 62 -> GOOD
                raw >= 38 -> FAIR
                else -> POOR
            }
        }
    }

    /** 一条景观预测的完整结果。 */
    data class Forecast(
        val rating: Rating,
        val score: Int,
        /** 主导原因，给 UI 显示一行提示（中文）。 */
        val reason: String,
        /** 最佳时段（分钟，UTC 零点起），无则 null。 */
        val bestMinute: Double?,
    )

    /**
     * 晚霞 / 朝霞通用判定。
     *
     * @param sunrise true=朝霞（黎明前），false=晚霞（日落前）
     * @param elems该时刻前后的小���气象要素；为空表示无网络数据，退化为纯天文判断
     */
    fun twilightGlow(
        sunrise: Boolean,
        lat: Double,
        lng: Double,
        dateMs: Long,
        elems: WeatherElements?,
    ): Forecast {
        val st = Astronomy.sunTimes(dateMs, lat, lng)
        val core = if (sunrise) st.sunrise else st.sunset
        if (core == null) {
            return Forecast(Rating.POOR, 0, "极昼或极夜，此日无日出日落", null)
        }

        // 太阳沉到地平线以下的「黄金时刻」：霞光在这段时间最盛
        val golden = if (sunrise) st.dawn else st.goldenHourEnd
        val bestMin = golden ?: core

        // 上游扇区加权云量：朝霞看东方（太阳升起的方向），晚霞看西方
        val sunAzDeg = if (sunrise) 90.0 else 270.0
        val upstream = elems?.weightedCloud(sunAzDeg) ?: return pureAstronomyForecast(sunrise, bestMin)

        // 判据1：上游必须有中高云（云底受光的前提）
        val midHigh = elems!!.cloudMid + elems.cloudHigh
        val hasMidHigh = midHigh >= 25

        // 判据2：低云不能太多（会挡住中高云的受光面）
        val lowOk = elems.cloudLow <= 70

        // 判据3：能见度要够（朝霞靠低层大气散射）
        val visOk = elems.visibilityMeters >= 8000

        // 判据4：不宜降水（雨幕会彻底遮蔽）
        val noRain = elems.precipitationMm <= 0.5

        var score = 0
        val reasons = ArrayList<String>()

        if (hasMidHigh) {
            score += 30
            reasons += "上游有中高云（云底可被照亮）"
        } else {
            reasons += "上游缺中高云（$midHigh%）"
        }
        if (upstream in 15.0..85.0) {
            score += 25
            reasons += "上游云量适中"
        } else if (upstream > 85.0) {
            score += 5
            reasons += "上游云量过厚"
        }
        if (lowOk) {
            score += 15
        } else {
            reasons += "低云偏厚"
        }
        if (visOk) {
            score += 15
        } else {
            reasons += "能见度偏低"
        }
        if (noRain) {
            score += 15
        } else {
            reasons += "有降水"
        }

        val clamped = score.coerceIn(0, 100)
        val rating = Rating.of(clamped)
        return Forecast(rating, clamped, reasons.firstOrNull() ?: "条件一般", bestMin)
    }

    /** 无气象数据时的纯天文判断：只有「太阳确实会落下 + 黄金时刻存在」这一层。 */
    private fun pureAstronomyForecast(sunrise: Boolean, bestMin: Double): Forecast {
        // 无云量信息时，理论上任何云都可能形成霞光；给中性分，并在 UI 标注「未取到云量」
        return Forecast(
            Rating.FAIR,
            45,
            "天文条件可用（${if (sunrise) "朝霞" else "晚霞"}时段已定），但未取到云量",
            bestMin,
        )
    }

    /**
     * 云海判定（**近似版**）。
     *
     * ⚠️ 诚实声明：真正的云海判定需要「云底高度」，而ECMWF 主预报 API **不提供**
     * 该变量（只有区域性模型如 ICON-CH 才有）。所以这里用温湿风做近似：
     * 山顶海拔 + 近地面接近饱和 + 低云适量 + 风小 + 出现在日出日落前后。
     * UI 上必须标注「基于温湿风估算」，不要宣称是云底高度判定。
     */
    fun cloudSea(
        lat: Double,
        lng: Double,
        dateMs: Long,
        summitElevationM: Double,
        elems: WeatherElements?,
    ): Forecast {
        // 低海拔基本没有云海，直接排除，省得给无意义的分数
        if (summitElevationM < 800) {
            return Forecast(Rating.POOR, 0, "海拔过低（${summitElevationM.toInt()}m），难形成云海", null)
        }
        if (elems == null) {
            return Forecast(Rating.FAIR, 40, "未取到气象数据，云海无法判断", null)
        }

        val st = Astronomy.sunTimes(dateMs, lat, lng)
        val reasons = ArrayList<String>()
        var score = 0

        // 条件1：接近饱和（露点差小）——水汽充足是云海的燃料
        val dewGap = elems.temperatureC - elems.dewPointC
        if (dewGap <= 1.5) {
            score += 35
            reasons += "近地面接近饱和（露点差 ${"%.1f".format(dewGap)}℃）"
        } else if (dewGap <= 4.0) {
            score += 20
            reasons += "湿度偏高（露点差 ${"%.1f".format(dewGap)}℃）"
        } else {
            reasons += "偏干（露点差 ${"%.1f".format(dewGap)}℃）"
        }

        // 条件2：相对湿度
        if (elems.relativeHumidity >= 85) {
            score += 25
        } else if (elems.relativeHumidity >= 70) {
            score += 12
        }

        // 条件3：低云量要「有但不厚」——太少没有云海，太多就是阴天
        if (elems.cloudLow in 40.0..90.0) {
            score += 20
            reasons += "低云量适中（${elems.cloudLow.toInt()}%）"
        } else if (elems.cloudLow > 90) {
            score += 5
            reasons += "低云过厚，多为阴天"
        }

        // 条件4：风小（风大云海被吹散）
        if (elems.windSpeedMs <= 5.0) {
            score += 15
        } else if (elems.windSpeedMs <= 9.0) {
            score += 7
            reasons += "风力偏大（${"%.1f".format(elems.windSpeedMs)} m/s）"
        } else {
            reasons += "风大，云海易散"
        }

        // 条件5：出现在晨昏时段最佳（正好是景观需求叠加的地方）
        val dawnMin = st.dawn
        val duskMin = st.goldenHourEnd
        if (dawnMin != null || duskMin != null) {
            score += 5
        }

        val clamped = score.coerceIn(0, 100)
        return Forecast(Rating.of(clamped), clamped, reasons.firstOrNull() ?: "条件一般", dawnMin)
    }

    /**
     * 观星 / 银河条件。
     *
     * 判据：天文暮光结束（太阳足够低）+ 月光干扰小 + 云少 + 光污染低。
     * 其中**光污染是最强约束**——满月下的暗夜基本看不到银河。
     */
    fun stargazing(
        dateMs: Long,
        lat: Double,
        lng: Double,
        elems: WeatherElements?,
        lightPollution: LightPollution,
    ): Forecast {
        val sun = Astronomy.sunPosition(dateMs, lat, lng)
        val moon = Astronomy.moonPosition(dateMs, lat, lng)
        val galaxy = Astronomy.galacticCenter(dateMs, lat, lng)

        val reasons = ArrayList<String>()
        var score = 0

        // 条件1：太阳必须足够低（天文暮光结束）
        if (sun.altitude < -18) {
            score += 30
            reasons += "天文暮光已结束（太阳 ${sun.altitude.toInt()}°）"
        } else if (sun.altitude < -12) {
            score += 12
            reasons += "天文暮光未结束，银河偏淡"
        } else {
            return Forecast(Rating.POOR, score, "还是白天/太亮，看不到银河", null)
        }

        // 条件2：月亮是最大干扰源
        val moonUp = moon.altitude > 0
        val moonPenalty = when {
            moon.fraction < 0.15 -> 0.0            // 新月：几乎无干扰
            moon.fraction > 0.85 && moonUp -> 30.0// 满月且在天上：最差
            moonUp -> 12.0                        // 白天/蛾眉月：一般
            else -> 3.0                           // 月亮在地平线下
        }
        score -= moonPenalty.toInt()
        reasons += if (moon.fraction < 0.15) "新月无月光干扰"
        else "月亮干扰（被照亮 ${(moon.fraction * 100).toInt()}%，高度 ${moon.altitude.toInt()}°）"

        // 条件3：云量
        val cloud = elems?.cloudTotal
        if (cloud != null) {
            if (cloud <= 20) {
                score += 25
                reasons += "少云（${cloud.toInt()}%）"
            } else if (cloud <= 45) {
                score += 12
                reasons += "云量一般（${cloud.toInt()}%）"
            } else {
                reasons += "云多（${cloud.toInt()}%）"
            }
        } else {
            score += 10
            reasons += "未取到云量"
        }

        // 条件4：光污染（强约束）
        score += when (lightPollution.bortleClass) {
            1 -> 20; 2 -> 18; 3 -> 14; 4 -> 8; 5 -> 3
            6 -> 0; 7 -> -4
            else -> -14
        }
        if (lightPollution.bortleClass <= 3) {
            reasons += "光污染低（Bortle ${lightPollution.bortleClass}）"
        }

        // 条件5：银心在地平线之上才看得到
        val galaxyVisible = galaxy.altitude > 10
        if (galaxyVisible) {
            score += 15
        }

        val clamped = score.coerceIn(0, 100)
        return Forecast(Rating.of(clamped), clamped, reasons.firstOrNull() ?: "条件一般", null)
    }

    /** 银河拱桥大致方位：与太阳方位相对（银心与太阳赤经相差约 180°）。 */
    fun galaxyAzimuth(dateMs: Long, lat: Double, lng: Double): Double =
        Astronomy.galacticCenter(dateMs, lat, lng).azimuth
}