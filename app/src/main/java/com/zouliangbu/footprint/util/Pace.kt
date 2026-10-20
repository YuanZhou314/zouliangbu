package com.zouliangbu.footprint.util

import com.zouliangbu.footprint.data.db.TrackPoint

/**
 * 配速 / 速度统计（ROADMAP P3.2 / P3.5）。
 *
 * 两个坑：
 *
 * 1. **瞬时速度会炸**：GPS 精度 5 m 时，相邻两点算出的瞬时速度可能是 0 km/h 也可能是 30 km/h。
 *    直接对所有点求平均，会被停车时的 0 和漂移时的 30 拉飞。
 *    做法：先做 **30 秒滑动窗口**平滑，再对平滑后的速度求平均。
 *
 * 2. **停走要剔除**：用户停下来休息 10 分钟，这段速度是 0，
 *    算「平均速度」时必须排除，否则平均速度会被严重拉低（这是配速 App 的经典 bug）。
 *    判据：窗口内位移小于 [MIN_WINDOW_DISTANCE_M] 视为「静止」，不参与平均。
 */
object Pace {

    /** 滑动窗口长度（毫秒）。30 秒是业界惯例，兼顾平滑度与响应性。 */
    const val WINDOW_MS = 30_000L

    /**
     * 窗口内位移小于该值（米）视为静止，不计入平均速度。
     *
     * 取 10 m：低于 GPS 噪声水平，判定为「没动」。
     */
    const val MIN_WINDOW_DISTANCE_M = 10.0

    /**
     * 一个滑动窗口的统计结果。
     *
     * @param speedMps  平滑后的速度（米/秒）
     * @param distanceM 窗口内位移（米）
     * @param durationMs 窗口时长（毫秒）
     */
    data class Window(
        val speedMps: Double,
        val distanceM: Double,
        val durationMs: Long
    ) {
        /** 窗口内是否几乎没动（静止，不计入平均速度）。 */
        val isStationary: Boolean get() = distanceM < MIN_WINDOW_DISTANCE_M
    }

    /**
     * 整体统计结果。
     *
     * @param averageSpeedMps  平均速度（米/秒），**已剔除静止窗口**
     * @param maxSpeedMps      最高速度（米/秒）
     * @param movingDurationMs 运动时长（毫秒），即剔除静止后的总时长
     * @param totalDistanceM  总位移（米），含静止段
     */
    data class Stats(
        val averageSpeedMps: Double,
        val maxSpeedMps: Double,
        val movingDurationMs: Long,
        val totalDistanceM: Double
    ) {
        companion object {
            val ZERO = Stats(0.0, 0.0, 0L, 0.0)
        }

        /** 平均配速（分钟/公里）。速度为 0 时返回 null（UI 显示「—」而不是 Infinity）。 */
        val averagePaceSecPerKm: Double?
            get() = if (averageSpeedMps <= 0.0) null else 1000.0 / averageSpeedMps
    }

    /**
     * 计算整条轨迹的速度统计。
     *
     * @param points 轨迹点（按时间升序）
     * @param windowMs 滑动窗口长度
     */
    fun statsOf(points: List<TrackPoint>, windowMs: Long = WINDOW_MS): Stats {
        if (points.size < 2) return Stats.ZERO
        val windows = windows(points, windowMs)
        if (windows.isEmpty()) return Stats.ZERO

        val moving = windows.filter { !it.isStationary }
        if (moving.isEmpty()) {
            // 全程静止：平均速度 0（不是 Infinity），但总位移与时长仍如实返回。
            val total = windows.sumOf { it.distanceM }
            return Stats(0.0, 0.0, 0L, total)
        }

        // 加权平均：按窗口时长加权，比「对窗口速度取算术平均」更准
        // （窗口时长不完全相等时，算术平均会让长窗口和短窗口等权，不合理）。
        val weightedSpeedSum = moving.sumOf { it.speedMps * it.durationMs }
        val movingDuration = moving.sumOf { it.durationMs }
        val avg = if (movingDuration > 0L) weightedSpeedSum / movingDuration else 0.0
        val max = windows.maxOf { it.speedMps }
        val totalDistance = windows.sumOf { it.distanceM }
        return Stats(
            averageSpeedMps = avg,
            maxSpeedMps = max,
            movingDurationMs = movingDuration,
            totalDistanceM = totalDistance
        )
    }

    /**
     * 切成滑动窗口并逐窗算速度。
     *
     * 每个窗口是「起点 + 之后 windowMs 内所有点」，窗口内的位移按 [TrackMetrics] 的
     * 同一套规则累计（精度 30 m / 单段 500 m 不计），保证与总距离口径一致。
     */
    fun windows(points: List<TrackPoint>, windowMs: Long = WINDOW_MS): List<Window> {
        if (points.size < 2 || windowMs <= 0L) return emptyList()
        val out = ArrayList<Window>()
        var windowStart = 0

        for (end in points.indices) {
            val startTime = points[windowStart].timestamp
            val endTime = points[end].timestamp
            if (endTime - startTime < windowMs) {
                if (end == points.lastIndex) {
                    // 收尾：把最后不足一个窗口的尾巴也算成一个窗口，否则末尾位移会丢失。
                    val w = windowOf(points, windowStart, end)
                    if (w != null) out.add(w)
                }
                continue
            }
            val w = windowOf(points, windowStart, end)
            if (w != null) out.add(w)
            // 下一个窗口从当前窗口的最后一个点开始（重叠一点，保证连续性）。
            windowStart = if (end > windowStart) end else windowStart + 1
        }
        return out
    }

    /** 计算 [start]..[end] 这段的速度窗口。 */
    private fun windowOf(points: List<TrackPoint>, start: Int, end: Int): Window? {
        if (end <= start) return null
        val durationMs = points[end].timestamp - points[start].timestamp
        if (durationMs <= 0L) return null
        var dist = 0.0
        for (i in start + 1..end) {
            TrackMetrics.countedSegment(points[i - 1], points[i])?.let { dist += it }
        }
        return Window(
            speedMps = dist / (durationMs / 1000.0),
            distanceM = dist,
            durationMs = durationMs
        )
    }
}
