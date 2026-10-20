package com.zouliangbu.footprint.export

import com.zouliangbu.footprint.util.CoordTransform
import org.xml.sax.Attributes
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

/**
 * GPX 1.1 导入解析。
 *
 * 为什么用 `javax.xml.parsers` 的 SAX 而不是 `android.util.Xml` 的 XmlPullParser：
 * 后者是 Android 框架类，**在 JVM 单元测试里是 stub，一调就抛异常**。用 SAX 则同一份代码
 * 在真机和 `testDebugUnitTest` 里都能跑，GPX 这种"格式一错就全错"的解析必须能回归测试。
 *
 * 坐标约定：GPX 里是 **WGS-84**，入库前统一 `CoordTransform.wgs84ToGcj02()` 转成 GCJ-02，
 * 否则导入的轨迹在高德地图上会整体偏移几百米。
 *
 * 只解析 `<trk>/<trkseg>/<trkpt>`（徒步轨迹的标准结构）；`<rte>/<rtept>`（路线）与
 * `<wpt>`（航点）暂不支持——将来要支持，在这里加分支即可，别改调用方。
 */
object GpxParser {

    data class ParsedPoint(
        val lat: Double,
        val lng: Double,
        val altitude: Double?,
        val timeMs: Long?
    )

    data class ParsedTrack(
        val name: String?,
        val points: List<ParsedPoint>
    )

    /**
     * 解析 GPX，返回已转成 GCJ-02 的点。
     *
     * @throws IllegalArgumentException XML 格式错误（带行号），或流读取失败。
     */
    fun parse(input: InputStream): ParsedTrack {
        val handler = Handler()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
            // 外部文件不可信：关掉 DTD / 外部实体，避免 XXE。
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }

        return try {
            factory.newSAXParser().parse(input, handler)
            ParsedTrack(handler.trackName, handler.points.toList())
        } catch (e: SAXParseException) {
            throw IllegalArgumentException("GPX 解析失败：XML 格式错误（第 ${e.lineNumber} 行）", e)
        }
    }

    private class Handler : DefaultHandler() {

        val points = mutableListOf<ParsedPoint>()
        var trackName: String? = null

        private var inTrkpt = false
        private var curLat = Double.NaN
        private var curLng = Double.NaN
        private var curEle: Double? = null
        private var curTime: Long? = null

        /** 当前正在收集文本的标签名（ele / time / name），其他标签的文本一律忽略。 */
        private var capture: String? = null
        private val text = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            val tag = tagOf(localName, qName)
            when (tag) {
                "trkpt" -> {
                    inTrkpt = true
                    curLat = attributes?.getValue("lat")?.toDoubleOrNull() ?: Double.NaN
                    curLng = attributes?.getValue("lon")?.toDoubleOrNull() ?: Double.NaN
                    curEle = null
                    curTime = null
                }
                "ele", "time" -> {
                    capture = tag
                    text.setLength(0)
                }
                "name" -> if (trackName.isNullOrBlank()) {
                    capture = "name"
                    text.setLength(0)
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capture != null) text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val tag = tagOf(localName, qName)
            when (tag) {
                "ele" -> if (inTrkpt) curEle = text.toString().trim().toDoubleOrNull()
                "time" -> if (inTrkpt) curTime = parseTime(text.toString().trim())
                "name" -> {
                    val n = text.toString().trim()
                    if (trackName.isNullOrBlank() && n.isNotEmpty()) trackName = n
                }
                "trkpt" -> {
                    if (isValid(curLat, curLng)) {
                        // WGS-84 → GCJ-02：DB 与地图全链路都是 GCJ-02。
                        val gcj = CoordTransform.wgs84ToGcj02(curLat, curLng)
                        points += ParsedPoint(gcj.lat, gcj.lng, curEle, curTime)
                    }
                    inTrkpt = false
                }
            }
            if (capture == tag) capture = null
        }

        /** 命名空间感知时 localName 有值；无命名空间的 GPX（很常见）只能靠 qName。 */
        private fun tagOf(localName: String?, qName: String?): String =
            (localName ?: "").ifEmpty { qName ?: "" }.lowercase(Locale.US)

        private fun parseTime(raw: String): Long? {
            if (raw.isEmpty()) return null
            return try {
                OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            }
        }

        private fun isValid(lat: Double, lng: Double): Boolean =
            lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
    }
}
