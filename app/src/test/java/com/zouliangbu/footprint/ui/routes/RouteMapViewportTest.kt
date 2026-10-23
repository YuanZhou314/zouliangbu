package com.zouliangbu.footprint.ui.routes

import com.zouliangbu.footprint.data.model.RouteItem
import com.zouliangbu.footprint.ui.map.MapBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [routesInBounds] 视口筛选单测：
 * 视区内命中 / 边距外排除 / 数量截断 / 缺坐标跳过。
 */
class RouteMapViewportTest {

    private fun route(id: String, lat: Double?, lng: Double?) = RouteItem(
        id = id,
        name = "线路$id",
        region = "测试",
        distanceKm = 10.0,
        ascentMeters = 500,
        coverStyle = 0,
        startLat = lat,
        startLng = lng
    )

    /** 可视区：纬 22~23、经 113.5~114.5（跨度各 1 度）。 */
    private val bounds = MapBounds(north = 23.0, south = 22.0, east = 114.5, west = 113.5)

    @Test
    fun `视区内路线命中`() {
        val inView = listOf(
            route("a", 22.5, 114.0),   // 正中
            route("b", 23.0, 113.5)    // 视区角上（闭区间边界算在内）
        )
        val out = route("c", 20.0, 110.0)
        val result = routesInBounds(inView + out, bounds, margin = 0.0, limit = 20)
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `边距内的贴边路线保留_边距外排除`() {
        // margin=0.2、跨度 1 度 → 每边外扩 0.2 度：纬 21.8~23.2、经 113.3~114.7
        val atMarginEdge = route("edge", 23.2, 114.0)     // 恰在外扩边界上，保留
        val justOutside = route("out", 23.25, 114.0)      // 边界外 0.05 度，排除
        val outsideLng = route("outLng", 22.5, 115.0)     // 经度方向出界，排除
        val result = routesInBounds(listOf(atMarginEdge, justOutside, outsideLng), bounds, 0.2, 20)
        assertEquals(listOf("edge"), result.map { it.id })
    }

    @Test
    fun `超出 limit 时按离视口中心由近到远截断`() {
        val routes = listOf(
            route("far", 23.2, 114.7),    // 外扩区角上，最远
            route("near", 22.5, 114.0),   // 近中心
            route("mid", 22.8, 114.2),
            route("nullCoord", null, null) // 不参与计数也不参与排序
        )
        val result = routesInBounds(routes, bounds, 0.2, limit = 2)
        assertEquals(listOf("near", "mid"), result.map { it.id })
    }

    @Test
    fun `缺坐标的路线跳过不报错`() {
        val routes = listOf(
            route("noLat", null, 114.0),
            route("noLng", 22.5, null),
            route("both", null, null)
        )
        assertTrue(routesInBounds(routes, bounds, 0.2, 20).isEmpty())
    }

    @Test
    fun `limit 为 0 或负数返回空`() {
        val routes = listOf(route("a", 22.5, 114.0))
        assertTrue(routesInBounds(routes, bounds, 0.2, 0).isEmpty())
        assertTrue(routesInBounds(routes, bounds, 0.2, -1).isEmpty())
    }

    @Test
    fun `距离排序考虑经度随纬度收缩`() {
        // 中心 (22.5, 114.0)：两个点度数偏移相同（0.5 度），但纯经度偏移要乘
        // cos(22.5°)≈0.924，真实距离更近，应排在纯纬度偏移前面。
        val lngOffset = route("lng", 22.5, 114.5)   // 只有经度差（东边界上，margin=0 时在内）
        val latOffset = route("lat", 23.0, 114.0)   // 只有纬度差（北边界上）
        val result = routesInBounds(listOf(latOffset, lngOffset), bounds, margin = 0.0, limit = 2)
        assertEquals("lng", result.first().id)
        assertEquals("lat", result.last().id)
    }
}
