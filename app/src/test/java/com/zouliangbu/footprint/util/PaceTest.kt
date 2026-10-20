package com.zouliangbu.footprint.util

import com.zouliangbu.footprint.data.db.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Pace] 30 秒滑窗配速/速度单测。 */
class PaceTest {

    private fun pt(id: Long, timeMs: Long, lat: Double, lng: Double) = TrackPoint(
        id = id, sessionId = 1L, lat = lat, lng = lng,
        altitude = 0.0, timestamp = timeMs, accuracy = 5f
    )

    @Test
    fun `空列表返回零统计`() {
        val stats = Pace.statsOf(emptyList())
        assertEquals(Pace.Stats.ZERO, stats)
    }

    @Test
    fun `静止点不计移动时间`() {
        val pts = listOf(
            pt(1, 0L, 30.0, 120.0),
            pt(2, 10_000L, 30.0, 120.0),
            pt(3, 20_000L, 30.0, 120.0)
        )
        val stats = Pace.statsOf(pts)
        // 全部静止：移动时间应为 0，平均速度 0。
        assertEquals(0L, stats.movingDurationMs)
    }

    @Test
    fun `移动中算出合理速度`() {
        // 30 秒移动 100 米（≈ 3.33 m/s）
        val pts = listOf(
            pt(1, 0L, 30.0, 120.0),
            pt(2, 15_000L, 30.0, 120.001),
            pt(3, 30_000L, 30.0, 120.002)
        )
        val stats = Pace.statsOf(pts)
        assertTrue(stats.totalDistanceM > 50.0)
    }

    @Test
    fun `平均配速为 null 表示无有效移动`() {
        val pts = listOf(pt(1, 0L, 30.0, 120.0))
        val stats = Pace.statsOf(pts)
        assertEquals(null, stats.averagePaceSecPerKm)
    }

    @Test
    fun `windows 单点返回空`() {
        val pts = listOf(pt(1, 0L, 30.0, 120.0))
        val windows = Pace.windows(pts, 30_000L)
        assertTrue(windows.isEmpty())
    }
}
