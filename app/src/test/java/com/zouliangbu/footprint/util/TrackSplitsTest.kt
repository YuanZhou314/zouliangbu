package com.zouliangbu.footprint.util

import com.zouliangbu.footprint.data.db.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TrackSplits] 每公里分段单测。 */
class TrackSplitsTest {

    private fun pt(id: Long, timeMs: Long, lat: Double, lng: Double) = TrackPoint(
        id = id, sessionId = 1L, lat = lat, lng = lng,
        altitude = 0.0, timestamp = timeMs, accuracy = 5f
    )

    /** 0.0001 度纬度 ≈ 11.1 米。 */
    private fun makePoints(km: Int): List<TrackPoint> {
        val pts = ArrayList<TrackPoint>()
        val step = 0.0001  // 11.1m
        var i = 0L
        for (k in 0 until km) {
            for (j in 0 until 90) {  // 每公里约 90 步
                pts.add(pt(i++, (i * 1000L), 30.0 + (i * step), 120.0))
            }
        }
        return pts
    }

    @Test
    fun `空列表返回空分段`() {
        assertEquals(0, TrackSplits.splitsOf(emptyList()).size)
    }

    @Test
    fun `1公里以内只有 1 段`() {
        // 只走 0.5 km
        val pts = (0 until 50).map { i ->
            pt(i.toLong(), (i * 1000L), 30.0 + i * 0.0001, 120.0)
        }
        val splits = TrackSplits.splitsOf(pts)
        assertEquals(1, splits.size)
        assertTrue(splits[0].partial)
    }

    @Test
    fun `不足 1km 尾段是 partial`() {
        // 1 km 完整段 + 0.4 km 尾段
        val pts = ArrayList<TrackPoint>()
        for (i in 0 until 130) {
            pts.add(pt(i.toLong(), (i * 1000L), 30.0 + i * 0.0001, 120.0))
        }
        val splits = TrackSplits.splitsOf(pts)
        assertTrue(splits.size >= 1)
    }

    @Test
    fun `单段索引递增`() {
        val pts = (0 until 300).map { i ->
            pt(i.toLong(), (i * 1000L), 30.0 + i * 0.0001, 120.0)
        }
        val splits = TrackSplits.splitsOf(pts)
        for (i in 1 until splits.size) {
            assertTrue(splits[i].index > splits[i - 1].index)
        }
    }
}
