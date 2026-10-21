package com.zouliangbu.footprint.data.remote

import com.zouliangbu.footprint.data.model.FeedDetail
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
 * 背景：FeedItem/RouteItem/GearCategory 曾漏 @Serializable，运行期抛 "Serializer not found"
 * 且被 Remote*Repository 的 runCatching 吞掉，App 一直静默回退 Mock。此测试防止回归。
 */
class SerializationSmokeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** GET /api/v1/feed 的真实响应形状（ContentDtos.FeedPage）。 */
    @Test
    fun `decode feed page with nested FeedItem`() {
        val raw = """
            {"code":0,"msg":"ok","data":{"items":[{"id":"1","title":"武功山反穿","author":"山野拾光",
            "likeCount":128,"city":"萍乡","coverStyle":1,"coverAspectRatio":1.25,"coverUrl":null}],
            "nextCursor":"MTA"}}
        """.trimIndent()
        val page = json.decodeFromString<ApiResponse<FeedPage>>(raw)
        assertTrue(page.ok)
        val item = page.data!!.items.single()
        assertEquals("1", item.id)
        assertEquals(128, item.likeCount)
        assertEquals(1, item.coverStyle)
        assertEquals(1.25f, item.coverAspectRatio)
        assertNull(item.coverUrl)
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

    /** GET /api/v1/feed/{id}：未知字段被忽略，content 为空串兜底。 */
    @Test
    fun `decode feed detail ignores unknown keys`() {
        val raw = """
            {"code":0,"msg":"ok","data":{"id":"1","title":"武功山反穿：两天一夜看云海日出全记录",
            "author":"山野拾光","likeCount":128,"city":"萍乡","coverStyle":1,"coverUrl":null,
            "coverAspectRatio":1.25,"coverAuthor":null,"content":"","createdAt":"2026-10-21T06:02:06",
            "someFutureField":42}}
        """.trimIndent()
        val detail = json.decodeFromString<ApiResponse<FeedDetail>>(raw)
        val d = detail.data!!
        assertEquals("1", d.id)
        assertEquals("", d.content)
        assertNull(d.coverAuthor)
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
