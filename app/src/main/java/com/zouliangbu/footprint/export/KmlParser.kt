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
 * KML 2.2 导入解析（两步路 / Google Earth 都能导出这种格式）。
 *
 * 支持两种轨迹写法：
 *  - `gx:Track`：`<when>` 与 `<gx:coord>` 交替出现（两步路 9.x 的导出格式，坐标带时间）；
 *  - `LineString/coordinates`：一串 "lng,lat[,alt]"（无时间，Google Earth 常见）。
 *
 * 坐标约定：KML 规范里是 **lng lat alt 顺序**（和 GPX 的 lat/lng 属性相反，别搞混），
 * 且为 **WGS-84**，入库前统一 `CoordTransform.wgs84ToGcj02()` 转 GCJ-02。
 *
 * 解析结果直接复用 [GpxParser.ParsedTrack]：后续的转 payload / 落库逻辑与 GPX 共用
 * [GpxImport.toPayload]，不写第二份。
 *
 * 与 [GpxParser] 同样的三个硬约束：
 *  1. 用 `javax.xml` SAX（android.util.Xml 在 JVM 单测是 stub）；
 *  2. 关闭 DTD / 外部实体（XXE 防护）；
 *  3. XML 格式错误抛 [IllegalArgumentException] 且带行号。
 */
object KmlParser {

    /**
     * 解析 KML，返回已转成 GCJ-02 的点。
     *
     * @throws IllegalArgumentException XML 格式错误（带行号），或流读取失败。
     */
    fun parse(input: InputStream): GpxParser.ParsedTrack {
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
            GpxParser.ParsedTrack(handler.trackName, handler.points.toList())
        } catch (e: SAXParseException) {
            throw IllegalArgumentException("KML 解析失败：XML 格式错误（第 ${e.lineNumber} 行）", e)
        }
    }

    private class Handler : DefaultHandler() {

        val points = mutableListOf<GpxParser.ParsedPoint>()
        var trackName: String? = null

        /** true = 已拿到名字（Document 的 name 优先，后面的 Placemark name 不再覆盖）。 */
        private var nameLocked = false

        // gx:Track 内的 when/coord 收集：按出现顺序配对（when[i] ↔ coord[i]）。
        private var trackDepth = 0
        private val pendingTimes = mutableListOf<Long?>()
        private val pendingCoords = mutableListOf<ParsedCoord?>()

        // gx:coord 文本收集（文本可能被 SAX 分段回调，必须累积）
        private var captureCoord = false
        private val coordText = StringBuilder()

        // LineString/coordinates 收集
        private var inCoordinates = false
        private val lineText = StringBuilder()

        /** 当前正在收集文本的标签：when / name（coord/coordinates 单独处理）。 */
        private var captureWhen = false
        private val whenText = StringBuilder()
        private var captureName = false
        private val nameText = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            val tag = tagOf(localName, qName)
            when (tag) {
                "track" -> trackDepth++
                "when" -> if (trackDepth > 0) {
                    captureWhen = true
                    whenText.setLength(0)
                }
                "coord" -> if (trackDepth > 0) {
                    captureCoord = true
                    coordText.setLength(0)
                }
                "linestring" -> Unit
                "coordinates" -> {
                    inCoordinates = true
                    lineText.setLength(0)
                }
                "name" -> if (!nameLocked) {
                    captureName = true
                    nameText.setLength(0)
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (captureWhen) whenText.append(ch, start, length)
            if (captureName) nameText.append(ch, start, length)
            if (captureCoord) coordText.append(ch, start, length)
            if (inCoordinates) lineText.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val tag = tagOf(localName, qName)
            when (tag) {
                "when" -> {
                    if (trackDepth > 0) pendingTimes.add(parseTime(whenText.toString().trim()))
                    captureWhen = false
                }
                "coord" -> {
                    if (trackDepth > 0) {
                        pendingCoords.add(parseCoord(coordText.toString()))
                        captureCoord = false
                    }
                }
                "track" -> {
                    trackDepth--
                    flushTrack()
                }
                "coordinates" -> {
                    flushLineString(lineText.toString())
                    inCoordinates = false
                }
                "name" -> {
                    val n = nameText.toString().trim()
                    if (!nameLocked && n.isNotEmpty()) {
                        trackName = n
                        nameLocked = true
                    }
                    captureName = false
                }
            }
        }

        /** gx:Track 结束：when[i] ↔ coord[i] 按下标配对，缺时间的点记 null。 */
        private fun flushTrack() {
            pendingCoords.forEachIndexed { i, c ->
                if (c != null) {
                    val time = pendingTimes.getOrNull(i)
                    points += toPoint(c, time)
                }
            }
            pendingTimes.clear()
            pendingCoords.clear()
        }

        private fun flushLineString(raw: String) {
            raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { triple ->
                parseCoord(triple)?.let { points += toPoint(it, null) }
            }
        }

        private fun toPoint(c: ParsedCoord, timeMs: Long?): GpxParser.ParsedPoint {
            // WGS-84 → GCJ-02：DB 与地图全链路都是 GCJ-02。
            val gcj = CoordTransform.wgs84ToGcj02(c.lat, c.lng)
            return GpxParser.ParsedPoint(gcj.lat, gcj.lng, c.altitude, timeMs)
        }

        /** "lng lat alt"（gx:coord）或 "lng,lat[,alt]"（coordinates）→ 有效坐标或 null。 */
        private fun parseCoord(text: String): ParsedCoord? {
            val parts = text.trim()
                .split(Regex("[\\s,]+"))
                .filter { it.isNotBlank() }
            if (parts.size < 2) return null
            val lng = parts[0].toDoubleOrNull() ?: return null
            val lat = parts[1].toDoubleOrNull() ?: return null
            if (!isValid(lat, lng)) return null
            val alt = parts.getOrNull(2)?.toDoubleOrNull()
            return ParsedCoord(lat, lng, alt)
        }

        private fun parseTime(raw: String): Long? {
            if (raw.isEmpty()) return null
            return try {
                OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            }
        }

        private data class ParsedCoord(val lat: Double, val lng: Double, val altitude: Double?)

        private fun isValid(lat: Double, lng: Double): Boolean =
            lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0

        /** 命名空间感知时 localName 有值；无命名空间的 KML（很少见）只能靠 qName（gx: 前缀剥离）。 */
        private fun tagOf(localName: String?, qName: String?): String {
            (localName ?: "").takeIf { it.isNotEmpty() }?.let { return it.lowercase(Locale.US) }
            return (qName ?: "").substringAfter(':').lowercase(Locale.US)
        }
    }
}
