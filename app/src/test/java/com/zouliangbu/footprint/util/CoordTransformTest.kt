package com.zouliangbu.footprint.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 坐标转换测试。
 *
 * 外部权威值（在线工具）没法在单测里比对，所以这里用三条自洽性质来锁行为：
 *   1. 境外不偏移（否则国外轨迹会被带偏）
 *   2. 境内偏移量级在 300–800 m（量级错了说明公式抄错）
 *   3. 往返可逆（gcj→wgs→gcj 残差 < 0.3 m，说明迭代收敛正常）
 */
class CoordTransformTest {

    // 北京天安门附近的 WGS-84 点
    private val beijingLat = 39.90923
    private val beijingLng = 116.397428

    @Test
    fun `境外坐标不做偏移`() {
        // 东京
        val out = CoordTransform.wgs84ToGcj02(35.6895, 139.6917)
        assertEquals(35.6895, out.lat, 1e-12)
        assertEquals(139.6917, out.lng, 1e-12)

        assertTrue(CoordTransform.isOutOfChina(35.6895, 139.6917))
        assertFalse(CoordTransform.isOutOfChina(beijingLat, beijingLng))
    }

    @Test
    fun `境内偏移量级应在 300 到 800 米之间`() {
        val gcj = CoordTransform.wgs84ToGcj02(beijingLat, beijingLng)
        val offset = Distance.haversineMeters(beijingLat, beijingLng, gcj.lat, gcj.lng)
        assertTrue("北京偏移 $offset m，超出合理量级（公式可能抄错）", offset in 300.0..800.0)
    }

    @Test
    fun `北京偏移方向应为东北`() {
        val gcj = CoordTransform.wgs84ToGcj02(beijingLat, beijingLng)
        assertTrue("GCJ 纬度应更大（北偏）", gcj.lat > beijingLat)
        assertTrue("GCJ 经度应更大（东偏）", gcj.lng > beijingLng)
    }

    @Test
    fun `WGS 到 GCJ 再回 WGS 残差小于 0_3 米`() {
        val gcj = CoordTransform.wgs84ToGcj02(beijingLat, beijingLng)
        val back = CoordTransform.gcj02ToWgs84(gcj.lat, gcj.lng)
        val residual = Distance.haversineMeters(beijingLat, beijingLng, back.lat, back.lng)
        assertTrue("往返残差 $residual m", residual < 0.3)
    }

    @Test
    fun `多个城市的往返都不漂`() {
        val cities = listOf(
            "北京" to (39.90923 to 116.397428),
            "广州" to (23.1291 to 113.2644),
            "拉萨" to (29.6520 to 91.1721),
            "哈尔滨" to (45.8038 to 126.5349),
            "昆明" to (25.0389 to 102.7183)
        )
        for ((name, lla) in cities) {
            val gcj = CoordTransform.wgs84ToGcj02(lla.first, lla.second)
            val back = CoordTransform.gcj02ToWgs84(gcj.lat, gcj.lng)
            val residual = Distance.haversineMeters(lla.first, lla.second, back.lat, back.lng)
            assertTrue("$name 往返残差 $residual m 过大", residual < 0.3)
        }
    }

    @Test
    fun `往返后经纬度分量残差都很小`() {
        val gcj = CoordTransform.wgs84ToGcj02(beijingLat, beijingLng)
        val back = CoordTransform.gcj02ToWgs84(gcj.lat, gcj.lng)
        assertTrue(abs(back.lat - beijingLat) < 1e-6)
        assertTrue(abs(back.lng - beijingLng) < 1e-6)
    }
}
