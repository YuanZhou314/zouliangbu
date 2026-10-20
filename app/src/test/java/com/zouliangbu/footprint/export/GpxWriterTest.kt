package com.zouliangbu.footprint.export

import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSession
import com.zouliangbu.footprint.util.CoordTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

/**
 * GPX 导出测试。
 *
 * 重点不是"能生成字符串"，而是三件会真出事的事：
 *   - 产出的 XML 必须能被标准解析器解析（外面软件打不开等于白做）
 *   - 坐标必须是 WGS-84（不转换的话整条轨迹偏几百米）
 *   - 脏数据（越界坐标 / 缺海拔）不能把文档搞坏
 */
class GpxWriterTest {

    private val session = TrackSession(
        id = 1L,
        startTime = 1_700_000_000_000L,   // 2023-11-15 06:13:20 UTC
        endTime = 1_700_000_600_000L,
        totalDistanceMeters = 1234.5f
    )

    private fun point(
        lat: Double,
        lng: Double,
        altitude: Double = 52.0,
        timestamp: Long = 1_700_000_000_000L
    ) = TrackPoint(
        sessionId = 1L,
        lat = lat,
        lng = lng,
        altitude = altitude,
        timestamp = timestamp,
        accuracy = 5f
    )

    private fun parseXml(xml: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    @Test
    fun `输出是合法 XML 且符合 GPX 1_1 结构`() {
        val xml = GpxWriter.build(session, listOf(point(39.90923, 116.397428)))
        val doc = parseXml(xml)

        assertEquals("gpx", doc.documentElement.tagName)
        assertEquals("1.1", doc.documentElement.getAttribute("version"))
        assertEquals(1, doc.getElementsByTagName("trkseg").length)
        assertEquals(1, doc.getElementsByTagName("trkpt").length)
        assertTrue("creator 不应为空", doc.documentElement.getAttribute("creator").isNotEmpty())
    }

    @Test
    fun `导出的坐标是 WGS84 而不是库里的 GCJ02`() {
        val gcjLat = 39.9123
        val gcjLng = 116.4038
        val xml = GpxWriter.build(session, listOf(point(gcjLat, gcjLng)))

        val lat = Regex("lat=\"(-?[0-9.]+)\"").find(xml)!!.groupValues[1].toDouble()
        val lng = Regex("lon=\"(-?[0-9.]+)\"").find(xml)!!.groupValues[1].toDouble()

        val expected = CoordTransform.gcj02ToWgs84(gcjLat, gcjLng)
        assertEquals(expected.lat, lat, 1e-6)
        assertEquals(expected.lng, lng, 1e-6)

        // 与库里原值必须有可见差异，否则说明根本没转换
        assertTrue("纬度没变化，疑似漏了坐标转换", abs(lat - gcjLat) > 1e-4)
        assertTrue("经度没变化，疑似漏了坐标转换", abs(lng - gcjLng) > 1e-4)
    }

    @Test
    fun `越界坐标的点被跳过`() {
        val xml = GpxWriter.build(
            session,
            listOf(
                point(200.0, 116.0),      // 纬度越界
                point(39.9, 300.0),       // 经度越界
                point(39.9, 116.4)        // 正常
            )
        )
        assertEquals(1, Regex("<trkpt ").findAll(xml).count())
    }

    @Test
    fun `海拔为 0 或非有限值时不写 ele`() {
        val zero = GpxWriter.build(session, listOf(point(39.9, 116.4, altitude = 0.0)))
        assertFalse(zero.contains("<ele>"))

        val nan = GpxWriter.build(session, listOf(point(39.9, 116.4, altitude = Double.NaN)))
        assertFalse(nan.contains("<ele>"))
    }

    @Test
    fun `有时间戳的点会写出 UTC 时间`() {
        val xml = GpxWriter.build(session, listOf(point(39.9, 116.4)))
        assertTrue(xml.contains("<time>2023-11-14T22:13:20Z</time>"))
    }

    @Test
    fun `空轨迹也能生成可解析的 GPX`() {
        val xml = GpxWriter.build(session, emptyList())
        val doc = parseXml(xml)
        assertEquals(0, doc.getElementsByTagName("trkpt").length)
        assertEquals(1, doc.getElementsByTagName("trkseg").length)
    }

    @Test
    fun `文件名带日期且扩展名为 gpx`() {
        val name = GpxWriter.suggestFileName(session)
        assertTrue(name.startsWith("走两步_"))
        assertTrue(name.endsWith(".gpx"))
        assertEquals(1, Regex("""走两步_\d{8}_\d{4}\.gpx""").findAll(name).count())
    }

    @Test
    fun `轨迹名默认带开始时间`() {
        assertTrue(GpxWriter.defaultName(session).startsWith("走两步 "))
    }
}
