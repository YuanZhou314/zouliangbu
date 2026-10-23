package com.zouliangbu.footprint.ui.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 逐小时多维表纯逻辑测试：空气质量分级、PM2.5→AQI 折算、夜间时段、日期分段。
 */
class HourlyTableLogicTest {

    // ===== aqiGrade：US AQI 直接分段 =====

    @Test
    fun `US AQI 按国标六档分段`() {
        assertEquals(0, aqiGrade(usAqi = 42.0, pm25 = null))
        assertEquals(1, aqiGrade(usAqi = 88.0, pm25 = null))
        assertEquals(2, aqiGrade(usAqi = 130.0, pm25 = null))
        assertEquals(3, aqiGrade(usAqi = 180.0, pm25 = null))
        assertEquals(4, aqiGrade(usAqi = 260.0, pm25 = null))
        assertEquals(5, aqiGrade(usAqi = 350.0, pm25 = null))
    }

    @Test
    fun `边界值落在较优档`() {
        assertEquals(0, aqiGrade(usAqi = 50.0, pm25 = null))
        assertEquals(1, aqiGrade(usAqi = 51.0, pm25 = null))
        assertEquals(1, aqiGrade(usAqi = 100.0, pm25 = null))
        assertEquals(2, aqiGrade(usAqi = 101.0, pm25 = null))
        assertEquals(4, aqiGrade(usAqi = 300.0, pm25 = null))
        assertEquals(5, aqiGrade(usAqi = 301.0, pm25 = null))
    }

    @Test
    fun `两源皆空返回缺失哨兵`() {
        assertEquals(AQI_GRADE_MISSING, aqiGrade(null, null))
    }

    // ===== aqiGrade：只有 PM2.5 时按国标 24h 限值折算 =====

    @Test
    fun `PM2_5 折算后分级与浓度限值一致`() {
        assertEquals(0, aqiGrade(null, pm25 = 20.0))   // 20/35*50 ≈ 28.6 → 优
        assertEquals(1, aqiGrade(null, pm25 = 50.0))   // 50 + 15/40*50 ≈ 69 → 良
        assertEquals(2, aqiGrade(null, pm25 = 100.0))  // 100 + 25/40*50 ≈ 131 → 轻度
        assertEquals(3, aqiGrade(null, pm25 = 130.0))  // 115-150 段 → 中度
        assertEquals(4, aqiGrade(null, pm25 = 200.0))  // 150-250 段 → 重度
        assertEquals(5, aqiGrade(null, pm25 = 300.0))  // 250-350 段 → 严重
    }

    @Test
    fun `PM2_5 折算在断点处精确落位`() {
        assertEquals(0.0, estimateAqiFromPm25(0.0), 1e-9)
        assertEquals(50.0, estimateAqiFromPm25(35.0), 1e-9)
        assertEquals(100.0, estimateAqiFromPm25(75.0), 1e-9)
        assertEquals(500.0, estimateAqiFromPm25(500.0), 1e-9)
        // 超界截断
        assertEquals(500.0, estimateAqiFromPm25(9999.0), 1e-9)
        // 段内单调
        assertTrue(estimateAqiFromPm25(40.0) < estimateAqiFromPm25(60.0))
    }

    @Test
    fun `US AQI 优先于 PM2_5`() {
        // usAqi=30（优）而 pm25 折算会是更高档：应取 usAqi 的分档
        assertEquals(0, aqiGrade(usAqi = 30.0, pm25 = 100.0))
    }

    // ===== isNightHour =====

    @Test
    fun `夜间时段判定`() {
        assertTrue(isNightHour(23))
        assertTrue(isNightHour(0))
        assertTrue(isNightHour(5))
        assertTrue(isNightHour(18))
        assertFalse(isNightHour(6))
        assertFalse(isNightHour(12))
        assertFalse(isNightHour(17))
    }

    // ===== dayBreakIndices =====

    @Test
    fun `同一天只有首下标`() {
        val d = LocalDate.of(2026, 10, 23)
        assertEquals(listOf(0), dayBreakIndices(List(36) { d }))
    }

    @Test
    fun `跨天在切点分段`() {
        val d23 = LocalDate.of(2026, 10, 23)
        val d24 = LocalDate.of(2026, 10, 24)
        assertEquals(listOf(0, 3), dayBreakIndices(listOf(d23, d23, d23, d24, d24)))
        assertEquals(listOf(0, 2, 5), dayBreakIndices(listOf(d23, d23, d24, d24, d24, d23)))
    }

    @Test
    fun `空表返回空表`() {
        assertTrue(dayBreakIndices(emptyList()).isEmpty())
    }
}
