package com.zouliangbu.footprint.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * 两步路导出的真实 KML 布局回归（用赣南齐云山那份文件的结构写的）。
 *
 * 这个格式有两个和「教科书 KML」不一样的地方，单测之前都没覆盖：
 *  1. `gx:Track` 里是**先全部 `gx:coord`，再全部 `<when>`**，不是 when/coord 交替；
 *  2. 同一文件里还有「标注点」文件夹，用 `<Point><coordinates>` 放起点/终点标记。
 *
 * 第 2 点是真坑：`Point` 的 coordinates 若被当轨迹点，会插在列表最前面，
 * 把后面所有点的时间配对整体错位（1676 点里前 18 个时间变 null）。
 */
class KmlParserRealLayoutTest {

    private fun parse(xml: String) =
        KmlParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    private fun coords(count: Int, lat: Double = 27.45, lng: Double = 114.12) =
        (0 until count).joinToString("\n") {
            "          <gx:coord>${lng + it * 0.0001} $lat 1500</gx:coord>"
        }

    private fun whens(count: Int) =
        (0 until count).joinToString("\n") { i ->
            "          <when>2026-06-19T08:${(9 + i / 60).toString().padStart(2, '0')}:${(i % 60).toString().padStart(2, '0')}Z</when>"
        }

    @Test
    fun `gx Track 是先全部 coord 再全部 when 也要正确配对`() {
        val xml = """
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
              <Document><name>赣南齐云山穿越</name>
                <Folder id="TbuluHisPointFolder"><name>标注点</name>
                  <Placemark id="startPoint"><name>起点</name>
                    <TimeStamp><when>2026-06-19T08:09:08Z</when></TimeStamp>
                    <Point><coordinates>114.0087123,25.9306165,1188.63</coordinates></Point>
                  </Placemark>
                  <Placemark id="endPoint"><name>终点</name>
                    <TimeStamp><when>2026-06-20T01:03:18Z</when></TimeStamp>
                    <Point><coordinates>114.052459,25.9204847,819.76</coordinates></Point>
                  </Placemark>
                </Folder>
                <Placemark><gx:Track>
${coords(5)}
${whens(5)}
                </gx:Track></Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val track = parse(xml)
        // 只有 5 个真实轨迹点；起终点标记不能混进来
        assertEquals(5, track.points.size)
        assertEquals("赣南齐云山穿越", track.name)
        assertTrue("每个点都该有时间", track.points.all { it.timeMs != null })
        assertEquals(1500.0, track.points[0].altitude!!, 1e-6)
        // 时间应从 track 内第一个 when 起算：2026-06-19T08:09:08Z，
        // 而不是标注点 startPoint 里的时间（那个恰好也是 08:09:08，
        // 所以再多断言一步：后面每点 +1s，不能被标注点的 when 打乱）。
        val t0 = track.points[0].timeMs!!
        assertEquals(1_000L, track.points[1].timeMs!! - t0)
        assertEquals(2_000L, track.points[2].timeMs!! - t0)
    }

    @Test
    fun `标注点的 Point coordinates 不算轨迹点`() {
        val xml = """
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
              <Document>
                <Folder><name>标注点</name>
                  <Placemark><Point><coordinates>114.1,27.1,100</coordinates></Point></Placemark>
                  <Placemark><Point><coordinates>114.2,27.2,200</coordinates></Point></Placemark>
                </Folder>
                <Placemark><gx:Track>
${coords(3)}
${whens(3)}
                </gx:Track></Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val track = parse(xml)
        assertEquals(3, track.points.size)
    }

    @Test
    fun `多分段 gx Track 各自配对自己的时间`() {
        val xml = """
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
              <Document>
                <Placemark><gx:Track>
${coords(4, lat = 27.40)}
${whens(4)}
                </gx:Track></Placemark>
                <Placemark><gx:Track>
${coords(2, lat = 27.60)}
${whens(2)}
                </gx:Track></Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val track = parse(xml)
        assertEquals(6, track.points.size)
        assertTrue(track.points.all { it.timeMs != null })
    }
}