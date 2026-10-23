package com.zouliangbu.footprint.ui.routes

import com.zouliangbu.footprint.data.model.RouteItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 线路地图模式搜索过滤（filterRoutes）的 JVM 单测：
 * name / region / tags 包含匹配、大小写不敏感、空关键词原样返回。
 */
class RouteListFilterTest {

    private fun route(
        id: String,
        name: String,
        region: String = "广东深圳",
        tags: List<String> = emptyList()
    ) = RouteItem(
        id = id,
        name = name,
        region = region,
        distanceKm = 10.0,
        ascentMeters = 500,
        coverStyle = 0,
        tags = tags
    )

    private val routes = listOf(
        route("1", "梧桐山经典线", tags = listOf("徒步", "看日出")),
        route("2", "七娘山环线", region = "广东深圳大鹏", tags = listOf("海岸")),
        route("3", "KTV步道", tags = listOf("亲子"))
    )

    @Test
    fun `空关键词原样返回同一列表`() {
        assertSame(routes, filterRoutes(routes, ""))
        assertSame(routes, filterRoutes(routes, "   "))
    }

    @Test
    fun `按名称包含匹配且大小写不敏感`() {
        assertEquals(listOf("3"), filterRoutes(routes, "ktv").map { it.id })
        assertEquals(listOf("1"), filterRoutes(routes, "梧桐").map { it.id })
    }

    @Test
    fun `按地区包含匹配`() {
        assertEquals(listOf("2"), filterRoutes(routes, "大鹏").map { it.id })
    }

    @Test
    fun `按标签包含匹配`() {
        assertEquals(listOf("1"), filterRoutes(routes, "日出").map { it.id })
        assertEquals(listOf("3"), filterRoutes(routes, "亲子").map { it.id })
    }

    @Test
    fun `多字段并集命中且保持原顺序`() {
        // "山"同时命中名称（1、2），原顺序返回
        assertEquals(listOf("1", "2"), filterRoutes(routes, "山").map { it.id })
    }

    @Test
    fun `无命中返回空列表`() {
        assertEquals(emptyList<RouteItem>(), filterRoutes(routes, "黄山"))
    }

    @Test
    fun `关键词先trim再匹配`() {
        assertEquals(listOf("1"), filterRoutes(routes, " 梧桐 ").map { it.id })
    }
}
