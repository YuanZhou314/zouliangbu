package com.zouliangbu.footprint.ui.gear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GearScreen 里被各装备页复用的格式化/图标纯函数（internal，测试与主源集同模块可见）。
 */
class GearFormatTest {

    // ---- fmtWeight：克 → 人话 ----

    @Test
    fun `零克显示为 0g`() {
        assertEquals("0g", fmtWeight(0))
    }

    @Test
    fun `不满 1kg 显示克`() {
        assertEquals("999g", fmtWeight(999))
        assertEquals("350g", fmtWeight(350))
    }

    @Test
    fun `整 1kg 显示两位小数`() {
        assertEquals("1.00 kg", fmtWeight(1000))
    }

    @Test
    fun `大于 1kg 保留两位小数`() {
        assertEquals("12.50 kg", fmtWeight(12500))
        assertEquals("1.23 kg", fmtWeight(1230))
    }

    // ---- fmtPrice：分 → 人话 ----

    @Test
    fun `整元不带小数`() {
        assertEquals("¥15", fmtPrice(1500L))
        assertEquals("¥2380", fmtPrice(238000L))
    }

    @Test
    fun `带分保留两位小数`() {
        assertEquals("¥15.99", fmtPrice(1599L))
        assertEquals("¥0.01", fmtPrice(1L))
    }

    @Test
    fun `零分显示为 ¥0`() {
        assertEquals("¥0", fmtPrice(0L))
    }

    // ---- iconFor：分类 id → 图标 ----

    @Test
    fun `g1 到 g7 各有专属图标`() {
        for (id in listOf("g1", "g2", "g3", "g4", "g5", "g6", "g7")) {
            assertTrue("g$id 应有专属图标", iconFor(id) != iconFor("unknown"))
        }
    }

    @Test
    fun `未知分类走兜底图标`() {
        // 注意：g8 也落在 else 分支，与未知 id 共用兜底图标（实现现状，记入测试报告）
        assertEquals(iconFor("g8"), iconFor("whatever"))
    }

    @Test
    fun `已知分类都有非空图标`() {
        // g1 背包和 g2 徒步不应是兜底图标（unknown 映射到 WbSunny）
        assertTrue(iconFor("g1") != iconFor("g9"))
        assertTrue(iconFor("g3") != iconFor("g9"))
    }
}
