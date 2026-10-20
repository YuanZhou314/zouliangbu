package com.zouliangbu.footprint.util

import com.zouliangbu.footprint.data.db.TrackPoint

/**
 * 海拔与爬升计算（ROADMAP P3.1 / P3.3）。
 *
 * 两个问题：
 *
 * 1. **GPS 海拔噪声**：手机 GPS 的高程是卫星解算出来的，弱信号下能抖 ±30 m。
 *    直接累加相邻点的海拔差，平地走一圈能算出几百米「爬升」——户外 App 最容易翻车的指标。
 *
 * 2. **爬升阈值**：即使滤波后仍有小幅波动。业界通行做法是设一个阈值，
 *    累计上升超过阈值才计入爬升，回落后抵消一部分下降。
 *
 * 算法（两步，顺序不能反）：
 *   1. **中值滤波**（去尖刺）：对每个点的海拔取邻域中位数，窗口半径 [FILTER_RADIUS]。
 *   2. **阈值累加**（去抖动）：维护一个「参考高度」，上升超过 [ASCENT_THRESHOLD_M] 才计入爬升，
 *      参考高度同步抬高；下降超过阈值才计入下降，参考高度同步降低。
 *
 * 阈值取 3 m：小于 3 m 的起伏是噪声，大于 3 m 认为是真实坡度。
 * 注意阈值直接决定结果（3 m vs 5 m 可能差 100+ m），改这里要同步改 CLAUDE.md。
 */
object Elevation {

    /** 中值滤波窗口半径（点数）。半径 3 → 取 7 个点的中位数。 */
    const val FILTER_RADIUS = 3

    /**
     * 爬升/下降阈值（米）。小于该值的海拔变化视为噪声。
     *
     * 3 m 是业界常见取值（Strava / Garmin 量级）。改成 1 m 会虚高，改成 10 m 会偏低。
     */
    const val ASCENT_THRESHOLD_M = 3.0

    /** 爬升/下降结果（米）。 */
    data class Ascent(
        val ascentMeters: Double,
        val descentMeters: Double
    ) {
        companion object {
            val ZERO = Ascent(0.0, 0.0)
        }
    }

    /**
     * 对原始海拔序列做中值滤波。
     *
     * @param altitudes 原始海拔序列（米），按时间升序
     * @return 滤波后的等长序列
     */
    fun medianFilter(altitudes: List<Double>, radius: Int = FILTER_RADIUS): List<Double> {
        if (altitudes.isEmpty()) return altitudes
        if (altitudes.size == 1 || radius <= 0) return altitudes
        return altitudes.mapIndexed { index, _ ->
            val from = (index - radius).coerceAtLeast(0)
            val to = (index + radius).coerceAtMost(altitudes.size - 1)
            val window = altitudes.subList(from, to + 1).sorted()
            window[window.size / 2]
        }
    }

    /**
     * 从原始海拔序列算爬升/下降。
     *
     * @param altitudes 原始海拔序列（米）
     * @param thresholdM 阈值（米）
     */
    fun ascentOf(altitudes: List<Double>, thresholdM: Double = ASCENT_THRESHOLD_M): Ascent {
        if (altitudes.size < 2) return Ascent.ZERO
        val filtered = medianFilter(altitudes)
        return ascentOfFiltered(filtered, thresholdM)
    }

    /**
     * 对**已滤波**的海拔序列算爬升/下降（阈值累加）。
     *
     * 参考高度法：以第一个点为起点，维护 reference。
     *   - 当前高度比 reference 高出 threshold → 累加爬升，reference 抬到当前高度
     *   - 当前高度比 reference 低出 threshold → 累加下降，reference 降到当前高度
     *   - 中间地带不动（维持 reference），这样小幅波动不会累积成假爬升
     */
    fun ascentOfFiltered(filtered: List<Double>, thresholdM: Double = ASCENT_THRESHOLD_M): Ascent {
        if (filtered.size < 2) return Ascent.ZERO
        var ascent = 0.0
        var descent = 0.0
        var reference = filtered.first()

        for (i in 1 until filtered.size) {
            val h = filtered[i]
            val diff = h - reference
            when {
                diff > thresholdM -> {
                    ascent += diff
                    reference = h
                }
                diff < -thresholdM -> {
                    descent += -diff
                    reference = h
                }
                else -> Unit // 噪声带内：不动 reference
            }
        }
        return Ascent(ascent, descent)
    }

    /**
     * 便捷方法：直接从轨迹点算爬升/下降。
     *
     * @param points 轨迹点（按时间升序）
     */
    fun ascentOf(points: List<TrackPoint>): Ascent {
        if (points.size < 2) return Ascent.ZERO
        // 精度太差的海拔（accuracy > 60 m）直接剔除：这种点的海拔基本是瞎猜的。
        // 剔除后如果点太少就返回 0，而不是拿噪声算出一个假数字。
        val usable = points.filter { it.accuracy <= 60f }
        if (usable.size < 2) return Ascent.ZERO
        return ascentOf(usable.map { it.altitude })
    }
}
