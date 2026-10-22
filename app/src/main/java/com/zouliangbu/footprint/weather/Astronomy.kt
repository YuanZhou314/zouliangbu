package com.zouliangbu.footprint.weather

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * 天文计算（日 / 月 / 银河）。**纯本地、零网络、零依赖。**
 *
 * 这是天气系统的 L1 层：断网时唯一完整可用的部分，也是"朝霞晚霞"和"观星"
 * 两类判定的骨架。按产品判据第 3 条（断网还剩什么），它必须能独立工作。
 *
 * 算法来自 Jean Meeus《Astronomical Algorithms》，与 SunCalc（BSD-2）同源同约定，
 * 精度参照 USNO / timeanddate.com。移植要点：
 *   - 内部一律用「UTC 天的分数」表示时刻，避免跨时区时日出日落被算偏一天；
 *   - 对外角度一律「度」，只在三角函数内部转弧度；
 *   - 输入经纬度约定 **WGS-84**（调用方负责 GCJ-02 转换，见 WeatherApi）。
 */
object Astronomy {

    private const val RAD = Math.PI / 180.0
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private const val OBLIQUITY = 23.4397 * RAD

    /** 当天各类天文时刻，单位「距当天 UTC 零点的分钟数」；当天不发生者为 null。 */
    class SunTimes(
        val sunrise: Double?,
        val sunset: Double?,
        val dawn: Double?,          // 民用暮光始（太阳 -6°）
        val dusk: Double?,          // 民用暮光终
        val nauticalDawn: Double?,  // 航海暮光始（-12°）
        val nauticalDusk: Double?,
        val nightEnd: Double?,      // 天文暮光终（-18°）= 可观星开始
        val nightStart: Double?,    // 天文暮光始
        val goldenHourEnd: Double?,
        val goldenHour: Double?
    )

    data class SunPosition(val altitude: Double, val azimuth: Double)

    data class MoonPosition(
        val altitude: Double,
        val azimuth: Double,
        val phase: Double,       // 0=新月, 0.5=满月
        val fraction: Double     // 被照亮比例 0..1
    )

    data class MoonTimes(
        val moonrise: Double?,
        val moonset: Double?
    )

    // ===== 儒略日 =====

    /** 儒略日（含时刻，不取整）。所有「某一时刻」的计算都必须用它。 */
    private fun julian(dateMs: Long): Double = dateMs / 86400000.0 + J1970

    /** 儒略日的整数天（当天 UTC 零点）。只用它算「当天有哪些时刻」。 */
    private fun days(dateMs: Long): Double = floor(julian(dateMs))

    /**
     * 自 J2000（2000-01-01 12:00 TT）起的天数，供恒星时使用。
     *
     * 注意：儒略日 J2000 基准 = 2451545.0 指的是**中午**，而 [julian] 的原点是
     * Unix 纪元（1970-01-01 00:00），两者差半个���。直接 `julian - 2451545`
     * 会让恒星时整体偏移 12 小时——表现为正午出现在午夜。
     */
    private fun daysSinceJ2000(dateMs: Long): Double = julian(dateMs) - J2000 - 0.5

    private fun centuries(d: Double): Double = (d - J2000) / 36525.0

