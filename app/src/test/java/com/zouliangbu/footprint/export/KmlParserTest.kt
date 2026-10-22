package com.zouliangbu.footprint.export

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
 * KML 导入测试。覆盖两步路导出的 gx:Track 多分段格式与 Google Earth 的 LineString。
 */
class KmlParserTest {

    private fun parse(xml: String) =
        KmlParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    @Test
    fun `解析 gx Track 的 when coord 配对`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
              <Document>
                <name>赣南齐云山穿越</name>
                <Placemark>
                  <gx:Track>
                    <when>2026-10-18T03:00:00Z</when>
                    <gx:coord>114.1234 27.4567 1520.5</gx:coord>
                    <when>2026-10-18T03:00:05Z</when>
                    <gx:coord>114.1244 27.4577 1540.0</gx:coord>
                  </gx:Track>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val track = parse(xml)
        assertEquals("赣南齐云山穿越", track.name)
        assertEquals(2, track.points.size)
        // KML 是 lng lat alt 顺序
        assertEquals(1520.5, track.points[0].altitude!!, 1e-6)
        assertNotNull(track.points[0].timeMs)
        assertEquals(5_000L, track.points[1].timeMs!! - track.points[0].timeMs!!)
    }

    @Test
    fun `两步路多分段 gx Track 都能解析`() {
        val xml = """
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
              <Document><Folder><name>轨迹</name>
                <Placemark><gx:Track>
                  <when>2026-10-18T03:00:00Z</when><gx:coord>114.1 27.4</gx:coord>
                  <when>2026-10-18T03:00:10Z</when><gx:coord>114.2 27.5</gx:coord>
                </gx:Track></Placemark>
                <Placemark><gx:Track>
                  <when>2026-10-18T03:01:00Z</when><gx:coord>114.3 27.6</gx:coord>
                </gx:Track></Placemark>
              </Folder></Document>
            </kml>
        """.trimIndent()

        val track = parse(xml)
        assertEquals(3, track.points.size)
        assertEquals(10_000L, track.points[1].timeMs!! - track.points[0].timeMs!!)
        assertEquals(50_000L, track.points[2].timeMs!! - track.points[1].timeMs!!)
    }

    @Test
    fun `解析 LineString coordinates 无时间兜底`() {
        val xml = """
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
              <Placemark>
                <LineString><coordinates>
                  114.1234,27.4567,1520 114.1244,27.4577,1540
                </coordinates></LineString>
              </Placemark>
            </kml>
        """.trimIndent()

        val track = parse(xml)
        assertEquals(2, track.points.size)
        assertEquals(1540.0, track.points[1].altitude!!, 1e-6)
        assertNull(track.points[0].timeMs)
    }

    @Test
    fun `导入时坐标从 WGS84 转成 GCJ02`() {
        val wgsLat = 39.90923
        val wgsLng = 116.397428
        val xml = """
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
              <Placemark><LineString><coordinates>$wgsLng,$wgsLat</coordinates></LineString></Placemark>
            </kml>
        """.trimIndent()

        val p = parse(xml).points.single()
        val expected = CoordTransform.wgs84ToGcj02(wgsLat, wgsLng)
        assertEquals(expected.lat, p.lat, 1e-9)
        assertEquals(expected.lng, p.lng, 1e-9)
        assertTrue("纬度没偏移，疑似漏了导入转换", abs(p.lat - wgsLat) > 1e-4)
    }

    @Test
    fun `非法坐标的点被忽略`() {
        val xml = """
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
              <Placemark><gx:Track>
                <when>2026-10-18T03:00:00Z</when><gx:coord>999 27.4</gx:coord>
                <when>2026-10-18T03:00:05Z</when><gx:coord>abc 27.4</gx:coord>
                <when>2026-10-18T03:00:10Z</when><gx:coord>114.1 27.4</gx:coord>
              </gx:Track></Placemark>
            </kml>
        """.trimIndent()

        assertEquals(1, parse(xml).points.size)
    }

    @Test
    fun `when 比 coord 多时缺时间的点记 null`() {
        val xml = """
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
              <Placemark><gx:Track>
                <when>2026-10-18T03:00:00Z</when><gx:coord>114.1 27.4</gx:coord>
                <when>2026-10-18T03:00:05Z</when><gx:coord>114.2 27.5</gx:coord>
                <gx:coord>114.3 27.6</gx:coord>
              </gx:Track></Placemark>
            </kml>
        """.trimIndent()

        val track = parse(xml)
        assertEquals(3, track.points.size)
        assertNotNull(track.points[0].timeMs)
        assertNull(track.points[2].timeMs)
    }

    @Test
    fun `XML 格式错误抛 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("<kml><Document><Placemark>")
        }
    }
}
