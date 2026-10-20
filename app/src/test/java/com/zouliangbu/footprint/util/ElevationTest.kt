package com.zouliangbu.footprint.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Elevation] 中值滤波 + 累计爬升/下降单测。 */
class ElevationTest {

    @Test
    fun `中值滤波滤掉单点尖刺`() {
        val raw = listOf(0.0, 0.0, 50.0, 0.0, 0.0)
        val filtered = Elevation.medianFilter(raw)
        // 50 是单点尖刺，中值滤波后应该被压平。
        assertEquals(0.0, filtered[2], 0.01)
    }

    @Test
    fun `持续爬升保留`() {
        val raw = (0..11).map { it.toDouble() * 10.0 }
        val filtered = Elevation.medianFilter(raw)
        // 持续 0→100 的爬升，中值滤波后仍应是单调递增。
        assertEquals(100.0, filtered.last(), 0.01)
    }

    @Test
    fun `ascentOfFiltered 按阈值累加`() {
        val alt = listOf(0.0, 1.0, 2.0, 3.5, 0.0, 0.0)
        val a = Elevation.ascentOfFiltered(alt, thresholdM = 3.0)
        // 0→3.5 = +3.5（>=3.0 计入），3.5→0 = -3.5（>=3.0 计入下降）
        assertEquals(3.5, a.ascentMeters, 0.01)
        assertEquals(3.5, a.descentMeters, 0.01)
    }

    @Test
    fun `小抖动不累加`() {
        val alt = listOf(0.0, 1.0, 0.5, 1.0, 0.5)
        val a = Elevation.ascentOfFiltered(alt, thresholdM = 3.0)
        assertEquals(0.0, a.ascentMeters, 0.01)
        assertEquals(0.0, a.descentMeters, 0.01)
    }

    @Test
    fun `空列表返回零`() {
        assertEquals(Elevation.Ascent.ZERO, Elevation.ascentOfFiltered(emptyList()))
    }
}
