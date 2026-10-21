package com.zouliangbu.footprint.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SegmentMatch 单测：共同路段匹配 + 对比指标。
 *
 * 用合成直线轨迹（沿纬度方向走），避免真实轨迹的 GPS 噪声干扰断言。
 * 1 度纬度 ≈ 111320 m，100 m ≈ 0.000898 度。
 */
class SegmentMatchTest {

    private val stepLat = 100.0 / 111_320.0

    /** 沿正北方向走 count 个点，每点间隔 100m。 */
    private fun line(
        count: Int,
        startLat: Double = 30.0,
        startLng: Double = 120.0,
        startTime: Long? = null,
        timeStepMs: Long = 60_000,
        altStep: Double? = null
    ): List<SegmentMatch.Sample> = (0 until count).map { i ->
        SegmentMatch.Sample(
            lat = startLat + i * stepLat,
            lng = startLng,
            timestamp = startTime?.plus(i * timeStepMs),
            altitude = altStep?.let { 100.0 + i * it }
        )
    }

    @Test
    fun `两条相同轨迹能匹配出共同路段`() {
        val a = line(10)
        val b = line(10)
        val overlap = SegmentMatch.findOverlap(a, b)
        assertNotNull(overlap)
        // 10 点、9 段 × 100m ≈ 900m
        assertEquals(900.0, overlap!!.meters, 5.0)
        // aStart / aEnd 是顶点索引（闭区间）：10 个点 → A[0]..A[9]
        assertEquals(0, overlap.aStart)
        assertEquals(9, overlap.aEnd)
    }

    @Test
    fun `相距一公里的两条线不判定为同一条路`() {
        val a = line(10)
        val b = line(10, startLat = 30.01) // 约 1.1km 外
        assertNull(SegmentMatch.findOverlap(a, b))
        assertTrue(!SegmentMatch.isSameRoute(a, b))
    }

    @Test
    fun `B 是 A 的子集时共同路段覆盖 B 全程`() {
        val a = line(30)
        val b = line(10, startLat = 30.0 + 5 * stepLat) // 从 A 的第 5 点开始
        val overlap = SegmentMatch.findOverlap(a, b)
        assertNotNull(overlap)
        assertEquals(0, overlap!!.bStart)
        assertEquals(9, overlap.bEnd)
        assertEquals(900.0, overlap.meters, 5.0)
    }

    @Test
    fun `对比给出距离比与时长比`() {
        // A：10 点，每点 60s；B：同样路线，每点 90s（更慢）
        val a = line(10, startTime = 0L, timeStepMs = 60_000)
        val b = line(10, startTime = 0L, timeStepMs = 90_000)
        val cmp = SegmentMatch.compare(a, b)
        assertNotNull(cmp)
        // 距离相同 → 比值 1.0
        assertEquals(1.0, cmp!!.distanceRatio, 0.02)
        // 时长比 = 90/60 = 1.5（b 更慢）
        val ratio = cmp.timeRatio
        assertNotNull(ratio)
        assertEquals(1.5, ratio!!, 0.05)
        // 均速：900m / 540s ≈ 6.0 km/h
        assertEquals(6.0, cmp.aAvgSpeedKmh!!, 0.2)
        assertEquals(4.0, cmp.bAvgSpeedKmh!!, 0.2)
    }

    @Test
    fun `对比给出爬升差`() {
        val a = line(10, altStep = 10.0) // 累计上升 90m
        val b = line(10, altStep = 5.0)  // 累计上升 45m
        val cmp = SegmentMatch.compare(a, b)
        assertNotNull(cmp)
        assertEquals(90.0, cmp!!.aAscentMeters, 0.5)
        assertEquals(45.0, cmp.bAscentMeters, 0.5)
        assertEquals(-45.0, cmp.ascentDeltaMeters, 0.5)
    }

    @Test
    fun `点太少的轨迹不参与对比`() {
        assertNull(SegmentMatch.findOverlap(line(1), line(1)))
        assertNull(SegmentMatch.findOverlap(emptyList(), line(5)))
    }

    @Test
    fun `短暂重合不足 200 米不算同一条路`() {
        val a = line(3) // 约 200m
        val b = listOf(
            SegmentMatch.Sample(30.0, 120.0),
            SegmentMatch.Sample(30.0 + stepLat * 0.5, 120.0)
        )
        assertNull(SegmentMatch.findOverlap(a, b))
    }
}
