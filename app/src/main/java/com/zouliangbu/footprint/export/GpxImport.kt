package com.zouliangbu.footprint.export

import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.util.Distance

/**
 * GPX 解析结果 → 待入库的 TrackPoint 列表（**纯计算，不碰任何 Android API**）。
 *
 * 单独拆出来的原因：整条导入链路里唯一有判断逻辑、也唯一会算错的就是这段
 * （时间兜底、海拔兜底、距离累计），而它完全可以在 JVM 单测里跑。
 * ContentResolver / Uri 那层没法测，就别让它和这段混在一起。
 */
object GpxImport {

    data class Payload(
        val name: String?,
        val points: List<TrackPoint>,
        val startTime: Long,
        val endTime: Long,
        val distanceMeters: Float
    )

    /**
     * @param fallbackNow 轨迹里一个 `<time>` 都没有时用的时间基准。
     *        不传就用 `System.currentTimeMillis()` 会让单测不可重复，所以由调用方注入。
     * @return 点集为空时返回 null（调用方据此提示"文件里没有轨迹点"）。
     */
    fun toPayload(track: GpxParser.ParsedTrack, fallbackNow: Long): Payload? {
        if (track.points.isEmpty()) return null

        val times = track.points.mapNotNull { it.timeMs }
        val earliest = times.minOrNull() ?: fallbackNow
        val latest = times.maxOrNull() ?: fallbackNow

        val points = track.points.mapIndexed { index, p ->
            TrackPoint(
                sessionId = 0, // 由 TrackRepository.importTrack 回填真实 id
                lat = p.lat,
                lng = p.lng,
                // 没有 <ele> 记 0 而不是 NaN：SQLite 对 NaN 的比较行为不可控，
                // 排序/统计会出怪结果。UI 侧把 0 当"无海拔数据"。
                altitude = p.altitude ?: 0.0,
                // 时间全缺时按 1 秒间隔编造，保证列表能排序、详情能算时长。
                timestamp = p.timeMs ?: (earliest + index * 1_000L),
                // 导入点没有精度信息；0 表示"未知"，不参与记录流程的精度过滤。
                accuracy = 0f
            )
        }

        val distance = points.zipWithNext().sumOf { (a, b) ->
            Distance.haversineMeters(a.lat, a.lng, b.lat, b.lng)
        }.toFloat()

        return Payload(
            name = track.name?.takeIf { it.isNotBlank() },
            points = points,
            startTime = earliest,
            endTime = latest,
            distanceMeters = distance
        )
    }
}
