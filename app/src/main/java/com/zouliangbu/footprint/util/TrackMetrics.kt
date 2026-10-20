package com.zouliangbu.footprint.util

import com.zouliangbu.footprint.data.db.TrackPoint

/**
 * 距离统计规则（记录中累加、崩溃恢复时重算、导入时校验，三处必须一致）。
 *
 * 抽成纯函数是为了能在 JVM 单测里验证：这三处如果各写一套判断，
 * 就会出现「记录时说 5.2 km，详情页重算变 5.6 km」这种没法排查的差异。
 */
object TrackMetrics {

    /** 精度差于该值（米）的点不计距离，但仍落库保证轨迹连续。 */
    const val ACCURACY_LIMIT_M = 30f

    /** 单段超过该值（米）视为 GPS 漂移/瞬移，不计距离。 */
    const val MAX_SEGMENT_M = 500f

    /**
     * 计算 [prev] → [next] 这一段能计入的距离；不计时返回 null。
     *
     * 用**后一个点**的精度做判断：GPS 精度是"这个定位结果有多可信"，
     * 只有两端都可信的段才算准，取端点里更差的那个更保守——但实测取 next
     * 与取 max(prev, next) 差异极小，保持与旧逻辑（只判 next）一致以免历史数据对不上。
     */
    fun countedSegment(prev: TrackPoint, next: TrackPoint): Float? {
        if (next.accuracy > ACCURACY_LIMIT_M) return null
        val meters = Distance.haversineMeters(prev.lat, prev.lng, next.lat, next.lng).toFloat()
        return if (meters < MAX_SEGMENT_M) meters else null
    }

    /**
     * 整条轨迹的距离（米）。按点序累加所有可计入的相邻段。
     *
     * @param points 需按 timestamp 升序（`TrackPointDao.getForSession` 已保证）。
     */
    fun distanceMeters(points: List<TrackPoint>): Float {
        if (points.size < 2) return 0f
        var total = 0.0
        for (i in 1 until points.size) {
            val inc = countedSegment(points[i - 1], points[i]) ?: continue
            total += inc
        }
        return total.toFloat()
    }
}

/**
 * 记录时长的计算：只算「活动」时间，扣除暂停。
 *
 * 时间基准必须用 [android.os.SystemClock.elapsedRealtime]（单调递增，不受 NTP 校时影响），
 * 不能用 `System.currentTimeMillis()`——用户改系统时间会让时长瞬间变成负数或几十小时。
 */
object TrackClock {

    /**
     * @param nowElapsed      当前 elapsedRealtime
     * @param startedElapsed  本次记录开始时的 elapsedRealtime
     * @param pausedMs        已累计的暂停时长（不含当前这次暂停）
     * @param pausedAtElapsed 当前这次暂停开始的 elapsedRealtime；未暂停传 0
     * @return 活动时长（毫秒），不会为负
     */
    fun activeElapsedMs(
        nowElapsed: Long,
        startedElapsed: Long,
        pausedMs: Long,
        pausedAtElapsed: Long = 0L
    ): Long {
        val currentPause = if (pausedAtElapsed > 0L) {
            (nowElapsed - pausedAtElapsed).coerceAtLeast(0L)
        } else {
            0L
        }
        return (nowElapsed - startedElapsed - pausedMs - currentPause).coerceAtLeast(0L)
    }
}
