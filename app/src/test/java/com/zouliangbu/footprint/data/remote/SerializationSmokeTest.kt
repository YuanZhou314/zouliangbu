package com.zouliangbu.footprint.data.remote

import com.zouliangbu.footprint.data.model.GearCategory
import com.zouliangbu.footprint.data.model.RouteCatalog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 序列化冒烟测试：用服务端真实响应 JSON 验证 Retrofit 泛型链上的所有 DTO 都有 @Serializable。
 *
 * 背景：RouteItem/GearCategory 曾漏 @Serializable，运行期抛 "Serializer not found"
 * 且被 Remote*Repository 的 runCatching 吞掉，App 一直静默回退 Mock。此测试防止回归。
 */
class SerializationSmokeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** GET /api/v1/routes 的真实响应形状（嵌套 RouteCatalog → RouteSection → RouteItem）。 */
    @Test
    fun `decode route catalog with nested sections`() {
        val raw = """
            {"code":0,"msg":"ok","data":{"recommendations":[{"id":"1","name":"武功山反穿线",
            "region":"萍乡","distanceKm":13.5,"ascentMeters":1200,"coverStyle":1,
            "tags":["户外毕业线","露营"],"coverUrl":"/uploads/2026-10/x.jpg"}],"sections":[]}}
        """.trimIndent()
        val catalog = json.decodeFromString<ApiResponse<RouteCatalog>>(raw)
        val rec = catalog.data!!.recommendations.single()
        assertEquals("武功山反穿线", rec.name)
        assertEquals(listOf("户外毕业线", "露营"), rec.tags)
        assertEquals("/uploads/2026-10/x.jpg", rec.coverUrl)
    }

    /** POST /api/v1/routes/submit：请求体可编码，响应可解码。 */
    @Test
    fun `roundtrip submit request and response`() {
        val encoded = json.encodeToString(SubmitRouteRequest("https://www.2bulu.com/track/tZz.htm", "uuid-1", null))
        assertTrue(encoded.contains("2bulu.com"))
        val resp = json.decodeFromString<ApiResponse<Long>>("""{"code":0,"msg":"ok","data":7}""")
        assertEquals(7L, resp.data)
    }

    /** GET /api/v1/gear/categories：GearCategory（历史漏注解的重灾区）可解码。 */
    @Test
    fun `decode gear categories`() {
        val raw = """
            {"code":0,"msg":"ok","data":[{"id":"tent","name":"帐篷","note":"庇护所"}]}
        """.trimIndent()
        val list = json.decodeFromString<ApiResponse<List<GearCategory>>>(raw)
        assertEquals("帐篷", list.data!!.single().name)
    }
}
