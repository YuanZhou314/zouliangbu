package com.zouliangbu.footprint.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Polyline] 抽稀（Douglas-Peucker）单测。 */
class PolylineTest {

    @Test
    fun `共线点抽稀到只剩首尾`() {
        // 一条直线上的 100 个点：抽稀后只剩首尾 2 个。
        val pts = (0 until 100).map { i ->
            com.zouliangbu.footprint.data.db.TrackPoint(
                id = i.toLong(),
                sessionId = 1L,
                lat = 30.0 + i * 0.0001,
                lng = 120.0 + i * 0.0001,
                altitude = 0.0,
                timestamp = 0L,
                accuracy = 5f
            )
        }
        val simplified = Polyline.simplifyPoints(pts)
        assertEquals(2, simplified.size)
        assertEquals(pts.first(), simplified.first())
        assertEquals(pts.last(), simplified.last())
    }

    @Test
    fun `少于最小点数直接原样返回`() {
        val pts = listOf(
            com.zouliangbu.footprint.data.db.TrackPoint(1L, 1L, 30.0, 120.0, 0.0, 0L, 5f),
            com.zouliangbu.footprint.data.db.TrackPoint(2L, 1L, 30.1, 120.1, 0.0, 0L, 5f)
        )
        val simplified = Polyline.simplifyPoints(pts)
        assertEquals(2, simplified.size)
    }

    @Test
    fun `抖动点按距离被保留`() {
        // 一个大尖峰：Douglas-Peucker 应保留那个尖峰。
        val pts = listOf(
            com.zouliangbu.footprint.data.db.TrackPoint(1L, 1L, 30.0, 120.0, 0.0, 0L, 5f),
            com.zouliangbu.footprint.data.db.TrackPoint(2L, 1L, 30.001, 120.001, 0.0, 0L, 5f),
            com.zouliangbu.footprint.data.db.TrackPoint(3L, 1L, 30.002, 120.002, 0.0, 0L, 5f),
            com.zouliangbu.footprint.data.db.TrackPoint(4L, 1L, 30.010, 120.010, 0.0, 0L, 5f),
            com.zouliangbu.footprint.data.db.TrackPoint(5L, 1L, 30.004, 120.004, 0.0, 0L, 5f)
        )
        val simplified = Polyline.simplifyPoints(pts)
        // 抽稀后仍至少 3 个点（首、尖峰、尾）。
        assertTrue(simplified.size >= 3)
    }

    @Test
    fun `空列表返回空列表`() {
        val simplified = Polyline.simplifyPoints(emptyList())
        assertTrue(simplified.isEmpty())
    }

    @Test
    fun `simplifyOrKeep 空列表不抛异常`() {
        val out = Polyline.simplifyOrKeep(emptyList())
        assertTrue(out.isEmpty())
    }
}
