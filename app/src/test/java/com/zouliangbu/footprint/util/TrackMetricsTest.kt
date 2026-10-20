package com.zouliangbu.footprint.util

import com.zouliangbu.footprint.data.db.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 距离统计规则的回归测试。
 *
 * 这些规则同时被三处使用：Service 记录时累加、崩溃恢复时重算、GPX 导入时校验。
 * 任何一处跑偏都会导致「记录时说 5.2 km，详情页显示 5.6 km」这种对不上的问题。
 */
class TrackMetricsTest {

    private fun point(
        lat: Double,
        lng: Double = 116.0,
        accuracy: Float = 5f,
        timestamp: Long = 0L
    ) = TrackPoint(
        sessionId = 1L,
        lat = lat,
        lng = lng,
        altitude = 0.0,
        timestamp = timestamp,
        accuracy = accuracy
    )

    @Test
    fun `空轨迹和单点距离为 0`() {
        assertEquals(0f, TrackMetrics.distanceMeters(emptyList()), 0.001f)
        assertEquals(0f, TrackMetrics.distanceMeters(listOf(point(39.9))), 0.001f)
    }

    @Test
    fun `正常两点按 haversine 累加`() {
        // 纬度 0.001° ≈ 111.2 m
        val points = listOf(point(39.900), point(39.901))
        val expected = Distance.haversineMeters(39.900, 116.0, 39.901, 116.0).toFloat()
        assertEquals(expected, TrackMetrics.distanceMeters(points), 0.01f)
    }

    @Test
    fun `低精度点所在段不计距离`() {
        // next 的 accuracy 超过阈值 → 该段丢弃
        val seg = TrackMetrics.countedSegment(point(39.900), point(39.901, accuracy = 50f))
        assertNull(seg)
        assertEquals(0f, TrackMetrics.distanceMeters(listOf(point(39.900), point(39.901, accuracy = 50f))), 0.001f)
    }

    @Test
    fun `超过 500 米的单段视为漂移`() {
        // 0.01° 纬度 ≈ 1.1 km，远超 500 m
        assertNull(TrackMetrics.countedSegment(point(39.900), point(39.910)))
        assertEquals(0f, TrackMetrics.distanceMeters(listOf(point(39.900), point(39.910))), 0.001f)
    }

    @Test
    fun `低精度点仍是下一段的起点`() {
        // A(准) → B(不准) 丢弃；B → C(准) 计入。与 Service 记录时的增量逻辑一致：
        // 低精度点照样落库、照样成为 lastPoint，只是它参与的那一段按 next 的精度判定。
        val a = point(39.900, accuracy = 5f)
        val b = point(39.901, accuracy = 50f)
        val c = point(39.902, accuracy = 5f)
        val expected = Distance.haversineMeters(b.lat, b.lng, c.lat, c.lng).toFloat()
        assertEquals(expected, TrackMetrics.distanceMeters(listOf(a, b, c)), 0.01f)
    }

    @Test
    fun `每次只累加可信段且跳过漂移`() {
        // A→B 正常 111m，B→C 瞬移 11km 丢弃，C→D 正常 111m
        val points = listOf(
            point(39.900),
            point(39.901),
            point(40.000),
            point(40.001)
        )
        val seg1 = Distance.haversineMeters(39.900, 116.0, 39.901, 116.0).toFloat()
        val seg2 = Distance.haversineMeters(40.000, 116.0, 40.001, 116.0).toFloat()
        assertEquals(seg1 + seg2, TrackMetrics.distanceMeters(points), 0.05f)
    }

    @Test
    fun `阈值常量与 UI 文案约定一致`() {
        // 精度阈值写在 CLAUDE.md 与 GPX 分析里，改动时测试会提醒
        assertEquals(30f, TrackMetrics.ACCURACY_LIMIT_M, 0f)
        assertEquals(500f, TrackMetrics.MAX_SEGMENT_M, 0f)
        assertNotNull(TrackMetrics.countedSegment(point(39.9), point(39.9000001)))
    }
}
