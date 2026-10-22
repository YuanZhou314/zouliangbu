package com.zouliangbu.footprint.util

import com.zouliangbu.footprint.data.db.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSeriesTest {

    private fun pt(lat: Double, lng: Double, alt: Double, t: Long) =
        TrackPoint(sessionId = 1L, lat = lat, lng = lng, altitude = alt, timestamp = t, accuracy = 5f)

    @Test
    fun `等距走 1km 应切出 100m 一桶的序列`() {
        // 每 50m 一个点、10 秒一个（约 18 km/h），共 21 个点 ≈ 1000 m
        val pts = (0..20).map { i ->
            // 纬度每 0.00045 度 ≈ 50 m
            pt(30.0 + i * 0.00045, 120.0, 100.0 + i, 1_700_000_000_000L + i * 10_000L)
        }
        val b = TrackSeries.build(pts)
        assertTrue("应有速度序列", b.speedKmh.values.size >= 9)
        assertTrue("应有海拔序列", b.altitude.values.size >= 9)
        // 匀速时各桶速度应接近
        val avg = b.speedKmh.values.average().toFloat()
        b.speedKmh.values.forEach { assertEquals(avg, it, avg * 0.35f) }
        assertTrue(b.totalKm in 0.9f..1.1f)
    }

    @Test
    fun `累计爬升下降单调递增且合计等于总落差`() {
        val pts = (0..40).map { i ->
            // 先爬 40m 再降 40m
            val alt = if (i <= 20) 100.0 + i * 2.0 else 140.0 - (i - 20) * 2.0
            pt(30.0 + i * 0.00045, 120.0, alt, 1_700_000_000_000L + i * 10_000L)
        }
        val b = TrackSeries.build(pts)
        val asc = b.cumulativeAscent.values
        val desc = b.cumulativeDescent.values
        assertEquals(asc, asc.sorted())
        assertEquals(desc, desc.sorted())
        // 3 m 阈值会吃掉首尾的零头（40 m 起伏实测回落 36 m 左右），所以给区间而不是精确值
        assertTrue("累计爬升应在 32..40 之间，实际 " + asc.last(), asc.last() in 32f..40f)
        assertTrue("累计下降应在 32..40 之间，实际 " + desc.last(), desc.last() in 32f..40f)
    }

    @Test
    fun `低质量点和时间倒退不产生速度尖峰`() {
        val pts = mutableListOf(
            pt(30.0, 120.0, 100.0, 1_700_000_000_000L),
            pt(30.0 + 0.00045, 120.0, 100.0, 1_700_000_010_000L),
            // 精度差：距离不计
            TrackPoint(sessionId = 1L, lat = 30.5, lng = 120.0, altitude = 100.0, timestamp = 1_700_000_020_000L, accuracy = 99f),
            // 时间倒退
            pt(30.6, 120.0, 100.0, 1_699_999_000_000L),
            pt(30.0 + 4 * 0.00045, 120.0, 100.0, 1_700_000_040_000L)
        )
        val b = TrackSeries.build(pts)
        b.speedKmh.values.forEach {
            assertTrue("速度不该出现尖峰：" + it, it < 100f)
        }
    }

    @Test
    fun `点太少时返回空序列`() {
        val b = TrackSeries.build(listOf(pt(30.0, 120.0, 100.0, 1L)))
        assertTrue(b.speedKmh.values.isEmpty())
        assertEquals(0f, b.totalKm, 1e-6f)
    }
}
