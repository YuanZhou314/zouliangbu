package com.zouliangbu.footprint.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 导入转换的纯逻辑测试。
 *
 * 覆盖的都是"算错也看不出来"的地方：时间兜底（错了列表排序会乱）、
 * 海拔兜底（错了爬升统计会崩）、距离累计（错了总里程就假）。
 */
class GpxImportTest {

    private fun point(
        lat: Double = 30.0,
        lng: Double = 120.0,
        alt: Double? = 10.0,
        time: Long? = null
    ) = GpxParser.ParsedPoint(lat, lng, alt, time)

    @Test
    fun `空点集返回 null`() {
        val payload = GpxImport.toPayload(GpxParser.ParsedTrack("空轨迹", emptyList()), 1_000L)
        assertNull(payload)
    }

    @Test
    fun `有 time 时用原始时间且起止取极值`() {
        val track = GpxParser.ParsedTrack(
            "武功山",
            listOf(
                point(time = 5_000L),
                point(time = 3_000L), // 故意乱序，验证取 min/max 而不是首尾
                point(time = 9_000L)
            )
        )
        val payload = nn(GpxImport.toPayload(track, 1_000L))
        assertEquals(3_000L, payload.startTime)
        assertEquals(9_000L, payload.endTime)
        assertEquals(listOf(5_000L, 3_000L, 9_000L), payload.points.map { it.timestamp })
    }

    @Test
    fun `完全无 time 时按一秒间隔编造且起止等于 fallback`() {
        val track = GpxParser.ParsedTrack(
            null,
            listOf(point(), point(), point())
        )
        val payload = nn(GpxImport.toPayload(track, 1_700_000_000_000L))
        assertEquals(1_700_000_000_000L, payload.startTime)
        assertEquals(1_700_000_000_000L, payload.endTime)
        assertEquals(
            listOf(
                1_700_000_000_000L,
                1_700_000_001_000L,
                1_700_000_002_000L
            ),
            payload.points.map { it.timestamp }
        )
    }

    @Test
    fun `部分点缺 time 时只补缺失的`() {
        val track = GpxParser.ParsedTrack(
            null,
            listOf(point(time = 1_000L), point(time = null), point(time = 3_000L))
        )
        val payload = nn(GpxImport.toPayload(track, 999_999L))
        // 第 2 个点（index=1）按 earliest + 1000 补，earliest 来自有点里的最小值
        assertEquals(1_000L, payload.points[0].timestamp)
        assertEquals(2_000L, payload.points[1].timestamp)
        assertEquals(3_000L, payload.points[2].timestamp)
    }

    @Test
    fun `缺海拔记 0 而不是 null 或 NaN`() {
        val track = GpxParser.ParsedTrack(null, listOf(point(alt = null), point(alt = 128.5)))
        val payload = nn(GpxImport.toPayload(track, 0L))
        assertEquals(0.0, payload.points[0].altitude, 1e-9)
        assertEquals(128.5, payload.points[1].altitude, 1e-9)
    }

    @Test
    fun `空白轨迹名归一为 null`() {
        val blank = GpxImport.toPayload(GpxParser.ParsedTrack("   ", listOf(point())), 0L)
        assertNull(nn(blank).name)

        val named = GpxImport.toPayload(GpxParser.ParsedTrack("环西湖", listOf(point())), 0L)
        assertEquals("环西湖", nn(named).name)
    }

    @Test
    fun `距离按相邻点累计`() {
        // 纬度差 0.001° ≈ 111.19 m（赤道附近），杭州纬度下经度方向会略短，这里只走纬度
        val track = GpxParser.ParsedTrack(
            null,
            listOf(point(lat = 30.000), point(lat = 30.001), point(lat = 30.002))
        )
        val payload = nn(GpxImport.toPayload(track, 0L))
        assertEquals(2 * 111.19, payload.distanceMeters.toDouble(), 1.0)
    }

    @Test
    fun `单点轨迹距离为 0 且不崩`() {
        val payload = nn(GpxImport.toPayload(GpxParser.ParsedTrack(null, listOf(point())), 5L))
        assertEquals(0f, payload.distanceMeters, 1e-6f)
        assertEquals(1, payload.points.size)
    }

    @Test
    fun `导入点精度统一为 0 且 sessionId 待回填`() {
        val payload = nn(
            GpxImport.toPayload(GpxParser.ParsedTrack(null, listOf(point(), point(lat = 30.01))), 0L)
        )
        payload.points.forEach {
            assertEquals(0f, it.accuracy, 1e-6f)
            assertEquals(0L, it.sessionId)
        }
    }

    /** 非空断言并返回值，省掉满屏的 !!。 */
    private fun <T : Any> nn(value: T?): T = requireNotNull(value)
}