    private fun geomMeanLongSun(t: Double) = (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0

    private fun geomMeanAnomalySun(t: Double) = 357.52911 + t * (35999.05029 - 0.0001537 * t)

    private fun eccentricity(t: Double) = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    private fun sunEqOfCenter(t: Double): Double {
        val m = geomMeanAnomalySun(t) * RAD
        return sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(2 * m) * (0.019993 - 0.000101 * t) +
                sin(3 * m) * 0.000289
    }

    private fun meanObliquity(t: Double) =
        23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0

    private fun obliquity(t: Double) = meanObliquity(t) + 0.00256 * cos((125.04 - 1934.136 * t) * RAD)

    /** 太阳视黄经（度）。 */
    private fun sunApparentLong(t: Double): Double {
        val trueLong = geomMeanLongSun(t) + sunEqOfCenter(t)
        val omega = 125.04 - 1934.136 * t
        return trueLong - 0.00569 - 0.00478 * sin(omega * RAD)
    }

    private fun equationOfTime(t: Double): Double {
        val e = eccentricity(t)
        val m = geomMeanAnomalySun(t) * RAD
        val l0 = geomMeanLongSun(t) * RAD
        val y = tan(obliquity(t) * RAD / 2.0)
        val y2 = y * y
        val eot = y * sin(2 * l0) -
                2 * e * sin(m) +
                4 * e * y * sin(m) * cos(2 * l0) -
                0.5 * y2 * sin(4 * l0) -
                1.25 * e * e * sin(2 * m)
        return eot * 4 * 180.0 / Math.PI
    }

    /** 太阳视赤经/赤纬（度）。 */
    private fun sunRaDec(t: Double): DoubleArray {
        val lw = sunApparentLong(t) * RAD
        val eps = obliquity(t) * RAD
        val x = cos(eps) * cos(lw)
        val y = cos(eps) * sin(lw)
        val z = sin(eps) * sin(lw)
        val r = kotlin.math.sqrt(x * x + y * y + z * z)
        val ra = (atan2(y, x) / RAD + 360.0) % 360.0
        val dec = asin(z / r) / RAD
        return doubleArrayOf(ra, dec)
    }

    // ===== 时间基准统一约定 =====
    // 经度统一以「度」传入，月角统一以「度」处理；只有进入三角函数时才转弧度。
    // lw = -lng（度），表示西经为正的本地经度，与 SunCalc 一致。

    /**
     * 格林尼治平恒星时（度）。
     * @param d 自J2000 起的天数（[julian] − J2000）
     * @param lng 东经为正的经度（度）
     *
     * 关键：本地恒星时 = GMST **加** 东经。早期写成加 lw(=−lng) 会让整个时角
     * 反号，日出日落���能量对、正午高度角却全错——已由太阳位置单测守住。
     */
    private fun siderealTime(dSinceJ2000: Double, lng: Double): Double =
        (280.46061837 + 360.98564736629 * dSinceJ2000 + lng) % 360.0

    private fun altitudeOf(dec: Double, hourAngleDeg: Double, lat: Double): Double {
        val hr = hourAngleDeg * RAD
        return asin(sin(lat * RAD) * sin(dec * RAD) + cos(lat * RAD) * cos(dec * RAD) * cos(hr)) / RAD
    }

    /**
     * 方位角（度，0=正北，顺时针）。必须传入**实际时角**，不能用经度反推——
     * 早期版本传的是 lw，结果方位角全天恒定在 335° 左右。
     */
    private fun azimuthOf(hourAngleDeg: Double, dec: Double, lat: Double): Double {
        val h = hourAngleDeg * RAD
        val phi = lat * RAD
        val decR = dec * RAD
        return (atan2(
            sin(h),
            cos(h) * sin(phi) - tan(decR) * cos(phi)
        ) / RAD + 540.0) % 360.0
    }

    /** 太阳位置。 */
    fun sunPosition(dateMs: Long, lat: Double, lng: Double): SunPosition {
        val d = julian(dateMs)
        val rad = sunRaDec(centuries(d))
        val lw = -lng
        val st = siderealTime(daysSinceJ2000(dateMs), lng)
        val h = st - rad[0]
        return SunPosition(altitudeOf(rad[1], h, lat), azimuthOf(h, rad[1], lat))
    }

    /**
     * 太阳视正午的分数儒略日。[lng] 以度为单位（东经为正）。
     *
     * 推导：本地真太阳时12:00 对应的 UTC 时刻 = 12:00 − 经度时差 − 时差，
     * 换成天 = 0.5 − lng/360 − EoT/1440。
     * 注意是**减**lng：东经区的正午在 UTC 上更早。
     */
    private fun solarMeanJnoon(d: Double, lng: Double): Double =
        d + 0.5 - lng / 360.0 - equationOfTime(centuries(d)) / 1440.0

    /** 把「距某 UTC 零点的分钟数」归一到 [0,1440)。日出可能算出负数，需绕回当天。 */
    private fun normMin(m: Double): Double = ((m % 1440.0) + 1440.0) % 1440.0

    /**
     * 太阳达到高度角 [hDeg]（度）的分数儒略日。
     * @param dir 1=西落（儒略日更大），-1=东升
     */
    private fun getSetJ(
        ra: Double, dec: Double, lng: Double, lat: Double,
        hDeg: Double, dir: Int, jnoon: Double
    ): Double? {
        val cosH = (sin(hDeg * RAD) - sin(lat * RAD) * sin(dec * RAD)) /
                (cos(lat * RAD) * cos(dec * RAD))
        if (cosH < -1.0 || cosH > 1.0) return null // 极昼/极夜：该高度角当天不发生
        val a = acos(cosH) / RAD / 360.0
        return jnoon + a * dir
    }
    /** 某天的日月与黄金时刻（分钟，UTC 零点起）。 */
    fun sunTimes(dateMs: Long, lat: Double, lng: Double): SunTimes {
        val d = days(dateMs)
        val t = centuries(d)
        val rad = sunRaDec(t)
        val ra = rad[0]
        val dec = rad[1]
        val jnoon = solarMeanJnoon(d, lng)

        fun mins(j: Double?): Double? = j?.let { normMin((it - d) * 1440.0) }

        /**
         * 求「东升 / 西落」这一对时刻。
         * @param hDeg 目标高度角（度）
         * @param dir 1=只解西落，-1=只解东升
         * @return (东升分钟, 西落分钟)，当天不发生者为 null
         *
         * 注意：东升与西落是**方向**决定的，不是「分钟数大小」——
         * 北京夏至日落 UTC 11:46、日出 UTC 20:46，日出的分钟数反而更大。
         * 所以只解一个方向，另一个用对称关系 jrise = 2*jnoon − jset 得到。
         */
        fun pair(hDeg: Double): Pair<Double?, Double?> {
            val set = getSetJ(ra, dec, lng, lat, hDeg, 1, jnoon)
            if (set == null) return null to null
            val rise = 2 * jnoon - set
            return mins(rise) to mins(set)
        }
        val (jrise, jset) = pair(-0.833)
        val (jdawn, jdusk) = pair(-6.0)
        val (jnDawn, jnDusk) = pair(-12.0)
        val (jnightEnd, jnightStart) = pair(-18.0)
        val (jgolden, jgoldenEnd) = pair(6.0)
        return SunTimes(
            sunrise = jrise,
            sunset = jset,
            dawn = jdawn,
            dusk = jdusk,
            nauticalDawn = jnDawn,
            nauticalDusk = jnDusk,
            nightEnd = jnightEnd,
            nightStart = jnightStart,
            goldenHour = jgolden,
            goldenHourEnd = jgoldenEnd
        )
    }

    // ===== 月亮 =====

    private fun geomMeanLongMoon(t: Double) = (218.316 + 481267.8813 * t) % 360.0

    private fun moonPerigee(t: Double): Double =
        Math.toDegrees(atan2(
            0.99996627 * sin(0.55365 + 132.87 * t * RAD - 2.532 * t * t * RAD * RAD),
            0.00216541 * sin(0.55365 + 132.87 * t * RAD - 2.532 * t * t * RAD * RAD)
        ))

    /**
     * 月亮黄经/黄纬/地心距（**度, 度, km**）。
     * 黄经不取模：481267.8813*t 项很大，做差时能自动抵消，取模反而引入跳变。
     */
    private fun moonCoords(t: Double): DoubleArray {
        val l = 218.316 + 481267.8813 * t + 6.289 * sin((134.963 + 477198.8676 * t) * RAD)
        val m = 357.529 + 35999.050 * t
        val f = 93.272 + 483202.018 * t
        return doubleArrayOf(
            l + 6.289 * sin(m * RAD),
            5.128 * sin(f * RAD),
            385001.0 - 20905.0 * cos(m * RAD),
        )
    }

    /** 太阳黄经/黄纬/地心距（**度, 度, km**）。 */
    private fun sunCoords(t: Double): DoubleArray {
        val l = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val g = (357.52911 + 35999.05029 * t - 0.0001537 * t * t) * RAD
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(g) +
                (0.019993 - 0.000101 * t) * sin(2 * g) +
                0.000289 * sin(3 * g)
        return doubleArrayOf(l + c, 0.0, 149598000.0)
    }

    /** 由黄经/黄纬（度）求赤经/赤纬（度）。[t] 用于取黄赤交角。 */
    private fun eclipticToRaDec(l: Double, b: Double, t: Double): DoubleArray {
        val e = obliquity(t) * RAD
        val lr = l * RAD
        val br = b * RAD
        val ra = (atan2(sin(lr) * cos(e) - tan(br) * sin(e), cos(lr)) / RAD + 360.0) % 360.0
        val dec = asin(sin(br) * cos(e) + cos(br) * sin(e) * sin(lr)) / RAD
        return doubleArrayOf(ra, dec)
    }

    /** 月亮位置与月相。phase: 0=新月, 0.5=满月；fraction: 被照亮比例 0..1。 */
    fun moonPosition(dateMs: Long, lat: Double, lng: Double): MoonPosition {
        val d = julian(dateMs)
        val t = centuries(d)
        val mc = moonCoords(t)
        val sc = sunCoords(t)
        val m = eclipticToRaDec(mc[0], mc[1], t)
        val s = eclipticToRaDec(sc[0], sc[1], t)
        val st = siderealTime(daysSinceJ2000(dateMs), lng)
        val h = st - m[0]

        // 相角：日–地–月夹角（球面余弦定理）。
        // sdist 是日地距离，单位必须和 moonCoords 的地心距一致（km），
        // 否则 atan2 的分子分母量级差 1000 倍，inc 恒≈0，月相永远是满月。
        val sdist = 149598000.0
        val phi = acos(
            sin(s[1] * RAD) * sin(m[1] * RAD) +
                cos(s[1] * RAD) * cos(m[1] * RAD) * cos((s[0] - m[0]) * RAD)
        )
        val inc = atan2(sdist * sin(phi), mc[2] - sdist * cos(phi))
        val fraction = (1 + cos(inc)) / 2.0

        // 月相 =（月黄经 − 太阳黄经）/ 360°，mod 1
        val phase = ((mc[0] - sc[0]) / 360.0 % 1.0 + 1.0) % 1.0

        return MoonPosition(
            altitudeOf(m[1], h, lat),
            azimuthOf(h, m[1], lat),
            phase,
            fraction.coerceIn(0.0, 1.0),
        )
    }

    /** 月出月落（分钟，UTC 零点起）。以「月亮过中天」为基准，与太阳解耦。 */
    fun moonTimes(dateMs: Long, lat: Double, lng: Double): MoonTimes {
        val d = days(dateMs)
        val dec = eclipticToRaDec(moonCoords(centuries(d))[0], moonCoords(centuries(d))[1], centuries(d))[1]

        // 地平线以下 0.125°（蒙气差 + 月面半径修正）
        val cosH = (sin(-0.125 * RAD) - sin(lat * RAD) * sin(dec * RAD)) /
                (cos(lat * RAD) * cos(dec * RAD))
        if (cosH < -1.0 || cosH > 1.0) return MoonTimes(null, null) // 极昼/极夜永不出没
        val a = acos(cosH) / RAD / 360.0

        // 月亮过中天：时角为 0，即 siderealTime == 月亮赤经。不动点迭代三次即收敛。
        var transitJd = d - J2000 - 0.5 + 0.5
        repeat(3) {
            val t = centuries(julian(dateMs) + (transitJd - (d - J2000 - 0.5)))
            val ra = eclipticToRaDec(moonCoords(t)[0], moonCoords(t)[1], t)[0]
            val st = siderealTime(transitJd, lng)
            // 赤经每差 1° 需 1/360.9856 天；归一到 ±180° 避免跨天跳变
            transitJd += ((st - ra + 540.0) % 360.0 - 180.0) / 360.98564736629
        }
        val transit = transitJd + J2000 + 0.5

        return MoonTimes(
            moonrise = normMin((transit - a - d) * 1440.0),
            moonset = normMin((transit + a - d) * 1440.0),
        )
    }

    /**
     * 银河中心（人马座 A*）位置。赤经 266.4°、赤纬 -28.94°（J2000）。
     */
    fun galacticCenter(dateMs: Long, lat: Double, lng: Double): SunPosition {
        val d = julian(dateMs)
        val lw = -lng
        val st = siderealTime(daysSinceJ2000(dateMs), lng)
        val ra = 266.4
        val dec = -28.94
        val h = st - ra
        return SunPosition(altitudeOf(dec, h, lat), azimuthOf(h, dec, lat))
    }
}
