package com.zouliangbu.footprint.ui.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [nearestAnchor] 三档吸附单测：档位命中 / 中间值就近 / 越界钳到端点。 */
class RouteMapAnchorsTest {

    @Test
    fun `三档位置直接命中`() {
        assertEquals(MAP_ANCHOR_TOP, nearestAnchor(MAP_ANCHOR_TOP, MAP_ANCHORS))
        assertEquals(MAP_ANCHOR_MIDDLE, nearestAnchor(MAP_ANCHOR_MIDDLE, MAP_ANCHORS))
        assertEquals(MAP_ANCHOR_BOTTOM, nearestAnchor(MAP_ANCHOR_BOTTOM, MAP_ANCHORS))
    }

    @Test
    fun `中间值吸附到最近档`() {
        // 上半段：偏向 top 档
        assertEquals(MAP_ANCHOR_TOP, nearestAnchor(0.15f, MAP_ANCHORS))
        // 偏向 middle 档（top 与 middle 之间）
        assertEquals(MAP_ANCHOR_MIDDLE, nearestAnchor(0.35f, MAP_ANCHORS))
        // middle 与 bottom 之间偏向 middle
        assertEquals(MAP_ANCHOR_MIDDLE, nearestAnchor(0.65f, MAP_ANCHORS))
        // 偏向 bottom 档
        assertEquals(MAP_ANCHOR_BOTTOM, nearestAnchor(0.85f, MAP_ANCHORS))
    }

    @Test
    fun `越界值钳到最近端点档`() {
        // 拖拽 clamp 允许到 0.02..0.95，松手值超出档位范围时要回到端点档
        assertEquals(MAP_ANCHOR_TOP, nearestAnchor(0.0f, MAP_ANCHORS))
        assertEquals(MAP_ANCHOR_BOTTOM, nearestAnchor(1.0f, MAP_ANCHORS))
    }

    @Test
    fun `空档位表回退为原值`() {
        assertEquals(0.3f, nearestAnchor(0.3f, emptyList()))
    }

    @Test
    fun `三档覆盖预期区间且单调递增`() {
        // 守护常量语义：top≈一条状态栏、middle 对半、bottom 只剩把手
        assertTrue(MAP_ANCHOR_TOP < 0.1f)
        assertEquals(0.5f, MAP_ANCHOR_MIDDLE)
        assertTrue(MAP_ANCHOR_BOTTOM > 0.9f)
        assertTrue(MAP_ANCHOR_TOP < MAP_ANCHOR_MIDDLE && MAP_ANCHOR_MIDDLE < MAP_ANCHOR_BOTTOM)
    }
}
