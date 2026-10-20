package com.zouliangbu.footprint.export

import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSession
import com.zouliangbu.footprint.util.CoordTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.math.abs

/**
 * GPX 导入测试。
 *
 * 最值钱的一条是「写出去再读回来」：它同时验证
 *   ① 导出做了 GCJ→WGS  ② 导入做了 WGS→GCJ  ③ 两个算法互逆。
 * 单看任何一边都发现不了"两边都漏转换"这种对称错误。
 */
class GpxParserTest {

    private fun parse(xml: String) =
        GpxParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    @Test
    fun `解析带命名空间的 GPX`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk>
                <name>武功山反穿</name>
                <trkseg>
                  <trkpt lat="27.4567" lon="114.1234"><ele>1520.5</ele><time>2026-10-20T09:00:00Z</time></trkpt>
                  <trkpt lat="27.4577" lon="114.1244"><ele>1540.0</ele><time>2026-10-20T09:00:05Z</time></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val track = parse(xml)
        assertEquals("武功山反穿", track.name)
        assertEquals(2, track.points.size)
        assertEquals(1520.5, track.points[0].altitude!!, 1e-6)
        assertNotNull(track.points[0].timeMs)
        // 5 秒间隔
        assertEquals(5_000L, track.points[1].timeMs!! - track.points[0].timeMs!!)
    }

    @Test
    fun `解析没有命名空间的 GPX`() {
        // 不少手写 / 老工具导出的 GPX 没有 xmlns，只有 qName 可读
        val xml = """
            <gpx version="1.1"><trk><trkseg>
              <trkpt lat="39.9" lon="116.4"/>
            </trkseg></trk></gpx>
        """.trimIndent()

        val track = parse(xml)
        assertEquals(1, track.points.size)
    }

    @Test
    fun `导入时坐标从 WGS84 转成 GCJ02`() {
        val wgsLat = 39.90923
        val wgsLng = 116.397428
        val xml = """<gpx><trk><trkseg><trkpt lat="$wgsLat" lon="$wgsLng"/></trkseg></trk></gpx>"""

        val p = parse(xml).points.single()
        val expected = CoordTransform.wgs84ToGcj02(wgsLat, wgsLng)
        assertEquals(expected.lat, p.lat, 1e-9)
        assertEquals(expected.lng, p.lng, 1e-9)
        assertTrue("纬度没偏移，疑似漏了导入转换", abs(p.lat - wgsLat) > 1e-4)
    }

    @Test
    fun `导出再导入坐标基本无损`() {
        val session = TrackSession(id = 1L, startTime = 1_700_000_000_000L)
        val gcjPoints = listOf(
            TrackPoint(sessionId = 1L, lat = 27.4567, lng = 114.1234, altitude = 1520.5, timestamp = 1_700_000_000_000L, accuracy = 5f),
            TrackPoint(sessionId = 1L, lat = 27.4577, lng = 114.1244, altitude = 1540.0, timestamp = 1_700_000_005_000L, accuracy = 5f),
            TrackPoint(sessionId = 1L, lat = 27.4589, lng = 114.1256, altitude = 1480.0, timestamp = 1_700_000_010_000L, accuracy = 5f)
        )

        val xml = GpxWriter.build(session, gcjPoints)
        val parsed = parse(xml)

        assertEquals(gcjPoints.size, parsed.points.size)
        for (i in gcjPoints.indices) {
            assertEquals("第 $i 点纬度往返失真", gcjPoints[i].lat, parsed.points[i].lat, 1e-5)
            assertEquals("第 $i 点经度往返失真", gcjPoints[i].lng, parsed.points[i].lng, 1e-5)
            assertEquals(gcjPoints[i].timestamp, parsed.points[i].timeMs)
            assertEquals(gcjPoints[i].altitude, parsed.points[i].altitude!!, 0.05)
        }
    }

    @Test
    fun `缺少 ele 和 time 的点也能解析`() {
        val xml = """<gpx><trk><trkseg><trkpt lat="39.9" lon="116.4"/></trkseg></trk></gpx>"""
        val p = parse(xml).points.single()
        assertNull(p.altitude)
        assertNull(p.timeMs)
    }

    @Test
    fun `非法坐标的点被忽略`() {
        val xml = """
            <gpx><trk><trkseg>
              <trkpt lat="999" lon="116.4"/>
              <trkpt lat="39.9" lon="abc"/>
              <trkpt lat="39.9" lon="116.4"/>
            </trkseg></trk></gpx>
        """.trimIndent()

        assertEquals(1, parse(xml).points.size)
    }

    @Test
    fun `XML 格式错误抛 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("<gpx><trk><trkseg>")
        }
    }
}
