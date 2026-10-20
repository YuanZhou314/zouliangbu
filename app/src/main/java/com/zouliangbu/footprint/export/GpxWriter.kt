package com.zouliangbu.footprint.export

import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSession
import com.zouliangbu.footprint.util.CoordTransform
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * GPX 1.1 导出（手写 XML，不引三方库——GDAL/OSR 那套是体积陷阱，见 ROADMAP §6）。
 *
 * **最重要的一条**：数据库里的点是 **GCJ-02**，GPX 的行业惯例是 **WGS-84**，
 * 所以每个点写出前必须 `CoordTransform.gcj02ToWgs84()`。不转换的结果是在 OsmAnd /
 * 两步路 / Google Earth 里整体偏移 300–800 m，看上去"路都跑到山沟里去了"。
 *
 * 输出规则：
 *   - 坐标保留 6 位小数（约 0.11 m，够用且不啰嗦）
 *   - 时间用 UTC + `Z` 结尾（GPX 规范要求 ISO-8601；本地时区会让别的软件算错配速）
 *   - 海拔为 0 或非有限值时不输出 `<ele>`（高德在没拿到海拔时给 0，写进去会变成"海平面"）
 *   - 非法坐标（NaN / 越界）的点直接跳过，不污染整条轨迹
 */
object GpxWriter {

    private const val CREATOR = "走两步 zouliangbu"
    private const val GPX_NS = "http://www.topografix.com/GPX/1/1"

    private val gpxTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).withZone(ZoneOffset.UTC)

    private val fileNameTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.US).withZone(ZoneId.systemDefault())

    private val displayTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US).withZone(ZoneId.systemDefault())

    /** 生成本次会话的 GPX 文本。 */
    fun build(session: TrackSession, points: List<TrackPoint>): String {
        val name = defaultName(session)
        val sb = StringBuilder(1024 + points.size * 128)

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"").append(escape(CREATOR)).append('"')
            .append(" xmlns=\"").append(GPX_NS).append('"')
            .append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
            .append(" xsi:schemaLocation=\"").append(GPX_NS).append(' ').append(GPX_NS)
            .append("/gpx.xsd\">\n")

        sb.append("  <metadata>\n")
        sb.append("    <name>").append(escape(name)).append("</name>\n")
        sb.append("    <time>").append(gpxTime.format(Instant.ofEpochMilli(session.startTime)))
            .append("</time>\n")
        sb.append("  </metadata>\n")

        sb.append("  <trk>\n")
        sb.append("    <name>").append(escape(name)).append("</name>\n")
        sb.append("    <type>hiking</type>\n")
        sb.append("    <trkseg>\n")

        for (p in points) {
            if (!isValid(p.lat, p.lng)) continue
            // GCJ-02 → WGS-84：不转就整体偏移几百米。
            val wgs = CoordTransform.gcj02ToWgs84(p.lat, p.lng)

            sb.append("      <trkpt lat=\"").append(fmt(wgs.lat))
                .append("\" lon=\"").append(fmt(wgs.lng)).append("\">\n")
            if (p.altitude.isFinite() && p.altitude != 0.0) {
                sb.append("        <ele>").append(String.format(Locale.US, "%.1f", p.altitude))
                    .append("</ele>\n")
            }
            sb.append("        <time>").append(gpxTime.format(Instant.ofEpochMilli(p.timestamp)))
                .append("</time>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    /** 直接写进输出流（UTF-8）。调用方负责关流。 */
    fun write(session: TrackSession, points: List<TrackPoint>, out: OutputStream) {
        out.write(build(session, points).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** 分享用的文件名，如 `走两步_20261020_1735.gpx`。 */
    fun suggestFileName(session: TrackSession): String =
        "走两步_" + fileNameTime.format(Instant.ofEpochMilli(session.startTime)) + ".gpx"

    /** 轨迹默认名（TrackSession 目前没有用户命名字段，用开始时间兜底）。 */
    fun defaultName(session: TrackSession): String =
        "走两步 " + displayTime.format(Instant.ofEpochMilli(session.startTime))

    private fun isValid(lat: Double, lng: Double): Boolean =
        lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0

    private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)

    /** XML 特殊字符转义（轨迹名以后可能带用户输入，别让 `<` 把文档撑破）。 */
    private fun escape(s: String): String = buildString(s.length + 8) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
}
